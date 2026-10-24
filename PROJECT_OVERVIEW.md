# fullstack-scaffold 项目概览

> 面向 AI 学习的项目导览。读取本文件后，配合目录结构与代码可快速建立全局心智模型。

## 1. 项目定位

**fullstack-scaffold** 是一套面向中小团队的「全栈脚手架」参考实现，演示一条完整的「鉴权 + RBAC + 业务 CRUD」链路，覆盖后端微服务、前端双端（React / Vue）对称实现、Docker Compose 一键部署三大维度。

核心设计目标：
- **开闭原则**：鉴权方案、验证码、可信设备策略等均通过接口 + 配置切换，新增实现不改核心
- **双端对称**：React 与 Vue 前端使用同一套 API、同一套权限判定口径、同一套交互模式
- **可切换**：JWT 无状态 ↔ Sa-Token 有状态一行配置切换，前端零改动
- **可观测**：统一响应 / 异常 / 分页契约，Knife4j 文档，MyBatis-Plus SQL 日志

## 2. 顶层结构

```
fullstack-scaffold/
├── backend/                  # Maven 多模块后端
│   ├── pom.xml               # 父 pom：模块声明 + 版本治理
│   ├── common/               # 统一响应/异常/分页 + CORS/OpenAPI 基础
│   ├── auth-starter/         # 鉴权抽象（JWT / Sa-Token 双实现）
│   ├── captcha-starter/      # 验证码三层 Bean（生成/渲染/缓存）
│   ├── gateway-service/      # Spring Cloud Gateway（WebFlux）
│   └── user-service/         # 业务服务（Servlet）：用户/角色/权限/设备
├── frontend/                 # pnpm monorepo 前端
│   ├── pnpm-workspace.yaml
│   ├── apps/
│   │   ├── web-react/        # React 18 + antd 5 + react-router 6
│   │   ├── web-vue/          # Vue 3.5 + ant-design-vue 4 + vue-router 4
│   │   └── backend-mock/     # 独立 mock 服务
│   └── packages/
│       ├── auth/             # @fs/auth 权限判定纯函数
│       ├── request/         # @fs/request axios 封装 + 401/403 处理
│       ├── types/            # @fs/types 共享 TS 类型
│       ├── locales/          # @fs/locales i18n 资源
│       ├── utils/            # @fs/utils 通用工具
│       └── tsconfig/         # @fs/tsconfig 共享 tsconfig
└── docker/                   # 基础设施
    ├── docker-compose.yml    # 7 个服务编排
    ├── mysql/{conf,init}/    # MySQL 配置与建表脚本
    ├── redis/redis.conf
    ├── nacos/conf/
    ├── user-service/Dockerfile
    ├── gateway-service/Dockerfile
    ├── web-react/Dockerfile  # node 编译 → nginx 静态服务
    └── web-vue/Dockerfile
```

## 3. 后端架构

### 3.1 技术栈版本（父 pom 锁定，禁止被动升级）

| 依赖 | 版本 |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.5 |
| Spring Cloud | 2023.0.3 |
| Spring Cloud Alibaba | 2023.0.1.0 |
| MyBatis-Plus | 3.5.7 |
| Sa-Token | 1.39.0 |
| jjwt | 0.12.6 |
| springdoc | 2.3.0 |
| knife4j | 4.4.0 |
| hutool | 5.8.27 |
| lombok | 1.18.32 |

> 父 pom 不继承 `spring-boot-starter-parent`，因此 `spring-boot-maven-plugin` 显式绑定 `repackage` execution 到 package 阶段；`maven.compiler.parameters=true` 必须开启，否则 Spring 6 反射拿不到参数名。

### 3.2 Maven 模块职责

