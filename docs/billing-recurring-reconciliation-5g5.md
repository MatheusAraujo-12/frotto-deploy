# 5G.5 — Reconciliação financeira recorrente

A reconciliação descobre fatos financeiros perdidos para subscriptions locais
`PAYMENT_PROVIDER` / `MERCADO_PAGO` com `externalSubscriptionId` preenchido.
Não depende de `BillingCheckout`. O scheduler de checkout 5E.3 permanece separado.
A funcionalidade nasce desabilitada; este documento não autoriza ativação remota.

## Reserva persistente

`Subscription.lastFinancialReconciliationAt` representa o instante em que uma
tentativa foi **reservada para iniciar**, não o último sucesso. É o único campo
novo: `last_financial_reconciliation_at datetime(6) NULL`, sem backfill.

A seleção filtra source, provider e cutoff e ordena pelo timestamp mais antigo,
depois pelo ID local. Usa projeção SQL escalar, sem depender do cache ORM.
O índice `idx_subscription_financial_reconciliation` contém
`(source, external_provider, last_financial_reconciliation_at)`.

Uma atualização SQL condicional em transação `REQUIRES_NEW` confirma a identidade
local/provider e o cutoff. Somente retorno 1 autoriza HTTP; retorno 0 produz
`SKIP_ALREADY_RESERVED`. A reserva é confirmada antes de qualquer HTTP e permanece
após busca vazia, timeout, 404, 5xx, falha operacional ou restart. Nova elegibilidade
é calculada por timestamp + intervalo mínimo. Subscriptions existentes começam
com NULL e entram gradualmente nos limites do lote e do orçamento.

O atributo JPA é `insertable=false, updatable=false`, sem setter operacional:
saves comuns de uma entidade carregada antes da reserva não sobrescrevem o campo.
Não há `@Version`, lease, cursor ou segunda coluna. A reserva limita a aquisição
por janela; não é um lock distribuído que impeça sobreposição de execuções que
ultrapassem o intervalo mínimo. A ingestão mantém seus locks e identidades únicas.

## Discovery e ingestão

1. Reservar subscription em transação curta independente.
2. `GET /authorized_payments/search?preapproval_id=...&offset=...&limit=...`.
3. Validar `paging.offset`, `paging.limit`, `paging.total`, `results` e progresso.
4. Deduplicar IDs e processá-los em ordem lexical local determinística.
5. Consultar cada `GET /authorized_payments/{id}` e exigir `preapprovalId` igual
   ao da subscription selecionada, antes dos demais GETs.
6. Buscar payment (quando indicado) e preapproval pela lógica compartilhada 5G.3.
7. Persistir o snapshot pelo proxy transacional da mesma ingestão 5G.3.

O fluxo novo suspende qualquer transação externa antes de HTTP. A gravação recebe
também o ID local esperado, confirmado novamente sob o lock da ingestão.
A entrada de webhook existente reutiliza o mesmo corpo de persistência; sua
demarcação transacional anterior não foi ampliada pela reconciliação.

Search fornece somente IDs, nunca prova de pagamento. Não se usa `sort`, `criteria`,
filtro temporal ou ordenação presumida do provider. A ordem lexical apenas torna
o processamento local reproduzível; não representa ordem temporal financeira.
`providerUpdatedAt`, correlação, valor/moeda, idempotência e unique constraints
continuam sendo tratados pela 5G.3; competências pela 5G.4A; cobertura pela 5G.4.
Nenhuma regra de grace, enum persistido ou API pública foi alterada.

## Configuração

Todas as variáveis abaixo usam o prefixo `BILLING_RECURRING_RECONCILIATION_` e
correspondem a `billing.recurring-reconciliation` no application.yml.

| Sufixo | Default | Faixa aceita | Escopo |
| --- | ---: | --- | --- |
| ENABLED | false | boolean | Habilitação; provider também precisa estar configurado |
| INTERVAL_MINUTES | 15 | 1–1440 | Fixed delay entre execuções do scheduler |
| MIN_INTERVAL_MINUTES | 60 | 1–10080 | Intervalo mínimo por subscription |
| BATCH_SIZE | 10 | 1–100 | Subscriptions selecionadas por execução |
| PAGE_SIZE | 20 | 1–100 | Tamanho máximo solicitado por página |
| MAX_PAGES | 5 | 1–100 | Páginas por subscription |
| MAX_DISCOVERED_ITEMS | 100 | 1–1000 | IDs distintos por subscription |
| MAX_HTTP_CALLS | 100 | 1–1000 | Todos os GETs da execução, compartilhados pelo lote |

O limite HTTP conta search, authorized payment, payment e preapproval antes de
cada chamada. Não há retry imediato. Configuração fora das faixas é rejeitada
antes de iniciar a reconciliação.

## Resultados e limites

O resultado contém subscription ID local, outcome, descobertos, ingeridos,
ignorados e quantidade de chamadas. Logs operacionais não incluem tokens,
payloads ou mensagens de exceção do provider.

`DISCOVERY_INCOMPLETE` sinaliza limite de páginas, itens ou HTTP. Nunca é reportado
como COMPLETE quando o orçamento impede concluir o trabalho. IDs já descobertos
podem ser ingeridos se houver orçamento. Falhas subsequentes podem resultar em
outcome operacional específico, sem declarar completude. COMPLETE significa
conclusão da tentativa dentro da resposta/paginação observada, não confirmação
de adimplência nem garantia de snapshot estável do provider.

Cada tentativa reinicia em offset 0. Não há cursor ou garantia de varredura completa
de históricos maiores que o orçamento. Alterações concorrentes no provider podem
mudar páginas; respostas incoerentes são rejeitadas, sem inferir fatos financeiros.

429 interrompe novas chamadas provider em toda a execução atual. Não há bloqueio
global persistente entre instâncias ou execuções. Outros erros resultam em falha
operacional e preservam fatos anteriores; não fabricam inadimplência, competência,
invoice ou PaymentAttempt a partir de falhas de transporte.

## Cancelamento e pendências 5G.7

Subscriptions canceladas continuam elegíveis sob os mesmos limites e reserva.
Não existe prazo arbitrário, nem uso de periodEnd/gracePeriodEnd como prova de
ausência de fatos tardios. Reconciliar não reativa a subscription: preapproval
authorized sozinho não concede cobertura; competência PAID válida respeita seu
próprio período pela 5G.4.

Pendências explícitas para 5G.7: política comprovável de encerramento definitivo
de canceladas, rate limit entre execuções/instâncias e estratégia para históricos
que excedam o orçamento. Esta etapa não adiciona infraestrutura para esses casos.

## Validação local

Testes usam provider mockado e a infraestrutura MySQL 8.0.36/Testcontainers já
existente. Cobrem descoberta de primeiro pagamento e renovação sem webhook,
recovery, webhook/reconciliação nas duas ordens, correlação, budgets e falhas.
O teste MySQL aplica a migration incrementalmente sobre uma subscription legada,
verifica coluna e índice, disputa de duas instâncias, restart e save ORM antigo.
Nenhum teste requer API real Mercado Pago, staging ou produção.
