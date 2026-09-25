# 5G.12 / 5G.12.1 — Upgrade/Downgrade de plano

Permite mudar de plano pago sem criar uma nova assinatura Mercado Pago: a MESMA preapproval
(`externalSubscriptionId`) tem apenas `auto_recurring.transaction_amount` atualizado
(`MercadoPagoClient#updatePreapprovalAmount`). Nunca cria um novo `preapproval`/checkout recorrente
para upgrade ou downgrade entre planos pagos.

## Upgrade (5G.12.1 — com cobrança proporcional)

O usuário **não** recebe o plano superior de graça pelo restante do ciclo já pago. Constatado em
staging: o `PUT` de `transaction_amount` na preapproval **não** gera cobrança imediata — por isso a
diferença proporcional é uma cobrança avulsa separada (Checkout Pro), e o plano só muda depois dela.

```
priceDifference = targetPrice - currentPrice          (currentPrice = contractedPrice pago no ciclo)
remainingRatio  = (cycleEnd - now) / (cycleEnd - cycleStart)
upgradeCharge   = priceDifference * remainingRatio     (BigDecimal, 1 arredondamento HALF_UP, 2 casas)
```

- Preço destino sempre de `PricingService#calculatePriceForPlan` com a frota contada no backend;
  nenhum valor vem do frontend. Diferença ≤ 0 → cobrança 0,00 (nunca negativa/crédito).
- Ciclo (`PlanUpgradeProration#resolveCycle`), nunca "30 dias fixos":
  1. competência **PAID** de `SubscriptionFinancialCoverageService` (período da `BillingInvoice`);
  2. só sem nenhuma invoice (`NO_INVOICE`): `next_payment_date` do GET autoritativo da preapproval
     como fim, e início = fim − `frequency` meses **no offset do próprio provider** (mesma convenção
     de `MercadoPagoInvoiceTemporalEnricher`).
  Qualquer outro estado (GRACE, PAST_DUE, conflito, frequência ≠ `months`, `now` fora do ciclo…)
  → `409 BILLING_PLAN_CHANGE_PERIOD_UNCONFIRMED`, sem estimar.
- A recorrência precisa estar `authorized` e cobrando exatamente `contractedPrice` (ou o valor do
  downgrade agendado); caso contrário, fail closed.

### Fluxo

```
POST /change-plan (UPGRADE)
  -> quote (GET /preapproval autoritativo + PricingService + ciclo)
  -> SubscriptionPlanUpgrade AWAITING_PAYMENT (lock do usuário)        nada concedido
  -> POST /checkout/preferences (valor proporcional, external_reference própria,
     expira em 30 min, binary_mode, sem boleto/ATM, 1 parcela)
  <- status UPGRADE_PAYMENT_REQUIRED + checkoutUrl
usuário paga no Mercado Pago
  -> webhook "payment" / GET /plan-upgrade (polling do retorno) / scheduler a cada 2 min
  -> GET /v1/payments/search?external_reference=… + GET /v1/payments/{id} autoritativo
  -> approved, valor e moeda EXATOS -> APPLYING
  -> PUT auto_recurring.transaction_amount = targetPrice (mesma preapproval)
  -> GET /preapproval confirma o novo valor
  -> plano / contracted_price / contracted_vehicle_count trocados, downgrade agendado limpo -> APPLIED
```

O retorno do navegador (query string do `back_url`) **nunca** é fonte da verdade — só dispara a
reconciliação server-side. O payload do webhook também não: sempre GET autoritativo.

### State machine (`subscription_plan_upgrade.status`)

| De | Para | Quando |
|---|---|---|
| AWAITING_PAYMENT | APPLYING | pagamento aprovado com valor/moeda exatos (ou cobrança 0,00) |
| AWAITING_PAYMENT | EXPIRED | janela do checkout + 15 min fechada, busca completa sem pagamento aprovado/pendente |
| AWAITING_PAYMENT | FAILED | criação da preferência rejeitada de forma definitiva (nada cobrado) |
| AWAITING_PAYMENT | REQUIRES_REVIEW | pagamento aprovado com valor divergente, ou aprovado após o fim do ciclo |
| APPLYING | APPLIED | recorrência confirmada no novo valor, plano aplicado |
| APPLYING | REQUIRES_REVIEW | assinatura deixou de ser elegível (cancelamento, status, plano mudou, ciclo acabou) ou 5 falhas seguidas ao atualizar a recorrência |
| EXPIRED/FAILED | REQUIRES_REVIEW | pagamento aprovado chegou depois (nunca descartado em silêncio) |

`REQUIRES_REVIEW` = pago mas não aplicável com segurança: **nunca** concede o plano e **nunca**
some; exige operador (estorno/ajuste). Logado com `upgradeId`/`subscriptionId` apenas.

### Falhas e idempotência

- Pagamento recusado/pendente/expirado: plano, `contracted_price`, recorrência e downgrade
  agendado permanecem exatamente como estavam.
- Duplo clique / retry / reload: sob o lock do usuário, a tentativa aberta para o MESMO alvo é
  reutilizada (mesmo link); uma criação de checkout ainda em andamento faz a segunda requisição
  receber `409 BILLING_PLAN_UPGRADE_IN_PROGRESS` — nunca dois links para a mesma tentativa.
  Criação ambígua (timeout/5xx) é refeita com a mesma `external_reference`.
- Webhook duplicado / reconciliation repetida: toda transição trava a linha
  (`PESSIMISTIC_WRITE`) e só avança a partir do estado esperado; o PUT da recorrência usa uma
  idempotency key por tentativa (`plan-upgrade-<id>-amount-<n>`).
