# 5G.1 — Contratos e política financeira de recorrência

Especificação normativa para implementação a partir da 5G.2. Data: 2026-09-11.
Branch de referência: `staging/security-prebilling`. Este documento não descreve
capacidades já entregues: define o comportamento futuro e seus critérios de aceite.
As políticas comerciais fornecidas nesta etapa são a base normativa. Nomes de
estados e campos abaixo são conceituais; não adicionam enums nem mudam APIs atuais.

## 1. Objetivos e princípios

Separar contrato recorrente, mensalidade e tentativa de pagamento; conceder acesso
somente por cobertura paga comprovada ou tolerância finita de renovação. Preservar
histórico, cancelamento existente e independência de ADMIN_GRANT. Todo processamento
financeiro deve convergir ao mesmo resultado por webhook ou reconciliação.

O sistema atual possui lacunas identificadas na auditoria: ativação por autorização,
uso de próxima cobrança como período pago, ausência de histórico de competências e
reconciliação limitada ao checkout. Esta especificação substitui essas premissas
no desenho futuro, sem alterar sua implementação nesta etapa.

## 2. Glossário e convenções temporais

| Termo | Definição normativa |
| --- | --- |
| Subscription | Contrato de recorrência de um usuário e plano |
| BillingInvoice / competência | Obrigação financeira de um intervalo de serviço específico |
| PaymentAttempt | Tentativa identificável de liquidar uma única competência |
| authorized payment | Recurso de cobrança recorrente do provider; não é sinônimo de pagamento aprovado |
| Evidência autoritativa | Recurso consultado pela API autenticada do provider e validado localmente |
| dueAt | Instante imutável de vencimento da competência |
| servicePeriod | Intervalo semiaberto `[servicePeriodStart, servicePeriodEnd)` |
| paidAt | Instante da aprovação financeira informado pelo provider, não o recebimento do webhook |
| providerUpdatedAt | Versão temporal do recurso no provider, comparável somente para o mesmo recurso |
| observedAt | Instante local da consulta; não substitui a versão do provider |
| gracePeriodEnd | `dueAt + Duration.ofDays(3)`, exatamente 72 horas corridas |
| Entitlement | Plano efetivamente disponível ao usuário em determinado instante |

Persistir/comparar instantes em UTC; apresentação em America/Sao_Paulo. A tolerância
é duração de 72 horas, sem arredondamento para meia-noite e sem dias úteis. No instante
exato `gracePeriodEnd`, a tolerância terminou. No instante exato `servicePeriodEnd`,
a competência deixou de cobrir acesso. Datas ausentes nunca significam validade infinita.

Mensalidade usa meses de calendário, não 30 dias. O contrato fixa âncora de cobrança,
horário e zona; cada limite é calculado diretamente da âncora original, limitando o
dia ao último dia do mês quando necessário, sem deslocamento cumulativo. Exemplo:
âncora dia 31 produz 31/jan → 28/fev → 31/mar. Para este produto, `dueAt` coincide
com o início do período. SET/2026 é apenas rótulo: a identidade é o intervalo real.
Mudanças de âncora exigem fluxo explícito futuro; notificações não podem alterá-la.

## 3. Separação de responsabilidades e contratos de dados

```text
Subscription 1 ── N BillingInvoice 1 ── N PaymentAttempt
```

| Conceito | Dados mínimos futuros | Responsabilidade |
| --- | --- | --- |
| Subscription | usuário, plano, origem, provider, externalSubscriptionId, preço contratado, moeda, ciclo/âncora, estado contratual, início, cancelamento, cancelAtPeriodEnd | Identidade e termos da recorrência; projeções agregadas não são livro financeiro |
| BillingInvoice | identidade local, subscription, intervalo de serviço, dueAt, gracePeriodEnd, valor esperado, moeda, estado, externalAuthorizedPaymentId quando conhecido, paidAt, identificação da liquidação lógica, datas de criação/atualização | Uma obrigação por período; termos congelados para auditoria |
| PaymentAttempt | identidade local, invoice, provider, externalPaymentId quando conhecido, estado normalizado e original, valor/moeda, aprovado/reembolsado, paidAt, providerUpdatedAt, observedAt e motivo seguro | Histórico das tentativas e evolução de um pagamento |

Registrar alterações financeiras e estornos de forma rastreável; não apagar a aprovação
original. Cada recurso tem sua própria versão. IDs externos são internos, nunca
expostos na tela Meu Plano. Correlação não usa userId vindo de webhook/cliente,
nem e-mail, valor ou proximidade de datas como prova isolada de propriedade.

