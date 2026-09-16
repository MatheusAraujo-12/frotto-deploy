# 5G.7 — Billing hardening, security e operational safety

A 5G.7 é hardening puro sobre o billing recorrente já existente (5G.1–5G.6).
Não introduz nenhuma funcionalidade comercial nova, nenhum gateway, nenhuma
mudança de preço, entitlement, período ou política de cancelamento. Onde uma
decisão financeira/entitlement não estava definida no prompt, a implementação
não a inventou (ver "Limitações e lacunas").

SHA base: `f8c17b7` (feat: handle recurring payment refunds and chargebacks — 5G.6).

## Threat model resumido

Superfícies auditadas: webhook público (`POST /api/webhooks/mercadopago`),
scheduler de reconciliação recorrente (`RecurringBillingReconciliationScheduler`),
cliente HTTP do provider (`MercadoPagoHttpClient`) e configuração/segredos.

Riscos considerados: replay de webhook assinado antigo; scheduler martelando o
provider após 429; reconciliação eterna de subscriptions cancelled sem sinal
financeiro novo; scheduler overlap/concorrência; trabalho não limitado
(paginação/HTTP ilimitados); outage do provider sendo interpretado como fato
financeiro; vazamento de segredo em log; wildcard acidental em
SecurityConfiguration. A ingestão financeira (`MercadoPagoFinancialIngestion`),
o matching bruto e a precedência de fontes de assinatura já existiam e não
foram alterados nesta etapa — apenas auditados.

## Webhook: HMAC, replay e dedup

`MercadoPagoWebhookSignatureValidator` já validava o manifest documentado
(`id:{data.id};request-id:{x-request-id};ts:{ts};`) com HMAC-SHA256 e
comparação constant-time (`MessageDigest.isEqual`), rejeitando segredo vazio,
integração desabilitada, `ts`/`v1` ausentes e headers ausentes — isso não
mudou. O que faltava, e foi adicionado, é uma janela de replay sobre o `ts`
assinado:

- `WEBHOOK_REPLAY_WINDOW = 300s` (5 minutos), comparação `abs(now - ts) <= 300`
  — o limite é inclusivo dos dois lados (testado explicitamente).
- `ts` é validado como inteiro não-negativo puro antes do parse (regex
  `[0-9]{1,19}`); ausente, não numérico, com sinal ou overflow → rejeita antes
  de qualquer HMAC.
- O `Clock` é injetável (`Clock.systemUTC()` em produção); a freshness nunca
  usa o timestamp do payload, apenas o `ts` que participa do HMAC.
- Configurável via `mercadopago.webhook-replay-window-seconds`
  (`MERCADOPAGO_WEBHOOK_REPLAY_WINDOW_SECONDS`, default 300). Um valor fora de
  `[1, 3600]` é sanitizado de volta para 300 em vez de desabilitar a checagem
  ou aceitar uma janela ilimitada.
- A checagem de freshness roda antes do HMAC (fail-fast, barato) — isso não
  cria oracle: o `ts` usado na comparação de freshness é exatamente o `ts` que
  entra no manifest assinado, então um atacante que altere `ts` sem recalcular
  o HMAC já falha na verificação de assinatura de qualquer forma.

Replay window é defesa adicional, não substitui o dedup existente
(`mercadopago_webhook_event`, constraint única `request_id + event_type +
resource_id`, verificada atomicamente em `MercadoPagoWebhookProcessor#process`
antes de qualquer efeito colateral). Os dois seguem necessários: uma entrega
duplicada dentro da janela converge pelo dedup; uma assinatura antiga e
criptograficamente válida é rejeitada pela freshness antes mesmo de chegar ao
dedup.

Nunca logamos `x-signature` completa, secret ou access token — confirmado por
teste existente (`logsNeverContainTheRawSignatureHeaderValue`) e mantido.

## Cancelled terminal reconciliation horizon

Política aprovada: uma subscription `PAYMENT_PROVIDER` cancelada deixa de ser
candidata a reconciliação recorrente automática 90 dias após o fim da última
competência financeira conhecida (`max(BillingInvoice.periodEnd)` daquela
subscription); na ausência de qualquer invoice com `periodEnd`, usa-se como
âncora o `Subscription.canceledAt` já existente e autoritativo (setado por
`MercadoPagoWebhookProcessor` a partir de `provider.getLastModified()`, ou do
instante do processamento quando o provider não informa). Nenhuma data nova
foi inventada: `canceledAt` já era um campo confiável antes da 5G.7.

`terminalAt = anchor + cancelledTerminalHorizonDays` (default 90,
`billing.recurring-reconciliation.cancelled-terminal-horizon-days`). Exclusão
só ocorre quando `now >= terminalAt` (boundary exato **não** reconcilia,
testado). A implementação é um `LEFT JOIN` de agregação
(`max(period_end) group by subscription_id`) dentro de
`SubscriptionRepository#findFinancialCandidates`, com `coalesce(max_period_end,
canceled_at)` como âncora — sem N+1, sem migration (as colunas já existiam).

