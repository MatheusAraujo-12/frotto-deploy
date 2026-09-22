# 5G.12 — Upgrade/Downgrade de plano

Permite mudar de plano pago sem criar uma nova assinatura Mercado Pago: a MESMA preapproval
(`externalSubscriptionId`) tem apenas `auto_recurring.transaction_amount` atualizado
(`MercadoPagoClient#updatePreapprovalAmount`). Nunca cria um novo `preapproval`/checkout para
upgrade ou downgrade entre planos pagos.

## Upgrade

- Aplica-se **imediatamente**: `Subscription.plan`/`contractedPrice`/`contractedVehicleCount`
  mudam assim que o provider confirma o novo valor.
- **Sem pró-rata** nesta primeira versão — decisão de produto intencional. O período atual não é
  cobrado proporcionalmente; o novo valor só é usado a partir da próxima renovação.
- `currentPeriodEnd` nunca é alterado artificialmente.

## Downgrade

- **Não retira benefícios imediatamente.** O plano atual continua valendo até `currentPeriodEnd`.
- O valor da preapproval no Mercado Pago é atualizado desde já (para a próxima cobrança), mas o
  Frotto continua mostrando o plano atual até a data de efetivação.
- A UI mostra "Mudança para X agendada para DD/MM/AAAA".
- A mudança definitiva só ocorre quando o mecanismo canônico de evidência financeira existente
  (`SubscriptionFinancialCoverageService`) confirma uma renovação **PAGA** com `coverageStart` a
  partir da data de efetivação (`SubscriptionPlanChangeSteps#effectuateIfDue`). O relógio sozinho
  nunca aplica a mudança. Inadimplência, chargeback, refund ou conflito financeiro simplesmente
  mantêm a mudança pendente — nunca aplicam nem descartam o downgrade.
- Nenhum novo conceito de pagamento foi criado: reutiliza `BillingInvoice`/`PaymentAttempt`/
  `SubscriptionFinancialCoverageService` tal como já usados para toda a entitlement.

## Ausência de pró-rata

Nem upgrade nem downgrade calculam valores proporcionais ao período já decorrido. Upgrade cobra o
novo valor cheio a partir da próxima renovação; downgrade só troca de plano/valor na renovação
seguinte à data de efetivação.

## Provider é autoritativo

- Preço nunca vem do frontend — sempre de `PricingService` (`calculatePriceForPlan`), com base na
  contagem real de veículos do usuário.
- Direção (upgrade/downgrade) é **estrutural**: comparação de `Plan.minVehicles`, nunca de preço
  (um plano progressivo pode ter base menor que um plano flat "inferior").
- Toda alteração de valor é sempre confirmada por um `GET /preapproval/{id}` autoritativo depois do
  `PUT`, mesmo quando o `PUT` parece ter tido sucesso ("não confie apenas na resposta do PUT").
  - GET confirma o **novo** valor/moeda → aplica.
  - GET confirma o valor **antigo** → não aplica (rollback do intent pendente).
  - GET não determina (erro/indeterminado) → *fail closed*, nunca aplica, nunca adivinha rollback.
- Idempotency key determinística (`change-plan-<externalSubscriptionId>-<targetPlanCode>`), mais
  uma chave própria para a segunda tentativa (spelling legada de cancelamento não foi tocada).

## Estados bloqueados

Mudança de plano normal só para `PAYMENT_PROVIDER` + `ACTIVE`. Bloqueada quando: `PAST_DUE`,
`PAUSED`, `EXPIRED`, conflito financeiro (`FINANCIAL_CONFLICT`/`REVERSED_OR_CANCELED`),
`cancelAtPeriodEnd=true`/já cancelado, referência do provider ausente, ou mais de uma assinatura
`PAYMENT_PROVIDER` ainda cobrável (nenhuma é escolhida arbitrariamente — 409 explícito).
`ADMIN_GRANT`/`GRANDFATHERED` nunca chamam o Mercado Pago (não possuem `PAYMENT_PROVIDER`
elegível).

## Pending plan

`Subscription` ganhou `pendingPlan` (FK), `pendingContractedPrice` (`DECIMAL(21,2)`),
`pendingContractedVehicleCount`, `planChangeEffectiveAt`, `planChangeRequestedAt`. No máximo uma
mudança agendada por assinatura — uma segunda tentativa enquanto existe uma pendente é recusada
(409 `BILLING_PLAN_CHANGE_ALREADY_PENDING`), nunca substituída silenciosamente.

Um upgrade também usa esses mesmos campos como "intent" transitório: são escritos ANTES da
chamada ao provider (na mesma transação curta que já validou e trava o usuário) e promovidos
imediatamente após a confirmação — isso fecha uma janela de corrida real em que dois pedidos de
upgrade simultâneos poderiam, de outra forma, chamar o provider duas vezes.

## Capacidade de frota durante downgrade agendado

Enquanto existir `pendingPlan`, o limite efetivo para **novas** inclusões de veículo é o menor
entre o limite do plano atual e o do plano pendente (`EntitlementService#getSnapshot`). Os demais
benefícios do plano atual continuam intactos até a efetivação. Reutiliza o mesmo ponto de
aplicação (`EntitlementSnapshot`/`CarResource`) já usado para o limite normal — nenhuma regra nova
no controller.

## FREE

`POST /api/billing/change-plan` não trata FREE como "só mais um plano":

- **FREE → pago** pelo endpoint: rejeitado (400), instrução para usar o checkout normal (não há
  contrato para alterar).
- **pago → FREE**: reutiliza o `SubscriptionCancellationService` já homologado — nunca chama o
  Mercado Pago diretamente pelo change-plan.

## Endpoint

`POST /api/billing/change-plan` — body `{"targetPlanCode": "SILVER"}`. Nunca recebe preço ou
userId. Resposta (`PlanChangeResultDTO`): `currentPlan`, `targetPlan`, `changeType`
(`UPGRADE`/`DOWNGRADE`), `effectiveAt`, `contractedPrice`, `pending`.

`GET /api/billing/me` ganhou (aditivamente) `pendingPlanCode`, `pendingPlanName`,
`pendingPlanPrice`, `planChangeEffectiveAt` — só presentes quando existe downgrade agendado.
`GET /api/billing/price-preview` aceita `planCode` opcional para precificar exatamente o plano
escolhido (útil quando o usuário escolhe um plano acima do recomendado).

Tanto `/me` quanto `/payment-state` efetivam qualquer mudança pendente já vencida
(`SubscriptionPlanChangeService#effectuateDueChangesForUser`) antes de responder.
