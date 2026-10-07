FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_KEYCLOAK_URL=https://splitit-auth.damiankaplon.site
ARG VITE_KEYCLOAK_REALM=split_it
ARG VITE_KEYCLOAK_CLIENT_ID=splitit-web
ARG VITE_API_BASE_URL=/api
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
