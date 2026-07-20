FROM node:20-alpine AS build
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# nginx 阶段：注入 LP_API_ORIGIN 到 nginx.conf 的 CSP connect-src
FROM nginx:alpine
ARG LP_API_ORIGIN=http://localhost:8080
ENV LP_API_ORIGIN=${LP_API_ORIGIN}
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80