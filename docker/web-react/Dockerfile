# 多阶段构建 web-react：node 编译 + nginx 静态服务
# build context 为仓库根，COPY frontend/ 才能拿到 pnpm workspace 全部包

FROM node:20-alpine AS builder
RUN corepack enable && corepack prepare pnpm@10.33.0 --activate
WORKDIR /build
COPY frontend/ ./
RUN pnpm install --frozen-lockfile
RUN pnpm --filter web-react build

FROM nginx:alpine
COPY --from=builder /build/apps/web-react/dist /usr/share/nginx/html
COPY docker/web-react/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
