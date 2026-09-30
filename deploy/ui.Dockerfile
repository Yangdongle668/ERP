# syntax=docker/dockerfile:1
# 前端镜像：Node 构建 erp-ui → Nginx 提供静态页面并把 /api 反向代理到 erp-server
FROM node:22-alpine AS build
# 可选：npm 镜像源（如 https://registry.npmmirror.com）
ARG NPM_REGISTRY=""
WORKDIR /src
COPY deploy/certs/ /tmp/certs/
COPY erp-ui/package.json erp-ui/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    set -e; cat /tmp/certs/*.crt > /tmp/extra-ca.pem 2>/dev/null || true; \
    if [ -s /tmp/extra-ca.pem ]; then export NODE_EXTRA_CA_CERTS=/tmp/extra-ca.pem; fi; \
    if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi; \
    npm ci --no-audit --no-fund
COPY erp-ui/ ./
RUN npm run build

FROM nginx:1.27-alpine
ENV TZ=Asia/Shanghai
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