Isso afeta **apenas** a seleção de candidatos do scheduler:

- Não apaga histórico nem altera `BillingInvoice`/`PaymentAttempt`.
- Não altera entitlement (`SubscriptionFinancialCoverageService` nunca é
  chamado por esse caminho).
- Não impede um webhook autoritativo de ser processado depois: o lookup do
  webhook (`SubscriptionRepository#findByExternalProviderAndExternalSubscriptionId`)
  é um método completamente separado, nunca filtrado por terminalAt — provado
  por teste de persistência dedicado.

## HTTP 429 / rate limit / cooldown

Antes da 5G.7, um 429 já interrompia chamadas dentro do **ciclo atual**
(`RecurringReconciliationBudget.stopForRateLimit()`), mas nada impedia o
scheduler de martelar o provider de novo no próximo tick, já que o budget é
recriado a cada execução do `@Scheduled`. Foi adicionado
`RecurringReconciliationCircuitBreaker`: um gate em memória, local à
instância, não persistido, consultado e mutado **apenas** por
`RecurringBillingReconciliationScheduler`.

- No início do ciclo, se o breaker está aberto (`isOpen()`), o scheduler não
  chama `reservations.candidates(...)` nem toca o provider — log
  `outcome=SKIPPED_BACKOFF`.
- Ao final de um ciclo em que o budget terminou `rateLimited()`, o scheduler
  abre o breaker pela duração calculada a partir do `Retry-After` do provider
  (se válido, `MercadoPagoHttpClient` agora extrai o header — apenas segundos,
  inteiro positivo, sem parser de HTTP-date), clampado ao teto configurado
  (`rate-limit-max-cooldown-seconds`, default 3600s = 1h, também o teto
  absoluto aceito). Sem `Retry-After` utilizável, usa o cooldown default
  configurável (`rate-limit-default-cooldown-seconds`, default 900s = 15min).
- `openFor` nunca encurta um cooldown já em vigor (apenas estende), então
  duas rodadas rate-limited seguidas não "resetam" para um valor menor.
- **Escopo**: o breaker é uma dependência exclusiva do scheduler de
  reconciliação recorrente. Checkout, webhook e qualquer fluxo manual/admin
  não o referenciam (confirmado por grep — nenhuma outra classe importa
  `RecurringReconciliationCircuitBreaker`) e continuam funcionando durante o
  cooldown.

## Provider outage / timeout

Timeout, 5xx e falha de rede já lançavam `MercadoPagoException` antes de
qualquer escrita financeira em `MercadoPagoFinancialIngestion#fetch` — a
persistência (`persist`) só é alcançada depois que todo o HTTP necessário já
teve sucesso. Confirmado por auditoria de código e pelos testes existentes
(`timeoutLeavesFinancialStateUntouched`,
`providerFailureHasNoHttpTransactionAndReservationSurvivesOuterRollback`).
Nenhuma mudança foi necessária aqui: outage nunca revoga entitlement, nunca
marca invoice como unpaid e nunca fabrica competência.

## Scheduler / concorrência

A reserva atômica por subscription já existente
(`SubscriptionRepository#reserveFinancialReconciliation`, um `UPDATE ...
WHERE` condicional executado em transação própria `REQUIRES_NEW`, sem lock de
DB mantido durante o HTTP) permanece suficiente e não foi substituída por lock
distribuído. `RecurringBillingReconciliationService#reconcile` roda com
`Propagation.NOT_SUPPORTED` — nenhuma transação ativa durante as chamadas de
rede. A ordenação financeira contra webhook concorrente já era protegida por
`providerUpdatedAt`/"newer" checks em `MercadoPagoFinancialIngestion` (não
alterado). O breaker não introduz nenhum novo ponto de concorrência: é um
`AtomicReference<Instant>` local à instância, sem coordenação entre
instâncias — ver limitações.

## Bounded work

Paginação, HTTP budget, batch size e itens descobertos já eram configuráveis
com defaults conservadores e `validate()` fail-fast contra `<= 0`. Duas novas
propriedades seguem o mesmo padrão (`cancelled-terminal-horizon-days` em
`[1, 3650]`, cooldowns em `[1, 3600]`, default nunca pode exceder o teto
configurado) — configuração inválida lança `IllegalArgumentException` no
próximo uso, nunca aceita silenciosamente um budget ilimitado.

## Observabilidade

Outcomes já distinguíveis em log permanecem (`COMPLETE`,
`DISCOVERY_INCOMPLETE`, `RATE_LIMITED`, `PROVIDER_FAILURE`, etc., por
subscription). Adicionado no nível do ciclo: `SKIPPED_BACKOFF` (breaker
aberto, nenhuma chamada tentada) e o novo log ao abrir o breaker
(`outcome=RATE_LIMITED cooldownSeconds=... cooldownUntil=...`). Nenhum log
inclui segredo, token, corpo bruto do provider ou signature completa — os
sanitizadores existentes em `MercadoPagoHttpClient` (redação de Bearer/JWT/
access-token) não foram alterados.

