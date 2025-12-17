# ========================
# STAGE 1: build do frontend
# ========================
FROM node:20-alpine AS build
WORKDIR /app

# (Opcional) evita que CRA falhe por warnings em ambiente CI/CD
ENV CI=false

# Copia package.json / package-lock.json
COPY package.json package-lock.json ./

# Instala as dependências
RUN npm ci

# Copia o restante do código
COPY . .

# Diagnóstico (aparece no log do Coolify)
RUN node -v && npm -v
RUN echo "==== Listing src/pages (if exists) ====" \
  && ls -la src || true \
  && ls -la src/pages || true

# Gera o build de produção (com log mais explícito)
RUN npm run build -- --verbose

# ========================
# STAGE 2: nginx servindo estáticos
# ========================
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 80