Uma competência pode nascer localmente com ID de cobrança ainda desconhecido,
permitindo avaliar vencimento mesmo sem webhook. Uma tentativa ainda sem payment ID
só existe se houver chave estável de tentativa fornecida pelo provider. Caso contrário,
registrar a observação na cobrança sem inventar tentativa ou IDs sintéticos por consulta.
Ao descobrir um ID, vincular ao mesmo registro; nunca criar uma segunda competência.

## 4. Estados propostos de Subscription

Separar três eixos; não reutilizar um único enum como prova de recebimento:

| Eixo | Estados conceituais | Significado |
| --- | --- | --- |
| providerSubscriptionStatus | PENDING, AUTHORIZED, PAUSED, CANCELED, UNKNOWN | Espelho normalizado do contrato remoto, preservando também o valor original |
| billingStatus | AWAITING_PAYMENT, CURRENT, PAST_DUE, REVERSED, NO_CURRENT_INVOICE | Situação da competência que cobre o instante atual; dívidas históricas são informação separada |
| entitlementStatus do provider | PAID, GRACE, NONE | Motivo da cobertura local, sempre com início e fim |

Estado público agregado derivado, nesta ordem:

1. Cobertura paga válida: ACTIVE, inclusive com cancelamento agendado ou pausa
   contratual; eventual dívida atual aparece separadamente em billingStatus.
2. Cobertura por tolerância: PAST_DUE, com acesso e data limite explícitos.
3. Contrato cancelado sem cobertura: CANCELED.
4. Competência atual inadimplente ou revertida, com primeira liquidação já ocorrida
   no histórico do contrato: PAST_DUE, sem acesso pago.
5. Contrato pausado sem cobertura: PAUSED.
6. Nenhum primeiro pagamento aprovado e contrato não encerrado: AWAITING_PAYMENT.
7. Cobertura encerrada sem competência recuperável atual: EXPIRED.

`UNKNOWN` contratual não apaga cobertura paga existente nem cria cobertura nova.
Uma mudança para AUTHORIZED nunca modifica billingStatus para CURRENT por si só.
O plano efetivo e o estado da assinatura do provider são informações distintas:
ADMIN_GRANT pode manter o usuário em outro plano mesmo com cobrança PAST_DUE.

## 5. Estados propostos de BillingInvoice

| Estado | Semântica | Retry/liquidação posterior | Cobertura |
| --- | --- | --- | --- |
| PENDING | Ainda não vencida e sem processamento financeiro em curso | Sim | Nenhuma |
| PROCESSING | Tentativa em andamento antes do vencimento | Sim | Nenhuma |
| PAID | Uma liquidação válida aprovada e não integralmente revertida | Não iniciar nova cobrança | Período de serviço, se vigente |
| PAST_DUE | Vencimento alcançado sem liquidação válida | Sim, inclusive após tolerância | Somente tolerância elegível |
| REFUNDED | Liquidação integralmente reembolsada | Não automaticamente | Nenhuma por essa competência |
| CHARGEDBACK | Liquidação contestada/revertida por chargeback confirmado | Não automaticamente | Nenhuma por essa competência |
| CANCELED | Obrigação anulada antes de liquidar | Não | Nenhuma |

PAST_DUE expressa vencimento, não esgotamento de retries: uma tentativa rejeitada
isolada não encerra a cobrança. PAID é terminal para novas concessões, mas permite
estorno. REFUNDED e CHARGEDBACK são terminais no fluxo automático ordinário;
correção autoritativa de reversão exige trilha auditável, revalidação integral e
reprocessamento da mesma liquidação, nunca um período adicional. CANCELED é terminal
para cobrança intencional, mas aprovação tardia deve ser registrada e encaminhada
como conflito financeiro, sem acesso automático ou desaparecimento do recebimento.

Reembolso parcial é dimensão separada (`refundedAmount` e histórico), mantendo PAID.
Esgotamento de retries é dimensão da cobrança, não estado que decide acesso.
Observações sem correlação/versão confiável ficam pendentes de conciliação; não são
forçadas para qualquer um desses estados.

## 6. Estados propostos de PaymentAttempt

