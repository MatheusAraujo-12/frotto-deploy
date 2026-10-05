# Armazenamento de arquivos na VPS (staging) — etapas 0–3

Infraestrutura comum para substituir o legado AWS S3. **Avatar e logomarca** usam o storage desde a etapa 3;
avarias, documentos e checklist continuam no fluxo legado.

## Avatar e logomarca (etapa 3)

- `FileStorageGateway` escolhe pelo modo: `s3` → `LegacyS3FileStorageService` (o `AWSS3FileService` de sempre,
  mesmas keys `{millis}_USER_{id}` / `{millis}_LOGO_{id}`); `local` → volume, sem nenhum caminho de escrita ou
  exclusão no S3 (`deleteFromLegacyS3` lança exceção em `local`).
- `GET/PATCH/DELETE /api/me/*` devolvem, além de `imageUrl`/`logoUrl` (keys, contrato inalterado):
  `avatarUrl` / `logoAccessUrl` = URL a carregar. Modo local: `""` quando não há imagem (placeholder).
  Modo s3: campo omitido quando o valor não é resolvível — o frontend mantém a lógica antiga.
- `image_url` só muda pelo upload/remoção: `/api/register`, `/api/account`, `PATCH /api/me` e o admin de usuários
  ignoram `imageUrl` enviado pelo cliente.
- Estratégia transacional (modo local), via `StorageTransactionSupport`:
  1. o arquivo novo é gravado **antes** de alterar o usuário (falha de storage → 400/503, nada muda);
  2. rollback confirmado → o arquivo novo é apagado (sem órfão);
  3. commit → só então o arquivo anterior é apagado (substituição/remoção);
  4. resultado de commit desconhecido → nada é apagado (pode sobrar órfão, nunca referência quebrada);
  5. keys históricas nunca são apagadas (nem no S3, nem a cópia em `legacy/`).
  No modo s3 o comportamento legado é mantido: substituição não apaga o anterior; remoção apaga no S3 na hora.

## Fotos de avarias (etapa 4)

- `CarBodyDamageResource` usa o mesmo gateway, categoria `car-damages/` (JPEG/PNG/WEBP até 10 MB, multipart ou
  Base64 do app mobile). `image_path`/`image_path_2` continuam guardando só a key; nada muda no banco.
- Respostas de `/api/car-body-damages/**` trazem `imageUrl`/`imageUrl2` (somente leitura, mesmo contrato de
  `avatarUrl`: URL assinada, bucket legado, `""` = sem foto, omitido no modo s3 quando irresolúvel).
- Inspeções (`/api/inspections/**`) não mudaram: as avarias aninhadas trazem só as keys e o modal de avaria busca
  `GET /api/car-body-damages/{id}` para obter as URLs.
- Modo local: mesma estratégia transacional (novo antes do banco; rollback apaga só o novo; commit apaga o anterior;
  exclusão da avaria apaga os arquivos só depois do commit; keys históricas nunca são apagadas; zero chamadas ao S3).
- Modo s3: chamadas e ordem idênticas ao legado (substituição apaga o objeto anterior antes do upload; exclusão
  apaga no S3 na hora).

## Exclusão S3 com key vazia (correção pós-etapa 4)

O SDK AWS v1 só rejeita key nula: `deleteObject(bucket, "")` vira `DELETE` na raiz do bucket (requisição de
DeleteBucket). `AWSS3FileService.deleteFile` e `LegacyS3FileStorageService.delete` ignoram key nula, vazia ou só com
espaços, para qualquer fluxo. Keys válidas continuam sendo apagadas normalmente.

## Documentos e anexos (etapa 5)

- Arquivos persistidos: somente os anexos de `driver_document` (`POST /api/documents/{id}/attachments`, inclusive as
  fotos do checklist). Categoria `documents/`: JPEG/PNG/WEBP/PDF até 10 MB, tipo pelo conteúdo. `attachments_json`
  mantém o formato (array JSON de keys); as fotos do checklist continuam referenciadas também em `payload_json`.
