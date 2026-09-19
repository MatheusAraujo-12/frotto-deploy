# 5G.9 — Production Billing Closure

Auditoria/implementação cirúrgica sobre o estado atual de
`staging/security-prebilling`. Cada diagnóstico abaixo foi confirmado lendo o
código real e, onde aplicável, reproduzido em teste antes de qualquer
alteração. Nenhuma regra de preço, plano, precedência ADMIN_GRANT >
PAYMENT_PROVIDER > GRANDFATHERED, evidência financeira, período mensal, grace
de 72h, HMAC, idempotência ou política de refund 5G.6 foi alterada, exceto
onde esta etapa pedia explicitamente (webhook `ts` em C). Sem commit, push,
merge ou deploy — mudanças permanecem no working tree para revisão.

## Status

**Blocker fechado nesta etapa; revalidação em staging ainda pendente antes de
qualquer nova conclusão de prontidão.** Ver `docs/billing-final-validation-
5g8.md`, que teve sua classificação "READY FOR STAGING" marcada como
superada por este documento.

> **Atualização (5G.10):** a política fail-closed financeira desta etapa
> (seções A/E) causou uma regressão real de produto: uma `Subscription`
> `PAYMENT_PROVIDER` `ACTIVE` autorizada pelo Mercado Pago, sem nenhum
> `BillingInvoice` ainda ingerido, passou a ser tratada como `FREE`. Essa
> exigência foi revertida para a ativação inicial do plano — ver
> `docs/billing-entitlement-activation-5g10.md` para a decisão final e a
> correção. A seção E abaixo (invoice existente porém sem `periodStart`)
> não foi afetada e continua fail-closed pelo mesmo motivo original: ali
> existe uma invoice real e incompleta, um cenário diferente de "nenhuma
> invoice existe ainda".

## Rodada de fechamento (segunda passagem)

Após a implementação inicial (seções A-G abaixo), uma segunda passagem
revisou cada item procurando problemas objetivos ainda não fechados e
corrigiu-os imediatamente em vez de apenas os documentar. Resumo por item:

1. **RecurringSubscriptionGuardService — edge cases**: revisão confirmou que
   a lógica central (iterar TODAS as linhas `PAYMENT_PROVIDER` via
   `findByUserIdAndSource`, nunca só "a mais recente") já cobria todos os
   cenários pedidos, incluindo múltiplos registros históricos. Lacunas reais
   encontradas e fechadas: faltava teste explícito para `EXPIRED` (com e sem
   cobertura residual) e para múltiplos registros históricos simultâneos (um
   bloqueante + um encerrado, nas duas ordens de lista, e todos encerrados).
   5 testes novos em `RecurringSubscriptionGuardServiceTest`.
2. **Frontend 409 `BILLING_RECURRING_SUBSCRIPTION_EXISTS`**: bug real
   confirmado — sem tratamento dedicado, `getApiErrorMessage` caía no
   fallback genérico de 409 ("Conflito ao salvar..."). Adicionados
   `isRecurringSubscriptionExistsError`/`recurringSubscriptionExistsMessage`
   em `myPlanLogic.ts`, com mensagem adaptada ao estado de cancelamento já
   conhecido (`canCancel`/`cancellationState`) e sem expor identificadores do
   provider. `MyPlanPage.tsx` atualizado; 4 testes novos (2 unitários, 2
   end-to-end na página). Nenhum mecanismo de upgrade foi criado.
3. **Cancelabilidade independente de entitlement — múltiplos registros**:
   bug real de robustez encontrado — `BillingPaymentStateService` escolhia o
   contrato a expor por "mais recentemente iniciado" (`findFirst...
   OrderByStartDateDesc`), o que poderia, em um cenário de dados histórico
   incomum, apontar `canCancel` para um contrato errado se um registro
   encerrado tivesse `startDate` mais recente que um registro ainda
   cobrável. Corrigido: `RecurringSubscriptionGuardService` ganhou um método
   público `isStillChargeable(Subscription)` (mesma regra usada para
   bloquear checkout), e `BillingPaymentStateService` agora escolhe primeiro
   a linha ainda cobrável entre TODAS as linhas do usuário; só cai para
   "mais recente" quando nenhuma está mais cobrável (puramente para exibição
   histórica, com `canCancel=false`). O método de repositório
   `findFirstByUserIdAndSourceOrderByStartDateDesc`, agora sem uso, foi
   removido. 2 testes novos provam o caso (ordem invertida de startDate) e o
   fallback de exibição.
