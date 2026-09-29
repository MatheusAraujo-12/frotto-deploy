# 5G.12.1 hotfix + 5G.13 Dynamic Fleet Billing

Base: `31549bd65fe0446828ef4624bec37eaba04ec2e8`, branch de trabalho `codex/5g13-dynamic-fleet-billing`. Desenvolvimento isolado do diretório original. Sem push, deploy, alteração de main/staging oficial ou stashes.

## 1. Causa raiz e correção do undo

A base contém o caminho objetivo de falso sucesso: `clearUndoneDowngrade` retorna a Subscription sem limpar quando divergem pendingPlan/id/requestedAt; o chamador ignora esse resultado e responde `DOWNGRADE_UNDONE`. O frontend ainda publica a mensagem antes de atualizar `/billing/me`. Isso confirma o defeito no código. Não houve acesso ao banco/logs da homologação para afirmar qual divergência ocorreu naquele incidente específico.

A igualdade exata de `Instant` como identidade da operação é frágil: Java suporta nanossegundos e MySQL armazena a precisão da coluna, potencialmente truncando/arredondando. A identidade agora é UUID, não timestamp. `@Version` protege atualizações concorrentes da Subscription. As etapas de mutação usam lock do usuário e/ou da assinatura, em transações curtas; HTTP permanece fora delas.

O undo faz GET autoritativo da mesma preapproval; se BRL/preço contratado já correspondem, não emite PUT. Caso contrário emite PUT e confirma por GET. Só então relê sob lock, confere plano pendente + token + titularidade/ausência de upgrade/cancelamento, limpa todos os cinco campos obrigatórios e token, faz `saveAndFlush` e consulta escalar ao banco confirmando a limpeza. Qualquer divergência gera `BILLING_DOWNGRADE_UNDO_CONFLICT` (409), nunca sucesso. O retry sem pendência ainda confirma o provider e relê o estado local antes de responder sucesso.

Uma reserva `plan_change_operation_until` impede que downgrade/undo/upgrade disputem o provider enquanto uma operação síncrona está em andamento. Liberação em `finally`, condicionada ao token. Cada undo recebe novo token para impedir finalização atrasada de tentativa anterior. Em crash de processo, a reserva expira em cinco minutos; o retry faz GET antes de qualquer PUT. Os timeouts padrão do client são conexão 3s e leitura 7s, bem abaixo da reserva. Não ampliar esses timeouts para além da reserva sem revisar essa garantia. Pendência sem confirmação autoritativa não entra no scheduler de frota.

No frontend, a resposta do undo é seguida de refresh; só mostra sucesso se `pendingPlanCode == null` e status `DOWNGRADE_UNDONE`. Refresh falho ou pendência remanescente mostram inconsistência. A renderização também impede mensagem de sucesso junto de “Mudança agendada”.

## 2. Veículos faturáveis, exclusão e restauração

Auditoria completa por ocorrência em [car-active-audit-5g13.md](car-active-audit-5g13.md).

`deleted` é a única autoridade de exclusão. `active` é legado espelhado na criação/delete/restore e não é editável efetivamente por PATCH. `adminStatus` é operacional; manutenção, bloqueado, à venda e retirado continuam faturáveis.

```sql
SELECT COUNT(*) FROM car WHERE user_id = :authenticatedUserId AND deleted = false;
```

Todos os consumidores de billing usam `countBillableByUserId`. `activeVehicleCount` permanece como alias de compatibilidade no DTO existente; o novo campo público é `billableVehicleCount`. Não há duas definições de contagem.