| 模块 | 职责 | 关键点 |
|---|---|---|
| `common` | 统一 `Result`/`ResultCode`/`BizException`/`PageQuery`/`PageResult`，CORS、OpenAPI 基础配置 | 不强依赖 spring-web，依赖均 `optional` |
| `auth-starter` | 鉴权抽象层，提供 `AuthManager` / `AuthUserLoader` / `LoginTokenEnhancer` 扩展点 | `auth.type=JWT\|SA_TOKEN` 一行切换；`JwtSecurityConfig` 用 `@ConditionalOnWebApplication(SERVLET)` 隔离 gateway |
| `captcha-starter` | 验证码三层 Bean：`CaptchaCodeGenerator` / `CaptchaImageRenderer` / `CaptchaCache` | `captcha.cache-mode=redis\|memory`；业务方覆盖 Bean 即可替换实现 |
| `gateway-service` | Spring Cloud Gateway，WebFlux 反应式网关 | 路由 `/api/user/**`、`/api/auth/**`、`/api/device/**`、`/api/role/**`、`/api/permission/**`、`/api/permission-group/**` 与文档路由到 `lb://user-service` |
| `user-service` | 业务服务（Servlet）：用户、角色、权限、权限组、设备、登录、验证码、菜单 | 同时实现 `UserService` 与 `AuthUserLoader` 双契约 |

### 3.3 `auth-starter` 扩展点

```
AuthManager          # 鉴权门面：login / parseToken / getTokenHeaderName
├── JwtAuthManager        # JWT 无状态实现
└── SaTokenAuthManager    # Sa-Token 有状态实现

AuthUserLoader        # 业务侧实现：loadUserByUsername(username) → LoginUser
LoginTokenEnhancer    # 签发前扩展点：可注入设备指纹/二次因子
JwtSecurityConfig     # Servlet 安全链，未认证返回 401 JSON（与网关一致）
```

装配规则见 [AuthAutoConfiguration.java](backend/auth-starter/src/main/java/com/scaffold/auth/AuthAutoConfiguration.java)：
- `auth.type=JWT`（默认）：装配 `JwtAuthManager`，`@Import` 引入 `JwtSecurityConfig`（受 `@ConditionalOnWebApplication(SERVLET)` 隔离，gateway 不触发）
- `auth.type=SA_TOKEN`：装配 `SaTokenAuthManager`，业务侧 Application 需排除 `SecurityAutoConfiguration`
- `AuthUserLoader` 经 `ObjectProvider` 延迟注入：gateway 不调 login 无需 loader 也能装配 `AuthManager` 用于校验

### 3.4 `captcha-starter` 三层 Bean

见 [CaptchaAutoConfiguration.java](backend/captcha-starter/src/main/java/com/scaffold/captcha/CaptchaAutoConfiguration.java)：
- 生成器：`captcha.code-mode=random`（默认）
- 渲染器：默认 AWT PNG，业务方可注册 `CaptchaImageRenderer` Bean 覆盖
- 缓存：`captcha.cache-mode=redis`（默认）/`memory`；redis 实现要求 classpath 存在 `StringRedisTemplate`
- 总开关：`captcha.enabled=false` 整个装配退出
- 签发前扩展：`LoginTokenEnhancer` 接口，`DeviceBindingLoginEnhancer` 是其设备绑定实现

### 3.5 `user-service` 包结构

```
com.scaffold.user
├── UserServiceApplication          # 启动类，排除 SecurityAutoConfiguration（Sa-Token 模式）
├── controller/
│   ├── LoginController              # /api/auth/login、/api/auth/logout、/api/auth/captcha
│   ├── CaptchaController            # /api/auth/captcha/image
│   ├── UserController               # /api/user/** CRUD + 分页 + 角色分配
│   ├── RoleController               # /api/role/**
│   ├── PermissionController         # /api/permission/**
│   ├── PermissionGroupController    # /api/permission-group/**
│   └── DeviceController             # /api/device/**
├── service/                         # 接口 + impl 双层
│   ├── UserService / RoleService / PermissionService / PermissionGroupService
│   └── RbacService                  # 角色与权限编码加载（写 token claims）
├── entity/
│   ├── BaseEntity                  # id + createTime（最小集，UserDevice 继承此级）
│   ├── BaseLogicEntity              # + status(枚举) + updateTime + deleted(逻辑删除)
│   ├── User / Role / Permission / PermissionGroup  # 均继承 BaseLogicEntity
│   └── UserDevice                   # 继承 BaseEntity（物理删除）
├── enums/
│   ├── EnableStatus                 # 启用/禁用枚举，@EnumValue + @JsonValue/@JsonCreator
│   └── DeleteFlag                   # 逻辑删除标记枚举
├── mapper/                          # MyBatis-Plus Mapper，注解 SQL
├── device/                          # 设备子领域：DeviceService / UserDeviceRegistry / TrustedDevicePolicy
├── config/                          # MyBatisPlusConfig（分页拦截器）/ MyBatisMetaObjectHandler / OpenApiConfig
├── dto/                             # MenuNode 等数据传输对象
├── exception/GlobalExceptionHandler
└── util/MenuTreeBuilder
```