- Pagamento aprovado mas PUT/GET da recorrência falhou: continua `APPLYING` e é refeito pelo
  polling/scheduler; nunca responde sucesso sem o GET confirmar.

### Por que uma tabela nova

Uma `BillingInvoice` avulsa na assinatura quebraria a cobertura:
`SubscriptionFinancialCoverageService` falha fechado com QUALQUER invoice sem período, e invoices
representam competências recorrentes. E os campos `subscription.pending_*` já pertencem ao
downgrade agendado, que precisa sobreviver a um upgrade malsucedido. Migration:
`20260924000000_add_subscription_plan_upgrade.xml`.

## Downgrade

- **Não retira benefícios imediatamente.** O plano atual continua valendo até `currentPeriodEnd`.
- O valor da preapproval no Mercado Pago é atualizado desde já (para a próxima cobrança), mas o
  Frotto continua mostrando o plano atual até a data de efetivação.
- A UI mostra "Mudança agendada — Seu plano mudará para X em DD/MM/AAAA".
- A mudança definitiva só ocorre quando o mecanismo canônico de evidência financeira existente
  (`SubscriptionFinancialCoverageService`) confirma uma renovação **PAGA** com `coverageStart` a
  partir da data de efetivação (`SubscriptionPlanChangeSteps#effectuateIfDue`). O relógio sozinho
  nunca aplica a mudança. Inadimplência, chargeback, refund ou conflito financeiro simplesmente
  mantêm a mudança pendente — nunca aplicam nem descartam o downgrade.
- 5G.12.1: um downgrade agendado **não bloqueia upgrade**. Um segundo downgrade continua recusado
  (`409 BILLING_PLAN_CHANGE_ALREADY_PENDING` — desfaça o atual primeiro).

## Desfazer downgrade (5G.12.1)

`POST /api/billing/change-plan/undo-downgrade` — sem cobrança:

1. lock + validação (existe downgrade agendado, nenhum upgrade aberto);
2. `PUT` da recorrência de volta para o `contractedPrice` do plano atual;
3. GET autoritativo confirma o valor;
4. só então limpa `pending_plan_id`, `pending_contracted_price`,
   `pending_contracted_vehicle_count`, `plan_change_effective_at`, `plan_change_requested_at`
   (e só se ainda for o MESMO agendamento).

Rejeição definitiva, GET ainda no valor do downgrade ou GET indeterminado → pending mantido,
`409 BILLING_DOWNGRADE_UNDO_REJECTED`.

## Provider é autoritativo

- Direção (upgrade/downgrade) é **estrutural**: comparação de `Plan.minVehicles`, nunca de preço.
- Toda alteração de valor é confirmada por um `GET /preapproval/{id}` autoritativo depois do `PUT`.
  - GET confirma o **novo** valor/moeda → aplica.
  - GET confirma o valor **antigo** → não aplica.
  - GET não determina (erro/indeterminado) → *fail closed*, nunca aplica, nunca adivinha rollback.

## Estados bloqueados

Mudança de plano só para `PAYMENT_PROVIDER` + `ACTIVE`. Bloqueada quando: `PAST_DUE`, `PAUSED`,
`EXPIRED`, conflito financeiro, `cancelAtPeriodEnd=true`/já cancelado, referência do provider
ausente, mais de uma assinatura `PAYMENT_PROVIDER` cobrável, ou (5G.12.1) um upgrade com pagamento
aguardando/sendo aplicado (`409 BILLING_PLAN_UPGRADE_IN_PROGRESS`). `ADMIN_GRANT`/`GRANDFATHERED`
nunca chamam o Mercado Pago.

## Capacidade de frota durante downgrade agendado

Enquanto existir `pendingPlan`, o limite efetivo para **novas** inclusões de veículo é o menor
entre o limite do plano atual e o do plano pendente (`EntitlementService#getSnapshot`).

## FREE

- **FREE → pago** pelo endpoint: rejeitado (400), usar o checkout normal.
- **pago → FREE**: reutiliza o `SubscriptionCancellationService` já homologado.

## Endpoints

- `POST /api/billing/change-plan` — body `{"targetPlanCode": "SILVER"}`. Nunca recebe preço ou
  userId. Resposta `PlanChangeResultDTO`: `status` (`UPGRADE_PAYMENT_REQUIRED`,
  `UPGRADE_PAYMENT_PENDING`, `UPGRADE_APPLIED`, `DOWNGRADE_SCHEDULED`, `DOWNGRADE_UNDONE`,
  `CANCELLATION_SCHEDULED`), `currentPlan`, `targetPlan`, `changeType`, `effectiveAt`,
  `chargeAmount`, `nextRenewalPrice`, `checkoutUrl` (só em `UPGRADE_PAYMENT_REQUIRED`),
  `contractedPrice`, `pending`.
- `GET /api/billing/change-plan/preview?targetPlanCode=…` — `chargeNow`, `newMonthlyPrice`,
  `cycleEnd` calculados no backend (informativo; o POST recalcula tudo).
- `POST /api/billing/change-plan/undo-downgrade`.
- `GET /api/billing/plan-upgrade` — última tentativa de upgrade do próprio usuário, reconciliada
  com o Mercado Pago antes de responder (`status`, planos, valores, `checkoutUrl` enquanto a
  janela estiver aberta, `paymentPending`, `paymentRejected`). Sem IDs do provider, referências
  ou chaves.
- `GET /api/billing/me` expõe `pendingPlanCode/Name/Price` e `planChangeEffectiveAt` quando há
  downgrade agendado; `/me` e `/payment-state` efetivam mudanças já vencidas antes de responder.

Todos sob `/api/**` autenticado; usuário sempre resolvido pelo SecurityContext.