- DELETE próprio: lock da conta, bloqueio se upgrade aberto, marca deleted/date/actor + active=false; nunca remove o Car ou suas relações.
- JSON não pode escrever deleted nem auditoria. PATCH de excluído é rejeitado, inclusive active/deleted/adminStatus. Criação força estado normal e ignora auditoria enviada.
- Receitas, despesas, motorista, manutenção, serviço, inspeção, dano, lembrete, pendência/pagamento operacional e documentos verificam o Car persistido antes de escrever. Referências filhas de inspeção/manutenção não podem mover despesas/pneus/danos/serviços de outro histórico; danos são associados a partir do registro autorizado persistido, não mesclados cegamente do JSON.
- `/api/cars/deleted` só lista veículos do usuário. `/menu/veiculos-excluidos` apresenta nome/placa/marca/modelo/data e consulta de receitas, despesas, motoristas, inspeções, danos, manutenções, lembretes e documentos (paginados). Sem comandos de edição/restauração. Consultas históricas e relatórios por veículo mantêm os dados.
- Placa normalizada já excluída na mesma conta bloqueia self-service (`VEHICLE_PREVIOUSLY_DELETED`); não há reativação implícita. A validação também impede duplicata de não excluído e roda sob lock da conta.
- `POST /api/admin/cars/{id}/restore`, `ROLE_ADMIN`, body `{ "reason": "Exclusão acidental" }`: motivo não vazio, até 500 caracteres; confere limite, downgrade pendente, upgrade aberto e colisão de placa. Atualiza deleted=false/active=true e restoredAt/restoredByUserId/restoreReason. Preserva adminStatus e auditoria anterior de exclusão. Não cobra.
- `GET /api/admin/cars/deleted` e `/menu/admin/veiculos-excluidos`: titular, placa, exclusão, ator, última restauração/ator/motivo. Lista também registros restaurados para consulta da última auditoria.
- Limites agora são obrigatórios nas mutações de frota, independentemente do antigo `BILLING_ENFORCEMENT_ENABLED`. O flag antigo não oferece bypass. Contas já acima da faixa precisam resolver o plano antes de adicionar/restaurar.

A auditoria por campos registra o último delete/restore; não é um ledger completo de todas as restaurações sucessivas.

## 3. Preços, API e UX

`PricingService.calculatePriceForPlan` e `PlanPricingTier` permanecem como configuração única. Sem matemática de preço no frontend.

| Plano | Comportamento |
|---|---|
| FREE / BRONZE / SILVER / GOLD | Preço fixo dentro do limite. |
| PLATINUM | Faixa 31–100; configuração existente: 31=82,40; 32=84,90; 100=254,90. |
| FROTTA | 101+; configuração existente: 101=256,90; 102=258,90. |
| PLATINUM com 100 | Bloqueia o 101º e orienta upgrade para FROTTA no fluxo pago 5G.12.1 existente. |

Criar/excluir/restaurar só muda a contagem e projeção local. Não chama Mercado Pago, não cria checkout, preapproval, assinatura, PaymentAttempt avulso, pró-rata ou estorno. Pró-rata de mudança real de categoria permanece inalterado.

`GET /api/billing/me` adiciona `billableVehicleCount`, `projectedNextRenewalPrice`, `nextRenewalPrice`, `nextRenewalVehicleCount`, `nextRenewalAt`, `nextRenewalLockedAt`, `nextRenewalSyncedAt`, `nextRenewalState`. Preço do ciclo atual continua `currentMonthlyPrice` (contractedPrice). Não expõe IDs/tokens do provider ou token do snapshot. Plano candidato é pendingPlan quando existe, ou plano efetivo.

Meu Plano para Platinum/Frotta distingue preço atual, estimativa, cobrança fechada aguardando confirmação, cobrança sincronizada e projeção seguinte quando diferente. Cadastro/exclusão consultam o backend para feedback de preço; se essa consulta falha, o sucesso da operação do veículo continua verdadeiro sem inventar preço. Restore admin confirma atualização da projeção e ausência de cobrança imediata.

## 4. Fechamento e sincronização

`DynamicFleetBillingScheduler` fica **desabilitado por padrão** (`BILLING_DYNAMIC_FLEET_ENABLED=false`). Quando habilitado e MP configurado, roda a cada 900000ms (configurável `BILLING_DYNAMIC_FLEET_INTERVAL_MS`). `billing.dynamic-fleet.max-per-run` limita a 500 contas por execução; paginação por ID continua nas seguintes execuções sem saltar contas quando elegibilidade muda. Um 429 interrompe o lote e retoma a conta na próxima execução. Para frotas de contas grandes, dimensionar lote/intervalo para percorrer todos os elegíveis dentro da janela; não há promessa de throughput ilimitado.

O scheduler usa GET da preapproval existente. `next_payment_date` autoritativo define a janela: futuro e até 24h. `currentPeriodEnd` é somente a informação local de UI até existir snapshot; não se inventam 30 dias.

Sob lock da conta + assinatura: valida assinatura ACTIVE/provider, não cancelada, provider authorized/BRL, ausência de upgrade e de operação de plano em andamento. Calcula candidato/count/preço e persiste snapshot com UUID, plano, preço, quantidade, data de renovação, lockedAt, syncedAt, appliedAt e estado. Snapshot fechado é imutável até aplicação financeira. Nenhum contractedPrice/count é alterado nesse momento.

