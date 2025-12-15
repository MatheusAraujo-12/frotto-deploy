# ========================
# STAGE 1: build do frontend
# ========================
FROM node:20-alpine AS build
WORKDIR /app

# Copia package.json / package-lock.json ou yarn.lock
COPY package.json package-lock.json ./

# Instala as dependências
# Se usar npm:
RUN npm ci

# Copia o restante do código
COPY . .

# Gera o build de produção
RUN npm run build

# ========================
# STAGE 2: nginx servindo estáticos
# ========================
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 80
