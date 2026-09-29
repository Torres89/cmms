# Installable mobile web app (PWA). Built once; API_URL and AGENT_URL are
# injected at container start (docker-entrypoint.sh), so the same image serves
# any deployment.
FROM node:20-bookworm-slim AS build

WORKDIR /usr/src/app
COPY package*.json .npmrc ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx expo export -p web

FROM nginx:1.27.0-alpine
COPY --from=build /usr/src/app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.sh /docker-entrypoint.d/40-atlas-env.sh
RUN chmod +x /docker-entrypoint.d/40-atlas-env.sh

EXPOSE 3000
