# fullstack-scaffold

拷贝即用的全栈项目骨架。开发者 clone 之后只需关注业务开发，不再重复搭脚手架。

前端 pnpm Monorepo（apps + packages）+ 后端 Maven 多模块（common / auth-starter / gateway / user-service）+ Docker 一键部署，三者并列放在仓库根，彼此独立但通过明确契约协作。

## 架构

```
                          ┌──────────────────────────────────────┐
                          │            浏览器 / 客户端            │
                          └──────────────┬───────────────────────┘
                                         │ HTTP /api/**
                          ┌──────────────▼───────────────────────┐
                          │   web-react(5173) / web-vue(5174)     │
                          │   dev: Vite proxy → gateway:8080      │
                          │   prod: nginx → gateway:8080          │
                          │   可切 backend-mock:5180 做纯前端联调    │
                          └──────────────┬───────────────────────┘
                                         │
                          ┌──────────────▼───────────────────────┐
                          │   gateway-service (8080, WebFlux)     │
                          │   AuthGlobalFilter 解析 token         │
                          │   路由 /api/auth/** /api/user/**      │
                          │        /doc.html /v3/api-docs →        │
                          └──────────────┬───────────────────────┘
                                         │ lb://user-service (Nacos 发现)
                          ┌──────────────▼───────────────────────┐
                          │   user-service (8001, Servlet)       │
                          │   auth-starter: auth.type=JWT|SA_TOKEN│
                          │   LoginController / UserController    │
                          │   RBAC: @RequirePermission 切面        │
                          │   knife4j: /doc.html API 文档           │
                          └──┬────────────┬────────────┬─────────┘
                             │            │            │
                       ┌─────▼─────┐ ┌─────▼─────┐ ┌────▼─────┐
                       │  MySQL 8  │ │  Redis 7  │ │ Nacos2.3 │
                       │  fs_user  │ │  缓存/会话 │ │ 注册发现  │
                       └───────────┘ └───────────┘ └──────────┘
```

## 技术栈

- **前端**：Node ≥18 + pnpm ≥9 + Vite 5 + React 18 / Vue 3（双实现，任选其一）+ antd 5 / ant-design-vue 4 + Turborepo
- **后端**：Java 17 + Spring Boot 3.2.5 + Spring Cloud 2023.0.3 + Spring Cloud Alibaba 2023.0.1.0 + MyBatis-Plus 3.5.7
- **鉴权**：JWT（jjwt 0.12.6）与 Sa-Token 1.39.0 双实现，`auth.type` 一行切换
- **RBAC**：数据库驱动角色/权限，`@RequirePermission` 注解 + AOP 切面，前端动态菜单 + 按钮指令
- **API 文档**：knife4j 4.4.0 + springdoc 2.3.0，网关路由聚合，`/doc.html` 统一入口
- **中间件**：MySQL 8.0 + Redis 7 + Nacos v2.3.2

## 目录结构

```
fullstack-scaffold/
├── frontend/                      # 前端 pnpm Monorepo
│   ├── apps/
│   │   ├── backend-mock/          # Nitro/h3 mock 后端（纯前端联调用）
│   │   ├── web-react/             # React 18 + antd5 + router v6 + zustand
│   │   └── web-vue/               # Vue 3 + ant-design-vue4 + router v4 + pinia
│   ├── packages/                  # @fs/* 共享包（auth/locales/request/types/utils/tsconfig）
│   ├── package.json               # workspace 根 + turbo
│   ├── pnpm-workspace.yaml
│   ├── turbo.json
│   └── .npmrc
├── backend/                       # 后端 Maven 多模块
│   ├── common/                    # 统一响应/异常/分页/常量
│   ├── auth-starter/              # 双鉴权可切换 Starter（JWT / SA_TOKEN）
│   ├── gateway-service/           # Spring Cloud Gateway + 全局鉴权过滤器
│   ├── user-service/              # 示例业务服务（用户 CRUD + 登录）
│   └── pom.xml                    # 父 pom
├── docker/                        # 部署编排
│   ├── mysql/{conf/my.cnf, init/init.sql}
│   ├── nacos/conf/application.properties
│   ├── redis/redis.conf
│   ├── gateway-service/{Dockerfile, .dockerignore}
│   ├── user-service/{Dockerfile, .dockerignore}
│   ├── web-react/{Dockerfile, nginx.conf}
│   ├── web-vue/{Dockerfile, nginx.conf}
│   ├── maven-settings.xml         # 阿里云镜像，注入到 builder 镜像加速依赖下载
│   ├── docker-compose.dev.yml     # 仅中间件（本地开发）
│   └── docker-compose.yml         # 中间件 + 全部微服务 + 前端（全栈）
├── .gitignore
├── LICENSE                        # MIT
└── README.md
```

## 端口分配

| 服务 | 端口 | 说明 |
|---|---|---|
| gateway-service | 8080 | 唯一对外入口，路由转发下游 |
| user-service | 8001 | 业务服务，注册到 Nacos |
| web-react | 5173 | React（dev: Vite 5173 / prod: nginx 5173→80） |
| web-vue | 5174 | Vue（dev: Vite 5174 / prod: nginx 5174→80） |
| API 文档 | /doc.html | knife4j UI，经网关路由到 user-service |
| backend-mock | 5180 | Nitro mock，纯前端联调用 |
| Nacos | 8848 + 9848 + 9849 | 8848 控制台，9848/9849 gRPC |
| MySQL | 3306 | 库 `fs_user` |
| Redis | 6379 | — |

## 快速开始

### 路线 A：全栈一键（Docker）

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

