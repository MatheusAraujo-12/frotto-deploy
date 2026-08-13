# ========================
# STAGE 1: frontend build
# ========================
FROM node:20.19-alpine AS build
WORKDIR /app

ENV CI=false
ENV NODE_OPTIONS=--max-old-space-size=2048
ENV GENERATE_SOURCEMAP=false
ENV NPM_CONFIG_AUDIT=false
ENV NPM_CONFIG_FUND=false
ENV NPM_CONFIG_LEGACY_PEER_DEPS=true
ENV NPM_CONFIG_UPDATE_NOTIFIER=false

ARG REACT_APP_API_URL
ENV REACT_APP_API_URL=${REACT_APP_API_URL}

COPY package.json package-lock.json ./
RUN node -v && npm -v
RUN npm install

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
