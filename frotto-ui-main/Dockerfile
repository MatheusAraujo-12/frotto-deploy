# ========================
# STAGE 1: frontend build
# ========================
FROM node:20-alpine AS build
WORKDIR /app

ENV CI=false
# Cap Node memory to avoid OOM when backend/frontend build in parallel.
ENV NODE_OPTIONS=--max-old-space-size=512

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

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