### 3.6 关键配置

[user-service/application.yml](backend/user-service/src/main/resources/application.yml)：
- `mybatis-plus.type-enums-package: com.scaffold.user.enums` + `default-enum-type-handler: MybatisEnumTypeHandler`
- 全局逻辑删除：`logic-delete-field: deleted` / `logic-delete-value: 1` / `logic-not-delete-value: 0`
- `auth.type` / `captcha.cache-mode` 等均通过环境变量覆盖

[gateway-service/application.yml](backend/gateway-service/src/main/resources/application.yml)：
- 路由 `/api/**` 前缀透传下游（`PreserveHostHeader`），便于下游统一鉴权与 OpenAPI
- 文档路由：`/doc.html`、`/v3/api-docs/**`、`/swagger-ui/**`、`/webjars/**`

## 4. 前端架构

### 4.1 技术栈版本

| | React 端 | Vue 端 |
|---|---|---|
| 框架 | react 18.3 / react-dom 18.3 | vue 3.5 |
| 路由 | react-router-dom 6.27 | vue-router 4.4 |
| UI | antd 5.21.5 | ant-design-vue 4.2.6 |
| 状态 | zustand 4.5 | pinia 2.2 |
| i18n | i18next 23 + react-i18next 15 | vue-i18n 9.14 |
| 图标 | lucide-react 1.54 | lucide-vue-next 1.0 |
| 构建 | vite 5.4 + @vitejs/plugin-react | vite 5.4 + @vitejs/plugin-vue + vue-tsc |
| HTTP | axios 1.7（封装在 @fs/request） | 同左 |

### 4.2 路由清单（两端对称）

见 [web-react/src/router/index.tsx](frontend/apps/web-react/src/router/index.tsx)：

| 路径 | 页面 | 菜单权限码 |
|---|---|---|
| `/login` | 登录页 | - |
| `/home` | 首页 | - |
| `/user` | 用户列表 | `menu:user` |
| `/user/create` / `/user/detail/:id` / `/user/edit/:id` | 用户三态表单 | - |
| `/device` | 设备列表 | `menu:device` |
| `/role` | 角色列表 | `menu:role` |
| `/permission` | 权限列表 | `menu:permission` |
| `/permission-group` | 权限组列表 | `menu:permission-group` |
| `*` | 404 | - |

路由 `handle` 携带 `title`（支持 i18n key 与函数式）与 `permission`，由 `BasicLayout` 在布局层做权限判定，无权限渲染 403。

### 4.3 公共模块（packages/）

| 模块 | 包名 | 职责 |
|---|---|---|
| auth | `@fs/auth` | 权限判定纯函数 `checkPermission`（权限码 → 角色 → customFn 三级裁决），React `usePermission`/`withPermission`/`PermissionGuard`，Vue 插槽式 `Permission` |
| request | `@fs/request` | axios 实例 + 拦截器，token 注入调用 `@fs/auth`，401/403 触发清 token + 跳登录 |
| types | `@fs/types` | 共享 TS 类型（User/Role/Permission/PageQuery 等） |
| locales | `@fs/locales` | i18n 资源，单花括号 `{count}` 插值；React 端 i18next 显式配置 `prefix:'{' suffix:'}'` 对齐 vue-i18n |
| utils | `@fs/utils` | 通用工具（防抖搜索、IME 组合处理等） |
| tsconfig | `@fs/tsconfig` | 共享 tsconfig |