## Segredos

Varredura do repositório (código-fonte e `application*.yml`) não encontrou
access token, webhook secret, `Authorization: Bearer` ou credencial de teste
hardcoded — todos os valores sensíveis em `application.yml` são
`${ENV_VAR:}` sem default não-vazio. Nenhum arquivo `.env` versionado.
Nenhuma rotação é necessária como resultado desta auditoria.

## SecurityConfiguration

`POST /api/webhooks/mercadopago` continua o único endpoint público sob
`/api/**` (`permitAll()` restrito a esse método+path exato, declarado antes do
catch-all `"/api/**" → authenticated()`); `/api/admin/**` continua
`ROLE_ADMIN`. Nenhuma alteração foi feita neste arquivo — nenhum wildcard foi
introduzido. Existe no working tree um teste paralelo untracked
(`SecurityConfigurationWebhookRuleTest`) que audita estruturalmente essas
mesmas garantias; ele não pertence à 5G.7 e não foi modificado nem
incorporado — apenas confirmado como compatível com o estado atual do
arquivo (já passa sem qualquer mudança desta etapa).

## Configuração nova

Sob `mercadopago:` — `webhook-replay-window-seconds` (env
`MERCADOPAGO_WEBHOOK_REPLAY_WINDOW_SECONDS`, default 300).

Sob `billing.recurring-reconciliation:` — `cancelled-terminal-horizon-days`
(env `BILLING_RECURRING_RECONCILIATION_CANCELLED_TERMINAL_HORIZON_DAYS`,
default 90), `rate-limit-default-cooldown-seconds` (env
`BILLING_RECURRING_RECONCILIATION_RATE_LIMIT_DEFAULT_COOLDOWN_SECONDS`,
default 900), `rate-limit-max-cooldown-seconds` (env
`BILLING_RECURRING_RECONCILIATION_RATE_LIMIT_MAX_COOLDOWN_SECONDS`, default
3600). Todas com default seguro; `recurring-reconciliation.enabled` continua
`false` por padrão em todo ambiente — nenhuma mudança nisso.

## Migrations

Nenhuma. As duas features com estado (replay window, terminal horizon) usam
exclusivamente colunas/tabelas já existentes (`Subscription.canceledAt`,
`BillingInvoice.periodEnd`, `mercadopago_webhook_event`). O breaker de rate
limit é puramente em memória, sem persistência nesta etapa.

## Limitações e lacunas deixadas para 5G.8

- **Breaker não é compartilhado entre instâncias**: em deployment
  multi-instância, cada JVM tem seu próprio cooldown local. Uma instância que
  não recebeu o 429 pode continuar chamando o provider enquanto outra está em
  cooldown. Aceitável para esta etapa (escopo explicitamente pediu "local à
  instância", não persistente); se o ambiente rodar múltiplas instâncias em
  produção, considerar um cooldown compartilhado (Redis/DB) na 5G.8.
- **Sem log dedicado para exclusão terminal por subscription**: subscriptions
  excluídas pelo terminal horizon simplesmente não aparecem na lista de
  candidatos — não há um `SKIPPED_CANCELLED_TERMINAL` por subscription no log
  do ciclo, pois isso exigiria uma query adicional só para contagem/observação.
  Deliberadamente fora do escopo desta etapa; considerar uma métrica agregada
  (quantas subscriptions cruzaram o terminal horizon neste ciclo) na 5G.8 se
  operação precisar desse sinal.
- **Cancelled sem nenhuma âncora confiável nunca é excluída**: quando não há
  nem `BillingInvoice.periodEnd` nem `Subscription.canceledAt`, a subscription
  permanece candidata indefinidamente (mesmo comportamento de antes da
  5G.7) — nenhuma data foi fabricada para resolver esse caso, por instrução
  explícita do escopo. Na prática isso só deveria ocorrer para dados legados
  anteriores ao rastreamento de `canceledAt`; não foi criada migration de
  backfill porque não haveria fato histórico confiável para preenchê-la.
- **Teste de persistência MySQL não executado nesta sessão**: o ambiente local
  tem um Docker funcional (`docker ps`/`docker info` respondem normalmente),
  mas o Testcontainers deste projeto resolve o pipe `docker_cli` do Docker
  Desktop, que devolve um payload `/info` mínimo incompatível com a
  negociação de versão do cliente `docker-java` usado
  (`BadRequestException 400`) — uma incompatibilidade de ambiente/tooling
  pré-existente, não uma regressão desta etapa (a falha ocorre inteiramente
  no bootstrap do Testcontainers, antes de qualquer código da 5G.7 rodar). A
  query SQL nova (`findFinancialCandidates` com o `LEFT JOIN` de terminal
  horizon) foi revisada manualmente linha a linha e os testes novos que a
  exercitam (`RecurringBillingPersistenceTest`) estão escritos e prontos;
  recomenda-se rodá-los em um ambiente onde o Testcontainers resolva o
  Docker corretamente antes do merge.
