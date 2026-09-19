# 5G.8 — Validação final E2E e prontidão para staging

> **SUPERSEDIDO por `docs/billing-production-closure-5g9.md`.** A 5G.9
> encontrou um BLOCKER de produção não coberto por esta matriz (proteção
> insuficiente contra segunda recorrência remota, docs/billing-recurring-
> contract-5g1.md invariante 16/seção 20 — ver 5G.9 seção A) e dois bugs
> adicionais (separação entitlement/cancelabilidade, timestamp de webhook em
> milissegundos). A classificação "READY FOR STAGING" ao final deste
> documento **não é mais válida** até que os itens da 5G.9 sejam fechados e
> revalidados. Mantido aqui sem edição retroativa do corpo do documento por
> rastreabilidade histórica — a 5G.9 é a fonte de verdade sobre o status atual.

Etapa de auditoria/validação, não de desenvolvimento. Nenhuma política
financeira, regra comercial ou frontend foi alterada. A única mudança de
produção nesta etapa é zero — os dois arquivos tocados são testes.

## SHA base e commits do bloco 5G

- 5G.5: `f2019e3` — feat: reconcile recurring billing lifecycle
- 5G.6: `f8c17b7` — feat: handle recurring payment refunds and chargebacks
- 5G.7: `e642fbd` — feat: harden recurring billing operations
- 5G.8 parte deste relatório: sem commit (não executado por instrução).

## Working tree inicial

Idêntico ao final da 5G.7: alterações paralelas legítimas em `pom.xml`,
`User.java`, `MeService`, `MeResponseDTO`, `MeMapper`, 13 resources
`web/rest/*Resource.java`, uma linha em `master.xml` (migration
`20260915000000_add_user_logo_field.xml`, untracked) e todo `frotto-ui-main/*`
— nenhum desses pertence ao bloco 5G e nenhum foi tocado.

## Auditoria transversal (Parte 1)

Recompilada linha a linha a interação entre webhook, ingestão financeira,
reconciliação recorrente e entitlement (código inalterado desde a 5G.7):

- **Refund depois de payment / payment depois de refund**: `newer()` em
  `MercadoPagoFinancialIngestion` compara `providerUpdatedAt` antes de
  aplicar qualquer observação; um evento mais antigo nunca sobrescreve um
  mais novo, em qualquer ordem de chegada.
- **Webhook concorrente com reconciliation**: ambos passam pelo mesmo
  `MercadoPagoFinancialIngestion.persist()`, que abre com
  `SubscriptionRepository#findForFinancialIngestion` (lock pessimista) —
  serializa o par completo, incluindo o primeiro insert.
- **Cancelled com pagamento ainda válido**: `SubscriptionFinancialCoverageService`
  nunca considera `Subscription.status` como prova de pagamento; avalia
  competências persistidas independentemente do status atual.
- **Refund/chargeback depois de cancelamento**: `MercadoPagoFinancialIngestion`
  não consulta `Subscription.status`/`canceledAt` para decidir se aceita a
  observação — recalcula evidência normalmente.
- **Late payment depois do grace**: `paymentDuringOrAfterGraceUsesOriginalCompetency`
  confirma que restaura apenas a competência original, nunca cria extensão.
- **Payment antigo depois de competência nova**: `oldReplayDoesNotHideNewUnpaidCompetency`
  e `newerIncompleteCompetencyCannotBeMaskedByAnOlderPaidOne` confirmam que o
  estado nunca regride.
- **Authorized preapproval sem payment**: `activeAuthorizedContractWithoutInvoicesIsAwaitingPayment`
  — nenhum entitlement pago é criado apenas por `status=authorized`.
- **Payment aprovado sem authorized payment correlacionável**: `fetch()` exige
  `findAuthorizedPaymentByPaymentId` não-vazio antes de qualquer persistência
  — sem correlação, `ignoredSnapshot("no_unique_charge")`, nada é escrito.
- **Webhook duplicado**: dedup único (`request_id+event_type+resource_id`)
  intacto, verificado atomicamente antes de qualquer efeito colateral.