Depois do commit: confirma que o mesmo provider/ciclo ainda é elegível; PUT somente `transaction_amount`/BRL na mesma preapproval com chave `fleet-<UUID>` quando necessário; GET confirma valor/moeda/identidade/data. Marca SYNCED apenas após confirmação. Se GET já traz o preço correto, dispensa PUT. Reexecuções usam snapshot congelado, não uma nova contagem.

Falha mantém RETRY; perto da renovação (menos de 1h) vira REVIEW e log estruturado com subscriptionId/outcome. Resposta desconhecida nunca vira sincronização. Snapshot vencido não dispara PUT tardio que alteraria outro ciclo. REVIEW sem evidência financeira correspondente exige investigação administrativa; não há desbloqueio automático inventando pagamento.

Após fechamento, adição/exclusão/restauração modifica só projeção seguinte, sem estorno. Para evitar trocar a categoria de um snapshot já fechado, **mudanças de plano e undo são bloqueados com BILLING_RENEWAL_LOCKED até a aplicação financeira desse snapshot**. É uma decisão conservadora explícita; não há alteração oculta do valor congelado.

## 5. Integrações e evidência financeira

- Pending downgrade é o plano candidato. Se progressivo, o preço usa a contagem no fechamento, sem sobrescrever o plano atual.
- Adição/restauração incompatível com o teto do pending é rejeitada com orientação para desfazer o downgrade. Não cancela a pendência automaticamente.
- Upgrade AWAITING_PAYMENT/APPLYING bloqueia criação/delete/restore; cotação de upgrade relê a contagem sob lock antes de abrir tentativa, prevenindo valor obsoleto.
- `cancelAtPeriodEnd`/canceledAt impedem fechamento e confirmação. Nunca reativa provider.
- `FleetRenewalEvidence` integra os dois caminhos de `MercadoPagoFinancialIngestion`, depois de correlação e GET autoritativo existentes. A competência é associada ao snapshot pela tolerância de âncora já usada (menos de um dia); grava expectedAmount/BRL e contagem/token/lockedAt/syncedAt no BillingInvoice.
- Só invoice PAID com PaymentAttempt APPROVED e dinheiro correspondente aplica contractedPrice/count e período. Valor/moeda divergentes deixam REVIEW sem promover o snapshot. Payload de webhook não substitui consulta autoritativa. Downgrade progressivo recebe os valores do snapshot pago antes da promoção pelo fluxo existente.
- BillingInvoice/PaymentAttempt/coverage/entitlement/recurring guard/webhook continuam canônicos. Refund/chargeback não recriam cobertura; regressões existentes são executadas. Dados de snapshot copiados ao invoice mantêm rastreabilidade quando a Subscription fecha o ciclo posterior.

## 6. Liquibase

Migrations novas, incluídas no final do master, sem reusar IDs:

1. `20260929000000_plan_change_token.xml`: UUID e reserva temporária da operação.
2. `20260929010000_vehicle_deletion.xml`: deleted/auditoria/@Version de Car, backfill active=false, índice (user_id,deleted), FKs de atores.
3. `20260929020000_fleet_renewal.xml`: @Version da Subscription, confirmação da pendência, snapshot da próxima renovação, FK para Plan, metadados financeiros no BillingInvoice.

Datas desconhecidas do legado ficam null. Não há hard delete, remoção de dados ou alteração de migrations antigas. O teste de persistência prepara linhas legadas antes de aplicar as migrations novas e verifica backfill; também cobre contagem de todos os adminStatus e round-trip temporal do undo.

**MySQL real ainda precisa passar antes da homologação:** nesta máquina Docker respondeu HTTP 500 e o teste falhou na inicialização do container, não em uma asserção SQL. Não se apresenta a validação Liquibase/MySQL como concluída. A classe aceita um MySQL descartável externo via `FROTTO_TEST_MYSQL_JDBC_URL`, `FROTTO_TEST_MYSQL_USER`, `FROTTO_TEST_MYSQL_PASSWORD`; nunca apontar para produção/staging com dados reais.

## 7. Testes e comandos reproduzíveis

Regressões novas/estendidas:

- SubscriptionPlanChangeServiceTest: limpeza efetiva, provider já restaurado, PUT+GET, token/plano divergente, precisão temporal simulada, escalar persistido não limpo, retry sem pendência, operação concorrente/reserva expirada, cotação de upgrade obsoleta.
- SubscriptionPlanChangeStepsBootstrapTest: construção do bean/AOP sem EntityManager artificial.
- VehicleLifecycleServiceTest, CarResourceEnforcementTest, VehicleRestorationSecurityTest: estados operacionais, contagem/limites, JSON read-only, delete/PATCH, placa, admin/motivo/auditoria, pending/upgrade, role USER negada no proxy real de método.
- InspectionHistoryProtectionTest: excluído só leitura e rejeição de referências a dano/serviço de outro histórico.
- DynamicFleetBillingTest/SchedulerTest: 24h, Platinum/Frotta, mesma preapproval, snapshot imutável, retries/falhas, REVIEW, downgrade/upgrade/cancelamento, paginação limitada e 429.
- FleetRenewalEvidenceTest: preço/contagem pagos, moeda/valor divergentes, competência anterior, refund e downgrade progressivo.
- BillingResourceTest/BillingMeDTOTest: campos seguros, ausência de pending, preço atual/fechado/projetado.
- RecurringBillingPersistenceTest: migration, contagem real e undo após round-trip MySQL (não executável aqui por Docker).
- Frontend: MyPlanPage (ordem do refresh/falso sucesso/projeções), DeletedCars (somente leitura/admin/motivo), fleetBillingFeedback (backend/sem checkout/ciclo seguinte), demais regressões existentes.

No desenvolvimento: 981 testes backend passaram com `-Dtest=!RecurringBillingPersistenceTest`; suite completa anterior apresentou somente erro de infraestrutura de Docker. Frontend: 17 suítes/190 testes passaram. TypeScript e build passaram; avisos existentes de tamanho de bundle/Browserslist não impedem o build. Os resultados do worktree limpo e hashes finais constam da entrega, pois devem ser medidos depois dos commits.

PowerShell, JDK 17/Node 22/npm 10 disponíveis no PATH:

```powershell
Set-Location frotto-server-main
.\mvnw.cmd clean test
# Somente para separar a limitação de Docker das regressões executáveis:
.\mvnw.cmd clean test '-Dtest=!RecurringBillingPersistenceTest'
Set-Location ..rotto-ui-main
npm ci --legacy-peer-deps --no-audit --no-fund
$env:CI='true'
npm test -- --watchAll=false --runInBand
npx tsc --noEmit
npm run build
```

`--legacy-peer-deps` é necessário com o lockfile herdado (npm ci padrão acusa yaml ausente); o lockfile não foi regenerado nem dependências atualizadas. Clean-worktree significa instalação nova de node_modules, target e build, sem copiar artefatos do desenvolvimento.

## 8. Roteiro exato de homologação em staging (não executado aqui)

