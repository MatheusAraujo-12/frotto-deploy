# 5G.3 — Ingestão financeira autoritativa

Implementação de ingestão e persistência financeira. Não concede entitlement, não
ativa plano e não altera cancelamento. Persistido não significa elegível para acesso.

## Eventos e fontes

- `payment`: GET `/v1/payments/{paymentId}` → busca
  `/authorized_payments/search?payment_id={paymentId}&limit=2&offset=0` → exatamente
  um resultado → GET individual `/authorized_payments/{id}` → GET
  `/preapproval/{preapprovalId}` → Subscription existente.
- `subscription_authorized_payment`: GET `/authorized_payments/{id}` → GET
  `/v1/payments/{payment.id}`, quando esse ID existir → GET do preapproval →
  Subscription existente. O status de pagamento embutido na cobrança não basta
  para comprovar aprovação. Sem paymentId, persiste uma observação provisória.
- `subscription_preapproval`: mantém a reconciliação contratual anterior, inclusive
  os guardas de cancelamento 5F. Não cria invoice ou tentativa financeira, nem
  produz PAID por estar authorized. A migração do entitlement legado para invoices
  pertence à etapa seguinte; esta entrega não redefine o comportamento contratual.

Os eventos financeiros agora seguem MercadoPagoFinancialIngestion, sem executar a
antiga reconcilePayment que escrevia ACTIVE/PAST_DUE na Subscription. Não se escreve
Subscription, plano, preço contratado ou datas de acesso nesses eventos. Não há
mudanças em EntitlementService, PricingService, fallback ou regras de cancelamento.

O body do webhook fornece somente o tipo e o identificador, conferido com o ID da
query validado pela assinatura. Status, valor e moeda do body não são usados.
Os DTOs preservam somente campos permitidos; não guardam cartão, payer ou JSON bruto.

Fontes oficiais consultadas em 2026-09-14:

- [Fatura individual](https://www.mercadopago.com.br/developers/pt/reference/online-payments/subscriptions/get-authorized-payment/get)
- [Busca de faturas](https://www.mercadopago.com.br/developers/pt/reference/online-payments/subscriptions/authorized-payment-search/get)
- [Pagamento](https://www.mercadopago.com.br/developers/pt/reference/online-payments/subscriptions/get-payment/get)
- [Preapproval](https://www.mercadopago.com.br/developers/pt/reference/online-payments/subscriptions/get-preapproval/get)

Não foram feitas chamadas autenticadas ao Mercado Pago. Os testes usam respostas simuladas.

## Correlação e inconsistências

A busca precisa ter total=1 e exatamente um elemento. O paymentId e o preapprovalId
precisam continuar consistentes no GET individual. Todos os IDs retornados são
conferidos; referências externas presentes e conflitantes também bloqueiam associação.
Subscription é resolvida por externalProvider=MERCADO_PAGO e externalSubscriptionId,
exigindo source=PAYMENT_PROVIDER. Nunca se escolhe por usuário, preço ou proximidade
de datas. Uma invoice existente precisa pertencer à mesma Subscription.

Busca vazia, Subscription ausente ou vínculo inconsistente não geram mutação financeira.
O processor registra a entrega como IGNORED. Busca múltipla, resposta inválida ou falha
HTTP propagam MercadoPagoException e retornam 503, sem registrar sucesso. Uma entrega
IGNORED já registrada não é reprocessada com o mesmo identificador de delivery;
reavaliação posterior exige outra entrega ou reconciliação futura. Não há heurística.

## Identidade e upsert

BillingInvoice usa `(provider, external_authorized_payment_id)`. O valor e a moeda
iniciais vêm da cobrança autoritativa; depois constituem o valor esperado preservado.
A chave de competência `(subscription_id, period_start, period_end)` da 5G.2 permanece.
Como o provider não fornece um período de serviço comprovado neste adapter, não se
associa uma invoice sem ID externo por datas presumidas. Competências distintas ficam
em linhas distintas, sem atualizar a Subscription ou outras invoices.

PaymentAttempt usa `(provider, external_payment_id)`. Antes desse ID existir, a chave
provisória estável é `(provider, external_attempt_id=authorized_payment:{chargeId})`.
Ela representa a observação pendente da cobrança, não uma enumeração inventada de
retries internos do provider. O primeiro payment enriquece essa mesma linha. Outros
paymentIds da cobrança criam tentativas distintas; replay de qualquer ID reutiliza
sua linha. Uma tentativa nunca é transferida entre invoices. Conflito de identidade
gera falha e rollback da transação.

## Valor, moeda e status

Valores são BigDecimal, não negativos e compatíveis com decimal(21,2). Comparação usa
compareTo: 15.90 e 15.9 são equivalentes. Moeda é normalizada com trim e uppercase
Locale.ROOT e precisa ter três letras. Não há moeda padrão ou valor zero inventado.

Sem valor/moeda válidos da cobrança não se cria invoice. Pagamento com valor/moeda
válidos mas divergentes é persistido como evidência auditável na tentativa; não marca
a invoice PAID. O valor esperado da invoice não é substituído para acomodar divergência.
Dados obrigatórios incompletos ou status desconhecidos não recebem estado fictício.
Logs indicam inconsistência sem valores, credenciais ou payloads.

MercadoPagoBillingStatusMapper conserva seu mapeamento: approved → APPROVED;
pending → PENDING; authorized/in_process/in_mediation → PROCESSING;
rejected → REJECTED; cancelled/canceled → CANCELED; refunded → REFUNDED;
charged_back → CHARGEDBACK. Na cobrança, processed representa PROCESSING, não PAID.

PAID requer uma tentativa APPROVED obtida do GET de payment, corretamente correlacionada,
com valor e moeda equivalentes à invoice e à cobrança observada. O estado é calculado
usando as tentativas da própria invoice: uma tentativa falha não apaga outra aprovada.
Refund/chargeback são registrados financeiramente, sem política de entitlement.
paidAt só recebe date_approved existente; não se usa horário local como aprovação.

## Proteção temporal

Invoice.providerUpdatedAt usa exclusivamente authorized_payment.last_modified.
PaymentAttempt.providerUpdatedAt usa exclusivamente payment.date_last_updated.
Não se compara o relógio da cobrança com o relógio do pagamento. Uma observação
provisória não fabrica timestamp de pagamento usando last_modified da cobrança.

Após a primeira observação, atualização exige timestamp estritamente mais novo;
eventos antigos, com timestamp igual ou sem timestamp não substituem snapshots
existentes. A primeira evidência de payment pode enriquecer a observação provisória.
Uma observação sem timestamp é persistida como tal, sem inventar providerUpdatedAt.
Um snapshot datado posterior pode atualizá-la. Estados aprovados/revertidos não
regridem para estados pendentes. Cobrança não liquidada não regride invoice já paga
ou revertida. Timestamps locais de auditoria não substituem timestamps do provider.

## Transações, replay e concorrência

O processor mantém uma transação para evidência financeira e registro da entrega.
A ingestão participa dela; falha na persistência não registra delivery bem-sucedido.
Antes de ler/gravar invoices e tentativas, bloqueia a Subscription existente com
PESSIMISTIC_WRITE. Isso serializa inclusive as primeiras inserções da mesma assinatura.
As consultas financeiras também usam locks para fazer leitura corrente no MySQL,
mesmo que a consulta anterior de delivery já tenha estabelecido snapshot REPEATABLE READ.
@Version permanece em ambas as entidades. Não há atualização SQL que ignore versões.

As UNIQUE constraints originais continuam como última defesa. Não se captura erro
financeiro de integridade para fingir replay: qualquer race residual, conflito de
versão ou falha inesperada aborta a transação e retorna erro. Uma nova entrega pode
reler o estado persistido. Não se tenta continuar dentro de transação já invalidada.

A correção parcial do webhook foi preservada: somente a constraint
ux_mp_webhook_delivery (inclusive qualificada pela tabela) com erro MySQL 1062
é reconhecida como duplicidade esperada. Outras DataIntegrityViolationException
retornam 503. Replays com novo requestId passam novamente pelos GETs e pelas chaves
financeiras, sem criar duplicatas.

## Datas incompletas e migration

Novo changelog: `20260914000000_billing_invoice_nullable_dates.xml`, incluído no master.
Ele remove NOT NULL de period_start, period_end, due_at e grace_period_end. A migration
original da 5G.2 não foi editada. Não há backfill ou migração automática de dados reais.

Os quatro campos Java permitem null e enriquecimento posterior. Outras obrigatoriedades
permanecem. Um período completo ainda exige duração positiva; dueAt e periodStart,
quando ambos conhecidos, continuam consistentes com o contrato 5G.2.

`date_created`, `last_modified`, `debit_date` e `next_payment_date` não são convertidos
arbitrariamente em limites de serviço ou vencimento. O adapter atual não tem evidência
suficiente para preencher essas datas, que permanecem nulas. Se uma invoice já possui
dueAt estabelecido, gracePeriodEnd é exatamente dueAt + Duration.ofHours(72).
Se dueAt é null, gracePeriodEnd também é null. Isso apenas persiste o limite; não
implementa tolerância efetiva, PAST_DUE, corte de acesso ou recuperação de entitlement.

## Validação e limites

Testes cobrem o caminho webhook → GET → correlação → invoice/tentativa, payload financeiro
não confiável, correlação vazia/múltipla/divergente, escala monetária, moeda, replay,
enriquecimento de tentativa, ordenação temporal, reversões, datas desconhecidas e 72h.
Os testes de MySQL usam Liquibase real, persistência de datas nulas, enriquecimento e
replay com repositories reais. Não se mascara indisponibilidade de Docker.

Permanecem fora da 5G.3: cálculo de entitlement por competência, grace efetiva, pagamento
inicial e recuperação, fallback ADMIN_GRANT > GRANDFATHERED > FREE, scheduler/backfill,
frontend e alterações de cancelamento. Nenhum deploy, commit ou push integra esta entrega.

### Resultado da validação local em 2026-09-14

JDK 17. Testes focados finais: 147 executados, todos aprovados, incluindo 35 cenários
de ingestão, 16 do cliente financeiro e 3 de validação temporal. A suíte `mvnw.cmd verify`
executou 471 testes: 470 aprovados, zero falhas de asserção, um erro na inicialização de
RecurringBillingPersistenceTest por Docker indisponível. Os cenários MySQL dessa classe,
incluindo os novos testes de migration/replay, não puderam executar; não foram desativados.
A validação real da migration e dos locks em MySQL permanece pendente neste ambiente.
`git diff --check` passou. Não houve chamada autenticada ao provider ou mudança em produção.
`mvnw.cmd -DskipTests package`: BUILD SUCCESS, JAR local gerado; testes omitidos apenas no empacotamento.
