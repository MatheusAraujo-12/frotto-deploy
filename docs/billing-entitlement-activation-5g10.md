# 5G.10 — Correção de regressão: ativação de plano não depende de BillingInvoice

## Contexto

A introdução da cobertura financeira fail-closed (5G.4/5G.9) tornou
`SubscriptionFinancialCoverageService` requisito obrigatório para que uma
`Subscription` `PAYMENT_PROVIDER` conceda o plano contratado. Isso incluiu,
sem intenção, a ativação **inicial** de uma assinatura: uma preapproval que o
Mercado Pago já confirmou como `authorized` (refletida localmente como
`Subscription.status=ACTIVE`) deixou de conceder o plano enquanto nenhum
`BillingInvoice` fosse ingerido — o que só acontece via webhook/reconciliação
de cobrança, um evento **separado e posterior** à autorização da assinatura
em si.

Evidência real de staging: `subscription id=4` e `id=5`,
`source=PAYMENT_PROVIDER`, `status=ACTIVE`, `external_provider=MERCADO_PAGO`,
`current_period_end` no futuro, e `SELECT COUNT(*) FROM billing_invoice` = 0.
`GET /api/billing/me` reportava `FREE` para esses usuários, quando o produto
sempre operou com o fluxo: `authorized → ACTIVE → plano liberado
imediatamente`.

## Decisão final de produto

> Uma preapproval `PAYMENT_PROVIDER` confirmada autoritativamente como
> `ACTIVE`/`authorized` concede o entitlement do período corrente.
> `BillingInvoice`/`PaymentAttempt` são usados para comprovar e reconciliar
> as cobranças recorrentes subsequentes, inadimplência e histórico
> financeiro, e não constituem pré-requisito para a ativação inicial do
> plano.

Isso substitui, exclusivamente para a questão "uma `Subscription`
`PAYMENT_PROVIDER` `ACTIVE` sem nenhum `BillingInvoice` concede entitlement?",
o texto de docs/billing-recurring-contract-5g1.md seção 19
("`Subscription.status=ACTIVE` sozinho não satisfaz providerEligible") e a
premissa equivalente registrada em docs/billing-production-closure-5g9.md.
Todo o resto dessas duas seções permanece válido: correlação de pagamento,
matching de valor/moeda, grace de 72h, refund/chargeback, idempotência,
reconciliação e o comportamento fail-closed de `SubscriptionFinancialCoverage
Service#evaluate` quando **existe** algum `BillingInvoice` incompleto ou
inconclusivo continuam exatamente como documentados — nada disso foi
enfraquecido (ver seção "O que não muda" abaixo).

## Onde foi corrigido

`SubscriptionService#getCurrentSubscription` (não
`SubscriptionFinancialCoverageService`, que permanece intocado e continua
sendo a fonte de verdade para `financiallyCovered` em `/api/billing/payment-
state`). Novo método privado `isProviderSubscriptionEntitled(subscription,
now)`:

1. Se `SubscriptionFinancialCoverageService#evaluate` já considera a
   competência coberta (`covered()=true`) — mantém exatamente como estava
   (uma invoice paga continua sendo o caminho normal).
2. Caso contrário, só há um único bypass estrito: quando o motivo da não-
   cobertura é `Reason.NO_INVOICE` (nenhum `BillingInvoice` existe para essa
   subscription — não "existe mas está incompleto/pendente/revertido").
   Nesse caso:
   - `status=ACTIVE` → concede o plano contratado imediatamente.
   - `status=CANCELED` ou `status=PAUSED` → concede apenas se
     `currentPeriodEnd` (o valor já persistido a partir do
     `next_payment_date` do Mercado Pago, nunca fabricado) ainda estiver no
     futuro; sem essa data, permanece fail-closed.
   - Qualquer outro status (`PAST_DUE`, `EXPIRED`) não é beneficiado pelo
     bypass — a regra de grace/inadimplência existente continua exigindo uma
     competência paga anterior contígua, inalterada.
3. Para qualquer motivo de não-cobertura diferente de `NO_INVOICE` (por
   exemplo `REVERSED_OR_CANCELED`, `FINANCIAL_CONFLICT`,
   `NO_APPROVED_PAYMENT`, `INCOMPLETE_PERIOD`), o veredito de
   `SubscriptionFinancialCoverageService` continua soberano e nega a
   cobertura exatamente como antes — a origem autoritativa financeira nunca
   é enfraquecida quando ela de fato existe.

Preço, plano e vehicle count continuam vindo do snapshot já persistido em
`Subscription`/`BillingCheckout` (`contractedPrice`, `contractedVehicleCount`,
`currentPeriodStart/End`) — nunca de query string, payload do cliente ou
dado enviado pelo frontend.

## Por que `ACTIVE` continua sendo confiável (não foi enfraquecida a origem autoritativa)

`Subscription.status=ACTIVE` de uma linha `PAYMENT_PROVIDER` só é escrito em
um único lugar do código: `MercadoPagoWebhookProcessor#reconcile`, alcançado
por exatamente dois caminhos, ambos autoritativos:

- **Webhook real**: `MercadoPagoWebhookResource` exige assinatura HMAC válida
  (`MercadoPagoWebhookSignatureValidator`) antes de qualquer chamada ao
  processor; o processor então faz `GET /preapproval` no Mercado Pago antes
  de gravar qualquer status — nunca confia no corpo do webhook por si só.