4. **Webhook timestamp — bordas**: revisão linha a linha confirmou que trim
   indevido, normalização antes do HMAC, aceitação de `+`/`-`/espaços,
   11/12/14 dígitos, overflow, replay window inconsistente entre
   segundos/milissegundos e comparação não-constante NÃO ocorriam — a
   implementação já fechava todos esses casos. Adicionados testes
   explícitos que faltavam (prefixo `+`, espaço interno/externo no valor,
   comparação de assinatura errada do mesmo tamanho vs. tamanho diferente)
   para deixar essas garantias auditáveis, sem alterar produção.
5. **Logs Mercado Pago**: busca global por `getMessage()`/`printStackTrace`/
   `responseBody`/`payerEmail`/`Authorization`/`accessToken`/`webhookSecret`
   nos caminhos de billing/MercadoPago/subscriptions/reconciliation/
   checkout/cancellation/webhook não encontrou nenhuma violação nova além
   das 3 já corrigidas na primeira passagem. Um uso de `getMessage()` em
   `MercadoPagoHttpClient` foi inspecionado e confirmado seguro (repassa uma
   mensagem já sanitizada de uma exceção para outra, nunca chega a um
   `log.*`). Nenhuma alteração de código necessária.
6. **`authorized_payments/search`**: confirmado que nenhum vestígio de
   `payments/search?external_reference=` ou `searchPaymentsByExternalReference`
   restou em `src/main` ou `src/test` após a reversão da primeira passagem.
   Paginação, ownership, valor, moeda, período e o vínculo preapproval →
   authorized payment → payment continuam intactos. Nenhuma alteração
   necessária.
7. **Invoice sem `periodStart`**: comportamento fail-closed mantido sem
   nenhuma mudança de lógica. Comentário no ponto exato do bloqueio em
   `SubscriptionFinancialCoverageService` foi expandido para explicar
   claramente por que bloqueia, citar a especificação normativa e nomear
   explicitamente os dois únicos lugares autorizados a preencher
   periodStart/periodEnd/dueAt (ambos ancorados em campos autoritativos do
   provider/contrato, nunca em `createdAt`/`observedAt`/`next_payment_date`).
   Confirmado por grep que nenhum outro ponto do código escreve esses
   campos. Teste de reprodução preservado sem alteração.
8. **Concorrência — teste de integração real**: não havia teste de
   integração real (JPA/MySQL) cobrindo especificamente a concorrência de
   `BillingCheckoutService#createCheckout` (apenas o teste unitário
   Mockito-sequencial já existente). Adicionado
   `RecurringBillingPersistenceTest#concurrentCheckoutsForTheSameUserProduce
   AtMostOneLogicalRecurrenceCreation`, reaproveitando a infraestrutura
   MySQL/Liquibase/Testcontainers já existente no arquivo: duas transações
   reais e concorrentes do mesmo usuário novo, com o lock pessimista real
   (`UserRepository#findByIdForBillingCheckoutLock`) e o guard real,
   provando exatamente uma criação lógica de recorrência e um conflito
   controlado na outra. Compilado e confirmado que falha somente no
   bootstrap de Docker/Testcontainers deste ambiente (mesma limitação
   conhecida), não por defeito do teste.
9. **Diagnóstico operacional de `authorized_payments`**: já estava correto
   (verificado em 6) — `RecurringReconciliationResult` expõe
   `failureCategory`/`failureHttpStatus`/`providerErrorCode` sanitizado, sem
   corpo bruto. Endpoint preservado, nenhuma alteração feita.
10. **Auditoria de dados para `periodStart`**: nova consulta somente-leitura
    em `docs/diagnostics/orphaned-invoices-periodstart-5g9.sql` — localiza
    toda `BillingInvoice` com `period_start IS NULL`, sua subscription/
    usuário, contagem por subscription, e especificamente subscriptions que
    têm tanto uma invoice órfã quanto uma invoice paga e ancorada (o caso
    que efetivamente bloquearia um cliente pagante hoje). Não executada
    nesta sessão; destinada a rodar manualmente em staging/produção com
    credencial somente-leitura.