| Estado | Semântica | Terminalidade e efeito |
| --- | --- | --- |
| PENDING | Criada, aguardando processamento | Não terminal; não comprova recebimento |
| PROCESSING | Em análise/processamento | Não terminal; não comprova recebimento |
| APPROVED | Pagamento aprovado autoritativamente | Final de processamento, sujeito a estorno; candidato à liquidação |
| REJECTED | Tentativa recusada | Final da tentativa ordinária; outra tentativa pode liquidar a mesma invoice |
| CANCELED | Tentativa cancelada | Final ordinário; não cancela automaticamente a invoice |
| REFUNDED | Pagamento integralmente reembolsado | Final de reversão ordinária |
| CHARGEDBACK | Chargeback confirmado | Final de reversão ordinária, alto risco |

Uma correção mais recente do mesmo recurso pode corrigir um estado final; não usar
uma classificação ordinal de enums para descartar correções legítimas. Reembolso
parcial preserva APPROVED e registra valores. Status remoto desconhecido preserva
estado validado anterior, registra observação e solicita conciliação; nunca vira APPROVED.

## 7. Matriz principal de transições

Todas as linhas financeiras pressupõem consulta autoritativa, correlação completa,
controle de versão e execução atômica. O relógio pode derivar inadimplência sem webhook.

| Origem da invoice | Gatilho/condição | Destino | Efeito no acesso |
| --- | --- | --- | --- |
| Ausente | Contrato/agenda validados identificam período único | PENDING ou PAST_DUE se já venceu | Não concede primeiro acesso |
| PENDING | Tentativa em curso e `now < dueAt` | PROCESSING | Nenhum |
| PENDING/PROCESSING | Tentativa rejeitada antes de dueAt | PENDING, ou PROCESSING se outra em curso | Nenhum; permite retry |
| PENDING/PROCESSING | `now >= dueAt`, sem liquidação | PAST_DUE | GRACE apenas para renovação elegível |
| PAST_DUE | Retry pendente, rejeitado ou esgotado | PAST_DUE | Mantém GRACE somente até limite |
| PENDING/PROCESSING/PAST_DUE | Aprovação com valor/moeda/correlação válidos | PAID | Concede apenas intervalo original ainda vigente |
| PAST_DUE | `now >= gracePeriodEnd`, sem liquidação | PAST_DUE | Remove GRACE; aplica fallback |
| PAID | Notificação repetida de aprovação ou rejeição de outra tentativa | PAID | Nenhuma concessão adicional |
| PAID | Reembolso parcial confirmado | PAID + valor reembolsado | Preserva cobertura |
| PAID | Reembolso integral confirmado | REFUNDED | Remove cobertura daquela invoice, sem nova tolerância |
| PAID | Chargeback confirmado | CHARGEDBACK | Remove cobertura daquela invoice, sem nova tolerância |
| PENDING/PROCESSING/PAST_DUE | Obrigação explicitamente anulada, sem liquidação | CANCELED | Remove eventual GRACE; preserva outras coberturas |
| CANCELED | Aprovação tardia | CANCELED + conflito financeiro registrado | Nenhuma concessão automática; revisão obrigatória |
| REFUNDED/CHARGEDBACK | Correção de reversão comprovada | PAID, após revalidação | Somente restante do mesmo intervalo |
| Qualquer | Evento antigo/duplicado sem nova evidência válida | Inalterado | Inalterado |

Transições ordinárias de tentativa: PENDING → PROCESSING/APPROVED/REJECTED/CANCELED;
PROCESSING → APPROVED/REJECTED/CANCELED; APPROVED → REFUNDED/CHARGEDBACK.
Estados intermediários podem ser omitidos pelo provider. Rejeição seguida de retry
com novo payment ID cria outra tentativa na mesma invoice. Correções do mesmo ID
seguem versão autoritativa e preservam histórico. Nenhuma tentativa rejeitada pode
desfazer liquidação aprovada por outra tentativa.

## 8. Primeiro pagamento

Checkout cria contrato remoto; autorização atualiza apenas o eixo contratual.
Sem aprovação válida, o estado público é AWAITING_PAYMENT, mesmo que a invoice já
esteja vencida financeiramente. Não há tolerância para primeira contratação.

Identificar cobrança inicial e pagamento; validar proprietário por encadeamento
interno, invoice, valor exato e moeda. Só então registrar liquidação e PAID.
O período inicial começa na âncora contratada, não na chegada do webhook. Se a
aprovação atrasar, concede apenas o restante do período, sem reiniciar o mês.
Não criar datas a partir de `next_payment_date` isoladamente. Sem termos suficientes
para determinar um intervalo verificável, manter pendência de conciliação e não
conceder período ilimitado. Migração de assinaturas antigas exige prova equivalente.

## 9. Renovação