启动后：
- gateway：http://localhost:8080
- API 文档（knife4j）：http://localhost:8080/doc.html
- Nacos 控制台：http://localhost:8848/nacos （nacos/nacos）
- web-react：http://localhost:5173（nginx 静态服务，/api 代理到 gateway）
- web-vue：http://localhost:5174（同上）
- 用 admin/123456 登录（admin 拥全部权限，guest/123456 仅 user:view）

### 路线 B：本地开发（中间件 + 源码）

1. 起中间件：

   ```bash
   docker compose -f docker/docker-compose.dev.yml up -d
   ```

2. 后端：

   ```bash
   cd backend
   mvn clean install -DskipTests
   mvn -pl user-service -am spring-boot:run      # 8001
   mvn -pl gateway-service -am spring-boot:run   # 8080（另开终端）
   ```

3. 前端：

   ```bash
   cd frontend
   pnpm install
   pnpm dev    # 并行起 web-react(5173) / web-vue(5174) / backend-mock(5180)
   ```

   `vite.config.ts` 的 `/api` 默认代理到 gateway(8080)；纯前端联调时把 target 改为 `http://localhost:5180` 即可走 backend-mock，无需启动后端。

4. 浏览器访问 http://localhost:5173，用 admin/123456 登录 → 进入 BasicLayout → 看到用户列表。

## 鉴权切换

`auth-starter` 同时支持 JWT 与 Sa-Token，在 `backend/user-service/src/main/resources/application.yml` 改一行即可：

```yaml
auth:
  type: JWT          # 或 SA_TOKEN
```

- `JWT`：Spring Security + jjwt，`Authorization: Bearer <token>` 头
- `SA_TOKEN`：Sa-Token，默认 token 名 `Authorization`

切换后重启 user-service 与 gateway-service 即可，业务代码（`LoginController` / `UserController`）无需改动。全栈档也可通过环境变量覆盖：`AUTH_TYPE=SA_TOKEN docker compose -f docker/docker-compose.yml up -d`。

## RBAC 权限

数据库驱动角色/权限模型，四表关联：

```
sys_user ──< sys_user_role >── sys_role ──< sys_role_permission >── sys_permission
```

- `sys_permission` 的 `type` 字段区分菜单(1)/按钮(2)/接口(3)
- 登录时将用户的角色码与权限码写入 JWT claims，`getLoginUser` 无需回查库
- 后端：`@RequirePermission("user:create")` 注解 + AOP 切面拦截，`*:*:*` 为超级权限通配符
- 前端：React `usePermission` hook + `AuthButton` 组件；Vue `v-permission` 指令
- 菜单：`/api/user/menus` 返回当前用户可见菜单树，前端动态渲染

种子数据：admin 用户绑 ADMIN 角色拥全部权限；guest 用户绑 USER 角色仅 user:view。

## API 文档

knife4j + springdoc 集成，经网关路由聚合：

- 访问 `http://localhost:8080/doc.html` → 网关路由 → user-service knife4j UI
- OpenAPI 规范：`http://localhost:8080/v3/api-docs/default`
- 支持 Bearer JWT 认证：在 knife4j 的「Authorize」输入 token，后续请求自动注入 `Authorization: Bearer {token}`
- 文档路径已在 `AuthGlobalFilter.PERMIT_PATHS` 放行，无需登录即可访问

## 共享包依赖方向

前端 `@fs/*` 包严格单向依赖，避免循环：

```
@fs/request → @fs/auth → @fs/types
@fs/locales（独立，仅依赖 i18next / vue-i18n）
```

token 注入请求头由 `@fs/request` 调用 `@fs/auth` 的 `getToken` 完成，`@fs/auth` 绝不反向依赖 `@fs/request`。React（zustand）与 Vue（pinia）各自实现 `@fs/auth` 的 `AuthStateStore` 接口，守卫与请求层跨框架复用。

## 常见问题

**Q: 切换 `auth.type=SA_TOKEN` 后 401？**
A: 确认 user-service 的 `Application` 类已排除 `SecurityAutoConfiguration`，否则 Spring Security 默认 FilterChain 会拦截 sa-token。见 [user-service/.../UserServiceApplication.java](backend/user-service/src/main/java/com/scaffold/user/UserServiceApplication.java)。

**Q: Nacos 客户端连不上？**
A: Nacos 2.x 除 8848 外还需 9848（gRPC client）与 9849（gRPC server）。compose 已映射，本地直连确认三个端口都通。

**Q: gateway 启动报 `spring-boot-starter-web` 冲突？**
A: Gateway 是 WebFlux，`gateway-service/pom.xml` 已显式排除 `spring-boot-starter-web` 与 `spring-boot-starter-security`（从 auth-starter 传递）。新增依赖时勿引入 servlet web。

**Q: 前端 antd 报「Cannot find React」？**
A: `frontend/.npmrc` 的 `public-hoist-pattern[]=*react*` / `*vue*` / `*antd*` 必须保留，否则 pnpm 严格 hoist 下 antd 找不到 React。

**Q: Docker 构建慢 / 报 xattr 失败？**
A: 首次构建需下载 maven 依赖，耗时较长。`docker/maven-settings.xml` 已配阿里云镜像加速。macOS 外置卷（非 APFS）可能因 `._*` AppleDouble 文件触发 buildx xattr 失败，改用旧 builder 即可：`DOCKER_BUILDKIT=0 docker compose -f docker/docker-compose.yml build`。

**Q: 前端 `pnpm build` 报 Top-level await is not available？**
A: Vite 默认构建目标（es2020）不支持顶层 await。`main.tsx` / `main.ts` 已用 `async function bootstrap()` 包裹，构建兼容 Chrome 87 / Firefox 78 / Safari 14。若在入口文件新增 `await`，同样需包裹在 async 函数中。

## 详细文档

各模块详细说明见对应目录下的 README（按需补充）。
