# ========================
# STAGE 1: build do frontend
# ========================
FROM node:20-alpine AS build
WORKDIR /app

ENV CI=false

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ========================
# STAGE 2: nginx
# ========================
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]