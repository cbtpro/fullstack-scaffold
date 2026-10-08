# fullstack-scaffold

拷贝即用的全栈项目骨架。前端 pnpm Monorepo + 后端 Maven 多模块 + Docker 一键部署。

## 技术栈

- **前端**：Node ≥18 + pnpm ≥9 + Vite 5 + React 18 / Vue 3（双实现，任选其一）+ antd 5 / ant-design-vue 4 + Turborepo
- **后端**：Java 17 + Spring Boot 3.2.5 + Spring Cloud 2023.0.3 + Spring Cloud Alibaba 2023.0.1.0 + MyBatis-Plus 3.5.7
- **鉴权**：JWT（jjwt 0.12.6）与 Sa-Token 1.39.0 双实现，配置切换
- **中间件**：MySQL 8.0 + Redis 7 + Nacos v2.3.2

## 目录结构

```
fullstack-scaffold/
├── frontend/          # 前端 pnpm Monorepo（apps + packages）
├── backend/           # 后端 Maven 多模块（common / auth-starter / gateway / user-service）
├── docker/            # 部署编排（docker-compose.dev.yml 仅中间件，docker-compose.yml 全栈）
├── .gitignore
├── LICENSE
└── README.md
```

## 端口分配

| 服务 | 端口 |
|---|---|
| gateway-service | 8080 |
| user-service | 8001 |
| web-react | 5173 |
| web-vue | 5174 |
| backend-mock | 5180 |
| Nacos | 8848 + 9848(gRPC) |
| MySQL | 3306 |
| Redis | 6379 |

## 快速开始

### 1. 起本地中间件（MySQL + Redis + Nacos）

```bash
docker compose -f docker/docker-compose.dev.yml up -d
```

- Nacos 控制台：http://localhost:8848/nacos （账号/密码：nacos/nacos）
- MySQL：localhost:3306，root/root123，库 `fs_user`
- Redis：localhost:6379

### 2. 后端启动

```bash
cd backend
mvn clean install -DskipTests
cd user-service && mvn spring-boot:run      # 8001
cd gateway-service && mvn spring-boot:run   # 8080
```

### 3. 前端启动

```bash
cd frontend
pnpm install
pnpm dev   # 并行起 web-react(5173) / web-vue(5174) / backend-mock(5180)
```

### 4. 全栈一键（Docker）

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

## 鉴权切换

在 `backend/user-service/src/main/resources/application.yml` 改一行即可在 JWT 与 Sa-Token 间切换：

```yaml
auth:
  type: JWT          # 或 SA_TOKEN
```

- `JWT`：Spring Security + jjwt，`Authorization: Bearer <token>` 头
- `SA_TOKEN`：Sa-Token，默认 token 名 `Authorization`，可通过 `auth.sa-token.token-name` 调整

## 详细文档

各模块详细说明见对应目录下的 README（按需补充）。