## A — Proteção contra múltiplas recorrências remotas (BLOCKER)

**Bug confirmado:** `BillingCheckoutService` bloqueava uma nova
`/preapproval` apenas quando o próprio `BillingCheckout` do usuário estava em
`CREATED`/`PROVIDER_PENDING`/`PROVIDER_UNKNOWN`. Assim que o checkout
avançava para `AUTHORIZED` (ou qualquer estado posterior), nada impedia o
mesmo usuário de iniciar um segundo checkout, mesmo com uma `Subscription`
`PAYMENT_PROVIDER` `ACTIVE`/`PAST_DUE`/`PAUSED` ainda capaz de cobrar — uma
violação direta de docs/billing-recurring-contract-5g1.md invariante 16 e
seção 20.

**Correção:** nova classe de domínio dedicada,
`RecurringSubscriptionGuardService` (`frotto-server-main/src/main/java/com/
localuz/service/RecurringSubscriptionGuardService.java`), chamada por
`BillingCheckoutService#createCheckout` imediatamente depois do lock
pessimista existente (`findByIdForBillingCheckoutLock`) e da checagem de
checkout ambíguo já existente, e antes de qualquer POST ao Mercado Pago.

Regra central (única, não replicada): para cada `Subscription` do usuário
com `source=PAYMENT_PROVIDER`,

- `status` em `ACTIVE`, `PAST_DUE` ou `PAUSED` sempre bloqueia — cobre
  ACTIVE com ou sem evidência financeira, PAST_DUE, PAUSED (assumido
  retomável por padrão, já que o modelo local não distingue um PAUSED
  irrecuperável), cancelamento apenas solicitado
  (`cancelAtPeriodEnd=true`/`canceledAt=null`) e cancelamento confirmado mas
  ainda diferido para o fim do período pago (`canceledAt` setado, status
  mantido ACTIVE/PAST_DUE — ver `MercadoPagoWebhookProcessor#reconcile`).
- `status=CANCELED` (ou o valor `EXPIRED`, hoje não emitido por nenhum
  fluxo): bloqueia somente se `SubscriptionFinancialCoverageService#evaluate`
  ainda considerar a competência coberta agora (cobertura paga
  remanescente); caso contrário libera nova contratação.
- ADMIN_GRANT e GRANDFATHERED nunca são lidos por este guard — apenas
  `PAYMENT_PROVIDER` é consultado, então nunca podem ser confundidos com
  recorrência remota.

Upgrade/troca de assinatura explicitamente NÃO foi implementado nesta etapa
(fora de escopo, conforme instrução) — enquanto bloqueado, uma nova
recorrência retorna **HTTP 409** com error key estável
`error.BILLING_RECURRING_SUBSCRIPTION_EXISTS`
(`BillingRecurringSubscriptionExistsException`,
`ErrorConstants.BILLING_RECURRING_SUBSCRIPTION_EXISTS_TYPE`).

A exclusão mútua por usuário e a garantia de no máximo uma chamada
`createPreapproval` por concorrência já existiam via o lock pessimista de
`UserRepository#findByIdForBillingCheckoutLock`; o novo guard roda dentro da
mesma seção travada, então herda essa serialização sem lógica adicional.

### Testes adicionados

- `RecurringSubscriptionGuardServiceTest` (11 casos): ACTIVE paga bloqueia;
  ACTIVE sem evidência financeira bloqueia; PAST_DUE bloqueia; PAUSED
  bloqueia; cancelamento pendente bloqueia; cancelamento confirmado com
  cobertura residual bloqueia (nos dois formatos possíveis — status ainda
  ACTIVE/diferido, e status já CANCELED terminal); CANCELED sem cobertura
  residual libera; ausência de linhas PAYMENT_PROVIDER nunca bloqueia
  (prova que ADMIN_GRANT/GRANDFATHERED nunca são consultados).
