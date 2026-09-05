FROM node@sha256:76789712cd1ae89a1225eac9077010d68987a423588042dac30446f502f1858c AS build

WORKDIR /workspace/web

COPY web/package*.json ./
RUN npm ci

COPY web/ ./
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

FROM nginx@sha256:62223d644fa234c3a1cc785ee14242ec47a77364226f1c811d2f669f96dc2ac8 AS runtime

RUN apk add --no-cache curl

COPY deploy/nginx/nginx.conf /etc/nginx/nginx.conf
COPY deploy/nginx/conf.d/ai-resume.conf /etc/nginx/conf.d/default.conf
COPY deploy/nginx/conf.d/ai-resume.prod.conf.template /etc/nginx/production/ai-resume.prod.conf.template
COPY deploy/nginx/entrypoint/40-configure-production.sh /docker-entrypoint.d/40-configure-production.sh
RUN chmod +x /docker-entrypoint.d/40-configure-production.sh
COPY --from=build /workspace/web/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=5 \
    CMD curl -fsS http://localhost/healthz >/dev/null || exit 1