- PDFs dos documentos e relatórios são gerados no navegador e nunca armazenados; `pdf_url` não é arquivo.
- `attachments` e `pdfUrl` enviados pelo cliente (criação, PATCH, generate-pdf) são ignorados.
- `GET /api/documents/{id}` traz `attachmentUrls` (referência → URL; `""` = indisponível; ausente = resolução legada,
  só no modo s3), cobrindo anexos e referências de fotos do checklist do payload que pertençam ao documento.
- Exclusão do documento: modo local apaga os anexos só depois do commit (históricos nunca); modo s3 apaga somente
  objetos do próprio documento (`{millis}_DOC_{id}...`), nunca `pdf_url` nem referências de outros registros.
- `driver.document_driver_license` / `document_driver_register` são dados textuais (CNH/registro), não arquivos.

## Dívida técnica

- Modo s3, avarias: a substituição apaga o objeto anterior **antes** do upload; se o upload falhar, a foto anterior é
  perdida. Mantido por compatibilidade com produção; corrigir na remoção do legado.
- Modo s3, avatar/logomarca: validação antiga (aceita HEIC, sem limite de 5 MB).
- Editor de documentos exibe a key como nome da foto do checklist (comportamento anterior).

## Modos

| `FROTTO_STORAGE_MODE` | Comportamento |
|---|---|
| ausente ou `s3` (padrão) | Comportamento legado. `GET /files/**` responde 404. Produção continua assim sem nenhuma mudança de configuração. |
| `local` | Arquivos novos no volume persistente, servidos por URLs assinadas. Habilitado **somente** por configuração explícita (staging). |

Valor inválido (ex.: `locl`) impede a inicialização — nunca escolhe um storage silenciosamente.

## Variáveis (somente backend, definidas no Coolify de cada ambiente)

O recurso é Docker Compose: o `docker-compose.yml` é a fonte de verdade.
As variáveis abaixo estão declaradas no `environment:` do serviço `backend` (sem isso o Coolify não as repassa ao
container) com padrões seguros, iguais ao comportamento atual de produção.

| Variável | Padrão no compose (produção sem configuração) | Valor no Coolify do staging |
|---|---|---|
| `FROTTO_STORAGE_MODE` | `s3` | `local` |
| `FROTTO_STORAGE_ROOT` | vazio | `/app/storage` |
| `FROTTO_FILES_BASE_URL` | vazio | `https://api-staging.frotto.com.br` |
| `FROTTO_FILES_SIGNING_SECRET` | vazio | segredo gerado na VPS (`openssl rand -hex 32`); nunca no frontend, chat ou log |
| `FROTTO_FILES_URL_TTL_SECONDS` | `21600` | `21600` (faixa aceita 300–604800) |
| `FROTTO_LEGACY_S3_READ_FALLBACK_ENABLED` | `false` | `true` (temporário; só leitura do bucket público) |

`FROTTO_LEGACY_S3_PUBLIC_BASE_URL` não está no compose: o padrão do perfil prod
(`https://localuz-locamais.s3.us-east-1.amazonaws.com`) é usado. Variáveis no Coolify: runtime (não "Build Variable");
o segredo marcado como secret.

Não configurar credenciais AWS no staging: o perfil `prod` aponta o código legado para o bucket de produção.

## Volume persistente (mount explícito, específico da branch de staging)

Na branch `staging/security-prebilling` o `docker-compose.yml` declara o mount explicitamente, só no serviço
`backend`:

```yaml
volumes:
  - /data/frotto/staging/files:/app/storage
```

- `FROTTO_STORAGE_ROOT=/app/storage` aponta para o destino do mount.
- `FROTTO_STORAGE_HOST_PATH` **não é mais utilizado**: o Coolify materializa o compose com o padrão de uma
  interpolação `${VAR:-padrão}` no caminho de origem do volume (ignorando a variável do ambiente), e este recurso
  não oferece Persistent Storage configurável. Por isso o caminho é literal.
- Produção continua em `FROTTO_STORAGE_MODE=s3` e não depende de volume local.
- **Antes de qualquer merge desta branch para `main`**, o compose deve ser revisado e o mount trocado pelo diretório
  próprio de produção (a ser preparado, com sentinela própria). **Nunca montar o diretório de staging na produção.**
