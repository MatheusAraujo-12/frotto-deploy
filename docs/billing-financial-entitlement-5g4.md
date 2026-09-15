# 5G.4 — Cobertura financeira e entitlement

Esta etapa consome a evidência financeira da [5G.3](billing-provider-ingestion-5g3.md)
e as competências da [5G.4A](billing-temporal-competency-5g4a.md), seguindo a política
da [5G.1](billing-recurring-contract-5g1.md) e as duas definições adicionais aprovadas:
continuidade não comprovada não concede grace; AWAITING_PAYMENT é somente derivado.

## Arquitetura e fonte da verdade

`SubscriptionFinancialCoverageService.evaluate(subscription, now)` retorna um
`FinancialCoverageEvaluation` imutável: covered, commercialState, relevantInvoiceId,
coverageStart, coverageEnd, gracePeriodEnd e reason. O overload sem now usa Clock.
Os enums desse resultado não são enums JPA, não alteram SubscriptionStatus e não
criam campos, migrations ou API pública.

`SubscriptionService` centraliza a seleção da assinatura efetiva para todas as
leituras do `EntitlementService`: plano, snapshot, limite, canAddVehicle e needsUpgrade.
Somente candidatos PAYMENT_PROVIDER passam pela avaliação financeira. ADMIN_GRANT
e GRANDFATHERED conservam seus critérios de estado/expiração e não precisam de invoices.

As leituras buscam todas as assinaturas do usuário, incluindo contratos cancelados:
um estado contratual cancelado pode coexistir com competência paga ainda vigente.
Não basta ACTIVE, PAST_DUE, preapproval authorized ou currentPeriodEnd futuro.

## Evidência financeira

Cobertura PAID exige simultaneamente invoice PAID e tentativa APPROVED vinculada
àquela invoice, no mesmo provider, com externalPaymentId. O predicado de comparação
de valor/moeda da 5G.3 foi extraído para `BillingPaymentEvidence` e é compartilhado
com a ingestão: não há segunda normalização, nova correlação ou regra monetária.
Tentativa APPROVED isolada não liquida invoice na leitura. Divergências, rejeição,
processamento e pendência não comprovam pagamento. `failed` não existe como enum
financeiro no modelo: o mapper existente não o transforma em aprovação.

REFUNDED, CHARGEDBACK e CANCELED na invoice não concedem cobertura nem grace de
substituição. Tentativa revertida também não satisfaz o predicado APPROVED.
Refund parcial não altera a política existente de invoice PAID. O lifecycle completo
de reversões, conflitos e recebimentos excedentes permanece fora desta etapa.

## Fonte temporal e seleção da competência

O avaliador não calcula meses, não usa debit_date, não consulta preapproval e não
altera periodStart, periodEnd, dueAt ou gracePeriodEnd. A fonte exclusiva desses
campos é a 5G.4A. Períodos são `[periodStart, periodEnd)`.

Seleção determinística:

1. Uma invoice sem periodStart não pode ser classificada temporalmente. A avaliação
   inteira da assinatura falha fechada com INCOMPLETE_PERIOD; timestamps de auditoria
   ou do provider não resolvem essa ambiguidade. Não se pressupõe que seja antiga.
2. Invoices cujo início conhecido está no futuro não substituem a competência atual,
   inclusive se ainda lhes faltar periodEnd.
3. Entre as invoices já iniciadas, a de maior periodStart é relevante. Não se recua
   para uma invoice antiga paga se a mais nova estiver vencida, incompleta ou revertida.
4. Dois registros com o mesmo início são ambíguos e não concedem cobertura. A escolha
   nunca desempata uma competência por ID, createdAt, updatedAt ou providerUpdatedAt.
5. A invoice relevante exige periodStart e periodEnd completos, com duração positiva.
   Ao atingir periodEnd, seu período deixa de cobrir acesso.

Sem invoice, ou apenas com invoice futura, não há cobertura atual. Competência
incompleta mais nova não é mascarada por uma invoice anterior paga. Ordenar por
início persistido permite que um replay de outubro não substitua novembro.

## Primeiro pagamento, renovação e estados comerciais