Criar/identificar a competência seguinte pela agenda contratual congelada, vincular
cobrança recorrente e tentativas reais, validar valor/moeda e liquidar só com approved.
Renovações podem ser materializadas antes do vencimento; pagamento antecipado não
concede o período antes do seu início. Cada período possui um único registro lógico.

Preços são snapshots dos termos aprovados para a competência; alterações posteriores
do catálogo não reescrevem dívidas ou recebimentos. Comparar dinheiro em unidades
mínimas inteiras da moeda, sem float. Nesta política não há soma automática de
pagamentos parciais: aprovação com valor divergente é conflito a conciliar e não
liquida a invoice. Taxas líquidas recebidas não substituem o valor bruto cobrado.

`next_payment_date` é pista operacional para descoberta e verificação da agenda,
nunca prova de recebimento. Períodos pagos são intervalos de invoices liquidadas;
não calcular cobertura como simples máximo global de datas, pois pode haver lacunas.

## 10. Grace period de três dias

Uma renovação é elegível somente se houver competência anterior contígua com
liquidação válida, não integralmente revertida, e continuidade contratual de cobrança
na fronteira. Primeira contratação, contrato cancelado/pausado na fronteira e
competência anulada/revertida não geram GRACE. Não encadear tolerâncias de meses não pagos.

Para invoice elegível e não paga: `dueAt <= now < gracePeriodEnd` implica PAST_DUE
com entitlement GRACE do plano contratado. Em `now >= gracePeriodEnd`, a invoice
continua PAST_DUE, retries podem continuar, mas o provider não concede acesso.
Falha de consulta, ausência de webhook ou execução tardia do scheduler não estendem
o prazo: avaliar cobertura também na leitura do entitlement.

Pagamento aprovado em D+1 transforma a invoice em PAID e mantém acesso contínuo.
Aviso público futuro deve informar pendência e limite da tolerância, distinguindo
PAST_DUE com acesso de PAST_DUE sem acesso. Não alterar a interface nesta etapa.

## 11. Pagamento recuperado

Após o prazo, aprovação válida restaura acesso imediatamente quando observada se
`servicePeriodStart <= now < servicePeriodEnd`, sem anulação ou reversão impeditiva.
A invoice passa a PAID mesmo se o intervalo já terminou; nesse caso apenas quita
histórico e não restaura o plano atual. Pagamento de competência futura só cobre
seu próprio intervalo. Cancelamento contratual não impede reconhecer dívida paga
anterior válida, mas jamais autoriza nova renovação ou reativação remota automática.

Se o provider aprovou dentro da tolerância e a notificação chegou depois, registrar
paidAt real e restaurar ao descobrir a evidência. Não alegar continuidade operacional
se o acesso foi interrompido por falta de informação. Reconciliação deve reduzir essa lacuna.

A concessão é projeção do intervalo original, identificada pela invoice, e não
operação de somar um mês a uma data. Uma segunda aprovação de outra tentativa é
recebimento excedente: registrar incidente financeiro, impedir dupla concessão e
encaminhar resolução; não disparar reembolso automático nesta política.

## 12. Cancelamento

Preservar a implementação e o contrato existentes. Confirmado no provider, manter
acesso até o fim do período já pago; não renovar após o limite. cancelAtPeriodEnd
isolado não é prova de confirmação. Os estados atuais NONE, PENDING_CONFIRMATION
e CONFIRMED permanecem independentes dos estados de invoice e tentativa.

Cancelamento não transforma PAID em CANCELED, não apaga tentativas e não inicia
tolerância no período seguinte. Uma invoice futura indevidamente criada não habilita
renovação após cancelamento. Cobrança indevida posterior deve gerar conflito financeiro
e revisão, sem reativação automática do contrato. ADMIN_GRANT permanece independente.
Estorno posterior ainda pode retirar cobertura da invoice estornada; isso é efeito
financeiro separado do cancelamento e exige sua própria evidência.

## 13. Refund

> **Nota de precedência (5G.9):** a política de refund parcial efetivamente
> implementada em `docs/billing-refund-chargeback-5g6.md` ("opção A": qualquer
> valor de refund positivo confirmado impede aquele attempt de cobrir
> integralmente a invoice, sem tolerância parcial) é mais estrita que o texto
> original abaixo (que previa preservar PAID/acesso até o refund cumulativo
> atingir o valor liquidado) e **prevalece** onde os dois textos conflitarem.
> Este documento permanece como registro histórico da proposta original; a
> 5G.6 é normativa para o comportamento hoje implementado.

Total: registrar ID/versão, valor, moeda, data e relação com pagamento original;
invoice REFUNDED e cobertura dessa competência removida quando confirmado. Não
reabrir GRACE para substituir um período estornado. Preservar o fato de ter sido PAID.

