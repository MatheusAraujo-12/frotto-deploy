# 5G.2 — Fundação de persistência recorrente

Implementa somente `Subscription <- BillingInvoice <- PaymentAttempt`, conforme
[contrato 5G.1](billing-recurring-contract-5g1.md). Nenhum produtor ou consumidor
financeiro foi conectado ao modelo. Persistir APPROVED não liquida uma invoice,
não altera outra competência e não muda Subscription ou entitlement.

## Modelo

- BillingInvoice contém período de serviço, vencimento, fim da tolerância, valor
  esperado/moeda, estado, datas financeiras, identidade/versão temporal do provider
  e última reconciliação. O período identifica a competência mesmo sem ID externo.
- PaymentAttempt contém identidade da tentativa/pagamento, estado normalizado e
  status original, detalhe técnico limitado a 128 caracteres, valor/moeda,
  valor cumulativo reembolsado e datas próprias. `statusDetail` é reservado a código
  seguro normalizado; não deve receber mensagem livre ou payload remoto.
- Ambos usam Instant, auditoria local por PrePersist/PreUpdate e versão otimista
  JPA (`@Version`), independente de providerUpdatedAt. Nenhum timestamp financeiro
  é inferido do status ou da data de persistência.
- Valores seguem o padrão existente BigDecimal / DECIMAL(21,2), com validação
  de não negatividade e precisão. Moeda é explícita, em três letras maiúsculas;
  não há default BRL. Este armazenamento suporta valores com até duas casas,
  não pretende cobrir moedas que exijam três casas sem evolução do schema.

Sem externalReference duplicada: a correlação da assinatura já existe no contrato
e a identidade interna da competência é seu período. `refundedAt` na invoice
representa a reversão total; `refundedAmount` na tentativa permite snapshot de
refund parcial. Não há rejectedAt redundante: a evolução remota possui
providerUpdatedAt. O histórico detalhado de eventos/reversões será modelado quando
o processamento financeiro for implementado; estes registros são snapshots.

## Identidade e relações

| Garantia | Constraint |
| --- | --- |
| Uma invoice por período exato da assinatura | ux_billing_invoice_period(subscription_id, period_start, period_end) |
| Uma cobrança remota por namespace | ux_billing_invoice_provider_id(provider, external_authorized_payment_id) |
| Um pagamento real por namespace | ux_payment_attempt_provider_id(provider, external_payment_id) |
| Uma tentativa remota estável antes do payment ID | ux_payment_attempt_provider_attempt(provider, external_attempt_id) |

MySQL permite múltiplos NULLs nas chaves externas únicas; invoices internas de
períodos distintos são legítimas. Isso não permite duas invoices para o mesmo
período: a constraint interna continua valendo. Uma tentativa exige payment ID ou
externalAttemptId estável do provider, validado no domínio; não inventar IDs por
consulta. Descoberta posterior preenche o mesmo registro.

As FKs obrigatórias usam RESTRICT. JPA usa ManyToOne LAZY, sem cascade e sem
orphanRemoval. Não adiciona coleção em Subscription: as relações 1:N são consultadas
pelos repositories, preservando também sua serialização atual. Excluir um filho
nunca exclui pai; excluir pai com filhos falha por FK. Provider, vínculo parental,
período e vencimento não são atualizáveis pelo JPA.

Unicidade é garantia do banco, não uma busca seguida de insert sem proteção.
O pipeline futuro deverá tratar corrida recarregando a identidade existente;
não foi criado service de upsert nem tratamento genérico de erro de integridade.
Unicidade do período exato não impede intervalos parcialmente sobrepostos: validar
agenda e serializar criação por assinatura continua sendo obrigação do futuro
escritor transacional da 5G.1, antes de integrar estes repositories aos fluxos.
Não existe concessão ou liquidação lógica automática nesta etapa.

## Índices e consultas

Invoice: índice único iniciado por subscription_id atende listagem da assinatura;
índices `(status,due_at)`, `(status,grace_period_end)` e `last_reconciled_at` suportam
consultas paginadas. Tentativa: índice billing_invoice_id e chaves externas únicas.
Nenhum scheduler foi adicionado. As consultas não decidem elegibilidade de tolerância.

Validação estrutural da invoice exige período positivo, dueAt igual ao início
conforme 5G.1 e gracePeriodEnd exatamente dueAt + 72h. Não calcula acesso, não muda
status e não reinicia períodos. A validação Bean Validation complementa FKs,
NOT NULL e UNIQUE; SQL direto não executa as validações Java.

## Migration e compatibilidade

`20260911000000_add_recurring_billing_model.xml` cria somente as duas tabelas,
constraints e índices, em dois changesets novos. master.xml recebe um include.
Não há alteração de migration antiga, backfill, mudança em subscription ou
inferência de pagamento para dados legados.

## Testes e execução

RecurringBillingPersistenceTest usa MySQL 8.0.36 descartável via Testcontainers,
as migrations de produção e repositories JPA reais, sem iniciar aplicação ou
provider. Instala schema anterior, insere assinatura existente e aplica a nova
migration. Cada teste usa transação revertida.

Valida SET/OUT/NOV independentes e múltiplos retries, ausência de liquidação
automática, unicidade e namespace, IDs inicialmente ausentes, descoberta posterior,
precisão monetária, datas distintas, consultas, ausência de cascades destrutivos,
FKs e cancelamento agendado com competência paga futura.

Requer Docker. Com a versão de Testcontainers já existente no projeto e Docker 29,
usar `-Dapi.version=1.44` na execução Maven para compatibilidade do cliente Docker;
não foi alterado pom.xml nem a configuração global do Docker.

```text
mvnw.cmd -Dapi.version=1.44 -Dtest=RecurringBillingPersistenceTest test
mvnw.cmd -Dapi.version=1.44 test
```

Validação local em 2026-09-11 com JDK 17: 15 testes focados e 397 testes na suíte
completa, sem falhas, erros ou skips. Ambos terminaram com BUILD SUCCESS.