Sem primeira competência financeiramente paga, uma cobrança corrente não aprovada
produz AWAITING_PAYMENT e covered=false. Não há grace inicial, nem mesmo se a invoice
estiver PAST_DUE. Ausência de dados necessários produz UNRESOLVED sem cobertura.

Uma competência PAID válida cobre seu próprio intervalo e produz ACTIVE. Não se
soma mês ao contrato nem se usa horário de aprovação como início. Cada renovação é
uma invoice independente; a leitura não cria invoices ou tentativas.

Uma renovação vencida sem pagamento, com competência paga anterior, produz PAST_DUE.
PAST_DUE não significa necessariamente bloqueio: a elegibilidade do grace é avaliada
separadamente. No resultado, PAID e GRACE são razões distintas.

Os estados comerciais são derivados a cada leitura. Não se sincroniza
Subscription.status nesta etapa, evitando writes em consultas frequentes e corridas
com webhook/cancelamento. O status contratual persistido e a projeção financeira
podem diferir. A API/admin existente não foi modificada para expor a projeção;
essa exposição deve consumir FinancialCoverageEvaluation em uma etapa posterior.

## Continuidade e grace: decisão aprovada

Somente a competência imediatamente anterior, completa e financeiramente paga,
pode sustentar grace. Exige-se `previous.periodEnd == renewal.periodStart` em Instant.
Uma tentativa aprovada válida também precisa comprovar aprovação antes dessa fronteira
por approvedAt. Timestamp de aprovação ausente ou aprovação somente após o período
anterior terminar não fabrica cobertura anterior retroativa. approvedAt é evidência
da liquidação; a ordem e os limites de competência continuam vindo apenas da 5G.4A.
Empate temporal no predecessor também falha fechado. Não se encadeia grace de meses
não pagos, não se usa qualquer pagamento histórico distante e não existe tolerância,
arredondamento, ajuste de offset ou extensão artificial de período.

Exemplo aprovado:

```text
anterior PAID: [28/fev, 28/mar)
renovação:    [31/mar, 30/abr)
```

A renovação não paga não herda grace. Se ela posteriormente ficar PAID, poderá cobrir
seu próprio intervalo `[31/mar, 30/abr)`. A lacuna afeta a herança de grace, não a
validade financeira independente da nova competência paga. Há teste explícito das
duas fases do mesmo cenário.

Grace exige invoice corrente PENDING/PROCESSING/PAST_DUE, dueAt e gracePeriodEnd
persistidos, competência válida e continuidade contratual. Cancelamento solicitado
ou confirmado e contrato PAUSED/CANCELED impedem grace de renovação.
Tentativa com valor/moeda divergente em renovação não liquidada resulta em
FINANCIAL_CONFLICT sem grace. Uma liquidação válida independente continua soberana:
uma retry divergente não apaga uma invoice comprovadamente paga por outra tentativa.

A validação temporal do modelo já estabelece gracePeriodEnd = dueAt + 72 horas.
O avaliador reutiliza `BillingInvoice.isPeriodValid()` para rejeitar limites inválidos,
sem corrigir ou preencher campos, e consome o limite persistido:

```text
dueAt <= now < gracePeriodEnd: pode cobrir por GRACE, se elegível
now >= gracePeriodEnd: sem cobertura de grace
```

Um milissegundo antes pode cobrir; no instante exato e um milissegundo depois não
cobre. Falta de webhook ou atraso de processamento não prolonga esse limite.

## Recuperação e pagamento tardio

A aprovação válida durante grace passa a fornecer PAID/ACTIVE. Depois do grace,
ela ainda pode restaurar cobertura se now pertencer ao intervalo original. Após
periodEnd, quita apenas a competência histórica e não concede acesso atual.
Nunca se cria paymentTime + 30 dias, now + mês ou extensão de currentPeriodEnd.

## Cancelamento 5F

O PUT e os guardas da 5F permanecem intactos, inclusive `cancelled` com dois L.
Cancelar não apaga PAID. Uma competência paga anterior à confirmação mantém acesso
até seu periodEnd exclusivo, mesmo com Subscription.status=CANCELED.

