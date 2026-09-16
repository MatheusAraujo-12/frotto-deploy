# 5G.6 — Refund e chargeback

A 5G.6 atualiza a validade da evidência financeira de cada PaymentAttempt.
Não modifica preço, matching bruto, competências, política de renovação, enums
persistidos, API pública ou precedência das fontes de assinatura.

## Modelo e matching

Não há migration: PaymentAttempt já possui amount, refundedAmount DECIMAL(21,2),
status, providerStatus, statusDetail, approvedAt e providerUpdatedAt, além de
identidades únicas e @Version. BillingInvoice já distingue PAID, REFUNDED e
CHARGEDBACK. Os mesmos registros são atualizados, sem deletar o pagamento
original ou criar uma competência para representar a devolução.

Mantém-se a igualdade econômica BigDecimal do bruto com o valor da invoice e a
igualdade da moeda normalizada. Para um attempt válido, o líquido é bruto menos
valor devolvido confirmado. Como o bruto precisa ser exatamente igual ao devido,
qualquer refund positivo impede aquele attempt de cobrir integralmente a invoice.
A implementação verifica essa condição equivalente sem somar attempts.

| Invoice | Bruto | Refund | Evidência integral |
| ---: | ---: | ---: | --- |
| 15,90 | 15,90 | 0 | Sim, se APPROVED e demais requisitos válidos |
| 15,90 | 15,90 | 0,10 | Não |
| 15,90 | 15,90 | 15,90 | Não |
| 15,90 | 16,00 | 0,10 | Não: matching bruto divergente |

Não há soma de retries, compensação entre períodos ou aceitação de sobrepagamento.
Diferenças de scale não mudam o resultado. O teste anterior de 16,00 contra 15,90
permanece intacto. O teste antigo que aceitava refund parcial foi atualizado
explicitamente para a opção A aprovada na 5G.6.

## Refund total, parcial e chargeback

REFUNDED e CHARGEDBACK do payment continuam mapeados para estados internos
distintos. Ambos deixam de ser evidência de pagamento válido. Refund parcial
pode manter o payment APPROVED no provider, com status_detail partially_refunded:
por isso a cobertura também verifica refundedAmount e esse marcador.
Referência pública: [estados de Payments do Mercado Pago](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-payments/response-handling/collection-results/introduction?scope=prod).

Uma invoice continua PAID se existir outro attempt independente que satisfaça
individualmente a regra. Na ausência de evidência válida, a agregação distingue
CHARGEDBACK e REFUNDED; CHARGEDBACK tem prioridade descritiva se ambos existirem.
REFUNDED na invoice também abrange refund parcial que deixou cobertura insuficiente;
os valores e estados específicos permanecem em cada attempt. Não significa que
todos os attempts ou todo o valor da invoice tenham sido devolvidos.

O evaluator verifica reversões também em dados já persistidos, mesmo se o status
da invoice ainda estiver PAID ou não tiver sido atualizado pela nova ingestão.
Uma reversão não recebe grace substituto. Uma outra competência PAID vigente
continua sendo avaliada pelo seu próprio período, sem ser invalidada por refund
de uma competência anterior.

## Auditabilidade e ordering

Preservam-se ID do attempt, vínculo com invoice, valor bruto, data original de
aprovação e paidAt histórico da invoice durante as transições normais de estorno.
Status financeiro atual, status do provider, valor devolvido e providerUpdatedAt
registram o estado observado. Não se apaga o attempt nem se troca seu ID.

O modelo é de snapshot evolutivo, não um ledger imutável de todas as versões.
@Version protege concorrência, mas não é histórico de auditoria. IDs individuais
de refunds, histórico completo de alterações e gestão de disputa não são criados
nesta etapa. refundedAt da invoice não é inventado a partir de date_last_updated:
esse timestamp indica atualização do payment, não necessariamente a data do refund.

O timestamp autoritativo do payment precisa ser estritamente posterior ao
providerUpdatedAt já persistido. Replay igual/antigo não desfaz refund ou chargeback.
Horário local de recebimento nunca ordena fatos financeiros. Uma aprovação
posterior incompleta, sem refundedAmount, não apaga uma reversão conhecida.
Não se cria uma regra de irreversibilidade baseada no horário local: snapshots
posteriores completos continuam sujeitos à máquina de estados existente.

## Dados ausentes ou inválidos

Observações legadas APPROVED sem refundedAmount e sem marcador de refund mantêm
a compatibilidade da 5G.3; ausência do campo, sozinha, não comprova um estorno.
Isso não converte NULL em um valor devolvido confirmado. REFUNDED/CHARGEDBACK
invalidam evidência pelo estado mesmo quando não há valor devolvido informado.

Um marcador partially_refunded sem valor positivo consistente é uma resposta
inconclusiva: a ingestão rejeita a atualização antes de gravar invoice/attempt.
Valores negativos, acima do bruto, com fração monetária inválida ou fora da
capacidade do campo também são rejeitados. JSON malformado nesse campo não é
silenciosamente convertido em ausência de refund.

Falhas HTTP, timeout, 404, 5xx, 429 ou resposta inválida preservam o último estado
financeiro conhecido. Não se revoga cobertura por um payload de webhook não
confirmado. O resultado operacional é registrado sem payloads ou segredos.

## Webhook e reconciliação

O endpoint e a validação de assinatura permanecem iguais. Os tipos payment e
subscription_authorized_payment já entram na ingestão financeira. Payment usa
GET payment, correlação única por payment_id e confirmação individual do
authorized payment; zero/múltiplas/divergentes correlações continuam fail-closed.

A reconciliação 5G.5 descobre IDs por preapproval_id e consulta authorized payment,
payment referenciado e preapproval antes da persistência. O GET payment existente
já confirma refund/chargeback, sem chamada adicional introduzida pela 5G.6.
Reserva, budgets, DISCOVERY_INCOMPLETE e interrupção por 429 permanecem iguais.
Search nunca comprova pagamento ou estorno. Ambos os caminhos usam a mesma
ingestão e o mesmo predicado de evidência; entitlement continua na 5G.4.

Não foi adicionado handler para tópicos autônomos de refunds/chargebacks com IDs
de recursos diferentes de payment. Também não se promete redescobrir todos os
payments históricos de retries que deixaram de ser referenciados pelo authorized
payment. A convergência sem webhook foi validada quando esse recurso referencia
o payment alterado. Históricos fora do orçamento ou correlação autoritativa
indisponível conservam as limitações de discovery da 5G.5.

## Validação e pendências

Testes cobrem bruto divergente, refund parcial/total, chargeback, replay nas duas
ordens, timestamp antigo/igual, outro attempt válido, ausência de soma de retries,
preservação de períodos, ausência de grace artificial, fallback de fontes,
respostas incompletas e budgets. MySQL/Testcontainers exercita commit e nova
sessão/novo serviço entre aprovação, reversão, replay e aprovação antiga.

ADMIN_GRANT e GRANDFATHERED não são gravados pela ingestão e mantêm precedência.
Não há frontend, refund solicitado pelo usuário/admin, chamada autenticada ao
provider, cobrança, deployment ou alteração de env real.

Para 5G.7: investigar histórico financeiro além do payment referenciado e dos
limites de discovery, observabilidade de dados incompletos e necessidade de
ledger de auditoria. Encerramento definitivo de polling de canceladas e bloqueio
global persistente de 429 continuam adiados. Não há mudança de política comercial
para esses casos nesta etapa.