- **Reconciliação backend** (`BillingAutoReconciliationScheduler`,
  `BillingReconciliationRunner`): chamam o mesmo `processor.process(...)`,
  logo passam pelo mesmo `GET /preapproval` autoritativo.

Não existe endpoint público que aceite um status de assinatura vindo do
cliente: `BillingResource` expõe apenas `/checkout`, `/cancel`, `/me`,
`/payment-state`, `/price-preview`, `/plans` — nenhum aceita `status` do
corpo/query. O redirect do browser (`?status=approved`,
`?collection_status=approved` etc.) nunca é lido pelo frontend para decidir
plano — `MyPlanPage` sempre busca `/api/billing/me` do backend
(`ignora query string ... e usa somente o backend`, já coberto por teste).
Confirmado por leitura de código nesta correção; nenhuma mudança foi
necessária aqui.

## Cancelamento permanece independente do entitlement

`canCancelRemoteContract != hasPaidEntitlement` (5G.9 seção B) não foi
tocado: `BillingPaymentStateService#getState`/`RecurringSubscriptionGuardService`
continuam decidindo `canCancel` a partir do status bruto da `Subscription`
(`SubscriptionCancellationSteps.CANCELLABLE_STATUSES`), nunca do resultado de
`SubscriptionFinancialCoverageService`. Uma assinatura `ACTIVE` sem invoice
ainda pode ser cancelada; um `ADMIN_GRANT` efetivo nunca esconde essa opção
(o botão é decidido a partir de `/api/billing/payment-state`, não de
`/api/billing/me`); IDs do provider continuam nunca expostos.

## Comportamento após cancelamento (com período vigente)

Se o Mercado Pago confirma `cancelled` mas `currentPeriodEnd` (já persistido
a partir do contrato autorizado) ainda está no futuro, o plano permanece
disponível até essa data — sem nova cobrança, sem reativação — e só então
volta para `FREE` ou outra fonte válida (`ADMIN_GRANT`/`GRANDFATHERED`),
seguindo a precedência existente. Isso vale tanto para o caso já existente
de cancelamento diferido (`status` permanece `ACTIVE`/`PAST_DUE` até o fim do
período — `MercadoPagoWebhookProcessor#reconcile`'s `deferToPeriodEnd`)
quanto para o caso de `status=CANCELED` sem nenhum `BillingInvoice` ainda
ingerido, agora também coberto por este ajuste. Ausência de
`currentPeriodEnd` numa assinatura cancelada permanece fail-closed — nenhuma
data é inventada.

## PAUSED

Uma assinatura pausada nunca é reativada automaticamente pela leitura de
entitlement (`status` permanece `PAUSED`, nunca reescrito para `ACTIVE`).
Se já existir um período contratado ainda vigente (`currentPeriodEnd` no
futuro, o mesmo valor autoritativo do Mercado Pago), o acesso já pago
permanece disponível até essa data — pausar cobranças futuras não revoga
retroativamente um período já contratado. Sem `currentPeriodEnd` (ou já no
passado), fail-closed, igual ao `CANCELED`.

## O que não muda

- `SubscriptionFinancialCoverageService` (código e comportamento) — intocado.
  Continua sendo a fonte de `financiallyCovered` em `/api/billing/payment-
  state`, continua decidindo grace de `PAST_DUE`, continua recusando
  cobertura diante de refund/chargeback/conflito financeiro/período
  incompleto sempre que **existe** algum `BillingInvoice`.
- `BillingInvoice`, `PaymentAttempt`, `RecurringBillingReconciliationService`,
  `authorized_payments/search`, ingestão de webhook `payment`, recuperação de
  webhook perdido — todos continuam existindo e sendo necessários para
  renovações, inadimplência, retries, histórico e refund.
- A investigação de `periodStart` nulo (5G.9 seção E/7) — comportamento
  fail-closed mantido; não tem relação com esta correção, que trata da
  ausência total de invoice, não de uma invoice existente porém incompleta.
- Grace de 72h para `PAST_DUE`, precedência ADMIN_GRANT > PAYMENT_PROVIDER >
  GRANDFATHERED > FREE, HMAC, idempotência, refund parcial (5G.6) — nenhum
  alterado.

## Testes

- `FinancialEntitlementTest` (real `SubscriptionFinancialCoverageService`,
  apenas persistência mockada): casos A-H do pedido de correção, incluindo
  ACTIVE/CANCELED/PAUSED/EXPIRED/PAST_DUE sem nenhum invoice, com e sem
  `currentPeriodEnd` futuro, e confirmação de que um refund/chargeback
  registrado ainda revoga o entitlement mesmo com `status=ACTIVE`.
- `SubscriptionServiceTest`: testes unitários diretos do novo branch
  (`Reason.NO_INVOICE` vs. qualquer outro motivo), isolados do resto da
  avaliação financeira.
- `BillingEndToEndLocalTest#freeToBronzeCheckoutWebhookAndReadModelsUseOnlyFakeProvider`:
  reprodução ponta a ponta do cenário real (checkout → webhook `authorized`
  → `GET /api/billing/me`) atualizada para o resultado correto (`BRONZE`/
  `ACTIVE`/`PAYMENT_PROVIDER`, antes incorretamente `FREE`).