Quando canceledAt está disponível, uma competência que começa nele ou depois dele não pode
reativar uma renovação. canceledAt restringe a elegibilidade; não fabrica período.
Para intenção pendente/registro legado sem timestamp de confirmação, o limite
currentPeriodEnd da 5F restringe as invoices elegíveis, mas nunca prova pagamento.
Sem esse limite, a restrição falha fechada. Invoices futuras posteriores ao limite
não concedem renovação. Não há chamada HTTP para confirmar cancelamento na leitura.

## Precedência e Clock

`ADMIN_GRANT válido > PAYMENT_PROVIDER coberto > GRANDFATHERED válido > FREE`.
Uma concessão administrativa válida dispensa consultas financeiras. Expiração de
grant ou grace aplica o fallback sem scheduler. Todos os candidatos usam o mesmo
Instant em cada resolução; produção usa Clock.systemUTC(), testes usam Clock.fixed().
O Clock é recebido por construtor conforme o padrão já usado na 5F.

## Persistência, concorrência e invariantes

O avaliador e a seleção são transações somente leitura. A consulta nova de tentativas
é restrita ao subscriptionId e não reutiliza consultas com PESSIMISTIC_WRITE da ingestão.
No MySQL, o snapshot transacional REPEATABLE READ mantém as leituras financeiras
consistentes. A ingestão continua responsável pela atualização atômica das evidências,
pelos locks por assinatura, idempotência e @Version. Nenhum lock global foi adicionado.

Sem writes, duas avaliações do mesmo snapshot/Instant produzem o mesmo resultado;
um replay não soma períodos, altera Subscription ou reinicia grace. A decisão depende
das competências, não da ordem de entrega dos eventos. O próximo snapshot observa
aprovação/reversão confirmada na transação financeira seguinte.

Não há migration, frontend, preços/limites novos, checkout, scheduler, polling,
backfill, commit, push, deploy ou chamada real ao Mercado Pago nesta entrega.

## Validação e pendências

Os testes cobrem primeiro pagamento e ausência de aprovação, limites do período e
grace, recuperação e atraso, ordering, dados incompletos, lacuna/continuidade,
cancelamento, divergência financeira e precedência. A integração local usa Liquibase,
MySQL 8.0.36 descartável e repositories reais, sem inicializar aplicação/provider.
As regressões 5F, 5G.2, 5G.3 e 5G.4A devem passar junto da suíte backend completa.

Para 5G.5: projeção financeira explícita para API/admin; reconciliação/descoberta de
competências ausentes; tratamento operacional de invoices sem âncora e conflitos;
lifecycle completo de refund/chargeback. Ausência de invoice de renovação não gera
grace fictício. Não há backfill automático de contratos legados autorizados sem
evidência financeira: eles deixam de ser fonte de entitlement pago.

### Resultado local — 2026-09-15

- JDK 17; `mvnw.cmd -Dapi.version=1.44 -Djacoco.append=false verify`: BUILD SUCCESS.
- Suíte completa: 609 testes, zero falhas/erros/ignorados.
- Subconjunto focado na execução final: 307 testes aprovados, incluindo 56 do novo
  avaliador e 10 de integração com entitlement. A rodada focada anterior teve 293
  testes aprovados; os casos adicionados na revisão final estão na suíte de 609.
- MySQL 8.0.36/Testcontainers: 20 testes aprovados com Liquibase real; database `test`
  em localhost:58717 na execução final, descartada ao concluir. Docker Desktop local.
- JAR gerado, zero violações Checkstyle e `git diff --check` sem erros.
- Foram adicionados 72 casos: 56 coverage, 10 entitlement, 4 ingestão→coverage e 2 MySQL.
- Avisos Maven preexistentes de dependency convergence, modelo de javassist e parâmetro
  resources somente leitura não impediram o build; nenhuma dependência foi alterada.
- Nenhuma chamada real ao Mercado Pago, staging ou produção. Nenhum commit ou staging de arquivos.
- O workspace recebeu mudanças externas concorrentes de logo/perfil, inclusive no
  frontend e Liquibase. Elas não pertencem à 5G.4 e foram preservadas; esta etapa não
  criou nem modificou migrations ou arquivos do frontend.
