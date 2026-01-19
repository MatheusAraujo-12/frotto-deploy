# ====== BUILD STAGE (Node) ======
FROM node:18-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ====== RUNTIME STAGE (Nginx) ======
FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
# Se seu build gerar /dist, troque /app/build por /app/dist

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]