- `BillingCheckoutServiceTest`: nova checagem roda depois do lock e antes de
  qualquer chamada ao provider (`blockingRemoteRecurrenceIsCheckedAfter
  LockAndBeforeAnyProviderCall`, com `InOrder`); duas chamadas concorrentes
  (guard vence na segunda) produzem no máximo uma chamada
  `createPreapproval` (`twoConcurrentRequestsProduceAtMostOneCreate
  PreapprovalCallWhenAGuardWinsTheRace`).
- Chamada REST direta: `BillingResource#createCheckout` delega
  incondicionalmente a `BillingCheckoutService#createCheckout` — não existe
  caminho de bypass client-side, então o mesmo teste de serviço já cobre
  qualquer chamada HTTP direta ao endpoint.
- `BillingCheckoutServiceTest#subscriptionStateIsOnlyReachedThroughTheDedicated
  Guard` substitui o antigo `serviceHasNoSubscriptionDependency` (que
  proibia qualquer dependência de Subscription em `BillingCheckoutService`):
  essa invariante mudou deliberadamente nesta etapa; o teste novo garante
  que o único ponto de acesso a estado de Subscription continua sendo o
  guard dedicado, nunca `SubscriptionRepository`/`SubscriptionService`
  direto.

## B — Separação entre entitlement e cancelabilidade

**Bug confirmado e reproduzido:** com uma preapproval autorizada,
`Subscription.source=PAYMENT_PROVIDER`/`status=ACTIVE` e nenhuma evidência
financeira (`BillingInvoice`/`PaymentAttempt`), `EntitlementService#getSnapshot`
(via `SubscriptionService#getCurrentSubscription`, que filtra PAYMENT_PROVIDER
por `SubscriptionFinancialCoverageService`) retorna `subscription=null`, e
`GET /api/billing/me` reporta `planCode=FREE`/`subscriptionSource=null`.
`MyPlanPage`/`myPlanLogic.isSubscriptionCancelable` decidia a partir desse
mesmo `BillingMeDTO`, então o botão "Cancelar assinatura" nunca aparecia —
mesmo com um contrato remoto real, ainda capaz de cobrar, existindo em
`/api/billing/payment-state`.

**Correção:** `GET /api/billing/payment-state` agora expõe, dentro de
`paymentProviderSubscription`, três campos novos e seguros:

- `canCancel` (boolean) — verdadeiro sempre que o status bruto da
  `Subscription` está em `SubscriptionCancellationSteps.CANCELLABLE_STATUSES`
  (fonte única, reaproveitada por `BillingPaymentStateService`, nunca
  duplicada), **independente** de `financiallyCovered`.
- `cancellationState` (`NONE`/`PENDING_CONFIRMATION`/`CONFIRMED`) e
  `currentPeriodEnd` — derivados diretamente da linha bruta de
  `Subscription`, para dirigir a mensagem de cancelamento mesmo quando
  `BillingMeDTO` não tem uma subscription "efetiva" para expor.

Nunca expostos (auditado por
`BillingPaymentStateServiceTest#contractDoesNotExposeProviderOrOwnershipIdentifiers`):
`providerSubscriptionId`, `externalReference`, `idempotencyKey`, payer,
payload do provider.

O frontend (`myPlanLogic.isSubscriptionCancelable`/`remoteCancellationState`/
`remoteCurrentPeriodEnd`) passou a ler exclusivamente
`payment-state.paymentProviderSubscription`, nunca mais `BillingMeDTO`, para
decidir se mostra a ação de cancelar — preservando
`canCancelRemoteContract != hasPaidEntitlement` também na UI (inclusive
quando ADMIN_GRANT é o plano efetivo mas ainda existe um contrato
PAYMENT_PROVIDER pagável por baixo, caso agora coberto por teste).

### PAUSED avaliado explicitamente