Parcial: registrar cada estorno idempotentemente; manter PAID e acesso até o fim do
intervalo. Quando o total cumulativo confirmado atingir o valor liquidado, aplicar
reembolso total. Valores inconsistentes ou acima do recebido exigem conciliação.
Não somar novamente um snapshot cumulativo recebido em notificações repetidas.

Refund histórico não remove cobertura de competência atual distinta. Nenhum refund
de PAYMENT_PROVIDER remove ADMIN_GRANT ou GRANDFATHERED válido.

## 14. Chargeback

Chargeback confirmado marca a tentativa e a invoice afetada como CHARGEDBACK,
registra evidência e ocorrência de alto risco para revisão operacional. Remove
imediatamente a cobertura daquela invoice; não gera tolerância compensatória.
Uma simples análise/disputa ainda sem confirmação registra risco, sem inventar
chargeback. A normalização futura deve distinguir esses sinais.

Política escolhida: chargeback histórico não bloqueia globalmente a conta, não
rebaixa competência atual paga nem cancela automaticamente a recorrência. A revisão
de risco pode recomendar ação explícita futura; nenhuma sanção cruzada é inferida.
Reversão de chargeback exige evidência autoritativa mais recente e auditável; restaura
somente a cobertura original ainda vigente. Não misturar refund e chargeback na
soma de estornos a ponto de contar a mesma reversão duas vezes.

## 15. Eventos fora de ordem

1. Resolver identidade do recurso e cadeia subscription → invoice → attempt.
2. Consultar estado atual autoritativamente; timestamp do webhook não ordena finanças.
3. Comparar providerUpdatedAt somente com a versão aplicada daquele mesmo recurso.
4. Versão inferior não altera projeção; versão igual e conteúdo igual é no-op.
5. Versão igual com conteúdo divergente, ausente ou não comparável exige nova
   consulta serializada e conciliação; não sobrescrever automaticamente evidência
   consolidada com um snapshot ambíguo. Registrar o conflito para resolução.
6. Aplicar observação válida à própria invoice e recalcular a projeção para `now`.

paidAt determina quando ocorreu a liquidação; servicePeriod determina o que ela
cobre. Nenhum deles substitui providerUpdatedAt na resolução de versões. Rejeição
antiga de OUT não torna NOV PAST_DUE. Aprovação de OUT atrasada quita OUT e não
estende NOV. Preapproval AUTHORIZED não limpa dívidas nem desfaz reversões.

## 16. Idempotência e concorrência

Identidades lógicas futuras, com garantias atômicas de unicidade:

- Subscription remota: `(provider, externalSubscriptionId)`.
- Invoice: `(subscription, servicePeriodStart, servicePeriodEnd)`; proibir também
  intervalos conflitantes/sobrepostos na mesma agenda mensal.
- Cobrança recorrente: `(provider, externalAuthorizedPaymentId)` aponta para uma invoice.
- Tentativa: `(provider, externalPaymentId)` aponta para uma invoice imutável.
- Liquidação lógica: no máximo uma por invoice; múltiplos recebimentos são fatos
  financeiros separados, sem multiplicar cobertura.
- Estorno: identidade externa e versão; preservar relação com pagamento original.

Deduplicação de entrega é otimização, não garantia financeira. Mesmo recurso pode
receber atualização legítima com outro requestId; webhook e reconciliação usam o
mesmo pipeline. Persistência do fato, versão e projeção deve ser atômica, com lock
ou compare-and-swap e unicidade. Perdedor de corrida recarrega e reavalia. Não
transformar qualquer erro de integridade em sucesso: somente duplicação comprovada
pode ser tratada como processamento já concluído. Nenhuma falha parcialmente
persistida pode aparentar uma liquidação completa.

## 17. Webhooks

| Tipo | Papel | Consulta e limites |
| --- | --- | --- |
| subscription_preapproval | Atualizar contrato e sinalizar descoberta de cobranças | Consultar preapproval; autorização não liquida invoice |
| subscription_authorized_payment | Descobrir/atualizar mensalidade e retries | Consultar cobrança, correlacionar preapproval e consultar pagamento real identificado |
| payment | Atualizar tentativa, aprovação e reversões | Consultar pagamento; resolver cobrança e subscription; se vínculo não puder ser provado, conciliar sem conceder acesso |

