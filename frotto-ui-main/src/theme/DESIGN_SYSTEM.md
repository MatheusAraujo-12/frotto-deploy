
# Frotto Design System — entrega da fundação

Etapa concluída em 13/09/2026. A implementação prepara a migração incremental da identidade visual. Não houve alteração de páginas, regras de negócio, APIs, rotas, autenticação, logomarca ou backend. Os tamanhos locais e breakpoints existentes foram preservados; a nova fonte e as superfícies já produzem mudanças visuais globais intencionais.

**1. Arquivos criados**

| Arquivo | Responsabilidade |
| --- | --- |
| `src/theme/tokens.css` | Primitivos de marca, escalas tipográficas, espaçamento e raios. |
| `src/theme/themes.css` | Contrato semântico completo Light/Dark, superfícies, texto, estados, foco e sombras. |
| `src/theme/ionic-overrides.css` | Regras Ionic extraídas e adaptação de controles, cores e overlays. |
| `src/theme/typography.css` | Fonte Inter local e ligação com `--ion-font-family`. |
| `src/theme/legacy.css` | Extração do bloco existente de componentes e páginas, conservando a sequência da cascata. |
| `src/assets/fonts/InterVariable.woff2` | Inter 4.1 variável, normal, pesos 100–900, 352.240 bytes. |
| `src/assets/fonts/OFL.txt` | Licença da fonte. |
| `src/services/theme.test.ts` | Seis testes de preferência, inicialização e sincronização. |
| `src/theme/designSystem.test.ts` | Quatro testes de contrato semântico, contraste e cores críticas iniciais. |
| `src/theme/DESIGN_SYSTEM.md` | Este relatório e orientações da migração. |
| `docs/design-system/*.png` | Quatro capturas: fundação e login, Light/Dark. |
| `docs/design-system/validation.json` | Resultados de 16 cenários de navegador. |