- Com o diretório errado montado (sem a sentinela), o modo local recusa gravar (ver abaixo).
- Preparação única no host do staging (já feita: `root:root`, diretório `0750`, sentinela `0640`; o backend roda como
  UID/GID `0:0`):
  ```sh
  install -d -m 0750 -o 0 -g 0 /data/frotto/staging/files
  install -m 0640 -o 0 -g 0 /dev/null /data/frotto/staging/files/.frotto-storage   # sentinela
  ```
  A troca para usuário não-root (e o dono acima) fica para a etapa do Dockerfile.

### Sentinela `.frotto-storage`

Obrigatória. O backend só grava se o diretório raiz existir, contiver `.frotto-storage` e for gravável. Sem o
mount (ou com o diretório errado montado) o diretório do container não tem a sentinela, então nada é
gravado no filesystem efêmero:

- a aplicação **sobe normalmente** (Billing e demais funções não são afetados) e registra `ERROR` na inicialização
  (`ROOT_MISSING`, `SENTINEL_MISSING`, `NOT_WRITABLE` ou `NOT_CONFIGURED`);
- uploads respondem **503** (`error.upload.storageunavailable`), sem criar diretórios;
- leituras locais retornam "não encontrado" (o resolver usa o fallback S3 para chaves históricas, se habilitado).

## Estrutura

```
/app/storage/                             (no container; no host do staging: /data/frotto/staging/files)
  .frotto-storage
  .tmp/                                   escrita atômica (fsync + rename, sem sobrescrever)
  legacy/{chave histórica exata}          cópias futuras do S3, mesmas chaves do banco
  avatars/{yyyy}/{mm}/{uuid}.{ext}
  logos/{yyyy}/{mm}/{uuid}.{ext}
  car-damages/{yyyy}/{mm}/{uuid}.{ext}
  documents/{yyyy}/{mm}/{uuid}.{ext}
```

O banco continua guardando apenas a chave. Chaves históricas (`1672926360659_Car_11.png`, `1771248962905_USER_4.png`,
`1774293055076_DOC_20.pdf`) não mudam: nenhuma alteração de banco é necessária.

## URLs assinadas

`{FROTTO_FILES_BASE_URL}/files/{key}?exp={epoch}&sig={HMAC-SHA256 base64url}`

- Autorização acontece quando o backend emite a URL (o chamador já validou o dono do registro); `/files/**` não usa JWT.
- `exp` é alinhado a janelas do TTL (mesma URL por um tempo, cache do navegador funciona); validade entre 1 e 2 TTLs.
- Assinatura verificada antes de tocar o disco: assinatura ruim → 403 (não revela existência); chave inválida → 404.
- Apenas `GET`/`HEAD` são permitidos em `/files/**`; demais métodos são negados.
- Respostas: `X-Content-Type-Options: nosniff`, `Content-Security-Policy: default-src 'none'; sandbox` (imagens e
  conteúdo desconhecido), `Cache-Control: private, max-age=<até expirar>`, `Referrer-Policy: no-referrer`;
  conteúdo não reconhecido é servido como `application/octet-stream` + `attachment`.

## Validação de uploads (usada a partir da etapa 3)

Tipo detectado pelo conteúdo (magic bytes), nunca por extensão/Content-Type do cliente; extensão derivada do tipo real.

| Categoria | Tipos | Máximo |
|---|---|---|
| avatar, logomarca | JPEG, PNG, WEBP | 5 MB |
| fotos de avaria | JPEG, PNG, WEBP | 10 MB |
| anexos de documento | JPEG, PNG, WEBP, PDF | 10 MB |

## Domínio

Homologação: `/files/**` servido pelo próprio backend em `https://api-staging.frotto.com.br` (sem DNS novo). Pré-condição:
esse domínio aponta direto para o serviço `backend` (o nginx do frontend só repassa `/api/`). Um domínio dedicado
(`arquivos-staging.frotto.com.br`) continua possível no futuro trocando apenas `FROTTO_FILES_BASE_URL`.