**关键约束**：`@fs/auth` 不反向依赖 `@fs/request`，token 注入由 `@fs/request` 调用 `@fs/auth` 完成，避免循环依赖。

### 4.4 双端对称实现模式

以下能力在 React 与 Vue 端均有等价实现，API 形态一致：
- 防抖搜索：React `useDebounceSearch` Hook ↔ Vue `useDebounceSearch` composable（300ms 防抖 + compositionstart/compositionend IME 处理）
- `ImeInput` 组件
- 吸底操作栏 `StickyActions`（三态状态机 hidden/sticky/inline + IntersectionObserver 降级）
- 多标签栏 `TabBar`（标签激活由 URL 派生，标签键 = pathname + 排序后 query）
- 路由级权限（`BasicLayout` 层判定 + 403/404 兜底）
- 用户三态表单（新建/详情/编辑，模式由路由派生）

## 5. 基础设施

### 5.1 docker-compose 服务清单

见 [docker/docker-compose.yml](docker/docker-compose.yml)：

| 服务 | 镜像 | 宿主端口 | 作用 |
|---|---|---|---|
| mysql | mysql:8.0 | `${MYSQL_PORT:-3306}` | 数据库 `fs_user`，init 脚本自动建表 |
| redis | redis:7-alpine | 6379 | 验证码缓存 / Sa-Token session |
| nacos | nacos/nacos-server:v2.3.2 | `${NACOS_HTTP_PORT:-8848}` + 9848/9849 | 服务发现 |
| user-service | 本地构建 | `${USER_SERVICE_PORT:-8001}` | 业务服务 |
| gateway-service | 本地构建 | `${GATEWAY_PORT:-8080}` | 网关，前端 proxy 指向此 |
| web-react | 本地构建（node→nginx） | `${WEB_REACT_PORT:-5173}:80` | React 前端 |
| web-vue | 本地构建（node→nginx） | `${WEB_VUE_PORT:-5174}:80` | Vue 前端 |

所有端口均支持 `${VAR:-default}` 形式环境变量覆盖，宿主端口被占用时一行参数即可绕开。

### 5.2 Dockerfile 分布

- `docker/user-service/Dockerfile`、`docker/gateway-service/Dockerfile`：Maven 多阶段构建，build context 为仓库根，注入阿里云 maven-settings.xml 加速
- `docker/web-react/Dockerfile`、`docker/web-vue/Dockerfile`：node 编译 → nginx 静态服务，配置 SPA fallback、`/api` 代理到 gateway、`/doc.html` 代理

### 5.3 启动命令

```bash
# 一键启动全栈
docker compose -f docker/docker-compose.yml up -d --build

# 宿主端口冲突时覆盖
MYSQL_PORT=13306 docker compose -f docker/docker-compose.yml up -d --build

# 切换鉴权方案为 Sa-Token
AUTH_TYPE=SA_TOKEN docker compose -f docker/docker-compose.yml up -d --build
```

## 6. 关键工程约定