- **Stale provider response / providerUpdatedAt ordering**: idem "newer()".
- **Retry de payment**: cada tentativa é avaliada individualmente contra a
  invoice; `twoInsufficientAttemptsAreNeverAddedTogether` confirma que
  retries nunca são somados.
- **Scheduler restart**: reserva é uma coluna persistida
  (`last_financial_reconciliation_at`), sobrevive a restart — confirmado por
  `reservationSurvivesRestartAndNormalStaleOrmSave` contra MySQL real.
- **Reservation concorrente**: `exactlyOneConcurrentInstanceReservesTheWindow`
  prova exclusividade com duas threads reais contra o mesmo banco.

Nenhuma inconsistência de integração foi encontrada. Nenhum código de
produção foi alterado como resultado desta auditoria.

## MySQL / Testcontainers — diagnóstico (Parte 2)

`docker version`/`docker info`: Docker Desktop 4.54.0, engine API 1.52,
plenamente funcional (`docker ps` normal). O projeto (herdado do BOM do
Spring Boot 2.7.18) resolve **Testcontainers 1.17.3 + docker-java 3.2.13**
(lançados em meados de 2022) — cerca de 3,5 anos mais antigo que este Docker
Desktop. `NpipeSocketClientProviderStrategy` resolve o pipe `docker_cli` do
Docker Desktop, que devolve um `/info` com campos majoritariamente vazios
(`BadRequestException 400`) para esse cliente antigo — uma incompatibilidade
de negociação de versão entre bibliotecas, não um problema de ambiente Docker
em si. Confirmado reprodutível com `dangerouslyDisableSandbox` e com
`DOCKER_HOST`/estratégia de cliente forçados — descarta causa de sandbox.

**Método usado (Parte 2, item 10)**: MySQL descartável 100% local via
`docker run` direto (bypassando Testcontainers), seguindo o mesmo padrão já
usado no projeto para essa finalidade (container anterior
`frotto-billing-5e2-mysql`, mesma convenção de nome/credenciais fake locais).
Container `frotto-billing-5g8-mysql` (`mysql:8.0.36`, credenciais
`frotto_root_fake_pw`/`frotto_local_fake_pw`, porta `127.0.0.1:33061`,
descartado ao final desta sessão).

`RecurringBillingPersistenceTest` foi adaptado (não criado um novo arquivo)
para aceitar opcionalmente `FROTTO_TEST_MYSQL_JDBC_URL`/`_USER`/`_PASSWORD`:
se ausentes, o comportamento é idêntico ao de antes (Testcontainers possui a
lifecycle do container); se presentes, o teste conecta no MySQL já em
execução em vez de subir um container via Testcontainers. **Nenhum código de
produção foi alterado** para isso — apenas o bootstrap do teste.

Na primeira execução contra o banco descartável, uma única falha apareceu —
um diff de exatas 3 horas (fuso BRT) num timestamp lido por JDBC puro.
Confirmado **não ser um bug de código**: reexecutando com `-Duser.timezone=UTC`
(uma flag de JVM do processo de teste, zero mudança de código), as 34/34
passam. Causa-raiz: o driver MySQL Connector/J aplica o timezone padrão da
JVM ao converter `DATETIME` sem `Calendar` explícito, e a JVM local (esta
máquina) tem default `America/Sao_Paulo`, não UTC — algo que já seria
verdade contra um MySQL real via Testcontainers nesta mesma máquina, não uma
particularidade do container manual. CI/produção tipicamente roda com TZ=UTC.
Documentado como limitação de ambiente local, não como bug — ver seção
Limitações.

## Liquibase (Parte 2)

Sequência de migrations de billing em `master.xml`, na ordem declarada:
`Plan` → `PlanPricingTier` → `Subscription` → grant/source update →
`BillingCheckout` → complete checkout → webhook reconciliation → recurring
billing model → invoice nullable dates → `subscription_financial_reconciliation`.
Ordem respeita dependências (nenhuma FK referencia uma tabela criada depois).
A migration paralela `20260915000000_add_user_logo_field.xml` (untracked, não
pertence ao 5G) foi deixada exatamente onde estava — **não incorporada, não
revertida, não alterada**. Nenhuma migration histórica foi tocada.

