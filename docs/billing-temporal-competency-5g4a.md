# 5G.4A — Enriquecimento temporal da competência

Esta etapa implementa somente a origem temporal da competência. Não concede entitlement,
não executa grace, não modifica Subscription, pricing, cancelamento ou frontend.

## Contrato e fontes

A regra aprovada para 5G.4A é uma **derivação contratual do Frotto**:

- Authorized Payment identifica a parcela/fatura do provider.
- `authorized_payment.debit_date` é a âncora programada dessa parcela adotada pelo Frotto.
- `preapproval.auto_recurring.frequency` e `frequency_type` fornecem a recorrência.
- `periodStart = debitDate`.
- `periodEnd = debitDate + frequency` em calendário.
- Intervalo semiaberto: `[periodStart, periodEnd)`, início inclusivo e fim exclusivo.
- `dueAt = debitDate`.
- `gracePeriodEnd = dueAt + Duration.ofHours(72)`.

O Mercado Pago não é apresentado como fornecedor direto de periodStart/periodEnd.
Esses limites são derivados pelo Frotto com a regra acima. A 5G.4A resolve a lacuna de
origem temporal documentada pela 5G.3, sem tornar persistência sinônimo de entitlement.

Não são fontes substitutas: date_created, last_modified, next_payment_date, horário do
webhook ou Instant.now(). Não se usa pagamento aprovado +30 dias para criar intervalo.
A regra desta etapa opera por parcela, a partir do debit_date dessa própria parcela;
não encadeia o fim da invoice anterior nem reconstrói uma âncora global a partir de datas
operacionais. O contrato específico aprovado aqui define a derivação temporal usada
por esta implementação.

## Tipos suportados e calendário

A integração existente cria checkout MONTHLY e envia frequency=1/frequency_type="months".
O enriquecimento suporta exclusivamente a unidade `months`, com frequência inteira
positiva, inclusive 2 meses. Unidades `years`, `days`, valores desconhecidos, zero,
negativos, números fracionários e frequências ausentes não completam a competência.
Não foi adicionado fluxo anual. Doze meses continuam sendo doze meses de calendário,
sem introduzir uma unidade nova no contrato.

Soma: `OffsetDateTime.plusMonths(frequency)`. O calendário ajusta um dia inexistente
para o último dia válido do mês de destino. Cada parcela deriva diretamente da sua
própria âncora; replay não executa existingPeriodEnd + frequency.

Exemplos:

| debit_date | frequency | fim derivado no mesmo offset |
| --- | --- | --- |
| 2026-01-31T12:00:00Z | 1 month | 2026-02-28T12:00:00Z |
| 2024-01-31T12:00:00Z | 1 month | 2024-02-29T12:00:00Z |
| 2026-01-31T12:00:00Z | 2 months | 2026-03-31T12:00:00Z |

Resultados fora do intervalo suportado por datetime(6) do MySQL não são persistidos.
Não existe conversão alternativa para duração fixa quando o cálculo é inválido.

## Offset e persistência

O parser mantém `debitDateWithOffset` em OffsetDateTime, além do getDebitDate() legado
em Instant. A soma de meses usa primeiro a data/hora civil e o offset explícito recebido.
Só depois converte o resultado para Instant. Não aplica timezone padrão da máquina,
America/Sao_Paulo presumido ou UTC antes da soma.

Um offset numérico não identifica região geográfica. Por isso o cálculo mantém o offset
fixo fornecido e não inventa regras regionais de horário de verão para o próximo limite.

Exemplo que distingue calendário civil e UTC:

- débito: `2026-01-31T23:30:00-03:00`;
- início persistido: `2026-02-01T02:30:00Z`;
- fim civil mensal: `2026-02-28T23:30:00-03:00`;
- fim persistido: `2026-03-01T02:30:00Z`.

Os quatro campos da BillingInvoice continuam sendo Instant. Offset ausente ou data
malformada não são substituídos por UTC. Os construtores legados que recebem somente
Instant continuam compatíveis, mas não permitem derivar calendário sem offset original.

## DTOs e fluxo

MercadoPagoPreapproval passa a carregar somente os dois campos adicionais necessários:
frequency e frequencyType de auto_recurring. nextPaymentDate já existia e permanece
independente da competência atual. start_date/end_date contratuais não são adicionados
porque a regra aprovada não os usa como âncora, substituto ou truncamento do intervalo.
Também não são alterados valor/moeda ou termos de preço para calcular datas.

MercadoPagoInvoiceTemporalEnricher concentra a derivação e a proteção de datas. É uma
função sem relógio, acesso HTTP ou persistência próprios, chamada por
MercadoPagoFinancialIngestion na mesma transação e sob os locks já existentes da 5G.3.
Ela compara a versão da cobrança antes de atualizar providerUpdatedAt da invoice.