Validar autenticidade antes de processar; payload é apenas sinal. Não confiar em
status, valor ou usuário do payload. Os três caminhos convergem na mesma identidade
financeira. Um pagamento avulso não correlacionado nunca vira mensalidade por
semelhança de valor. Falhas transitórias precisam de retry observável; sucesso HTTP
só após processamento durável ou enfileiramento durável futuro, sem perda silenciosa.
Este documento não altera endpoint, assinatura, CORS ou cliente Mercado Pago.

## 18. Reconciliação

Dois fluxos distintos compartilham o mesmo mecanismo de aplicação idempotente:

**Checkout inicial:** recuperar criação ambígua, autorização, cobrança inicial e
primeiro pagamento. Ausência de provider ID não autoriza criar outra assinatura:
buscar por correlação estável quando suportado, ou manter bloqueio para resolução.

**Recorrente:** descobrir competências de contratos já autorizados, verificar
mensalidades esperadas, pagamentos pendentes, retries e mudanças em pagamentos
aprovados, inclusive estornos de contratos cancelados. Não selecionar apenas
checkouts pendentes e não usar exclusivamente GET de preapproval.

| Prioridade | Alvos | Resultado esperado |
| --- | --- | --- |
| P0 | Falha conhecida de aplicação, conflito financeiro, suspeita de reversão, invoice junto ao fim de GRACE | Obter evidência e resolver divergência com maior urgência |
| P1 | Renovação vencida, em GRACE, retry em curso, cobrança inicial aguardada | Descobrir aprovação e atualizar cobertura |
| P2 | Próximos vencimentos e assinatura ativa sem cobrança esperada identificada | Materializar/correlacionar competências e detectar webhook perdido |
| P3 | Histórico aprovado, pendências antigas e contratos encerrados | Recuperar estornos, chargebacks e atualizações tardias |

Definir na implementação orçamento de API, paginação, cursores, backoff e fairness:
P0 não pode impedir indefinidamente P3. Não descartar histórico por fechamento
contratual. Usar varredura incremental com sobreposição e mecanismo de revarredura
completa, sem assumir janela máxima de estorno não comprovada. Falha de um item
não aborta os demais; registrar última tentativa, sucesso, erro seguro e próxima
tentativa. Alertar itens sem progresso. O relógio do entitlement continua válido
mesmo com reconciliação indisponível. Frequência operacional não modifica a política
de 72 horas. Não implementar scheduler nesta etapa.

## 19. Contrato formal de entitlement

Para instante `t` e invoice `i`:

```text
paidCoverage(i,t) =
  i possui liquidação aprovada, correlacionada e válida
  AND i.status = PAID
  AND i.servicePeriodStart <= t < i.servicePeriodEnd

graceCoverage(i,t) =
  i é renovação elegível conforme seção 10
  AND i.status = PAST_DUE
  AND i.dueAt <= t < min(i.gracePeriodEnd, i.servicePeriodEnd)

providerEligible(t) = existe i: paidCoverage(i,t) OR graceCoverage(i,t)

effectivePlan(t) = primeiro candidato válido nesta precedência:
  ADMIN_GRANT > PAYMENT_PROVIDER elegível > GRANDFATHERED > FREE
```

Cada fonte é avaliada de forma independente. Alterações de cobrança nunca editam
grants. Validade de grant segue seu próprio contrato existente. Para múltiplos
candidatos legados da mesma fonte, preservar seleção determinística existente;
registrar anomalia de contratos pagos duplicados, sem somar períodos ou benefícios.

O plano pago é o snapshot de plano contratado aplicável à invoice que concede
cobertura. `Subscription.status=ACTIVE` sozinho não satisfaz providerEligible.
Uma projeção pública de fim do período pago deve corresponder ao intervalo pago
vigente, sem incluir GRACE; expor o limite de tolerância separadamente no futuro.
Sem invoice paga/grace válida não existe acesso pago de duração indefinida.

## 20. Proteção contra assinatura duplicada

Antes de qualquer nova criação remota, bloquear por usuário quando houver contrato
PAYMENT_PROVIDER ainda capaz de cobrar: AUTHORIZED, PENDING, PAUSED passível de
retomada, UNKNOWN ou criação não resolvida. Isso vale mesmo sem entitlement,
durante PAST_DUE ou após expirar GRACE. Intenção de cancelamento pendente não libera
novo checkout. Exceção somente em fluxo explícito futuro de troca/substituição.

Como proteção adicional nesta política, bloquear nova contratação durante cobertura
paga remanescente de contrato com cancelamento confirmado; a contratação poderá
ocorrer após seu fim, evitando sobreposição sem fluxo de troca. Contrato confirmado
como encerrado, sem cobertura e sem criação ambígua, não bloqueia por histórico
financeiro isoladamente. ADMIN_GRANT não é assinatura remota capaz de cobrar.