1. Aprovar a revisão e executar `RecurringBillingPersistenceTest` em MySQL descartável. Exigir sucesso nas migrations/backfill/precisão/FKs. Conferir backup e resultado de `SELECT active,deleted,COUNT(*) FROM car GROUP BY active,deleted` na cópia; legado active=false deve estar deleted=true, com autoria/data desconhecidas null.
2. Integrar os commits somente após essa validação e autorização de publicação. Esta execução não faz merge/push/deploy. Instalar o HEAD revisado em staging pelo processo existente. Inicialmente manter `BILLING_DYNAMIC_FLEET_ENABLED=false`.
3. Com duas contas de teste distintas e um administrador, usar dados fictícios. Confirmar que a conta A não consulta/exclui/PATCHa veículos da B. Confirmar 403 no POST admin restore como USER. Não colocar tokens de MP no navegador, comandos salvos ou logs.
4. Criar assinatura de teste pelo fluxo já existente. Agendar downgrade em Meu Plano; registrar pending no `/api/billing/me`. Executar undo e confirmar: preço BRL original no GET servidor→MP, todos os cinco campos pending/request/effective null no banco, `/billing/me` sem quatro campos de pendência e só então sucesso na tela. Repetir downgrade/undo e retry do undo; não deve haver nova cobrança/preapproval.
5. Simular falha/timeout do provider e alteração concorrente de token em ambiente controlado de teste. Exigir erro sem sucesso de UI e sem apagar intenção diferente; repetir com preço MP já restaurado para validar limpeza local sem PUT. Testar duas requisições concorrentes e recuperação após reserva expirada (cinco minutos somente em crash, não espera normal).
6. Conta SILVER com 15 carros divididos entre ATIVO/MANUTENCAO/BLOQUEADO/A_VENDA: todos contam e o 16º falha. Excluir um: deleted/date/actor persistem, contagem cai e não aparece na lista normal. PATCH active=true/deleted=false/adminStatus falha. Tentar criar com mesma placa variando caixa/hífen/espaço: VEHICLE_PREVIOUSLY_DELETED.
7. Abrir Veículos excluídos → Ver histórico. Conferir receitas, despesas, motoristas, inspeções/danos, manutenção, documentos e relatórios anteriores. Tentar escritas diretas nas APIs operacionais do excluído: erro e histórico inalterado. Testar inspeção com ID filho pertencente ao excluído: rejeitada.
8. Como ADMIN, abrir Administração de cobrança → Veículos excluídos. Sem motivo, botão desabilitado/API rejeita. Restaurar com motivo: mesmo ID, deleted=false/active=true, adminStatus preservado, auditoria visível, contagem sobe, sem cobrança imediata. Testar limite de plano, placa conflitante e pending incompatível: rejeitados.
9. PLATINUM 31→32: preço atual permanece; projeção 82,40→84,90. Excluir antes do fechamento reduz só estimativa; restaurar a aumenta. PLATINUM100→101 deve orientar upgrade FROTTA existente. FROTTA101→102: 256,90→258,90 projetado. Verificar ausência de checkout, PaymentAttempt avulso, segunda assinatura/preapproval e estorno.
10. Em assinatura de teste cuja data autoritativa `next_payment_date` esteja dentro das próximas 24h (sem inventar data local), habilitar `BILLING_DYNAMIC_FLEET_ENABLED=true`. Opcionalmente reduzir intervalo em staging por `BILLING_DYNAMIC_FLEET_INTERVAL_MS`. Confirmar lockedAt/preço/count/plano/token, PUT na mesma preapproval e GET BRL/amount/date corretos; estado SYNCED. contractedPrice/count permanecem do ciclo atual.
11. Adicionar/excluir/restaurar depois do fechamento: próximo valor fechado não muda, projeção seguinte muda, nenhum estorno/cobrança extra. Rodar scheduler novamente: snapshot/token iguais, sem PUT conflitante. Meu Plano deve mostrar valor confirmado e projeção seguinte apenas se diferente.
12. Testar FROTTA→PLATINUM pendente dentro da faixa: snapshot usa PLATINUM e quantidade no fechamento. Adição/restauração acima do teto pending é rejeitada. Upgrade AWAITING_PAYMENT/APPLYING bloqueia create/delete/restore. Cancelamento agendado impede sync; preapproval não é reativada. Com snapshot fechado, troca/undo retorna BILLING_RENEWAL_LOCKED até aplicação.
13. Simular PUT falho, GET falho/valor antigo/moeda diferente e 429. Não pode marcar SYNCED nem alterar contracted*. Retry usa o snapshot, GET já correto dispensa PUT. A menos de 1h, exigir REVIEW/log. Não enviar PUT tardio depois de vencida a data.
14. Ingerir renovação real de teste via mecanismos existentes, consultando MP autoritativamente. Com status aprovado e dinheiro/correlação corretos, conferir invoice expectedAmount, metadados de snapshot, contractedPrice/count e período. Repetir evento: idempotente. Amount/moeda divergentes: REVIEW sem promoção. Exercitar refund/chargeback com a matriz existente de entitlement, sem inventar cobertura.
15. Repetir cancelamento, upgrade pago/pró-rata, downgrade, webhook, recurring guard e entitlement de 5G.12.1. Guardar evidências sem segredos. Só encerrar homologação após MySQL real e cenários provider/visuais aprovados.

## 9. Limites e riscos restantes

- MySQL/Liquibase e locks reais aguardam infraestrutura funcional. Testes unitários não substituem esse gate.
- Chamadas reais ao MP e homologação visual em staging não foram realizadas. Capacidade/limites do provider devem ser observados com o scheduler habilitado.
- Reservas expiradas pressupõem timeout HTTP muito menor que cinco minutos. Falhas ambíguas deixam estado conservador; REVIEW pode exigir intervenção administrativa.
- Não existe transação distribuída com MP. Cancelamento ou alteração externa durante HTTP pode produzir conflito; nenhuma resposta ambígua é tratada como confirmação.
- A reserva e o UUID previnem operações locais comuns concorrentes, mas não prometem fencing de uma requisição HTTP arbitrariamente suspensa além da reserva. Não alterar timeouts sem revisão.
- Bloqueio de mudança de plano após fechamento é deliberado para preservar a cobrança congelada. Pagamento divergente pode manter esse bloqueio até análise.
- Placas legadas duplicadas não são saneadas automaticamente; listagem administrativa de auditoria não é paginada nesta entrega. Para grande volume, paginar antes de uso amplo.
- Mutações operacionais que já estavam em transação quando ocorreu a exclusão podem exigir nova tentativa por controle de versão; homologar concorrência real com MySQL. Não foi implementado lock global em todas as relações históricas.