- subscription_authorized_payment: GET da parcela → GET de payment, quando identificado
  → GET de preapproval → Subscription/invoice → enriquecimento.
- payment: GET financeiro → correlação única com authorized payment → confirmação no
  GET individual → preapproval → Subscription/invoice → enriquecimento.
- subscription_preapproval sozinho: não cria invoice nem competência.

Nenhuma chamada HTTP adicional foi introduzida. Pagamento isolado, inclusive eventual
validação de cartão sem parcela recorrente correlacionável, não gera competência.

## Dados incompletos e primeira âncora

Sem debit_date válido com offset, nenhuma âncora é inventada. Datas já persistidas são
preservadas. Se uma invoice já possui dueAt confiável e só falta gracePeriodEnd, o limite
pode ser preenchido por +72 horas.

Se debit_date é conhecido, mas frequency/frequencyType ainda não permitem completar a
competência, preservam-se periodStart, dueAt e gracePeriodEnd; periodEnd permanece null.
Essa gravação parcial conserva a primeira âncora autoritativa observada. Ela não prova
cobertura e não concede acesso. Para completar depois, uma observação deve confirmar a
mesma âncora e fornecer recorrência válida. O offset usado para o cálculo vem dessa
observação autoritativa; depois de preenchido, periodEnd não é recalculado nem substituído.

## Replay, retries e conflitos

A identidade da invoice continua provider + external_authorized_payment_id. Uma retry
não cria competência nova. Os campos existentes nunca são sobrescritos pelo enriquecedor.

- Snapshot mais antigo que providerUpdatedAt da mesma cobrança: não enriquece.
- Snapshot sem versão quando já existe versão persistida: não enriquece.
- Mesma versão com dados antes ausentes: pode preencher campos nulos, se consistente.
- Payload incompleto: não apaga datas conhecidas.
- Mudança em debit_date, frequência ou limite que contradiga qualquer data já conhecida:
  retorna CONFLICT, mantém os quatro campos e registra `reason=competency_mismatch` com
  invoiceId. Não registra payload, credenciais ou dados completos do pagamento.
- Nenhuma divergência é corrigida automaticamente. Não se reinicia grace em retry.

O conflito temporal é registrado no log e não altera o protocolo financeiro da 5G.3:
resultados de pagamento continuam sendo auditados. Não foi criado mecanismo persistido
novo de resolução de conflitos; uma correção explícita deve ser revisada fora deste fluxo.

## Invariantes e limites

1. Datas temporais não concedem entitlement, mesmo em invoice PAID.
2. Amount/currency mismatch continua impedindo a liquidação válida pela regra 5G.3.
3. Grace é duração exata de 72h: dueAt 2026-10-09T14:56:39Z produz
   2026-10-12T14:56:39Z. Não são três dias civis.
4. Horário do processamento, inclusive diferenças de 1ms, não muda a competência.
5. Datas completas exigem evidência autoritativa suficiente; não há fallback por heurística.
6. Offset é preservado durante a soma, Instant durante a persistência.
7. Cancelamento 5F, inclusive payload cancelled, permanece intacto.
8. Nenhuma migration, backfill, scheduler, entitlement ou grace enforcement foi criado.

## Validação

Testes de calendário cobrem frequência 1/2, janeiro dia 31, ano bissexto, offsets positivos
e negativos atravessando dias UTC, milissegundos e grace exato. Testes de parser cobrem
recorrência ausente/inválida e debit_date sem offset. Testes da ingestão cobrem os dois
fluxos financeiros, replay, retry divergente com log, versão antiga, payload incompleto,
amount/currency mismatch e ausência de correlação. As regressões de preapproval e 5F
permanecem na suíte. O teste MySQL inclui enriquecimento de invoice inicialmente sem
datas, flush, recarga, replay e comprovação dos limites persistidos.

### Resultado local — 2026-09-15

- JDK 17; testes focados: 205 aprovados, zero falhas/erros/ignorados.
- RecurringBillingPersistenceTest em MySQL 8.0.36 descartável local: 18 aprovados.
- Suíte backend completa: 533 aprovados, zero falhas/erros/ignorados.
- `mvnw.cmd -Dapi.version=1.44 verify`: BUILD SUCCESS; JAR empacotado e zero violações Checkstyle.
- Docker usado somente pelo pipe local do Docker Desktop; opção de API limitada à execução.
- `git diff --check`: exit code 0.
- Nenhuma API real do Mercado Pago, staging ou produção foi acessada.
