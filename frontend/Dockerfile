FROM node:20-alpine AS build
WORKDIR /app
# .npmrc 必须与 package.json 一起 COPY，否则 pnpm install 层会 cache 命中
# 旧的非 hoist 模式，触发 vue-tsc 2.x bin 路径解析失败
COPY .npmrc package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# nginx 阶段：注入 LP_API_ORIGIN 到 nginx.conf 的 CSP connect-src
FROM nginx:alpine
ARG LP_API_ORIGIN=http://localhost:8080
ENV LP_API_ORIGIN=${LP_API_ORIGIN}
# nginx 官方镜像的 20-envsubst-on-templates.sh 默认扫 /etc/nginx/templates/*.template，
# 必须放这里才会替换 ${LP_API_ORIGIN} → 写到 /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80