A suíte de persistência (34 testes, executada contra MySQL 8.0.36 real neste
diagnóstico) verifica concretamente: aplicação idempotente do changelog
completo a partir de um schema legado real; `last_financial_reconciliation_at`
como `datetime(6)` nullable sem default; índice
`idx_subscription_financial_reconciliation` exatamente em
`(source, external_provider, last_financial_reconciliation_at)`; unique
constraints de `billing_invoice` (`provider+external_authorized_payment_id`,
`subscription_id+period_start+period_end`); `@Version` otimista em
`BillingInvoice`; e agora, com os testes de terminal horizon da 5G.7
(passando contra MySQL real pela primeira vez nesta sessão), o `LEFT JOIN`
de agregação `max(period_end)` funciona corretamente em MySQL real, não
apenas em H2/mock.

## Matriz E2E (Parte 3)

Toda a matriz foi mapeada contra a suíte já existente (715+ testes antes desta
etapa). Apenas uma lacuna real foi identificada (config bounds da 5G.7 sem
teste dedicado — ver "Testes criados"); a matriz de contrato financeiro em si
não tinha lacunas, então nenhum teste de cenário A–AJ foi duplicado.

| # | Cenário | Resultado | Evidência |
|---|---|---|---|
| A | FREE, 0 payment => FREE | PASS | `EntitlementServiceTest.snapshotFallsBackToFreeWhenNoCurrentSubscriptionExists` |
| B | authorized sem payment => FREE/fallback | PASS | `SubscriptionFinancialCoverageServiceTest.activeAuthorizedContractWithoutInvoicesIsAwaitingPayment` |
| C | 1º payment aprovado => paid só na própria competência | PASS | `SubscriptionFinancialCoverageServiceTest.paidCompetencyIsHalfOpen` |
| D | 1º payment falha => sem grace | PASS | `.firstUnapprovedPaymentNeverGrantsGrace` |
| E | renovação paga => nova competência válida | PASS | `RecurringBillingLifecycleTest` (fluxo completo), `SubscriptionFinancialCoverageServiceTest` |
| F | renovação não paga, continuidade exata => 72h grace | PASS | `.renewalGraceEndsAtExactly72Hours`, `.contiguousRenewalInGraceHasExplicitTemporaryCoverage` |
| G | now == gracePeriodEnd => sem grace | PASS | `.renewalGraceEndsAtExactly72Hours` (offset 0), `FinancialEntitlementTest.expiredGraceFallsBackAtBoundaryWithoutWriting` |
| H | gap entre competências => sem grace herdado | PASS | `.gapDeniesInheritedGraceButDoesNotPreventNewPaidCoverage` |
| I | late payment dentro do grace => ACTIVE | PASS | `.paymentDuringOrAfterGraceUsesOriginalCompetency` (hours=24) |
| J | late payment depois do grace => restaura só competência correspondente | PASS | `.paymentDuringOrAfterGraceUsesOriginalCompetency` (hours=96), `.oldLatePaymentCannotGiveCurrentCoverage` |
| K | payment antigo após competência nova => não regride | PASS | `.oldReplayDoesNotHideNewUnpaidCompetency`, `.newerIncompleteCompetencyCannotBeMaskedByAnOlderPaidOne` |
| L | refund parcial de attempt válido => deixa de cobrir | PASS | `.partialRefundInvalidatesAnApprovedPaidCompetencyUnder5G6Policy` |
| M | refund total => não cobre | PASS | `RecurringBillingLifecycleTest.reconciliationObservesLostReversalWithoutNewPeriodOrGrace` |
| N | chargeback => não cobre | PASS | `SubscriptionFinancialCoverageServiceTest.reversalsDoNotBecomeGrace` (CHARGEDBACK) |
| O | refund de payment divergente => não o valida | PASS | `RecurringBillingLifecycleTest.refundCannotRepairOriginallyMismatchedGrossPayment` |
| P | dois attempts parciais => não somam | PASS | `.twoInsufficientAttemptsAreNeverAddedTogether` |
| Q | 1 inválido + 1 válido => invoice coberta pelo válido | PASS | `.independentValidAttemptSurvivesRefund` |
| R | cancelamento durante período pago => acesso até periodEnd | PASS | `.canceledSubscriptionKeepsPaidPeriodWithoutReactivationOrNewGrace`, `SubscriptionFinancialCoverageServiceTest.cancellationKeepsPaidCompetencyAndStopsAtExclusiveEnd` |
| S | cancelamento sem período pago => sem entitlement artificial | PASS | `FinancialEntitlementTest.canceledProviderIsStillConsideredForRemainingPaidCoverage(withinPeriod=false)` |
| T | refund após cancelamento => recalcula normalmente | PASS | `.reconciliationObservesLostReversalWithoutNewPeriodOrGrace` (independe de status) |
| U | chargeback após cancelamento => recalcula normalmente | PASS | mesma evidência de T — ingestão não consulta status da subscription |
| V | ADMIN_GRANT + provider unpaid => ADMIN_GRANT | PASS | `FinancialEntitlementTest.validAdminGrantWinsWithoutFinancialQueries` |
| W | ADMIN_GRANT expira + provider pago => PAYMENT_PROVIDER | PASS | `.expiredAdminGrantFallsBackToValidProvider` |
| X | ADMIN_GRANT expira + provider inválido + grandfathered => GRANDFATHERED | PASS (por composição) | `.expiredAdminGrantFallsBackToValidProvider` + `.providerWithoutCoverageFallsBackToGrandfathered` + pipeline genérico `sorted().filter().findFirst()` em `SubscriptionService#getCurrentSubscription` (sem casing por contagem de candidatos — ver auditoria de código) |
| Y | nenhuma cobertura => FREE | PASS | `FinancialEntitlementTest.revokedAndExpiredNonProviderRowsCannotGrantAccess` |
| Z | provider timeout => último fato preservado | PASS | `RecurringBillingLifecycleTest.providerFailureDoesNotErasePreviousPayment` |
| AA | provider 5xx => último fato preservado | PASS | `RecurringBillingReconciliationServiceTest.providerErrorIsOperationalAndReservationRemains` (parametrizado 404/429/500) |
| AB | 429 => nenhum fato financeiro alterado | PASS | mesmo teste acima + `.rateLimitPropagatesRetryAfterSecondsToTheBudget` |
| AC | webhook duplicado => convergência idempotente | PASS | `MercadoPagoWebhookProcessorTest.duplicateDoesNotCallProvider`, `.duplicateCancelledWebhookIsIdempotentAndNeverReProcessesTheSubscription` |
| AD | webhook antigo => rejeitado por replay window | PASS | `MercadoPagoWebhookSignatureValidatorTest.cryptographicallyValidButStaleSignatureIsRejectedByFreshnessAlone` |
| AE | provider response antigo => providerUpdatedAt impede regressão | PASS | `RecurringBillingLifecycleTest.oldOrEqualApprovalCannotUndoNewerRefundOrChargeback` |
| AF | reconciliation descobre payment perdido pelo webhook => mesmo resultado | PASS | `.schedulerRecoversMissingFirstWebhookAndEffectivePaidPlan`, `.webhookAndReconciliationReplaySameFactInEitherOrder` |
| AG | cancelled depois do terminal horizon => sem polling | PASS | `RecurringBillingPersistenceTest.cancelledAfterTerminalAtIsNotReconciled`, `.cancelledExactlyAtTerminalAtIsNotReconciled` |
| AH | webhook válido após terminal horizon => ainda processável | PASS | `.terminalExclusionOnlyAffectsCandidateSelectionNeverTheWebhookLookupPathOrPersistedFields` |
| AI | reservation perdida => zero provider calls | PASS | `RecurringBillingReconciliationServiceTest.rejectedReservationNeverCallsProvider` |
| AJ | DISCOVERY_INCOMPLETE => não fabrica ausência financeira | PASS | `.exhaustedBudgetIsExplicitlyIncomplete` (nenhuma escrita de invoice ocorre) |

