# ========================
# STAGE 1: build do frontend
# ========================
FROM node:20-alpine AS build
WORKDIR /app

# Copia package.json / package-lock.json ou yarn.lock
COPY package*.json ./
# Se usar yarn:
# COPY package.json yarn.lock ./

RUN npm install
# ou: RUN yarn install

# Copia o restante do código
COPY . .

# Gera o build de produção
RUN npm run build

# ========================
# STAGE 2: nginx servindo estáticos
# ========================
FROM nginx:alpine
# Remove config padrão, se quiser
RUN rm /etc/nginx/conf.d/default.conf || true

# Copia o build para a pasta pública do nginx
# Se o seu bundler gera "dist" em vez de "build", troque aqui:
COPY --from=build /app/build /usr/share/nginx/html

# Config nginx básico
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