A fonte foi obtida do [projeto oficial Inter](https://rsms.me/inter/), com sua licença. O aplicativo não faz requisições a serviços externos para carregar a tipografia.

**2. Arquivos modificados**

- `src/theme/variables.css`: mantido como entrada de compatibilidade, importa tokens/temas/tipografia; contém aliases para as variáveis existentes e paletas legadas ainda necessárias. Removido o bloco automático duplicado.
- `src/theme/global.css`: fundação global; estilos Ionic e regras de componentes/páginas foram extraídos.
- `src/App.tsx`: adicionados os imports na ordem de migração; removida a inicialização assíncrona de tema do efeito.
- `src/index.tsx`: inicialização única antes de `createRoot`/render.
- `src/services/theme.ts`: aplicação, restauração e persistência do tema; eventos para assinantes e sincronização com sistema/outras abas.
- `src/components/Menu/Menu.tsx`: utiliza o serviço de tema e sua assinatura, sem reinicializá-lo. Navegação e carga de perfil/avatar preservadas.
- `public/index.html`: bootstrap síncrono antes do primeiro paint.

O diff desta etapa foi conferido contra uma cópia do estado local feita antes das alterações. Não foi usado reset/checkout nem regravado o conteúdo de arquivos de páginas. `package.json` e `package-lock.json` permaneceram intactos.

**3. Estrutura final dos tokens**

| Camada | Exemplos |
| --- | --- |
| Marca | `--frotto-blue`, `--frotto-blue-dark`, `--frotto-blue-light`, `--frotto-navy`, `--frotto-surface-dark`, `--frotto-slate`, `--frotto-ice`, `--frotto-cloud`, `--frotto-white`. |
| Primitivos complementares | Tons de slate, superfícies escuras, verde/âmbar/vermelho e `--frotto-ink` para contraste sobre o azul oficial. |
| Semânticos | `primary`, `primary-hover`, `primary-soft`, `primary-text`, `primary-contrast`; `background`; `surface`, `surface-secondary`, `surface-modal`, `surface-elevated`; `text-primary`, `text-secondary`, `text-muted`; `border`, `border-strong`. Todos prefixados por `--frotto-`. |
| Estados | `success`, `warning`, `danger`, `info`, respectivas variantes `-soft` e `-rgb`, e `status-contrast`. |
| Tipografia | Família Inter com fallbacks; tamanhos xs–3xl; pesos 400/500/600/700; alturas de linha 1,25/1,5/1,65; tracking tight/normal/wide. |
| Espaçamento | `space-1` a `space-9`: 4, 8, 12, 16, 20, 24, 32, 40 e 48px. |
| Raios | sm 8px, md 10px, lg 12px e full. Raios legados de cards continuam preservados. |
| Elevação | `shadow-sm`, `shadow-md`, `shadow-lg`, com valores diferentes por tema; sem glow decorativo global. |
| Acessibilidade | `focus-color`, `focus-ring`, pares de contraste para ações e borda forte para campos compartilhados. |

Os componentes novos devem consumir o significado semântico. Por exemplo, usar `var(--frotto-surface)` e `var(--frotto-danger)`, evitando escolher uma cor primitiva diretamente.

**4. Funcionamento Light/Dark**

1. O bootstrap no HTML lê `app-theme`, valida a preferência e, na ausência dela, consulta o sistema. Define `html[data-theme]`, `color-scheme` e o fundo crítico imediatamente.
2. `index.tsx` chama `initTheme()` uma vez antes de montar React. O serviço reaproveita a escolha inicial, aplica `body.light`/`body.dark` e libera o fundo inline para o CSS.
3. `setTheme()` persiste a preferência e notifica assinantes. Se o armazenamento estiver bloqueado, a escolha ainda funciona na sessão.
4. Sem escolha explícita, mudanças do sistema são acompanhadas. Eventos de armazenamento sincronizam abas; limpar a preferência retorna à seleção do sistema.
5. O menu assina o estado e solicita mudanças ao serviço. Não mantém outra lógica de restauração.

`themes.css` contém uma definição completa por tema; não há outra paleta automática em `prefers-color-scheme`. As classes no body continuam compatíveis com CSS legado. O atributo no html permite resolver a paleta antes de o body/React existir e prepara o consumo por um futuro Centro de Temas.

Exceção crítica documentada: os dois fundos de primeiro paint (`#F7F9FC` e `#0D1B2A`) também aparecem no bootstrap HTML, pois o CSS do aplicativo pode ainda não ter carregado. Um teste verifica a sincronização com os tokens e o navegador foi testado com o bundle bloqueado. Não há uma segunda paleta de componentes no bootstrap.

**5. Mapeamento Ionic**

| Variável existente | Fonte nova |
| --- | --- |
| `--ion-color-primary`, RGB e contrast | Tokens `--frotto-primary*`. |
| `--ion-color-primary-shade` / tint | Hover semântico / azul claro. |
| `--ion-background-color` | `--frotto-background`. |
| `--ion-text-color` | `--frotto-text-primary`. |
| `--ion-card-background` | `--frotto-surface`. |
| `--ion-item-background` | `--frotto-surface-secondary`. |
| `--ion-toolbar-background` | `--frotto-toolbar`. |
| `--ion-border-color` | `--frotto-border`. |
| `--ion-color-medium` | `--frotto-text-muted`. |
| `--ion-font-family` | `--frotto-font-family`. |
| `--app-card*`, texto, sombras e backdrop | Aliases semânticos Frotto. |

Os tokens antigos de success/warning/danger do Ionic e de status/ações de veículos continuam disponíveis com a paleta anterior, para evitar mudar a convenção de domínio nesta etapa. Os novos estados semânticos já estão disponíveis e foram adotados localmente nas mensagens de erro e exclusão compartilhada.

Removida a autorreferência do menu. Modais e overlays passam a usar níveis semânticos de superfície. Cards Ionic não interativos recebem o fundo no host: eles não possuem o `native` part em que a regra antiga tentava pintar a superfície.

Ionic 6 usa `!important` internamente em `--ion-color-base`. A adaptação configura os tokens de origem locais (`--ion-color-primary`/`danger`) em vez de disputar essa regra com outro `!important`. Os defaults de texto passaram a respeitar elementos `.ion-color`; os overrides globais de cor com `!important` foram removidos. Overrides locais de páginas ainda fazem parte do legado.

**6. Componentes compartilhados afetados**

- `Form*`/`FormItemWrapper`: a classe compartilhada de campo usa superfície, borda, raio, foco e erro semânticos; suporte a `:focus-within` além da classe existente. Mantidos eventos, valores, validação, labels e ARIA.
- `FormError`: cor semântica de perigo, preservando `role="alert"` e associação ao campo.
- `FormDeleteButton`: cores de perigo/contraste e espaçamento via tokens; confirmação de exclusão intacta.
- `ItemNotFound`: padding, peso e cores via tokens, com texto/estrutura preservados.
- `Menu`: apenas integração com o serviço de tema e correção da autorreferência CSS.

As mudanças de Form*/estado vazio foram feitas nas classes CSS existentes, sem modificar seu JSX. Nenhum componente de UI novo foi criado. `CarListItem` não foi alterado.

**7. Valores hardcoded ainda presentes**

- `variables.css`: cores congeladas de compatibilidade de estados, ações, secondary/tertiary, neutros e superfícies de cards legados.
- `legacy.css` e `ionic-overrides.css`: dimensões, sombras e opacidades anteriores que ainda não foram migradas; aviso âmbar literal no bloco legado.
- `DocumentsPage.css`: aviso `#a16207`.
- `DriverPendenciesSummary.css`: sombra azul `rgba(56, 128, 255, 0.22)` e borda literal.
- CSS local de páginas: tamanhos, pesos, raios, gaps e breakpoints específicos.
- SVGs de marcas de veículos e geradores de PDF possuem suas próprias cores, fora desta migração.
- Bootstrap HTML: somente os dois fundos críticos, cobertos por teste.

Não houve substituição indiscriminada desses valores.

**8. CSS legado e status preservados**

`global.css` deixou de acumular as aproximadamente 2.700 linhas anteriores. A ordem em `App.tsx` permanece: CSS Ionic → `variables.css` → `global.css` → `ionic-overrides.css` → `legacy.css` → CSS das páginas.

`legacy.css` ainda é grande: é a extração do bloco existente, com a mesma sequência de regras, para futura separação por proprietário. Mantém `.car-card__*`, `.cars-dashboard__*`, regras por ID de página, Meu Painel, listas aninhadas, layouts e media queries. Não houve exclusão por suspeita de desuso.

Divergência de status registrada e **resolvida em 14/09/2026** (ver seção 13): `CarListItem.tsx` mapeava `A_VENDA` para a família visual "info" enquanto `--app-car-status-sale-*` mantinha uma família âmbar própria. Decisão oficial: **A_VENDA → `FrottoBadge` variant="info"** — "À venda" é um estado informativo, não um warning. Os tokens `--app-car-status-sale-*` (`variables.css`) não foram removidos porque ainda são consumidos por um bloco `.car-card__*` em `legacy.css`; ver seção 13 para o estado exato desse bloco. Os demais mapeamentos de status não foram alterados.

**9. Riscos e limites**

- A nova família tipográfica pode mudar quebras de linha em telas ainda não verificadas. A checagem em produção do login mostrou a mesma altura de inputs com Inter e com o fallback Ionic (42,796875px), sem overflow a 390/1280px.
- A compatibilidade ainda mistura tokens novos e legados. Isso é esperado durante a migração e não constitui validação de contraste de todas as páginas.
- Os tons de texto semântico foram verificados; cores locais e estados antigos ainda precisam de revisão na respectiva tela piloto.
- Os testes iOS/Material usaram componentes Ionic reais em Edge headless. Não substituem validação em Safari/iPhone ou Android físico.
- Fluxos autenticados completos, teclado virtual e modais de negócio aninhados não foram exercitados nesta etapa.
- O bootstrap inline precisa continuar permitido pela política de conteúdo da implantação; uma futura CSP restritiva deverá fornecer nonce/hash.
- A Inter variável acrescenta um arquivo local de aproximadamente 344KiB, carregado como recurso de fonte, fora do JS.

**10. Validação**

| Verificação | Resultado |
| --- | --- |
| `npm run build` | Build de produção concluído com sucesso após as alterações. |
| `npm test -- --watchAll=false --runInBand` | 12 suítes existentes, 113 testes aprovados. |
| Teste adicional `src/services/theme.test.ts` | 6 testes aprovados. |
| Teste adicional `src/theme/designSystem.test.ts` | 4 testes aprovados. |
| `npx tsc --noEmit` | Sem erros. |
| `git diff --check` nos arquivos modificados | Sem erros de whitespace. |
| Componentes Ionic renderizados | 8 cenários: Light/Dark × iOS/Material × 390/1280px. Inter carregada, foco visível, modal aberto e ausência de overflow horizontal. |
| Primeiro paint com bundle bloqueado | 4 cenários de preferência salva/ausente/inválida, com tema e fundo corretos. |
| Login do build de produção | 4 cenários: Light/Dark × 390/1280px, sem erros JavaScript. Requisições de dados interceptadas localmente; sem chamadas de negócio reais. |

Total automatizado: 123 testes em 14 suítes, executados em três comandos. A instrumentação de navegador foi instalada em diretório temporário, sem adicionar dependências de UI ou alterar o lockfile do projeto.

Contrastes medidos de pares semânticos (texto normal):

| Par | Light | Dark |
| --- | --- | --- |
| Texto principal / fundo | 16,49:1 | 16,49:1 |
| Texto secundário / card | 7,58:1 | 10,35:1 |
| Texto muted / superfície secundária | 4,51:1 | 5,26:1 |
| Texto do botão / azul oficial | 4,70:1 | 4,70:1 |
| Texto de ação / card | 5,74:1 | 5,86:1 |

O navy oficial sobre o azul ficava ligeiramente abaixo de 4,5:1. O primitivo complementar `ink` foi usado para o texto do botão sólido, mantendo o azul oficial intacto. Estados semânticos success/warning/danger/info sobre a superfície modal também passaram em 4,5:1. Isso não é uma certificação de acessibilidade de todas as telas.

Warnings observados: Browserslist com base desatualizada, depreciação `fs.F_OK` no toolchain e bundle JS principal de aproximadamente 1,51MB gzip. A execução das suítes existentes também emite avisos de `act` e de fixtures Ionic sem content element. Nenhum teste falhou; essas questões não foram corrigidas nesta etapa.

Evidências: [dados da validação](../../docs/design-system/validation.json), [fundação Light](../../docs/design-system/foundation-light.png), [fundação Dark](../../docs/design-system/foundation-dark.png), [login Light](../../docs/design-system/login-light.png), [login Dark](../../docs/design-system/login-dark.png).

**11. Diff resumido**

- Sete arquivos existentes modificados, comparados ao estado local anterior à etapa.
- Nova camada de tokens/temas, fonte local, testes e documentação.
- Extração do CSS Ionic e legado explica a maior parte das linhas movidas.
- Removido o bloco automático redundante e as duas inicializações assíncronas de tema.
- Nenhum arquivo de página, modelo de negócio, API, rota ou backend modificado; nenhuma dependência adicionada ao aplicativo.

**12. Próxima etapa sugerida (histórico — ver seção 13 para o estado atual)**

Revisar esta fundação e decidir o mapeamento oficial dos status de veículos. Depois, mediante autorização, migrar apenas a tela de Veículos e um formulário associado, extraindo os estilos que lhes pertencem do legado e validando os dois temas com dados representativos.

Status: **concluído**. A tela de Veículos foi migrada em 13/09/2026 e revisada em 14/09/2026 — detalhes na seção 13. Nenhuma outra tela foi migrada.

**13. Migração piloto — Veículos (13–14/09/2026)**

Primeira tela migrada para a identidade Frotto. Escopo: `src/pages/Cars/Cars.tsx`, `Cars.css`, `CarListItem.tsx`, `CarListItem.css`, e a extensão aditiva de `src/components/List/ItemNotFound.tsx`. Backend, endpoints, autenticação e regras de negócio não foram alterados.

*Decisão de status oficializada:* `A_VENDA` → `FrottoBadge variant="info"`. "À venda" é tratado como estado informativo, não como warning. Ver seção 8 para o histórico da divergência. Os tokens legados `--app-car-status-sale-*` (âmbar) permanecem em `variables.css` como **candidatos a consolidação futura** — hoje só são referenciados por um bloco `.car-card__badge--*` / `.car-card__detail-value--*` em `legacy.css` (linhas ~1601–1730) que não tem nenhuma correspondência em `.tsx` atual (nenhum arquivo importa as classes `car-card__*` ou `cars-dashboard`); é CSS aparentemente órfão de uma versão anterior da tela de Veículos, documentado aqui e **não removido** sem confirmação.

*FrottoBadge — primeira primitiva oficial do Design System* (`src/components/UI/FrottoBadge.tsx`/`.css`): variantes `success/warning/danger/info/neutral/dark`. Revisão desta etapa: altura e padding consistentes entre variantes (mesma regra base, só cor muda), radius `--frotto-radius-full`, contraste Light/Dark verificado visualmente nos 5 status reais em screenshot do app rodando (seção de validação abaixo), permanece não-interativo (sem foco/cursor/role de botão — não deve virar botão), `white-space: nowrap` adequado para os rótulos atuais (nenhum excede a largura do card nos testes feitos, inclusive com nomes de veículo muito longos ao lado).

*FrottoCard:* **adiado deliberadamente**. `CarListItem` continua sendo um componente de domínio específico (usa `IonItem` com `::part(native)`), sem abstração de card genérica. Decisão a revisitar depois de mais telas migradas.

*As duas ações "+":*
- **"+" na toolbar** (`#cars-action-trigger`, ícone apenas): abre o menu de ações rápidas existente (Adicionar veículo, manutenção, lembrete, despesa, receita). Mesmo padrão usado em outras páginas do app (ex.: `Reminders.tsx`), preservado por consistência.
- **CTA "+ Novo veículo"** (corpo da página): ação contextual desta tela — vai direto para o modal de cadastro de veículo.
Revisão visual desta etapa (screenshots reais, ver abaixo): não há competição excessiva. Ocupam faixas visuais diferentes (toolbar escura vs. conteúdo claro), tamanhos diferentes (ícone vs. botão com rótulo) e a CTA contextual é a ação mais evidente dentro do conteúdo, como pedido. Nenhum fluxo foi alterado.

*Validação no app real (14/09/2026):* rodado localmente nesta sessão — MySQL 8 via Docker (container efêmero, descartado ao final), backend Spring Boot (profile `dev`, Liquibase com contexts `dev,faker`) e frontend (`react-scripts start`), autenticado com o usuário de desenvolvimento padrão do JHipster (`admin/admin`, seed do próprio repositório). Cinco veículos reais foram criados via API (`POST /api/cars`) cobrindo nome curto, nome muito longo, placa Mercosul, e os status ATIVO/A_VENDA/MANUTENCAO/BLOQUEADO/RETIRADO. Capturado com Chrome headless via CDP: Desktop Light, Desktop Dark, Mobile Light, Mobile Dark, estado vazio real, um único veículo, lista completa e busca sem resultado. Único ajuste decorrente dessa validação: o placeholder do campo de busca ("Buscar por veículo, placa ou motorista") truncava no mobile — encurtado para "Placa, veículo ou motorista". O cenário "com motorista/Alugado" não foi criado como registro real (endpoint de cadastro de motorista não está disponível como POST simples) — validado por leitura de código (`CarListItem.tsx`) e pela prévia estática publicada em 13/09.

Pendências conhecidas (dívida técnica, não relacionadas a branding): Jest não consegue rodar `App.test.tsx`/`Menu.test.tsx` por um problema pré-existente de parse de ESM do `@ionic/core` (não é desta migração, não foi investigado nesta etapa). `resolveCarIdentity` compõe o título como "Modelo - Marca" (ex.: "Uno - Fiat"), o que é lógica de dados existente, não um token/estilo — fora do escopo de uma migração visual.

**14. Golden Sample — Veículos (declarado em 14/09/2026)**

A partir desta data, a tela de Veículos (`src/pages/Cars/Cars.tsx`, `Cars.css`, `CarListItem.tsx`, `CarListItem.css`) é a **referência visual oficial** do Design System Frotto. Toda nova migração de tela deve olhar para Veículos como o exemplo mais completo e mais recente de como os tokens e primitivas se combinam na prática, para:

- hierarquia visual (eyebrow/título/subtítulo, peso e tamanho de fonte por nível);
- cabeçalhos de página (`ion-no-border` + `.app-toolbar-clean` para a toolbar principal, `.app-subtoolbar` para uma barra secundária quando houver busca/filtro);
- CTA principal (um botão preenchido único por contexto, ex.: `.cars-page-head__cta`; ações "+" de toolbar ficam só com ícone, sem competir com o CTA de conteúdo);
- campos de busca (`ion-searchbar` dentro de `.app-subtoolbar`, com o realce de foco em `--ion-color-primary` documentado em `Cars.css`);
- badges (`FrottoBadge`, variantes `success/warning/danger/info/neutral/dark` — mapeamento de status oficializado na seção 13);
- cards/list items (`CarListItem`: `IonItem` com `::part(native)` pintado via `--app-card`/`--app-border`/`--app-radius`/`--app-shadow`, hover com leve `translateY` e `--app-shadow-hover`, foco com `--frotto-focus-ring`);
- superfícies (`--app-card`, `--app-card-strong`/`--frotto-surface-secondary` para blocos internos de destaque);
- bordas (`--app-border`, `--frotto-border`);
- sombras (`--app-shadow`, `--app-shadow-hover`, sem glow decorativo);
- tipografia (escala Inter definida em `tokens.css`, pesos via `--frotto-font-weight-*`);
- densidade de informação (`.app-shell.app-shell--compact`, gap 14px entre blocos, grids `auto-fit`/`minmax` para metadados);
- comportamento Light/Dark (tokens semânticos únicos por tema em `themes.css`, sem paleta paralela);
- comportamento Desktop/Mobile (grids que colapsam para 1 coluna, ações que viram largura total, sem rolagem horizontal);
- estados vazios (`ItemNotFound`, com `message` simples ou `title`+`description`+`actionLabel`/`onAction` quando há uma ação de recuperação);
- foco e hover (contorno via `--frotto-focus-ring`, elevação sutil no hover, nunca mudança de cor agressiva).

**Isto não autoriza copiar o layout específico de Veículos para outras telas.** Cada tela deve reaplicar os mesmos princípios e tokens à sua própria estrutura de dados e fluxo — a semelhança é de linguagem visual, não de wireframe.

**15. Migração — Página do Carro (detalhe) (14/09/2026)**

Ver relatório de execução com auditoria, screenshots, arquivos alterados e validação em `docs/design-system/car-page-migration.md`.

**16. Decisões oficiais pós-revisão — Página do Carro (14/09/2026)**

Após revisão do resultado da seção 15, ficaram definidas as seguintes regras, vinculantes para as próximas migrações:

1. **"Despesas" não usa Warning.** A cor amber aplicada nesta etapa foi revertida (ver seção 17). Warning e Danger continuam reservados a estados de UI, não a categorias financeiras.
2. **Warning é reservado a atenção, manutenção e pendência.** Não usar Warning para representar "saída de dinheiro" ou qualquer outra categoria puramente financeira.
3. **Danger é reservado a erro, situação crítica e ações destrutivas** (ex.: excluir). Não usar Danger para representar despesa/saída financeira — despesa não é uma ação destrutiva.
4. **Semântica financeira própria, separada de success/danger.** Tokens `--frotto-financial-positive`/`-soft` e `--frotto-financial-negative`/`-soft` foram adicionados à fundação (`themes.css`, um valor por tema). Eles **não são aliases** de `--frotto-success`/`--frotto-danger` — hoje reaproveitam os mesmos primitivos de cor (`--frotto-green-700`/`-300` e `--frotto-red-700`/`-300`) e por isso têm aparência igual, mas são declarações independentes no grafo de tokens: mudar a paleta de success/danger no futuro não muda a de financial-positive/negative, e vice-versa. Ação "Despesas" da Página do Carro passou a usar `financial-negative`; "Receitas" passou a usar `financial-positive` (extensão não pedida explicitamente, mas aplicada por consistência conceitual — o par receita/despesa é financeiro nos dois lados, não faria sentido só um dos dois sair de success/warning). Aplicado via novas classes locais `.car-page__action--financial-positive`/`--financial-negative` em `Car.css`, que alimentam o mesmo mecanismo de `--app-semantic-*` já usado por `.app-semantic--success` etc., sem alterar o sistema compartilhado `--app-action-*` (usado por outras telas, fora do escopo desta migração).
5. **Sem card "Financeiro" artificial.** Confirmado: não criar um bloco dedicado só para hospedar as ações Receitas/Despesas/Editar sem dado financeiro próprio para mostrar. As ações continuam dentro do card de dados do veículo.
6. **CSS órfão `.car-card__*`/`.cars-fleet-summary*` em `Car.css` permanece documentado e não removido** nesta etapa (ver seção 15/relatório, "CSS legado encontrado").
7. **`FrottoCard` continua adiado.**

**17. Migração — Formulários de Veículo (cadastro/edição) (14/09/2026)**

Ver relatório de execução com auditoria, screenshots, arquivos alterados e validação em `docs/design-system/car-forms-migration.md`.

**18. Decisões oficiais pós-revisão — Formulários de Veículo (14/09/2026)**

Etapa aprovada. Decisões vinculantes para as próximas migrações:

1. **`Form*` (`FormInput`/`FormSelect`/`FormCurrency`/`FormDate`/`FormItemWrapper`/`FormError`/`FormInputLabel`/`FormDeleteButton`) é o Golden Sample oficial de formulários**, no mesmo nível de Veículos (listagem) e Página do Carro (detalhe). Toda nova tela com formulário deve reaproveitar esses componentes, não recriar campos.
2. **Título do modal de cadastro: "Novo veículo".**
3. **Título do modal de edição: "Editar veículo".** (Antes, os dois casos mostravam "Carro" — ver seção 17/relatório, ponto de aprovação 1. Implementado nesta atualização: `CarAdd.tsx` agora usa `formInitial.id ? "Editar veículo" : "Novo veículo"`.)
4. **Grid de 1 coluna mantido por enquanto** nos formulários (`.app-form-grid` sem 2 colunas em desktop). Não abrir uma segunda convenção de densidade dentro da família `Form*` sem essa decisão ser revisitada explicitamente.
5. **Melhorias em `FormDate` e `aria-required` aprovadas** — mudanças compartilhadas por múltiplos formulários (seção 17/relatório, pontos de aprovação 3 e 4), confirmado que não alteram comportamento/validação de nenhum consumidor existente.
6. **"Receitas" usa `financial-positive`.**
7. **"Despesas" usa `financial-negative`.** (Ambos já implementados na etapa anterior — decisão apenas ratifica o que já está em produção.)
8. **`FrottoModal` continua adiado** — só um modal de formulário foi auditado até agora (`CarAdd`); revisitar depois de mais telas.

**19. Migração — Motoristas e Pendências (14/09/2026)**

Ver relatório de execução com auditoria, screenshots, arquivos alterados e validação em `docs/design-system/drivers-pendencies-migration.md`.

Decisão de destaque desta etapa, **pendente de confirmação explícita** (ver relatório, seção 14, ponto 1): o status `OPEN` de uma pendência de motorista deixou de usar Danger (vermelho) e passou a usar warning (âmbar); `PARTIALLY_PAID` passou de warning para info (azul). Motivo: pendência em aberto é o estado normal antes da quitação, não uma situação crítica/erro — Danger fica reservado a erro e ação destrutiva, conforme já decidido na seção 16. O valor monetário em aberto/restante continua usando `financial-negative` independentemente do status, então uma pendência aberta com saldo mostra badge âmbar **e** valor vermelho ao mesmo tempo — combinação deliberada entre status operacional e significado financeiro.

**20. Decisões oficiais pós-revisão — Motoristas e Pendências (14/09/2026)**

Etapa aprovada. Decisões vinculantes:

1. **Mapeamento de status de Pendências confirmado**: `OPEN` → warning, `PARTIALLY_PAID` → info, `PAID` → success. Deixa de ser "pendente de confirmação" (seção 19) — é definitivo.
2. **Status operacional e significado financeiro permanecem conceitos separados.** Um badge de status (warning/info/success) e a cor de um valor monetário (financial-negative/positive) são decisões independentes e podem coexistir na mesma linha/card.
3. **Valores devidos/em aberto usam `financial-negative`.**
4. **Valores pagos/recebidos usam `financial-positive`.**
5. **A divisão de `DriverAdd` em três cards (Contrato / Motorista / Endereço) está aprovada** como padrão para formulários grandes com seções logicamente distintas.
6. **CSS órfão `.app-nested-list` em `#driver-pendencies-page` permanece documentado, não removido** (ver `docs/design-system/drivers-pendencies-migration.md` seção 8).
7. **Motoristas e Pendências passam a integrar as referências visuais oficiais do Design System**, junto de Veículos (listagem), Página do Carro (detalhe) e `Form*` (formulários).

**21. Consolidação — `FrottoCard` e `FrottoModal` (14/09/2026)**

Ver relatório de execução com auditoria, arquivos alterados, consumidores migrados/não migrados e validação em `docs/design-system/consolidation-frottocard-frottomodal.md`. Esta seção documenta a API oficial das duas primitivas.

**`FrottoCard`** (`src/components/UI/FrottoCard.tsx`) — wrapper fino sobre `IonCard`, não um `<div>` reimplementando a superfície. Motivo: a superfície (fundo, borda, raio, sombra, margem) já é aplicada globalmente a todo `<ion-card class="...">` por uma regra em `ionic-overrides.css`/`legacy.css` desde a fundação do Design System; `FrottoCard` continua emitindo a classe `app-panel-card` internamente para não divergir dessa regra nem quebrar CSS local já existente que a referencia (ex.: `.driver-pendencies-driver-card`, `.driver-pendency-payment-card`). O que `FrottoCard` adiciona de fato é a variante `interactive`.

```tsx
<FrottoCard>...</FrottoCard>

<FrottoCard interactive onClick={handleOpen}>...</FrottoCard>
```

Props: `interactive?: boolean` (default `false`) + todas as props padrão de `IonCard` (`className`, `onClick`, `onKeyDown`, etc., passadas adiante). Quando `interactive` é `true` **e** um `onClick` é passado, o componente adiciona `role="button"`, `tabIndex={0}` e ativação por teclado (Enter/Espaço chamam o `onClick`) — sem transformar o card num `<button>` real, preservando a estrutura de card. Sem `onClick`, `interactive` só adiciona o afinamento visual (cursor, foco) sem semântica de botão.

- **Quando usar**: qualquer cartão de conteúdo (com ou sem cabeçalho `app-panel-header`/ícone/título/subtítulo) que hoje usaria `<IonCard className="app-panel-card">` — formulários, painéis de resumo, blocos de detalhe.
- **Quando NÃO usar**: linhas de lista clicáveis com navegação (`routerLink`), exclusão inline ou qualquer outro comportamento específico do `IonItem` — ver seção "CarListItem" abaixo. Também não usar para substituir `IonModal`/`IonPage` (isso é `FrottoModal`).
- **Tokens consumidos**: nenhum novo — reaproveita inteiramente `--app-card`, `--app-border`, `--app-radius`, `--app-shadow`, `--app-shadow-hover` (via a regra global já existente) e `--frotto-focus-ring` (novo uso, só no anel de foco da variante `interactive`).
- **Acessibilidade**: `role="button"`/`tabIndex`/teclado só aparecem quando `interactive` + `onClick` estão presentes juntos: um card puramente decorativo nunca ganha semântica de botão por engano.
- **Limitação conhecida**: por ser um `<ion-card>`, herda o comportamento de foco/clique do Ionic — não foi testado como card *dentro* de outro elemento focável (nesting), cenário que não existe hoje no app.

**`CarListItem` e o padrão de lista interativa — decisão explícita de não migrar.** `CarListItem`, `.driver-pendency-list-item` e `.driver-list-item` (Motoristas) usam `IonItem` com `routerLink`/`button` e pintam a superfície via `::part(native)`. Isso é o mecanismo do próprio Ionic para navegação/ripple/teclado em itens de lista — recriar esse comportamento em cima de um `<div>` (`FrottoCard`) duplicaria a implementação e arriscaria regressão de acessibilidade/foco, exatamente o que a tarefa pediu para evitar. Os três continuam como estão, consumindo os mesmos tokens (`--app-card`/`--app-border`/`--app-radius`/`--app-shadow`/`--frotto-focus-ring`) via CSS local — visualmente consistentes com `FrottoCard`, sem serem o componente.

**`FrottoModal`** (`src/components/UI/FrottoModal.tsx`) — encapsula só o que é **de fato idêntico** nos 4 modais de formulário auditados: o cabeçalho (`IonHeader.ion-no-border > IonToolbar.app-toolbar-clean` com Cancelar à esquerda, título ao centro, ação primária à direita, barra de progresso opcional) e o `IonPage`/`IonContent` que os envolve. O corpo é 100% `children` — cada consumidor mantém sua própria composição interna (`app-shell`+`app-section` em três deles, `app-form-page__body` no quarto), porque essa parte **não** era idêntica entre os quatro e forçar um único formato teria sido inventar uma convenção que não existe.

```tsx
<FrottoModal
  pageId="car-add-page"
  title={isEditing ? TEXT.editCar : TEXT.newCar}
  onCancel={() => closeModal()}
  primaryLabel={TEXT.save}
  onPrimaryAction={handleSubmit(onSubmit)}
  primaryDisabled={isLoading}
  isLoading={isLoading}
>
  <div className="app-shell app-shell--compact">
    <section className="app-section">...</section>
  </div>
</FrottoModal>
```

Props: `pageId` (obrigatório — propagado para `<IonPage id={pageId}>`; preserva todo o CSS já escopado por id, ex. `#car-add-page`, `#driver-pendency-payment-page`), `title`, `onCancel`, `cancelLabel?` (default `TEXT.cancel`), `cancelDisabled?` (default `false` — usado por `DriverPendencyPaymentModal`, que desabilita Cancelar durante o loading), `primaryLabel`, `onPrimaryAction`, `primaryDisabled?`, `isLoading?` (controla a `IonProgressBar`), `children`.

- **Quando usar**: um modal de formulário de página inteira com o padrão Cancelar/Título/Ação primária no cabeçalho.
- **Quando NÃO usar**: modais que não seguem esse padrão de cabeçalho — ex. o modal de resumo de Pendências (bottom-sheet com botão fechar em X, sem ação primária) continua como estava, por ser uma estrutura genuinamente diferente, não um formulário.
- **Comportamento que `FrottoModal` explicitamente não decide**: salvar, excluir, validação, chamadas de API — tudo isso continua 100% nos componentes consumidores, passado via `onPrimaryAction`/`onCancel`/`children`.
- **Tokens consumidos**: nenhum novo — só reaproveita `.app-toolbar-clean`, `.app-outline-btn`, `.app-primary-btn`, já existentes.
- **Acessibilidade**: preserva o focus trap nativo do `IonModal`/`IonPage` (não reimplementado); botão Cancelar sempre com texto visível (nunca só ícone), então não depende de `aria-label` adicional.
- **Limitação conhecida**: não tem slot de footer — nenhum dos 4 consumidores atuais precisa de um; se um futuro modal precisar (ações adicionais fora do cabeçalho), isso exige estender a API, não usar `FrottoModal` como está.

**Componentes oficiais do Design System após esta etapa**: `FrottoBadge`, `FrottoCard`, `FrottoModal`, a família `Form*` (Golden Sample de formulários) e `ItemNotFound` (Golden Sample de empty states).

**22. Migração — Documentos (14/09/2026)**

Ver relatório de execução com auditoria, screenshots, arquivos alterados e validação em `docs/design-system/documents-migration.md`.

Domínio auditado antes de alterar: uma única página (`/documents`, `DocumentsPage.tsx`), 5 tipos de documento **todos já implementados** (Multa, Manutenção Compartilhada, Recibo de Aluguel, Confissão de Dívida, Entrega/Devolução Checklist) e 4 status (`DRAFT`/`FINAL`/`SENT`/`CANCELED`). Nada foi inventado — todos os tipos citados na tarefa já existiam no código.

**Novo mapeamento oficial de status de Documentos** (`FrottoBadge`): `DRAFT` → warning (rascunho, incompleto), `FINAL` → success (finalizado), `SENT` → info (enviado — estágio distinto de "finalizado", não reaproveita success nem o "medium" neutro que o código antigo usava de forma inconsistente com o ícone), `CANCELED` → neutral (estado morto/inativo, não é erro nem ação destrutiva — mesmo princípio já aplicado a `OPEN` em Pendências). O código anterior tinha **duas funções discordando entre si** sobre a cor de `SENT`/`CANCELED` (`resolveStatusColor` vs. `documentToneClass`) — unificado nesta etapa.

Ícone por tipo de documento adicionado (antes todos usavam o mesmo ícone genérico): Multa → alerta, Manutenção Compartilhada → ferramenta, Recibo de Aluguel → recibo, Confissão de Dívida → carteira, Entrega/Devolução Checklist → prancheta. Só a escolha do ícone existente em Ionicons — nenhuma biblioteca nova.

`FrottoCard` aplicado a todos os cards de container da página e dos dois modais (filtros, resultado, paginação, visualização, os 3 passos do wizard). `FrottoModal` **não** foi aplicado aos dois modais existentes (visualização e wizard) — ambos usam ações no rodapé (`IonFooter`) e um botão fechar em X, não o padrão Cancelar/Título/Ação-primária do cabeçalho; forçar `FrottoModal` exigiria remover esse rodapé (usado inclusive para a navegação Voltar/Próximo entre os 3 passos do wizard), o que seria uma mudança estrutural, não uma aplicação do padrão já existente — documentado como exceção, mesmo critério já usado para o modal de resumo de Pendências.

Os campos do wizard usavam uma família de inputs local (`TextField`/`SelectField`/`DecimalField`/etc.), paralela a `Form*`. `TextField`→`FormInput` e `SelectField`→`FormSelect` (troca direta segura, mesmo contrato valor/onChange). `DecimalField`/`IntegerField`/`AreaField`/`Autocomplete` mantiveram sua lógica exata (máscara decimal, regex de inteiro, sem limite de caracteres, busca assíncrona) e só ganharam o invólucro visual (`.app-form-item`/`FormInputLabel`) — trocá-los por `FormCurrency`/`FormInputArea` mudaria a máscara/o limite de caracteres, então não foram trocados.

Hardcode removido: `DocumentsPage.css` tinha `color: #a16207` (já documentado como pendência desde a seção 7) → `var(--frotto-warning)`.

**23. Decisões oficiais pós-revisão — Documentos (14/09/2026)**

Etapa aprovada. Decisões vinculantes:

1. **Mapeamento de status de Documentos confirmado**: `DRAFT` → warning, `FINAL` → success, `SENT` → info, `CANCELED` → neutral. Deixa de ser "pendente de confirmação" — é definitivo. **Danger não representa automaticamente cancelamento ou estado terminal** — reafirma o mesmo princípio já vinculante desde a seção 16 (Danger é só erro/situação crítica/ação destrutiva).
2. **Ícones distintos por tipo de documento são oficiais.** Ionicons diferentes por tipo ajudam reconhecimento rápido e **não** criam identidades ou paletas independentes — todos os tipos continuam usando a mesma superfície (`app-soft-icon`), a mesma tipografia e os mesmos tokens; só o glifo do ícone varia.
3. **Os dois modais de Documentos (visualização e wizard) são oficialmente exceções ao `FrottoModal`.** Representam um padrão distinto — "Modal de Fluxo/Wizard" (ações no rodapé, navegação multi-passo) — reconhecido mas **não abstraído ainda**. Não criar `FrottoWizardModal` nesta etapa nem nas próximas até haver evidência real de reutilização (um segundo consumidor com o mesmo padrão).
4. **Campos com comportamento especializado (máscara própria, regex, limite de caracteres, busca assíncrona) não devem ser substituídos por `Form*` quando isso alteraria esse comportamento.** É permitido e esperado aplicar só a linguagem visual (`.app-form-item`/`FormInputLabel`/tokens), preservando a lógica exatamente como está — mesmo critério já usado em `DecimalField`/`IntegerField`/`AreaField`/`Autocomplete` de Documentos, agora oficializado como padrão geral para futuras migrações.

**24. Migração — Meu Painel (15/09/2026)**

Ver relatório de execução com auditoria, screenshots, arquivos alterados e validação em `docs/design-system/mypanel-migration.md`.

**O que "Meu Painel" realmente é** (rota `/menu/meu-painel`, `MyPanelPage.tsx`): uma tela de **perfil/configurações da conta** com 3 abas — Pessoal, Fiscal, Segurança. **Não é** um dashboard de métricas/gráficos — não existe hoje nenhuma rota de "Dashboard/Visão Geral" no app (`/menu` redireciona direto para Veículos). As seções da tarefa sobre métricas/gráficos/tendências não se aplicam a esta página; documentado como tal, nada foi inventado para preenchê-las.

Achado de auditoria: `CadastroTab.tsx` (um 4º componente de aba, com avatar/fiscal-sync próprios) **não é importado por nenhum lugar do app** — código morto, sobreposto pela combinação atual de `PersonalTab`+`FiscalTab`. Suas classes CSS exclusivas (`my-panel-card`, `my-panel-item`, `my-panel-field-error`, `my-panel-empty-state`, `my-panel-inline-actions`, em `MyPanelPage.css`) são, por consequência, também órfãs. Documentado, não removido.

Ajustado: `IonCard`→`FrottoCard` nos cards de skeleton/erro/das 3 abas; estados vazios (`app-empty-state` custom) e o estado de erro de carregamento → `ItemNotFound`; labels (`IonLabel position="stacked"`) → `FormInputLabel`; mensagens de erro de campo (`IonText` local) → `FormError`, com `aria-invalid`/`aria-describedby` novos e o modificador `.app-form-field--invalid` (borda/fundo vermelhos) agora aplicado corretamente — antes só o texto do erro aparecia em vermelho, sem destacar o campo. Nenhuma lógica de validação, máscara (`onIonInput`/`onIonBlur` mantidos exatamente como estavam) ou payload foi alterada.

**25. Reestruturação — Meu Painel → Configurações (15/09/2026)**

Ver relatório de execução com auditoria funcional, mapa de campos, screenshots, arquivos alterados (frontend + backend) e validação em `docs/design-system/settings-migration.md`.

Mudança de produto aprovada, não apenas visual — auditoria funcional completa executada antes de qualquer implementação. Decisões vinculantes:

1. **"Meu Painel" → "Configurações"**, com navegação interna em duas seções: **Meu Cadastro** e **Segurança**. Desktop usa rail vertical (`.settings-rail`); mobile reaproveita o `IonSegment` já usado nas etapas anteriores — nenhuma navegação nova foi inventada, só uma reorganização de layout condicionada por breakpoint (720px, mesmo já usado no resto do app).
2. **Toggle PF/PJ permanece ligado ao dado fiscal real** (`taxPersonType`, já existente) — não ao conjunto "Pessoal". "Dados Cadastrais" (Seção 1 de Meu Cadastro) é a aba Fiscal de antes, reorganizada e rotulada como Pessoa Física/Pessoa Jurídica. Decisão tomada explicitamente para não correr o risco de apagar `taxLandlordName`/`taxCpf`/`taxEmail`/`taxPhone` na primeira vez que a conta salvasse como PJ (a troca de tipo já zera o branch oposto no backend — comportamento pré-existente, preservado).
3. **Os campos "Pessoal" (nome/CPF/nascimento/e-mail/telefone de quem está logado) viraram o bloco "Meus dados"**, dentro de "Dados complementares", sempre visíveis (sem toggle) — são um conceito genuinamente distinto de "Fiscal" no banco (colunas e endpoint próprios, `PATCH /api/me`), confirmado por auditoria como não consumido por Documentos nem Relatórios.
4. **Uma única ação "Salvar"** no rodapé de Meu Cadastro. Internamente continua chamando `PATCH /api/me` (Pessoal + avatar + logomarca) e `PATCH /api/me/tax-data` (Fiscal) como duas transações separadas — só a ORQUESTRAÇÃO (qual botão dispara qual chamada) mudou; nenhum payload, validação, máscara ou regra de negócio foi alterada. Cada endpoint só é chamado se sua respectiva parte estiver "dirty".
5. **Logomarca é oficialmente um conceito separado de avatar**, com coluna (`logo_url`) e endpoints próprios (`/api/me/logo`, PATCH/DELETE) no backend, reaproveitando a mesma infraestrutura S3 já usada pelo avatar (`AWSS3FileService`) — nenhum armazenamento improvisado. UI em "Identidade nos documentos" dentro de Meu Cadastro.
6. **Avatar removido do cabeçalho dos PDFs de Documentos e Relatórios.** Auditoria encontrou que o avatar já era usado como logomarca de fato em `pdfLetterhead.ts` (compartilhado pelos dois módulos) — contrariava a regra nova. Removido nesta etapa (decisão explícita do usuário). **Atualização (15/09/2026, continuação da mesma etapa): a logomarca foi conectada ao cabeçalho** — ver seção 26.
7. **Identidade fiscal nos documentos é sempre lida ao vivo de `/api/me`**, nunca congelada no documento — comportamento pré-existente, confirmado por auditoria e preservado sem alteração (só o payload de motorista/veículo é de fato um snapshot).
8. **`CadastroTab.tsx` e seu CSS órfão continuam não removidos** — mesma decisão da seção 24, dívida técnica documentada, fora de escopo desta etapa.

**Componentes/arquivos novos**: `MeuCadastroTab.tsx` (composer dos 3 cards de Meu Cadastro); `FiscalTab.tsx` deixou de exportar um card próprio e passou a exportar `FiscalCadastralFields`/`FiscalComplementaryFields` (campos puros, sem `FrottoCard`); `PersonalTab.tsx` da mesma forma passou a ser fields-only. Nenhum componente novo do Design System foi criado — tudo reaproveita `FrottoCard`/`FormInputLabel`/`FormError`/`ItemNotFound` já oficiais.

**26. Integração Logomarca × PDF — regra definitiva (15/09/2026)**

Ver relatório de execução em `docs/design-system/settings-migration.md` (seção "Integração da logomarca ao letterhead").

**Regra oficial e vinculante:**

> Avatar é identidade do usuário e nunca é utilizado como identidade documental.
> Logomarca é identidade documental e pode ser utilizada nos cabeçalhos dos PDFs.
> Na ausência de logomarca, o cabeçalho é textual e nenhum avatar é utilizado como fallback.

Implementação: `src/services/pdfLetterhead.ts` — `loadPdfLetterheadData()` agora busca `profile.logoUrl` (nunca `imageUrl`/`avatarUrl`) e retorna `{ profile, logoDataUrl }`; `buildPdfLetterhead(profile, logoDataUrl, pageSize, pageMargins)` renderiza a imagem no cabeçalho somente se `logoDataUrl` for truthy — caso contrário, cabeçalho 100% textual, sem placeholder, sem espaço reservado. Os dois consumidores (`documentPdf.ts` para Documentos, `pdfMaker.ts` para Relatórios) foram auditados e atualizados para passar `logoDataUrl` em vez do antigo `avatarDataUrl` — nenhum outro consumidor de `loadPdfLetterheadData`/`buildPdfLetterhead` existe no projeto. Variáveis internas de cache renomeadas de `avatar*` para `image*` (eram genéricas — também usadas por fotos de checklist — nunca deveriam ter tido nome de avatar).

Validado no app real, 4 cenários (Documento com/sem logomarca, Relatório com/sem logomarca) — screenshots do PDF renderizado e contagem de objetos `/Subtype /Image` em `docs/design-system/`. Confirmado: zero caminho restante de avatar para `pdfLetterhead.ts` (nenhuma ocorrência de `avatarUrl`/`imageUrl` no arquivo, fora dos comentários explicativos da regra).

**27. Migração — Relatórios (15/09/2026)**

Ver relatório de execução com auditoria, screenshots, arquivos alterados e validação em `docs/design-system/reports-migration.md`.

**Achado de auditoria mais importante**: "Relatórios" não tem hoje nenhuma área de resultado em tela — as 3 chamadas (`Do Mês`/`Histórico Financeiro`/`Manutenções`) buscam os dados via API e disparam **diretamente o download de um PDF** (`pdfMaker.ts`), sem exibir nada na página além do formulário de filtros. Não existem tabelas, cards de indicador, valores financeiros, badges de status ou gráficos em tela. A "estrutura conceitual" de 3 momentos (Configuração/Resultado/Exportação) sugerida para esta etapa **não existe hoje** como Resultado visível — Configuração e Exportação estão fundidas em uma única ação. Não foi inventada nenhuma tela de resultado (mudaria comportamento/escopo, fora do que foi pedido); documentado como ideia de produto para decisão futura.

Decisões desta etapa:

1. `IonCard className="app-panel-card"` → `FrottoCard` (o card único da página, "Gerar Relatório"), mesmo padrão de todas as etapas anteriores.
2. Seletor de tipo de relatório (`Do Mês`/`Histórico Financeiro`/`Manutenções`, antes um `FormSelect`) virou um `IonSegment` (`.app-segment-shell`) em telas largas, deixando explícito "qual relatório estou gerando" — mesmo padrão de segmento já usado em Configurações/Meu Painel. **Achado corrigido durante a própria validação**: com as 3 opções reais (rótulo "Histórico Financeiro" é longo), o segmento estourava horizontalmente dentro do card em mobile — violaria a regra de "sem overflow horizontal" já vinculante desde a etapa de Configurações. Resolvido com o mesmo padrão dual já usado no rail de Configurações: abaixo de 720px, o segmento cede lugar a um `FormSelect` (mesmo estado, mesmo `onChange`), sem overflow em nenhuma largura testada.
3. Adicionado um pequeno subtítulo "Filtros" (`.app-section-subtitle`) separando visualmente o tipo de relatório dos campos de filtro propriamente ditos, dentro do mesmo card — sem criar cards adicionais.
4. Botões secundários do seletor de carro ("Selecionar carro específico" / "Ver todos os carros do grupo") ganharam a classe `.app-outline-btn` (já oficial, só não estava aplicada) para consistência visual com o resto do app.
5. **Nenhum valor financeiro, total, tabela, gráfico, badge de status ou exportação em XLS/XLSX existe nesta página** — confirmado por auditoria, nada foi inventado para essas seções da tarefa. O único formato de exportação real é PDF, já validado (mesma chamada a `buildPdfLetterhead(profile, logoDataUrl, ...)` da seção 26, inalterada).
6. Confirmado, reproduzido e **não corrigido**: `NullPointerException` pré-existente em `ReportsResource.getReports()` quando `Car.initialValue` ou `Income.cost` são nulos — dívida técnica de backend, fora do escopo desta migração visual.

**28. Migração — Subpáginas do carro, Lote A: Receitas, Despesas, Lembretes (15/09/2026)**

Ver relatório de execução com auditoria, screenshots, arquivos alterados e validação em `docs/design-system/car-subpages-lot-a-migration.md`.

Decisões desta etapa:

1. `IonCard className="app-panel-card"` → `FrottoCard` em todos os cards dos 3 modais (`IncomeAdd`, `CarExpenseAdd`, `reminderAdd`) — mesmo padrão de todas as etapas anteriores, comportamento idêntico (a classe `app-panel-card` já é emitida internamente pelo componente).
2. `div.app-empty-state` manual → `ItemNotFound` nas 3 listas, preservando o texto exato já existente (`title`/`description`), sem inventar nova cópia.
3. **Semântica financeira estendida das ações da Página do Carro (seção 16/18) para as próprias listas de Receitas/Despesas.** O valor monetário de cada item passa a usar `.app-text-financial-positive` (Receitas) / `.app-text-financial-negative` (Despesas) — classes globais já existentes em `legacy.css`, não criadas nesta etapa. O ícone de categoria de cada item deixa de usar `app-soft-icon--success`/`app-soft-icon--danger` (uso informal, em desacordo com a regra da seção 16 de que Success/Danger são reservados a estados de UI, não a categorias financeiras) e passa a usar uma variante local (`.income-list-item__icon`/`.car-expense-list-item__icon`, escopada ao CSS de cada página, mesmo mecanismo de tokens locais já usado em `Car.css` seção 16) alimentada pelos tokens `--frotto-financial-positive`/`-negative`. Lembretes mantém `app-soft-icon--warning` no ícone — não é uma violação: "atenção" é justamente a semântica oficial de Warning (seção 16, item 2), e lembrete é informação de atenção, não valor monetário.
4. **Achado corrigido durante a própria validação**: a cor financeira aplicada ao valor (item 3) não aparecia visualmente porque a regra local `.income-list-item__value`/`.car-expense-list-item__value` (CSS por página, carregado depois de `legacy.css` na cascata) redeclarava `color: var(--ion-text-color)` com a mesma especificidade, vencendo por ordem de carregamento. Corrigido removendo o `color` fixo dessas regras locais, deixando a cor final ser decidida inteiramente pela classe utilitária aplicada no JSX.
5. **Achado corrigido durante a própria validação**: os cards de Lembretes (`.reminder-list-item`) mostravam uma linha divisória interna que Receitas/Despesas não têm, porque a variante local do `IonItem` não zerava `--inner-border-width`/`--inner-box-shadow` (as outras duas já zeravam) nem tinha `lines="none"` no componente. Corrigido para igualar exatamente o padrão das outras duas listas — mudança puramente visual, sem efeito em clique/navegação.
6. **Toggle "Replicar esta despesa em todos os carros" (`CarExpenseAdd`) confirmado estrutural mas hoje inalcançável a partir desta rota**: como `Despesas` só é aberta via `/menu/carros/:id/despesas` (sempre com `carId` fixo na URL), e o toggle é desabilitado quando `carId` está presente (`disabled={!!carId}`), o branch `POST /car-expenses/car-all` nunca é exercitado a partir do fluxo real de subpágina do carro. Comportamento preservado exatamente como estava — não é um bug desta migração, é uma observação de produto para decisão futura (ver relatório, seção "ideias de produto").
7. Dois bugs funcionais pré-existentes em Lembretes (`reminderAdd.tsx`/`Reminders.tsx`) — item excluído permanece na lista até recarregar a página, e mensagem de confirmação de exclusão duplicada ("Gostaria de Excluir Tem certeza que deseja excluir este lembrete??") — foram **reproduzidos ao vivo com Playwright e confirmados intocados**, conforme instrução explícita de não corrigir de passagem.
8. Nenhum badge de status foi adicionado a Lembretes: `ReminderModel` não tem nenhum campo de estado persistido, então não havia nada a mapear para `FrottoBadge` sem inventar um campo novo (mudança de produto, fora de escopo).

**29. Migração — Subpáginas do carro, Lote B: Manutenções (15/09/2026)**

Ver relatório de execução com auditoria, screenshots, arquivos alterados e validação em `docs/design-system/car-subpages-lot-b-maintenance-migration.md`.

Decisões desta etapa:

1. `IonCard className="app-panel-card"[...]` → `FrottoCard` nos 2 arquivos com card (`Maintenances` list não tinha card; `MaintenanceAdd` com 6 cards; `ServiceAddModal` com 1 card) — mesmo padrão do Lote A.
2. `div.app-empty-state` → `ItemNotFound` em 3 pontos (lista principal, lista de serviços vazia dentro do modal, lista de lembretes vazia dentro do modal), preservando texto exato.
3. **Consolidação: semântica financeira também se aplica a valores "Total" agregados, não só a valores de linha simples.** O card "Resumo da manutenção" usava `app-soft-box--success` para o box "Total" — uso indevido de Success para representar uma despesa (mesma regra da seção 16/28). Corrigido para `app-soft-box--neutral` (como os demais boxes do resumo), com o **valor** dentro do box recebendo uma classe modificadora local (`.maintenance-add-summary__value--financial-negative`) — o box em si fica neutro, só o número fica com a cor financeira. Um segundo total (inline, no cabeçalho da lista de serviços) usava `IonText color="primary"` (azul) e foi corrigido pelo mesmo motivo. **Regra ratificada**: quando uma tela tiver um valor "Total" derivado/consolidado (não só uma linha de item), a cor financeira vai no texto do valor, não no fundo do container que o cerca — evita que um "Total" pareça um estado de sucesso ou uma cor decorativa sem sentido semântico.
4. **Achado corrigido durante a própria validação**: o mesmo bug de especificidade CSS do Lote A (item 4 da seção 28) se repetiu — `.maintenance-list-item__value` redeclarava `color: var(--ion-text-color)`. Desta vez identificado e corrigido no mesmo commit da migração (não precisou de uma segunda rodada de screenshots), removendo o `color` fixo antes mesmo da primeira captura de tela.
5. **Achado corrigido durante a própria validação (fora do escopo de cor)**: o card "Resumo da manutenção" mostrava a data crua (ex. `2026-09-10`) em vez de `formatDateView`, inconsistente com o resto do app. Corrigido — apresentação apenas, valor armazenado/enviado inalterado.
6. **Confirmado por leitura direta de código, não presumido**: "Manutenção Compartilhada" (tipo de documento em `Documents/DocumentsPage.tsx`) não tem nenhuma relação técnica com a entidade `Maintenance`/página de Manutenções — são conceitos homônimos, não integrados. Nenhuma integração foi criada.
7. **Confirmado**: a relação com Lembretes é real (componente `ReminderAdd` completo embutido em `MaintenanceAdd`, só na criação) mas não persiste nenhum vínculo de dado entre as duas entidades — é conveniência de UI. Preservada exatamente, `Reminders/*` não foi tocado.
8. **CREATE de Manutenções não fica em `Maintenances.tsx`** — o botão real está na Página do Carro (`Car.tsx`, card "Última manutenção"). Confirmado e usado para validação funcional end-to-end nesta etapa; `Car.tsx` não foi editado (fora do escopo do Lote B).
9. 2 usos de `(x as any)?.campo` acessando campos inexistentes no modelo (`service.description`, `reminder.date` dentro de `MaintenanceAdd.tsx`) documentados como dívida técnica (código morto/type-unsafe) — não corrigidos.

**30. Migração — Subpáginas do carro, Lote C: Inspeções e Avarias (15/09/2026)**

Ver relatório de execução com auditoria, screenshots, arquivos alterados e validação em `docs/design-system/car-subpages-lot-c-inspections-damages-migration.md`. Lote de maior risco funcional do projeto (formulário de ~20 campos, 2 modais aninhados, relação real entre entidades) — tratado com mapeamento cirúrgico do grafo de dados antes de qualquer edição.

Decisões desta etapa:

1. `IonCard className="app-panel-card"` → `FrottoCard` em todos os cards de Inspeções/Avarias (5 no modal de Inspeção, 1 no modal de Despesa, 2 na lista de Avarias, 2 no modal de Avaria) — mesmo padrão dos Lotes A/B.
2. `IonBadge color="success"/"warning"` → `FrottoBadge variant="success"/"warning"` para o status real `resolved` de Avaria — segundo caso confirmado (depois de Pendências, seção 20) de status real mapeado para `FrottoBadge`; reforça que badge só deve ser usado quando há campo de estado persistido de verdade.
3. **Consolidação estendida do Lote B (item 3, seção 29)**: em uma entidade com múltiplos valores monetários (um consolidado, vários de itemização), só o valor **definitivo/consolidado** de cada entidade recebe `financial-negative` — o custo total de uma Inspeção (derivado da soma de despesas) e o custo de cada Avaria (valor próprio da entidade) recebem a cor; as linhas de itemização dentro do card "Despesas" de uma Inspeção (cada despesa individual, antes de somada) permanecem neutras, mesmo critério já usado para os itens de serviço de Manutenção.
4. **Achado funcional crítico, mapeado e explicitamente preservado (não é uma decisão de design, é uma constatação de arquitetura que qualquer migração futura nesta área precisa respeitar)**: o modal `BodyDamageAdd`, quando aberto de dentro do fluxo de criação de uma Inspeção, **persiste a avaria imediatamente no backend (multipart) assim que "Salvar" é clicado**, independente de a Inspeção em si já ter sido salva. Não existe modo "rascunho local" para Avaria dentro de Inspeção — o array `carBodyDamages[]` do formulário da Inspeção só guarda referências a objetos já persistidos. Confirmado ao vivo: uma avaria criada durante a criação de uma Inspeção apareceu imediatamente na página standalone de Avarias. Nenhuma migração visual futura desta área deve alterar esse mecanismo sem decisão de produto explícita.
5. **Confirmado por leitura direta de código**: `InspectionExpenseModel` (Despesas dentro de Inspeção) não é a mesma entidade que `CarExpense` (Lote A) — nomes parecidos, zero relação técnica. `ExpenseAddModal` de Inspeção nunca chama a API, é editor local de um array aninhado enviado dentro do próprio payload da Inspeção.
6. O modal de Avaria (`BodyDamageAdd.tsx`), identificado pela auditoria anterior como "o menos alinhado dos 6", foi totalmente realinhado ao padrão visual (toolbar, botões, `FrottoCard`, `app-form-grid`) — primeiro arquivo desta família a não ter CSS dedicado antes da migração; criado `BodyDamageAddModal/BodyDamageAdd.css` com as classes estruturais mínimas necessárias. Upload/foto (`usePhotoGallery`, `FormData`, `IonPhotoViewer`) intocado — só reorganização visual ao redor.
7. 2 botões de câmera icon-only (antes sem `aria-label`) corrigidos — mesmo padrão de acessibilidade já aplicado a botões icon-only nos Lotes A/B.
8. Campos `comment`/`score` de `InspectionModel` confirmados como nunca expostos na UI (`comment`) ou sempre hardcoded (`score` = 10) — documentados como dívida técnica pré-existente, não como bug, e não alterados.

**31. Migração — Meu Plano + Administração de Billing (17/09/2026)**

Ver relatório de execução completo em `docs/design-system/billing-plan-admin-migration.md`. Escopo liberado depois de o trabalho concorrente de recurring billing/Mercado Pago (5G, seção 17 do handoff) ter sido concluído — confirmado contra o código atual, não contra a auditoria antiga (`NEXT_ROUND_AUDIT.md` seções 8/9 ficam como referência histórica).

Decisões desta etapa (padrões consolidados, aplicáveis a qualquer tela futura do domínio Billing/assinatura):

1. `IonCard` cru → `FrottoCard` em Meu Plano (2 pontos) e Administração (5 cards) — mesmo padrão de todas as etapas anteriores, visualmente idêntico (a superfície já vinha da regra global `ion-card[class]`).
2. `div.app-empty-state` manual → `ItemNotFound` (busca sem resultado e histórico vazio em Administração; estado de erro/retry de carregamento em Meu Plano, usando `title`+`description`+`actionLabel`/`onAction`, o mesmo padrão já oficial para "erro de carregamento com ação de retry").
3. **Semântica de status real do domínio Billing, oficializada**: `SubscriptionStatus` (`ACTIVE`/`PAST_DUE`/`PAUSED`/`CANCELED`/`EXPIRED`, mais `null`=FREE) → `ACTIVE`→success, `PAST_DUE`→warning, `PAUSED`→warning, `CANCELED`→neutral, `EXPIRED`→neutral. Mesma regra da seção 9 do handoff (Danger nunca para cancelamento/estado terminal normal). Implementada como função pura testada (`statusVariant`) em Meu Plano e em Administração — não duplicada por acidente, cada página tem sua própria porque os tipos `SubscriptionStatus` de `BillingModels.ts`/`AdminBillingModels.ts` são declarações TypeScript independentes (mesmos valores, arquivos diferentes).
4. **`FrottoModal` confirmado como padrão também para confirmação simples (não só formulário de criação/edição)**: o modal "Cancelar assinatura" (Meu Plano) e "Conceder plano" (Administração) tinham cabeçalho já estruturalmente Cancelar/Título/Ação-primária — convertidos sem tocar no `IonModal` externo (`canDismiss`/`backdropDismiss`/`onDidDismiss` preservados). Terceiro e quarto consumidores reais depois de `CarAdd`/`DriverAdd`/`DriverPendencyAdd`/`DriverPendencyPaymentModal`.
5. **Exceção documentada, no espírito da seção 10 (exceções)**: o modal "Resumo do plano" (checkout, Meu Plano) foi deliberadamente preservado como `IonModal` cru — cabeçalho com X somente-ícone (não o contrato exato Cancelar-textual/Título/Ação-primária) e é o portal direto para o checkout do Mercado Pago. Convertê-lo exigiria uma decisão de design nova (trocar X por "Cancelar"/"Fechar" textual), o que a tarefa pediu para evitar em modais ligados ao fluxo Mercado Pago. Não convertido nesta rodada.
6. **`section-shell <página>-shell` → `app-shell app-shell--compact <página>-shell` confirmado seguro**: `.app-shell`/`.section-shell` têm regras base quase idênticas (mesmo `max-width` 1080px, `flex column`, `gap`); a classe específica da página (`my-plan-shell`/`admin-billing-shell`, seletor por ID) já sobrescrevia `max-width` de qualquer forma. Unificação de nomenclatura sem qualquer efeito visual — mais uma página migrada para o vocabulário padrão, restando cada vez menos usos de `section-shell` fora das subpáginas do carro.
7. **Bug funcional real corrigido (tipo/label, não regra de negócio)**: `AdminBillingModels.SubscriptionStatus` (frontend) não incluía `PAUSED`, apesar de o backend poder retorná-lo — causava `STATUS_LABELS[status]` undefined (texto de status em branco). Corrigido de forma aditiva; `REVOCABLE_STATUSES`/regras de elegibilidade não tocadas.
8. **Achado de arquitetura documentado, não corrigido**: `Subscription.status` persistido nunca é gravado como `PAST_DUE`/`EXPIRED` em nenhum caminho de backend encontrado — esses dois estados só existem hoje como `CommercialState` derivado (`SubscriptionFinancialCoverageService`), nunca persistidos de volta. A UI que lê o campo persistido pode mostrar "Ativa" durante um grace period tecnicamente `PAST_DUE`. Fora do escopo de uma migração visual; registrado como ideia de produto/backend.