## Bugs encontrados (Parte 9)

Nenhum. Nenhuma regressão, nenhuma inconsistência de contrato financeiro foi
encontrada em código de produção. O único achado ("timezone da JVM local
afeta um teste JDBC direto") não é um bug de produção — é uma característica
do ambiente de desenvolvimento local (ver seção MySQL/Testcontainers) e não
exigiu e não recebeu nenhuma correção de código de produção.

## Bugs corrigidos

Nenhum — não havia bug a corrigir.

## API contracts (Parte 4)

`GET /api/billing/me`, `/payment-state`, `/plans`, `/price-preview`,
`POST /api/billing/checkout`, `POST /api/billing/cancel`: todos resolvem o
usuário exclusivamente do contexto de segurança
(`BillingResource#getCurrentUser`), nenhum aceita `userId`. Confirmado por
`checkoutRequestDoesNotExposeAUserIdField`,
`cancelSubscriptionResolvesOnlyTheAuthenticatedUserNeverAnyIdFromTheRequest`,
`getMyBillingResolvesTheUserFromTheSecurityContextOnly`. Respostas de erro
não expõem segredo/payload do provider
(`rejectedCancellationReturns409WithSafeProblemWithoutProviderDetails`,
`cancelSubscriptionResponseNeverExposesProviderSubscriptionIdOrIdempotencyKey`).
`POST /api/webhooks/mercadopago` continua o único endpoint público exato.
`AdminBillingResource` tem `@PreAuthorize(ADMIN)` em nível de classe **e**
está sob `/api/admin/**` (`hasAuthority(ADMIN)` em `SecurityConfiguration`) —
dupla camada documentada no próprio código. Nenhuma mudança foi necessária.

## Checkout vs recurring (Parte 5)

Fronteira confirmada por leitura de código, sem sobreposição:

- **Checkout (5E.3, `BillingAutoReconciliationScheduler`)**: atua somente
  sobre `BillingCheckout` em `PROVIDER_PENDING/PROVIDER_UNKNOWN`, chama
  `MercadoPagoWebhookProcessor#process` com `subscription_preapproval` (GET
  `/preapproval`) — a mesma via que um webhook real usaria. Termina seu
  trabalho no momento em que o checkout sai desses status. Nunca toca
  `BillingInvoice`/`PaymentAttempt`.
- **Recurring (5G.5+, `RecurringBillingReconciliationScheduler`)**: atua
  somente sobre `Subscription` já existente com `source=PAYMENT_PROVIDER`,
  chama `MercadoPagoFinancialIngestion` (GET `/authorized_payments/search` e
  GETs individuais). `MercadoPagoFinancialIngestion#persist` exige uma
  subscription PAYMENT_PROVIDER **já existente** — nunca cria ou autoriza uma
  subscription a partir de um preapproval isolado.

Nenhuma competição de responsabilidade, nenhuma duplicação de Subscription,
nenhum pagamento fictício, nenhuma transformação de preapproval autorizado em
pagamento. Nenhuma alteração foi necessária.

## Security regression (Parte 6)

HMAC-SHA256 + constant-time compare, replay window 300s (boundary inclusivo
confirmado por teste em ambos os lados), dedup por
`request_id+event_type+resource_id` — os três coexistem, nenhum substitui o
outro (confirmado por `MercadoPagoWebhookSignatureValidatorTest` e
`MercadoPagoWebhookProcessorTest`). `SecurityConfiguration` inalterada desde
a 5G.7 (endpoint público exato, admin `ROLE_ADMIN`, billing autenticado).
Segredo/token nunca aparecem em log
(`logsNeverContainTheRawSignatureHeaderValue`,
sanitização em `MercadoPagoHttpClient`). Dados/valor/data malformados
falham fechado: `invalidFinancialResponse()` em `MercadoPagoHttpClient`,
`validAmount()`/`currency()` em `MercadoPagoFinancialIngestion`,
`isPeriodValid()` em `BillingInvoice` (assertion de domínio). Nenhuma
alteração foi necessária.

## Configuration matrix (Parte 7)

Mercado Pago disabled/enabled, recurring reconciliation disabled/enabled,
checkout reconciliation disabled, webhook secret ausente com MP habilitado,
replay window inválida — todos já cobertos pela suíte existente. Uma lacuna
real foi encontrada: os limites novos da 5G.7
(`cancelledTerminalHorizonDays`, `rateLimitDefaultCooldownSeconds`,
`rateLimitMaxCooldownSeconds`) não tinham teste dedicado de
`validate()` fora dos limites — corrigido com um teste novo (ver "Testes
criados"). Nenhuma feature de billing liga por acidente: todos os defaults
permanecem `false`/seguros.

## Testes criados

- `RecurringBillingReconciliationPropertiesTest.java` (8 testes): cobre a
  lacuna real encontrada na matriz de configuração — bounds de
  `cancelledTerminalHorizonDays`, `rateLimitDefaultCooldownSeconds` e
  `rateLimitMaxCooldownSeconds` fora do intervalo válido falham em
  `validate()`; defaults continuam seguros.

Nenhum outro teste foi criado — a matriz A–AJ já estava coberta pela suíte
existente (715 testes antes desta etapa).

## Arquivos alterados

- `RecurringBillingPersistenceTest.java`: adiciona um fallback opcional
  (`FROTTO_TEST_MYSQL_JDBC_URL`/`_USER`/`_PASSWORD`) para apontar o teste a um
  MySQL já em execução em vez de depender do Testcontainers. Sem a variável
  de ambiente, comportamento idêntico ao anterior. Nenhum código de produção
  tocado.

## Limitações

- **Testcontainers permanece incompatível nesta máquina** para execução via
  `mvn test` "out of the box" (sem a variável de ambiente). Isso é uma
  característica do ambiente local (Spring Boot 2.7.18 pinando Testcontainers
  1.17.3 contra um Docker Desktop muito mais novo), não do código do projeto.
  Recomenda-se, em uma etapa futura sem relação com política financeira,
  avaliar atualizar a dependência de Testcontainers (fora do escopo do bloco
  5G).
- **Timezone da JVM local**: rodar `RecurringBillingPersistenceTest`
  diretamente nesta máquina sem `-Duser.timezone=UTC` produz uma falha de
  teste espúria (diff de 3h). CI/staging tipicamente já roda com TZ=UTC; se
  não rodar, recomenda-se fixar isso na configuração do CI, não no código do
  teste.
- Backlog não bloqueante herdado da 5G.7 (breaker não compartilhado entre
  instâncias, sem log dedicado de exclusão terminal por subscription,
  cancelled sem âncora nunca é excluída) permanece documentado em
  `docs/billing-hardening-5g7.md` e não foi reavaliado nesta etapa por não
  ser um bloqueador técnico para staging.

## Backlog não bloqueante

- Atualizar Testcontainers/docker-java (ou o BOM do Spring Boot) para que
  `RecurringBillingPersistenceTest` rode via `mvn test` puro em máquinas com
  Docker Desktop recente, sem variável de ambiente.
- Considerar padronizar `-Duser.timezone=UTC` (ou `TZ=UTC` no processo) na
  configuração de build/CI deste módulo, evitando a mesma armadilha para
  qualquer futuro teste que leia timestamp via JDBC puro.

## Staging prerequisites

Nenhum pré-requisito de código pendente. Pré-requisitos operacionais (fora do
escopo desta etapa, não executados): configurar
`MERCADOPAGO_ACCESS_TOKEN`/`MERCADOPAGO_WEBHOOK_SECRET` reais de sandbox no
ambiente de staging; manter `mercadopago.enabled` e
`billing.recurring-reconciliation.enabled` desligados até a validação manual
inicial em staging ser concluída.

## Classificação técnica

**READY FOR STAGING.**

Justificativa objetiva: nenhum BLOCKER/HIGH encontrado; suíte backend
completa limpa (723 testes, 0 falhas, 0 erros, incluindo persistência MySQL
real); build limpo; Checkstyle limpo; `git diff --check` limpo; Liquibase
validado ponta a ponta contra MySQL real; todos os contratos financeiros
definitivos (preapproval≠pagamento, grace 72h exato, matching bruto, refund/
chargeback, precedência, cancelamento) confirmados intactos pela matriz E2E;
security regression limpa. A classificação é estritamente técnica — não
autoriza deploy, que permanece uma decisão manual e separada.