1. **API 契约**：所有响应统一 `Result<T>{code, message, data}`；分页 `PageResult<T>{total, current, size, records}`；分页参数 `PageQuery{page, size}`（非 pageNum/pageSize）
2. **API 路径前缀**：`/api/{domain}/**`（user/auth/device/role/permission/permission-group）；前端 vite proxy `/api → gateway:8080`，登录路径 `/api/auth/login`
3. **JWT 无状态**：用户信息全部编码在 token payload，后端仅验签 + 过期校验，不查库；`LoginUser.status` 保持 `Integer`（auth-starter 不反向依赖 user-service 枚举），衔接处在 `UserServiceImpl.loadUserByUsername` 用 `.getCode()` 转换
4. **枚举契约**：`EnableStatus`/`DeleteFlag` 使用 `@EnumValue` 落库、`@JsonValue`/`@JsonCreator` 保证对外 JSON 仍为 `1/0`，前端零改动；DB 列仍为 TINYINT，无需迁移；Mapper 注解 SQL 中硬编码 `status = 1`/`deleted = 0` 依然正确
5. **基类层次**：`BaseEntity(id, createTime)` → `BaseLogicEntity(status, updateTime, deleted)`，按 SRP 拆分以适配物理删除（UserDevice）与逻辑删除（User/Role/Permission/PermissionGroup）两种表结构
6. **逻辑删除**：`@TableLogic` + `@JsonIgnore`，序列化时隐藏；`MetaObjectHandler` 插入时自动填充 `DeleteFlag.NOT_DELETED`
7. **分页拦截器**：必须显式注册 `PaginationInnerInterceptor(DbType.MYSQL)`，否则 `selectPage` 不拼 LIMIT/COUNT，total 恒为 0
8. **角色/权限分配**：`@Transactional` 注解 + 先删后插原子操作（`assignPermissions` / `assignGroups`）
9. **菜单图标**：统一使用 lucide 图标库，React `lucide-react` / Vue `lucide-vue-next`，通过 `ICON_MAP` 做图标名映射（home→House、team→Users、user→User、setting→Settings）
10. **权限控制统一口径**：`@fs/auth` 的 `checkPermission` 纯函数为唯一判定入口，判定逻辑为：权限码任一满足 → 未指定非空 codes 时检查角色 → `customFn` 业务条件最终裁决，空数组表示不加限制
11. **列表页搜索规范**：300ms 防抖 + IME 组合事件处理，搜索值在执行时实时读取而非依赖输入框 onChange 快照
12. **后端分页 keyword**：嵌套 AND/OR 逻辑，对目标字段模糊匹配，不影响其他过滤条件
13. **macOS 外置卷构建陷阱**：非 APFS 卷上 `._*` AppleDouble 文件导致 Docker buildx xattr 失败，需用 `DOCKER_BUILDKIT=0` 并清理 `._` 文件
14. **Git 提交规范**：中文 conventional commit（`feat(scope): 描述` / `refactor(scope): 描述` / `fix(scope): 描述`）

## 7. 当前完成进度

- 用户、角色、权限、权限组、设备的 CRUD + 分页 + 关键词搜索
- JWT 无状态认证 + Sa-Token 双实现可切换
- 图形验证码（随机码 + AWT PNG + Redis/内存缓存）
- 设备绑定登录增强 + 可信设备策略扩展点
- 双端（React/Vue）对称实现：登录、布局、路由级权限、多标签栏、吸底操作栏、防抖搜索、三态用户表单
- Docker Compose 一键部署（mysql/redis/nacos/gateway/user-service/web-react/web-vue）
- Knife4j 文档（`/doc.html`）

## 8. 推荐学习路径

1. 读本文件建立全局认知
2. 读 [backend/pom.xml](backend/pom.xml) 理解后端版本治理与模块声明
3. 读 [AuthAutoConfiguration.java](backend/auth-starter/src/main/java/com/scaffold/auth/AuthAutoConfiguration.java) 理解鉴权扩展点与切换机制
4. 读 [UserServiceImpl.java](backend/user-service/src/main/java/com/scaffold/user/service/impl/UserServiceImpl.java) 理解业务服务同时实现 `UserService` 与 `AuthUserLoader` 双契约
5. 读 [web-react/src/router/index.tsx](frontend/apps/web-react/src/router/index.tsx) 理解前端路由 + 权限元数据
6. 读 `@fs/auth` 的 `checkPermission` 理解前端权限判定口径
7. 读 [docker-compose.yml](docker/docker-compose.yml) 理解一键部署
8. 跑一次 `docker compose -f docker/docker-compose.yml up -d --build`，访问 `http://localhost:5173`（React）或 `http://localhost:5174`（Vue），文档在 `http://localhost:8080/doc.html`