`SubscriptionCancellationSteps.CANCELLABLE_STATUSES` passou a incluir
`PAUSED`. Verificação na documentação pública do Mercado Pago ("Subscription
management" / "Gerenciamento de assinaturas assinaturas"): pausar, reativar
e cancelar uma preapproval são descritas como operações independentes via
`PUT /preapproval/{id}` (`status=paused`/`authorized`/`cancelled`
respectivamente), sem nenhuma precondição documentada de que uma assinatura
pausada precise ser reativada antes de poder ser cancelada. A documentação
também não confirma nem nega essa transição de forma explícita — não foi
"inventado" comportamento: a implementação existente de
`cancelPreapproval` já envia um PUT genérico `status=cancelled` sem
depender do status atual, e o fluxo de cancelamento já trata com segurança
uma rejeição definitiva do provider (`BillingCancellationProviderRejectedException`,
rollback do intent local, nenhum estado corrompido — ver
`SubscriptionCancellationSteps`/`SubscriptionCancellationService`). Ou seja,
permitir a tentativa não tem custo no caminho de falha, e resolve o caso
concreto em que um contrato remoto PAUSED real não podia ser cancelado via
Frotto de forma alguma.

### Testes adicionados

- `BillingPaymentStateServiceTest`: `canCancel` verdadeiro para
  ACTIVE/PAST_DUE/PAUSED e falso para CANCELED/EXPIRED, independente de
  `financiallyCovered`; `cancellationState`/`currentPeriodEnd` refletem a
  linha bruta.
- `SubscriptionCancellationServiceTest#pausedSubscriptionCancelsSuccessfully`.
- `myPlanLogic.test.ts`: cancelabilidade vem só de `payment-state.canCancel`
  (nunca de `BillingMeDTO`); `remoteCancellationState`/`remoteCurrentPeriodEnd`
  preferem o payment-state quando presente, caindo para `BillingMeDTO`
  quando não.
- `MyPlanPage.test.tsx`: reprodução end-to-end do bug (ACTIVE sem evidência
  financeira → botão de cancelar aparece); ADMIN_GRANT efetivo não esconde
  cancelamento de um contrato remoto pagável existente; ausência de
  contrato remoto (`paymentProviderSubscription=null`) ou contrato
  CANCELED sem cobertura não oferece cancelamento.

## C — Compatibilidade de timestamp de webhook (segundos/milissegundos)

**Bug confirmado e reproduzido:** `MercadoPagoWebhookSignatureValidator`
interpretava `ts` exclusivamente como epoch-segundos. Um webhook atual (ts em
milissegundos, formato documentado pelo Mercado Pago hoje) com um HMAC
válido era rejeitado só pela checagem de freshness/replay-window — nunca
chegava a comparar o HMAC.

**Correção:** o dígito do `ts` bruto decide a unidade — exatamente 10 dígitos
(faixa `1_000_000_000..9_999_999_999`) é interpretado como epoch-segundos;
exatamente 13 dígitos (`1_000_000_000_000..9_999_999_999_999`) como
epoch-milissegundos; qualquer outro formato (branco, não numérico, sinal
negativo, contagem de dígitos inesperada, overflow de `Long`) é rejeitado.
**O `ts` usado no manifesto HMAC nunca é normalizado** — o manifesto
continua construído com o texto bruto exato recebido
(`id:<dataId>;request-id:<requestId>;ts:<raw ts>;`); apenas a conversão para
Instant usada pelo replay-window interpreta a unidade. A janela de replay
(`webhookReplayWindowSeconds`) não foi alterada, nem seus limites
mín/máx/default; o boundary continua inclusivo nos dois formatos. A
comparação do HMAC continua constant-time (`MessageDigest.isEqual`).

### Testes adicionados

`MercadoPagoWebhookSignatureValidatorTest`: assinatura válida em segundos e
em milissegundos; limite exato do replay window nos dois formatos (aceito
na borda, rejeitado logo além, nas duas direções); overflow (19 noves);
valor negativo; contagens de dígitos inesperadas (8/11/12/14/20); HMAC
continua falhando se qualquer byte do `ts` bruto mudar, nos dois formatos —
prova de que o manifesto nunca é re-derivado do valor normalizado.

## D — Hardening de logs

Auditados `BillingAutoReconciliationScheduler`, `SubscriptionCancellationSteps`,
`BillingReconciliationRunner` e os fluxos Mercado Pago equivalentes
(`RecurringBillingReconciliationService`, `MercadoPagoFinancialIngestion`,
`MercadoPagoWebhookProcessor`, `SubscriptionCancellationService`).

**Violações confirmadas e corrigidas:** três sites logavam
`exception.getMessage()` de uma falha de provider —
`BillingAutoReconciliationScheduler` (listagem de checkouts elegíveis e
`reconcileOne`), `BillingReconciliationRunner#run`, e
`SubscriptionCancellationSteps#resolveAfterUnconfirmedResponse`. Um
`MercadoPagoException` pode carregar texto do provider em `getMessage()`
(seus construtores aceitam `providerMessage` como parte da mensagem), então
nunca é seguro logar. Substituído em cada site por
`category()`/`httpStatus()`/`getSafeProviderErrorCode()` (nunca `getMessage()`
nem `getProviderMessage()`); onde a exceção capturada é um `Exception`
genérico não garantidamente `MercadoPagoException` (ex.: possível
`DataAccessException`), o fallback loga apenas
`exception.getClass().getSimpleName()`, nunca a mensagem. Os demais fluxos
auditados já só logavam razões seguras próprias (`reason=...`,
`failureCategory`, `providerErrorCode`) — nenhuma mudança necessária ali.

Nenhum token, `Authorization`, segredo de webhook, e-mail de payer, corpo
bruto do provider ou identificador desnecessário é logado em nenhum dos
sites tocados.

### Testes adicionados

Usando o padrão já existente no projeto (Logback `ListAppender`,
`BillingAutoReconciliationSchedulerTest`/`MercadoPagoFinancialIngestionTest`):
`BillingAutoReconciliationSchedulerTest#failedReconciliationLogsSafeFieldsBut
NeverTheRawProviderMessage` e
`SubscriptionCancellationServiceTest#unconfirmedResolutionFailureLogsSafeFields
ButNeverTheRawProviderMessage` — ambos injetam uma mensagem de provider
distintiva (token/e-mail fabricados) e confirmam que ela nunca aparece no
log, enquanto `category`/`httpStatus`/`providerErrorCode` aparecem.

## E — Invoice sem `periodStart`: investigado, mantido fail-closed

**Reprodução:** um teste novo,
`SubscriptionFinancialCoverageServiceTest#historicalInvoiceWithNullPeriodStart
BlocksAnOtherwiseValidCurrentPaidInvoice`, cria uma invoice histórica órfã
(`periodStart=null`, um cenário plausível de um registro deixado por
`MercadoPagoFinancialIngestion#findOrCreateInvoiceForPayment` quando o
período ainda não pôde ser derivado) coexistindo com uma invoice atual
completamente válida, PAID, com `PaymentAttempt` `APPROVED`. Resultado atual
confirmado: `SubscriptionFinancialCoverageService#evaluate` falha fechado
para a **subscription inteira** (`covered=false`, `reason=INCOMPLETE_PERIOD`)
— o cliente pagante perde acesso por causa de um registro histórico não
relacionado.

**Análise:** não existe, no modelo de dados atual
(`BillingInvoice`/`PaymentAttempt`), um fato autoritativo que prove que a
invoice incompleta não pode ser a mesma competência que a invoice "atual"
identificada por outros meios (ex.: uma invoice duplicada/conflitante para o
período atual que simplesmente não foi enriquecida ainda), nem que ela não
carrega uma reversão (refund/chargeback) que deveria ter sido aplicada à
competência atual. Ignorar silenciosamente essa linha arriscaria mascarar
exatamente esse tipo de conflito. docs/billing-recurring-contract-5g1.md
seção 3 exige textualmente: "Caso não haja termos suficientes para
determinar um intervalo verificável, manter pendência de conciliação e não
conceder período ilimitado" — e proíbe explicitamente inferir o período a
partir de `createdAt`/`observedAt`/`next_payment_date` isolados, que são os
únicos outros sinais disponíveis nessa linha.

**Decisão:** manter o comportamento fail-closed atual sem alteração de
código de produção. Documentado como limitação operacional conhecida —
recomenda-se, fora do escopo desta etapa, um job de higiene de dados que
detecte invoices órfãs sem `periodStart` e as encaminhe para conciliação
manual (reparo ou cancelamento explícito da linha), em vez de relaxar o
evaluator.

## F — `authorized_payments/search` preservado

**Violação confirmada e revertida:** o working tree (antes desta etapa) já
continha uma substituição não autorizada de
`GET /authorized_payments/search?preapproval_id=...` (paginado, com
correlação por preapproval) por `GET /v1/payments/search?external_reference=...`
em `RecurringBillingReconciliationService`, motivada por uma resposta HTTP
400 observada uma vez em staging. O próprio javadoc da classe (antes da
reversão) admitia que a alternativa não conseguia descobrir renovações — só
o pagamento inicial. Revertido para o mecanismo original: descoberta paginada
via `searchAuthorizedPayments`, correlação por `preapproval_id`, GET
individual do authorized payment, GET do payment e GET da preapproval,
preservando todas as validações de ownership/valor/moeda/período já
existentes em `MercadoPagoFinancialIngestion#fetch`. O método
`searchPaymentsByExternalReference` foi removido de `MercadoPagoClient`/
`MercadoPagoHttpClient` (nada mais o utilizava). O caminho de ingestão
âncorado em `payment` (`fetchPaymentSnapshot`/`persistFromPayment`,
correlação via `point_of_interaction.transaction_data.subscription_id`) foi
preservado intacto — é uma capacidade legítima e separada, usada apenas pelo
webhook `payment` (docs/billing-recurring-contract-5g1.md seção 17), nunca
pela reconciliação recorrente.

Observabilidade preservada: `RecurringReconciliationResult` continua
expondo apenas `failureCategory`/`failureHttpStatus`/`providerErrorCode`
(sanitizado) em falha de provider.

## G — Documentação

- `docs/billing-final-validation-5g8.md`: nota adicionada marcando a
  classificação "READY FOR STAGING" como superada por este documento
  enquanto os achados da 5G.9 não forem revalidados.
- `docs/billing-recurring-contract-5g1.md` seção 13: nota de precedência
  explícita — a política de refund parcial efetivamente implementada em
  5G.6 ("opção A": qualquer refund parcial positivo confirmado invalida a
  cobertura integral daquele attempt) prevalece sobre o texto original da
  5G.1 (que prometia preservar cobertura até o refund cumulativo atingir o
  valor total) onde os dois conflitarem.
- Este documento: registra a proteção contra segunda recorrência (A), a
  separação entitlement/cancelabilidade (B) e a compatibilidade
  segundos/milissegundos do webhook (C).

## Validação executada

- Backend completo (`./mvnw -o test`): **771 passed / 0 failed / 0 errors**,
  excluindo `RecurringBillingPersistenceTest.schemaAndJpa` (ver "Testes não
  executados" abaixo). `test-compile` limpo. Checkstyle: `0 violations`.
- Frontend: `npx tsc --noEmit` limpo (zero erros de tipo); suíte de testes
  (`react-scripts test`): **15 suítes, 149 testes, 0 falhas**; `npm run
  build`: **"Compiled successfully"**.
- Testes MySQL/Liquibase/Testcontainers: **não executados** —
  `RecurringBillingPersistenceTest.schemaAndJpa` falha com
  `IllegalStateException: Could not find a valid Docker environment` — o
  Docker Desktop deste ambiente não é alcançável pelo cliente Testcontainers
  fixado nesta versão do projeto (mesma limitação já documentada em
  docs/billing-final-validation-5g8.md, seção "Limitações"). O restante da
  suíte de persistência roda normalmente contra H2/mocks e passa.

## Riscos remanescentes apenas para homologação

- PAUSED cancelável (seção B): a permissão de cancelar a partir de PAUSED é
  apoiada pela documentação pública do Mercado Pago e pelo comportamento já
  existente de `cancelPreapproval` (PUT genérico, sem precondição de
  status), mas não foi confirmada contra um preapproval PAUSED real em
  sandbox/produção. Recomenda-se validar manualmente em staging antes do
  primeiro uso real.
- Invoices órfãs sem `periodStart` (seção E): comportamento fail-closed
  mantido intencionalmente; recomenda-se auditoria de dados em staging/
  produção para detectar se alguma invoice órfã real já existe e bloquear
  clientes pagantes hoje.
- Testes MySQL/Liquibase/Testcontainers reais não puderam ser executados
  neste ambiente (ver acima) — recomenda-se rodá-los manualmente antes de
  qualquer promoção para produção, como já recomendado na 5G.8.