Garantir exclusão mútua por usuário e intenção persistida antes da chamada externa;
cliques concorrentes recebem a mesma operação ou conflito controlado. Retry usa
a mesma chave idempotente. Timeout/resultado desconhecido exige conciliação antes
de nova chave/criação. Essa proteção é pré-requisito para produção, sem ativação agora.

## 21. Casos completos e critérios de aceite

Fixture comum: plano contratado BRONZE, BRL 100,00; períodos mensais iniciando às
12:00Z no dia 1. SET = `[2026-09-01T12:00Z, 2026-10-01T12:00Z)`;
OUT = `[2026-10-01T12:00Z, 2026-11-01T12:00Z)`; NOV começa no fim de OUT.
OUT vence em 01/out 12:00Z e sua tolerância termina em 04/out 12:00Z.
Sem grant/grandfathered, fallback é FREE. Todos os pagamentos aprovados abaixo
têm correlação e valor/moeda validados, salvo indicação contrária.

| Caso | Preparação e sequência | Resultado verificável |
| --- | --- | --- |
| A — primeira aprovação | Preapproval AUTHORIZED; cobrança SET identificada; tentativa APPROVED em 01/set 12:01Z | Uma invoice SET PAID; público ACTIVE; BRONZE até 01/out 12:00Z; replay não soma mês |
| B — autorização sem pagamento | Mesmo contrato, nenhuma tentativa aprovada | Público AWAITING_PAYMENT; invoice vencida pode ser PAST_DUE; entitlement FREE, sem GRACE inicial |
| C — renovação normal | SET PAID; OUT aprovado no vencimento | OUT PAID; ACTIVE; cobertura até 01/nov 12:00Z; SET permanece histórico |
| D — retry em D+1 | SET PAID; OUT tentativa 1 REJECTED em D0; tentativa 2 APPROVED em 02/out 12:00Z | Em D0 PAST_DUE/GRACE e BRONZE; em D+1 PAID/ACTIVE; duas tentativas, uma invoice e uma liquidação |
| E — não pago em D+4 | SET PAID; OUT rejeitada e sem aprovação até 05/out 12:00Z | Até antes de 04/out 12:00Z BRONZE/GRACE; exatamente no limite FREE; em D+4 OUT PAST_DUE, retries ainda possíveis |
| F — aprovação após corte | Caso E; OUT aprovada em 05/out 13:00Z | Restaura BRONZE imediatamente após evidência; fim continua 01/nov 12:00Z; se aprovação só em 02/nov, quita OUT sem cobrir NOV |
| G — webhook perdido | OUT aprovada no provider em D0, sem notificação; reconciliação consulta cobrança e pagamento em D+1 | OUT PAID com paidAt de D0; ACTIVE; entrega posterior não altera período nem duplica tentativa |
| H — evento antigo | NOV PAID e vigente; chega sinal rejected de tentativa de OUT | Consulta atualiza apenas OUT se versão válida; NOV continua PAID/ACTIVE; dívida histórica não domina billingStatus atual |
| I — cancelamento agendado | OUT PAID; cancelamento confirmado em 10/out | ACTIVE e cancelamento agendado até 01/nov 12:00Z; depois CANCELED/FREE; não cria GRACE de NOV nem apaga OUT |
| J — refund atual total | OUT PAID; reembolso total confirmado em 15/out | OUT REFUNDED com aprovação preservada no histórico; sem GRACE; FREE imediatamente, salvo outra fonte válida |
| K — chargeback anterior | NOV PAID vigente; chargeback confirmado de OUT | OUT CHARGEDBACK, risco para revisão; NOV permanece PAID e BRONZE; sem bloqueio automático de conta |
| L — grant durante dívida | ADMIN_GRANT válido de plano OURO; OUT PAST_DUE antes/depois do limite | OURO permanece durante toda validade do grant; nenhuma mutação no grant; quando expirar, reavaliar provider/grandfathered/FREE |

Critérios complementares obrigatórios para a implementação futura:

- Em `gracePeriodEnd - 1ms`, GRACE; em `gracePeriodEnd`, nenhum GRACE.
- Reembolso parcial de BRL 20 mantém acesso; mais BRL 80 confirmados remove cobertura;
  repetição dos mesmos estornos não soma novamente.
- Aprovação com BRL 99,00 ou moeda diferente não liquida BRL 100,00; registrar conflito.
- Dois pagamentos approved concorrentes produzem uma liquidação lógica e incidente
  de recebimento excedente, nunca dois meses de acesso.
- Webhook e reconciliação concorrentes convergem em uma invoice e uma tentativa por ID.
- Preapproval AUTHORIZED após inadimplência não muda PAST_DUE nem o prazo.
- Datas faltantes não concedem acesso; invoice futura paga não cobre lacuna atual.
- Reversão antiga de OUT não remove acesso pago de NOV nem ADMIN_GRANT.
- Contratação inicial não paga não recebe tolerância; mês anterior também não pago
  não gera nova tolerância; cancelamento confirmado não cria competência de renovação.
- Dois checkouts simultâneos, contrato PAUSED ou criação ambígua não geram segunda
  recorrência remota. Cancelamento ainda pendente mantém o bloqueio.
- Com provider sem cobertura e GRANDFATHERED válido, fallback é GRANDFATHERED;
  expirado esse benefício e sem ADMIN_GRANT, fallback é FREE.

## 22. Invariantes obrigatórias

1. `authorized != paid`.
2. `webhook != fonte da verdade`; webhook é sinal para consulta autoritativa.
3. Uma competência possui no máximo uma confirmação lógica de pagamento, embora
   possa ter múltiplas tentativas e recebimentos excedentes auditados.
4. Uma tentativa nunca altera competência diferente da sua.
5. Competência antiga nunca determina inadimplência da competência atual.
6. Payment approved precisa ser correlacionado com subscription, invoice/competência,
   valor e moeda antes de conceder cobertura.
7. Entitlement pago não pode existir indefinidamente sem período pago ou grace period válido.
8. ADMIN_GRANT não depende do Mercado Pago.
9. Cancelamento não apaga período já pago; reversão financeira é fato independente.
10. Reconciliação deve ser idempotente.
11. Eventos duplicados não geram períodos adicionais.
12. Retries não criam novas competências.
13. Vencimento, início/fim de serviço e tolerância não são reiniciados por notificações.
14. Autorização contratual não limpa dívida nem comprova pagamento.
15. Estorno não apaga aprovação original; timestamps de recursos diferentes não se ordenam entre si.
16. Nenhuma nova recorrência pode ser criada enquanto uma anterior ainda puder cobrar,
    exceto fluxo explícito futuro de substituição.

## 23. O que não será implementado nesta etapa

Nenhuma entidade, enum, migration, alteração de banco, contrato HTTP, código de
backend/frontend, scheduler, integração Mercado Pago, cancelamento ou entitlement.
Não migrar assinaturas existentes nem presumir que ACTIVE legado comprova pagamento.
Não executar build ou suíte de aplicação para esta alteração documental. Não tocar
produção nem alterações anteriores do working tree. Sem commit, push ou deploy.

Na implementação futura, a transição de dados legados deve reconstruir cobertura
com evidência financeira ou encaminhar casos indeterminados para conciliação;
não fabricar invoice PAID a partir de preapproval AUTHORIZED ou datas legadas.

## 24. Decisões fechadas e dependências de implementação

Decisões fechadas nesta proposta: espera pública AWAITING_PAYMENT; primeira cobrança
sem tolerância; renovação com 72 horas exatas; restauração somente do intervalo
original recuperável; refund parcial mantém cobertura; total/chargeback retiram
somente cobertura afetada; chargeback histórico gera revisão de risco sem punição
cruzada; sem agregação automática de pagamentos parciais; sem dupla concessão;
bloqueio de nova contratação durante cobertura remanescente após cancelamento.

Não há decisão comercial aberta necessária para aplicar a matriz. Há dependências
técnicas a comprovar antes de implementar a integração: campos/endpoints capazes
de correlacionar payment e cobrança recorrente, identidade de retries, versões
autoritativas, âncora de serviço e mecanismo de descoberta paginada de cobranças e
reversões. Este documento não afirma que tais campos existem no formato desejado.
A implementação deve validar contratos reais e documentação oficial, registrar o
mapeamento e testes de fixtures. Se não houver evidência suficiente, manter pendência
de conciliação sem conceder cobertura nem sobrescrever fato consolidado; não relaxar
as invariantes para acomodar uma limitação da API.

Cadência operacional, orçamento de API, mecanismo de lock e desenho físico do banco
são escolhas da etapa de implementação, subordinadas a estas regras, sem mudar a
semântica financeira. Disponibilidade de evidência para dados legados é requisito
de validação da migração futura, não autorização para modificar dados nesta etapa.
