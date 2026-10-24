# 任务清单

> 完成情况：[x] 已完成 / [ ] 未完成。复核情况：已通过对应验证手段确认才算"已复核"。

## P0 安全与账户闭环

| 任务 | 完成情况 | 复核情况 |
| --- | --- | --- |
| P0-1 修改/重置密码：后端 `POST /api/auth/password`、`PUT /api/user/{id}/password/reset` | [x] | 已复核：E2E 17/17 通过（改密 4 分支 + 重置 4 分支 + 权限 403），脚本 `/tmp/fs-e2e.sh` |
| P0-1 修改/重置密码：前端 React/Vue 双端弹窗与入口（ChangePasswordModal / ResetPasswordModal） | [x] | 已复核（2026-10-17，UI 冒烟）：双端 typecheck / build 通过、新镜像已部署；管理员登录、改密/重置入口、弹窗、空表单必填提示及改密取消通过。浏览器未提交新密码，接口成功分支沿用既有 E2E 证据 |
| P0-2 登录限流：Gateway Redis 令牌桶 RequestRateLimiter | [x] | 已复核（2026-10-19）：30 次快速登录得 15×429 + 15×200，令牌桶耗尽即拒绝、静置 3s 自动恢复；限流键取客户端 IP（默认只信任最后一跳，客户端伪造的 X-Forwarded-For 段不采信） |
| P0-3 登录失败锁定：Redis 计数锁定账号（IP 仅记审计，避免 NAT 下误伤整片用户） | [x] | 已复核（2026-10-19）：连续 5 次失败后第 6 次返回 40003「账号已被锁定，请 15 分钟后重试」，锁键 TTL=897s；失败计数 TTL=900s（统计窗口），登录成功计数清零；计数与锁定在同一 Lua 脚本内完成，避免并发爆破卡在上限之下 |
| P0-4 默认密钥 fail-fast：非 dev profile 拒绝默认 JWT 密钥（JwtSecretValidator） | [x] | 已复核：prod profile 启动实测 Exited 1 + 中文报错 |
| P0-5 防绕过网关直连：X-Gateway-Secret 信任头 + compose 仅 expose 8001 | [x] | 已复核：宿主直连 8001 不可达、网内无信任头 403、正确密钥无 token 401、伪造 X-User-Id 401 |

## P1 脚手架核心能力

| 任务 | 完成情况 | 复核情况 |
| --- | --- | --- |
| P1-6 操作日志/审计：`@OperationLog` + AOP + 双端查询页 | [x] | 已复核（2026-10-19）：经网关 `GET /api/operation-log/list` admin=0 / guest=40003；字典模块增删改均落库且 `params` 已脱敏（`password` 类字段替换为 `***`，全表无 `123456` 明文）；AOP `@Order(HIGHEST_PRECEDENCE + 50)` 保证被鉴权拒绝的调用也留痕 |
| P1-7 登录日志：sys_login_log + 双端查询页 | [x] | 已复核（2026-10-19）：admin 查询/按 `eventType=LOGIN`/按 `success=false` 三种筛选均 code=0，guest 40003，库内记录非空（31 条）；登录成功与失败均写入 |
| P1-8 字典管理：sys_dict_type / sys_dict_item + 双端管理页 | [x] | 已复核（2026-10-19）：新增类型/项、按 `typeCode` 取项（`/api/dict-item/by-code/{code}` 免权限）、改项、删项、删类型（事务内先删项）全部 code=0；guest 新增类型 40003；Flyway V4 已建两表 |
| P1-9 Flyway 数据库版本管理：V1 基线 + baselineOnMigrate | [x] | 已复核：存量库 baseline 0→v1、空库 v1 建 13 表，双场景日志验证通过 |
| P1-10 个人中心页：本人查看/编辑昵称、邮箱、手机 | [x] | 已复核（2026-10-19）：`GET /api/user/profile` 按 token 解析 user id（无需权限码）、`PUT` 修改昵称后回读一致并已还原原值；用户名只读、三个可改字段长度校验（昵称 32 / 邮箱 64 / 手机 20） |

## P2 可观测性与工程质量

| 任务 | 完成情况 | 复核情况 |
| --- | --- | --- |
| P2-11 Metrics + 链路追踪（Micrometer + Prometheus + TraceId） | [x] | 已复核（2026-10-19）：依赖上，user-service 增 `micrometer-registry-prometheus` + `micrometer-tracing-bridge-brave`，gateway 增 `spring-boot-starter-actuator` + 同两项。指标：经网关 `GET /actuator/prometheus` → 200，180 行带 `application="gateway-service"`；user-service 容器内同路径 → 200，243 行带 `application="user-service"`，含 `http_server_requests_seconds_count`、`jvm_memory_used_bytes`、`hikaricp_connections_*`。链路：日志 pattern 为 `%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]`；带客户端 `traceparent`（traceId `4bf92f35…`）经网关请求后，user-service 请求线程日志为 `WARN [user-service,4bf92f3577b34da6a3ce929d0e0e4736,de50d0505d03dd9e]`（上游 traceId + 本机子 spanId），不带 trace 头时下游生成全新 trace；网关侧 `/actuator/conditions` 命中 `BraveAutoConfiguration` / `MicrometerTracingAutoConfiguration` / `WebFluxObservationAutoConfiguration` / `GatewayTracingConfiguration`，`gatewayPropagatingSenderTracingObservationHandler` 已装配（转发即注入 traceparent）。已知限制：网关自身（WebFlux）日志行取不到 traceId——brave 作用域基于线程本地、不跨 Reactor 边界，链路 ID 仍由网关生成并向下游传递，已在 gateway 的 `application.yml` 注明 |
| P2-12 前端 ESLint / Prettier 补齐 | [x] | 已复核（2026-10-19）：新增 `packages/eslint-config`（`@fs/eslint-config`，收敛双端共用规则，独立成包以避开 pnpm 幽灵依赖），两端 `eslint.config.mjs` 各自叠加 React / Vue 插件（Vue 取 `flat/essential` 只留正确性规则，格式交 Prettier）；根目录 `.prettierrc.json` + `.prettierignore`，`pnpm lint`（turbo 双端 2/2）、`pnpm format:check`（全部文件合规）、`pnpm typecheck`（8/8）、`pnpm build` 均通过。顺带修掉 3 处 lint 报错：React `StickyActions` 非组件导出（改为模块私有，与 Vue 端一致）、Vue `TabBar` 未使用的 `Translator` 类型、两处列表页 `any`（补树节点接口与 `customRender` 参数类型） |
| P2-13 CI 补前端 job | [x] | 已复核（2026-10-19）：新增 `.github/workflows/frontend-ci.yml`，按 `frontend/**` 路径触发，步骤为 frozen-lockfile 安装 → `format:check` → `lint` → `typecheck` → `build`；本地按同顺序跑通，且 `pnpm install --frozen-lockfile` 确认锁文件同步 |
| P2-14 业务模块测试 | [x] | 已复核（2026-10-19）：新增 30 个业务用例，全部通过。`common`/`auth-starter` 为纯单测（无 DB，CI 必跑）：`JacksonConfigTest`（雪花主键越界转字符串、小整数保持数字、时间格式往返）、`ClientIpResolverTest`（伪造 X-Forwarded-For 不采信）、`JwtAuthManagerTest`（claims 往返、篡改/过期/异密钥拒绝、logout 无操作）、`JwtSecretValidatorTest`（profile 矩阵）。`user-service` 为 DB 集成（跑 test profile）：`DictServiceBusinessTest`、`UserServiceBusinessTest`、`PermissionResolutionBusinessTest`。复核证据：`mvn -B -ntp -f backend/pom.xml -pl user-service -am test` 全绿（common 12 / auth-starter 16 / id-starter 33 / user-service 57）；另建**空库** `fs_ci_check` 重跑同一命令，新增用例仍全绿（证明用例自建数据、不依赖种子），验证后已删库；`backend-test.yml` 的 `mvn verify` 已自动纳入这些用例 |
| P2-15 多环境 profile 拆分（dev / test / prod） | [x] | 已复核（2026-10-19）：两个后端模块各拆 4 份配置（`application.yml` 通用 + `-dev/-test/-prod`），`application.yml` 声明 `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`、compose 两侧显式传 `-dev` 默认值。复核：dev 档（compose 实跑）E2E 17/17 全绿；test 档在**无 Nacos、无 Redis** 的独立容器里 `actuator/health=UP`；prod 档缺变量时 `Exited 1` 且报「缺少必需配置项 auth.gateway.trust-secret」、弱密钥时被 `JwtSecretValidator` 拒绝、注满变量则能正常起服务并关闭 `/v3/api-docs`（返回 404）；`docker inspect` 核对运行容器镜像 ID 与最新构建一致 |
| P2-16 窄屏横向滚动优化（前端双端） | [x] | 已复核（2026-10-19）：先定位再动手——375 宽下文档 `scrollWidth` 比视口多 164~805px（React）、164~805px（Vue），1024 宽（侧栏展开）下多 144~276px（React）、110~276px（Vue），来源是**筛选栏里写死的内联宽度**（搜索框 240 / 下拉 140~160 并排）与**宽表**（列宽合计 310~1059px）。修法为两侧 `index.css` 各加一条全局 `.ant-table-content { overflow-x: auto }`（表格在自身容器内横滚，1440 宽下表格恰好填满容器、规则自动失效）与一段 `@media (max-width: 768px)`（筛选栏折行、输入/下拉跟随可用宽度、按钮保持原尺寸）；媒体查询只命中含输入/下拉的 `Space`，卡片标题行不动，保证 React 与 Vue 布局一致。复核（重建两侧前端镜像并重新部署后，在**构建产物**上实测，非仅开发服务器）：1440 双端 0 溢出且表格未出现滚动条（无回归）；1024 双端 0 溢出（宽表转为容器内滚动：表格容器 213/内容 927，`overflow-x: auto` 生效）；375 与 320 双端全部路由（登录/首页/用户/角色/权限/权限组/操作日志/登录日志/字典/个人中心/用户新建）均 0 溢出；两端截图肉眼确认工具栏折行、分页完整、无内容裁切。另排查两处同类风险：多标签栏（8 个标签）由 antd `Tabs` 自身折叠 + more 按钮承载，不贡献页面溢出；弹窗两端已被 antd 限制为 `max-width: calc(100vw - 32px)`，无需改动。`pnpm format:check` 对两个 CSS 文件通过 |
| P2-17 Docker 生命周期脚本去除 Python 依赖 | [x] | 已复核（2026-10-24）：`docker/manage.py` 改写为 `docker/manage.sh`（bash 3.2 兼容，只用 bash + Docker CLI，不依赖 Python/jq），Maven 入口的 `exec-maven-plugin` 由 `python3 -c` 改为 `bash -c`，向上查找 `docker/manage.sh` 的定位逻辑保留。并发锁改用原子 `mkdir` + pid 判活 + 陈旧锁清理；镜像名由 `docker compose config` 的规范化 YAML 解析，避开 `config --images` 顺序不可靠。新增 `docker/tests/manage_test.sh` + `docker/tests/fake-bin/docker`（假 docker 记录调用，不触达真实守护进程），20 组 69 项断言全通过：共享库拒绝、父 reactor 子模块短路、超时/动作校验、父级 delete 不删卷不删镜像、子模块 stop/delete 只针对自身、create 走 `--no-start`、重复 up 不强制重建、build 只构建选中镜像且过滤 `node_modules`/`dist`/`._*`、旧卷闸门、拒绝覆盖非空目录、inspect 失败不误判缺失、远程守护进程拒绝、只读动作不建目录不持锁、环境文件提供数据根且命令行可覆盖。真实矩阵复核：`init` 建四个数据目录 → `migrate` 无旧卷时 no-op → Vue `deploy/stop/create/delete/remove-image` → 父级 `delete` 后容器 0、镜像 4、数据目录 4 且 `fs_user.user` 仍 10 行 → 父级 `create`/`start` 七个容器全部 healthy，5173/5174/8080 均 200、登录 200 |
| P2-18 数据目录统一与跨平台环境文件 | [x] | 已复核（2026-10-24）：此前 compose 是 `FS_DATA_PATH` 直连 bind，而脚本仍在创建容器根本不用的 4 个命名卷，`-Ddocker.data.dir` 也不影响实际数据位置，两个口径并存。现收敛为单一来源：四个数据目录都在 compose 用同一个 `${FS_DATA_PATH}` 声明（`fs_mysql_data`/`fs_redis_data`/`fs_nacos_data`/`fs_nacos_logs`），`manage.sh` 删除命名卷创建与目录匹配逻辑，改为准备宿主目录并把解析出的数据根作为 `FS_DATA_PATH` 传给 compose；优先级 `-Ddocker.data.dir` > 环境文件 `FS_DATA_PATH` > 默认 `/Volumes/WD_BLACK/docker/docker_files`。新增 `docker/env.template`（数据根、端口、profile、密钥、镜像标签），复制为 `docker/.env.unix`（macOS/Linux/WSL）或 `docker/.env.window`（Windows），脚本按 `uname` 选 `docker/.env.$PLATFORM`、找不到再退回 `.env`，只读取需要的键而不 source 文件；`.gitignore` 增加 `docker/.env.*`。复核：临时建 `.env.unix` 后 `mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=config` 通过，`docker compose config` 四个 volume 的 `source` 全部解析到 `/Volumes/WD_BLACK/docker/docker_files/fs_*`；`--data-dir D:/docker/...` 在 Windows 分支被接受、相对路径被拒；父子模块与全 reactor 三种 Maven 跑法均返回 0；脚本测试 69/69 |

## 附带修复（本轮顺手修的既有缺陷，已完成）

| 任务 | 完成情况 | 复核情况 |
| --- | --- | --- |
| 修复 `@RequirePermission` 全线失效：PermissionAspect 原仅 `@Component`，starter 包不被业务应用扫描，切面从未注册（guest 能创建用户） | [x] | 已复核：新增 PermissionAspectConfiguration 显式装配后，guest 调用创建用户/分配角色/重置密码返回 403 |
| 修复测试数据角色误绑：TestDataRebuildRunner 原先所有账号（含 guest/operator）无条件绑 ADMIN | [x] | 已复核：重建后库确认 admin=ADMIN / guest=无角色 / operator=OPERATOR |
| 修复网关放行路径未注入信任头：captcha/login 放行路径只剥离伪造头未注入 X-Gateway-Secret，被下游误判 403 | [x] | 已复核：E2E 17/17 通过 |
| 修复雪花 ID 精度丢失：user-service 未装配 `JacksonConfig`，Long 以 JSON number 下发，超 2^53 后不同记录在浏览器被舍入成同一数字 | [x] | 已复核（2026-10-19）：`/api/user/list` 返回 id 为字符串且互不相同、createTime 为 `yyyy-MM-dd HH:mm:ss`，小整数 `code`/`timestamp`/`parentId=0` 保持数字；E2E 17/17、双端 typecheck 通过 |
| 修复 Vue 中文界面弹窗按钮仍为 Cancel / OK：`a-config-provider` 未设置 `locale` | [x] | 已复核（2026-10-19）：web-vue 注入 `zh_CN` locale，typecheck 通过 |
| 修复停用角色仍继续授权：权限解析 SQL 未过滤角色状态，界面停用角色后权限照旧写进登录 token | [x] | 已复核（2026-10-19）：`PermissionMapper.selectByUserId` / `selectMenusByUserId` 双路径补角色启用与未删除过滤（写成 EXISTS 子查询，避开 UNION 中 `sys_role` 与 `sys_permission` 同名列 `sort`/`id` 的歧义）。由 `PermissionResolutionBusinessTest`「停用角色」用例暴露并固化；复核后 E2E 17/17、P1 探针 24/24（admin 菜单仍含字典/登录日志） |
| 修复密码为空时登录 500：`BCrypt.matches` 不接受 null 入参，抛 IllegalArgumentException 被当成服务端错误 | [x] | 已复核（2026-10-19）：JWT 与 Sa-Token 两条链路在调用 `matches` 前判空，缺凭据归为 401「密码错误」。由 `JwtAuthManagerTest` 暴露并固化为用例；复核后 E2E 17/17 |
| 修复个人中心空请求 500：三个字段都为 null 时会拼出没有 SET 子句的 UPDATE 语句 | [x] | 已复核（2026-10-19）：无字段可改时直接返回当前资料，不再下发给数据库。由 `UserServiceBusinessTest` 暴露并固化为用例 |
| 修复登录日志静默丢失：`LoginLogRecorder` 的 `@Async` 方法在异步线程读 `HttpServletRequest`，而容器在响应结束后已回收该对象（抛 "request object has been recycled"），异常打断整段逻辑，记录连 insert 都到不了 | [x] | 已复核（2026-10-19）：改为**在请求线程采集地址与 User-Agent**，成形的记录再交给 `auditLogExecutor` 落库（保留异步与 CallerRunsPolicy 语义），异步线程不再触碰 request。探针：带有效验证码连续 6 次失败登录，`sys_login_log` 由 83 行增至 89 行（恰为 6 条，修复前这类记录会丢），`client_ip` / `user_agent` 均有值、中文提示入库正常，`登录日志写入失败` 告警 0 次；新增 `LoginLogRecorderTest` 3 例（请求先回收再落库、请求为空、落库失败只告警）固化；`mvn -B -ntp -f backend/pom.xml -pl user-service -am test` 60/60 全绿；E2E 17/17 |

## 部署类

部署入口现已统一为 Maven `docker` profile，参见 [DOCKER.md](DOCKER.md)。后续使用该入口替代下方历史接力中的 docker commit 流程；镜像从仓库源码构建，数据存放宿主目录。

| 任务 | 完成情况 | 复核情况 |
| --- | --- | --- |
| 后端镜像固化（user-service / gateway-service 含全部修复的 fat jar） | [x] | 已复核：docker commit 后 compose --force-recreate 重建，E2E 仍 17/17 |
| 前端镜像重建并部署（web-react / web-vue 含 P0-1 新页面） | [x] | 已复核（2026-10-17）：利用本地已安装依赖完成双端 typecheck / build，打包 nginx 镜像并 compose 重建；HTTP 页面及入口资源与本地构建逐字节一致，双端 /api/auth/captcha 返回 code=0 |
| 前端镜像重建并部署（含 P1-6/7/8/10 新页面） | [x] | 已复核（2026-10-19）：`mvn -N -f docker/web-{react,vue}/pom.xml -Pdocker validate -Ddocker.action=build` 重建镜像，镜像内产物含新页面代码（`grep dict-item`/`login-log` 命中）；restart 后 `/operation-log`、`/login-log`、`/dict`、`/profile` 双端均返回 200 |
| 前端 P0-1 双端浏览器 UI 验证 | [x] | 已复核（2026-10-17，UI 冒烟）：管理员登录、修改密码菜单、用户行重置密码按钮、两个弹窗必填提示均通过；未在浏览器提交新密码。截图 `/tmp/fs-ui-proof/{react,vue}-reset.png` |
| 后端镜像重建并部署（含 P2-15 profile 拆分） | [x] | 已复核（2026-10-19）：`mvn -N -f backend/{user,gateway}-service/pom.xml -Pdocker validate -Ddocker.action=build` 重建镜像后 `action=up` 收敛容器；`docker inspect` 核对容器镜像 ID 与最新构建一致；E2E 17/17 全绿 |
| 后端镜像重建并部署（含 P2-11 可观测性与登录日志修复） | [x] | 已复核（2026-10-19）：两侧镜像均带 actuator + prometheus + brave 依赖（P2-11 阶段重建）；登录日志修复后 `mvn -N -f backend/user-service/pom.xml -Pdocker validate -Ddocker.action=build` + `action=up` 重新收敛 user-service，`docker inspect` 核对容器镜像 ID 与构建产物一致（`sha256:0bc011a5…`）；容器 `healthy`，E2E 17/17 全绿 |

---

# 接力上下文（供后续工具继续任务）

## 项目结构

- 根目录：`/Volumes/WD_BLACK/github/fullstack-scaffold`
- 后端：Maven 多模块 `backend/`（common、auth-starter、captcha-starter、id-starter、gateway-service、user-service），Spring Boot 3.2.5 / Java 17
- 前端：pnpm monorepo `frontend/`，`apps/web-react`（React18 + antd5 + zustand）与 `apps/web-vue`（Vue3.5 + ant-design-vue4 + pinia）**双端必须对称实现**，公共模块在 `frontend/packages/`（types、locales、auth、request）
- 部署：`docker/docker-compose.yml`，容器命名 `fs-*`，网络 `fullstack-scaffold_fs-net`

## 关键约定

- 种子账号 admin / guest / operator，密码均为 `123456`；BCrypt 加密；雪花 ID 主键
- ResultCode：SUCCESS=0 / PARAM_INVALID=40000 / UNAUTHORIZED=40001 / FORBIDDEN=40003 / CAPTCHA_INVALID=40005
- 权限判定：后端 `@RequirePermission`，超级权限通配符 `*:*:*`；前端公共模块 `@fs/auth` 的 `checkPermission` 为唯一判定口径
- 双端 i18n 插值统一单花括号 `{count}`

## 验证手段

- E2E 脚本：`/tmp/fs-e2e.sh`（经网关 8080，覆盖登录、验证码、改密、重置、权限 403），跑完应 17/17 全绿。注意该文件在 /tmp，重启会丢，需要时按仓库最新接口重写
- P1 探针：`/tmp/fs-p1-probe.sh`（经网关覆盖操作日志/登录日志/字典/个人中心的权限与增删改查），跑完应 24/24 全绿。同样在 /tmp
- 前端类型检查：`cd frontend && pnpm --filter web-react typecheck && pnpm --filter web-vue typecheck`
- 测试数据重建（角色修正版）：docker create 启动 user-service 镜像，环境变量 `TEST_DATA_ACTION=rebuild`、`TEST_DATA_CONFIRM=I_UNDERSTAND`、`AUTH_GATEWAY_ENFORCE_TRUST=false`

## 环境陷阱（已踩过的坑，勿重复踩）

1. **外置卷历史上已满（2026-10-17 复查尚余约 164GiB）**：`/Volumes/WD_BLACK`（exFAT）100% 满，本地 `mkdir target` / jar 构建会 "No space left on device"。一律在容器内构建，用 named volume `fs-m2` 缓存 Maven 依赖
2. **BuildKit xattr 失败**：exFAT 卷上的 `._*` AppleDouble 文件导致 `failed to xattr operation not permitted`。`rm`/`find -delete` 报 "Cross-device link" 删不掉，需用文件删除工具删；备选 `DOCKER_BUILDKIT=0`
3. **网络 TLS 抖动**：阿里云 / Maven Central / npmmirror 都会出现 `Remote host terminated the handshake`。容器内构建必须带重试循环：`for i in 1..6; do mvn ... && break; sleep 8; done`
4. **docker cp 热替换 jar 前必须核对大小**：fat jar 应为 gateway ~48MB / user-service ~72MB，9.5KB 的是 repackage 失败的 thin jar，cp 进去会让容器 Restarting 循环
5. **后台命令不要用管道收尾**：`docker run ... | tail -N` 会掩盖真实退出码，应重定向到文件后 grep "BUILD SUCCESS/FAILURE"
6. **不要前台 sleep 轮询后台任务**：之前因此卡死被用户终止。后台启动后等系统通知即可
7. **逻辑删除用户仍占 uk_username**：E2E 临时用户用 `e2e_pwd_$(date +%s)` 唯一名，清理用物理删除

## 镜像固化命令（后端改动后的标准流程）

容器内构建 fat jar → `docker create` 临时容器 cp 出 jar（核对大小）→ cp 进运行容器 → `docker commit` 固化 → `docker compose --force-recreate` 重建 → 跑 E2E 回归。

## 待办建议顺序

P1 已全部收口（见上表）。P2 亦已全部收口：

1. P2-12 前端 ESLint / Prettier 补齐、P2-13 CI 补前端 job（已完成）
2. P2-15 多环境 profile 拆分（已完成）
3. P2-14 业务模块测试（已完成）
4. P2-11 Metrics + 链路追踪（已完成，需重建 gateway / user-service 镜像，改动最大）

清单内任务已全部完成，本轮未提交代码，等用户指示后再 commit。

## 2026-10-19 接力（五）：P2-11 可观测性 + 登录日志缺陷修复

- P2-11 落地内容：
  - 依赖：user-service 增 `micrometer-registry-prometheus` + `micrometer-tracing-bridge-brave`（actuator 原已有）；gateway 增 `spring-boot-starter-actuator` + 同两项。
  - 配置：两侧 `application.yml` 增 `management`（`exposure.include: health,info,prometheus`、`metrics.tags.application: ${spring.application.name}`、`tracing.sampling.probability`）与 `logging.pattern.level`（`%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]`）；两侧 `application-prod.yml` 把 `management.server.port` 绑到 `${MANAGEMENT_PORT:}`（留空即与应用同端口）、采样率降到 0.1、只暴露 health + prometheus。
  - 采样率与指标标签都可经环境变量覆盖（`TRACING_SAMPLING_PROBABILITY`），生产按流量成本下调即可。
- **误判与纠正（本轮最大的坑）**：一开始在日志窗口里 grep 不到 traceId，据此认为「链路追踪未生效」，方向错了——user-service 正常配置下**业务代码几乎不写请求级日志**（只在 WARN/ERROR 时写），窗口里那几百行全是启动与 Nacos 心跳（非请求线程，MDC 本就为空）。判定 tracing 是否生效之前，先确认「这条代码路径到底写不写日志」。
  - 可复现的观测方式（不改配置）：取一次有效验证码后连续失败登录，第 5 次会触发 `LoginLockoutService` 的锁定告警，那是一行请求线程日志，`[user-service,<traceId>,<spanId>]` 齐全。
  - 需要看每个请求的 DEBUG 日志时，可用临时 compose override 注入 `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB=DEBUG`，验证完删掉 override 重新收敛即可（不要改仓库里的正式配置）。
- 跨服务贯穿的复核方式：客户端直接带 W3C `traceparent`（traceId 固定为 `4bf92f35…`）经网关发请求，user-service 日志里出现的是**同一个 traceId + 自己的子 spanId**；不带该头时下游生成全新 trace。网关侧不能看自己的日志（见下），改由 `/actuator/conditions` 佐证链路自动配置已装配。
- **网关（WebFlux）自身日志拿不到 traceId**：brave 的作用域基于线程本地，不跨 Reactor 边界，故网关产生的日志行 `[gateway-service,,]` 为空。这不影响链路本身——traceId 仍由网关生成并随转发注入下游。已在 `gateway-service/application.yml` 的 `logging.pattern.level` 注释写明，避免下一位读者重复排查。
- 探针顺带发现既有缺陷：`LoginLogRecorder` 的 `@Async` 方法在异步线程读 `HttpServletRequest`，容器已回收该对象 → 抛 "request object has been recycled"，记录静默丢失（详见「附带修复」表）。已改为请求线程采集 + 异步落库。
- 环境陷阱（新增，勿重复踩）：
  1. 诊断端点（`beans` / `conditions` / `configprops` / `loggers` / `httpexchanges`）可用临时 compose override 的 `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` 打开，**不必改仓库配置**。注意 `httpexchanges` 在当前依赖组合（Boot 3.2.5）下即使开了也不出现在 `/actuator` 列表里，别指望用它回看请求头。
  2. `/actuator/conditions` 的响应体是 `{contexts: {<name>: {positiveMatches, negativeMatches}}}` 嵌套两层，直接按顶层 `positiveMatches` 取值会拿到空结果、误判「自动配置没生效」。
  3. `MANAGEMENT_PORT` 必须留空才与应用同端口；设成独立端口时 compose 健康检查已同步跟随 `${MANAGEMENT_PORT:-8001}`，两处要一起改，否则容器一直 unhealthy。
  4. 日志里出现 "The request object has been recycled and is no longer associated with this facade" 就是**异步线程读了已回收的请求**，属 servlet 生命周期问题，不要往 JDBC/连接池方向查。
  5. `mvn -Dtest=X` 配合 `-am` 时，其他模块没有该用例会让 surefire 直接报 "No tests matching pattern" 而失败，要加 `-Dsurefire.failIfNoSpecifiedTests=false`。
- 本次未提交代码。

## 2026-10-19 接力（四）：P2-14 业务模块测试

- 新增 30 个用例（common 12 / auth-starter 16 / user-service 30 中的新增部分），分两层：
  - **纯单测（无 DB）**：`JacksonConfigTest`（越界 Long 转字符串、小整数保持数字、时间格式往返、null 语义）、`ClientIpResolverTest`（可信跳数矩阵、伪造转发头）、`JwtAuthManagerTest`（claims 往返、篡改/过期/异密钥拒绝、logout 无操作）、`JwtSecretValidatorTest`（profile 矩阵）。不起 Spring 上下文，CI 无依赖必跑。
  - **DB 集成（`@ActiveProfiles("test")`）**：`DictServiceBusinessTest`（编码唯一、项归属不可改、删类型连带删项、停用不下发）、`UserServiceBusinessTest`（BCrypt 存储形态、改密/重置校验、个人中心字段边界、逻辑删除）、`PermissionResolutionBusinessTest`（直接分配与权限组并集去重、停用权限组/权限/角色立即失效、菜单只取 type=1）。
- 复用 P2-15 的 test profile，因此不需要 Redis / Nacos / 种子数据；`ItDatabase` 提供「实例不可达即整类跳过」的守卫，本地无 DB 时不会误报失败。
- 用例数据自建自清：`TestDataFixture` 统一生成唯一用户名（纳秒后缀）、自建角色并登记回收；清理一律**物理删除**——逻辑删除只把 `deleted` 置 1，唯一索引仍占着用户名。
- **测试发现并修复 3 个既有缺陷**（详见「附带修复」表）：停用角色仍继续授权（权限解析 SQL 漏过滤角色状态，会影响真实权限，最严重）、密码为空时登录 500、个人中心空请求 500。这三处都不是新写坏的，是既有代码里「界面点不到就没人发现」的分支。
- 复核证据：`mvn -B -ntp -f backend/pom.xml -pl user-service -am test` → common 12 / auth-starter 16 / id-starter 33 / user-service 57 全绿（BUILD SUCCESS）；另建空库 `fs_ci_check` 重跑，新增用例同样全绿，证明不依赖种子（验证后已删库）；user-service 镜像重建 + `action=up` 重新部署后 E2E 17/17、P1 探针 24/24 全绿，`docker inspect` 核对镜像 ID 一致。
- 环境陷阱（新增）：
  1. 跑测试时若把 `it.db.url` 指到白名单外的库（如临时探针库），既有 `IdIntegrationTest.rebuildAndVerify` 会因「数据库不在白名单」而失败——那是重建程序的保护，不是用例缺陷。要么用 `fs_user` / `fs_user_test`，要么把临时库名加进 `allowed-databases`。
  2. `mvn -pl user-service` 不带 `-am` 时会去本地仓库取旧版 `common`/`auth-starter`，编译期报「找不到符号」。改公共模块后务必带 `-am`。
  3. 断言密码字段时要区分入口：`getById` / 列表接口会把密码清空，`loadUserByUsername` 返回的是密文（登录链路要用它做校验）。混淆这两个入口会写出永远失败的用例。
- 本次未提交代码。

## 2026-10-19 接力（三）：P2-15 多环境 profile 拆分

- 拆分结果：`backend/user-service` 与 `backend/gateway-service` 各 4 份配置。
  - `application.yml`：只留与环境无关的内容（端口、Flyway 策略、MyBatis-Plus、雪花发号器、限流/锁定开关、路由表、Sa-Token、captcha 长度与 TTL 等），并声明 `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`。
  - `application-dev.yml`：外部依赖默认指向 `127.0.0.1`、内置默认密钥（仅本档允许）、SQL 打印、`com.scaffold: DEBUG`。
  - `application-test.yml`：数据源走 `${it.db.url:…}`（沿用 `ItDatabase` 的系统属性名与默认值，`-Dit.db.url=…` 可直接切实例）、Nacos 发现关闭、`captcha.cache-mode=memory`、`login-lockout.enabled=false`、MyBatis `NoLoggingImpl`、关 knife4j、关 Redis 健康检查。网关的 test 档只覆盖 Redis/密钥，**保持服务发现启用**——服务发现是网关核心语义，关掉会让测试与实际部署行为不一致。
  - `application-prod.yml`：外部依赖与密钥一律无默认值，另用 `scaffold.required-properties` 声明必需项清单；关文档、关 SQL 日志、`management` 只暴露 health、`scaffold.id.worker-id` 必须显式指定。
- 默认档保持 dev 是**关键约束**：`docker/docker-compose.yml` 未设置该变量时行为与拆分前一致，不破坏既有部署；两侧 compose 现在也显式写明 `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}`，需要切档时改该变量即可。
- 新增 `RequiredPropertiesEnvironmentPostProcessor`（common 模块，`spring.factories` 注册）：Spring Boot 对 `@ConfigurationProperties` 的占位符是**非严格解析**，`host: ${REDIS_HOST}` 在变量缺失时会把字面量 `${REDIS_HOST}` 原样绑进去——故障被推迟到首次连接（Redis 懒连接，可能直到首次限流才暴露），共享密钥更会退化成仓库里公开的字面量、让网关信任校验静默通过。改为在 Environment 就绪后、Bean 实例化前按清单校验，缺失即抛「缺少必需配置项 xxx」并退出。必需项清单由配置驱动（增删只改 yml），取值来源不限——环境变量、`--spring.datasource.url=…`、配置中心均可。
- 复核证据：dev 档 compose 实跑 E2E 17/17 全绿；test 档独立容器（无 Nacos、无 Redis）`actuator/health=UP`；prod 档缺变量 `Exited 1` + 报「缺少必需配置项 auth.gateway.trust-secret」，弱密钥被 `JwtSecretValidator` 拒绝，注满变量后服务正常起、`/v3/api-docs` 返回 404（文档确已关闭）；`docker inspect` 核对运行容器镜像 ID 与最新构建一致。
- 环境陷阱（新增）：
  1. `@ConfigurationProperties` 的占位符不会因变量缺失而报错，只能靠上面的 `EnvironmentPostProcessor` 或等首次连接暴露——配置 prod 必需项时别指望 Spring 自己报「变量没设」。
  2. prod 探针容器必须显式设置 `SPRING_APPLICATION_NAME` 区分于线上同名实例，否则会以同名注册进 Nacos，网关在旧/新实例间轮询，症状是「时好时坏」（同 P0 复核时踩过的 `fs-us-probe`）。用完即 `docker stop` + `rm`。
  3. 用 `test` 档起容器时，Redis 健康检查会因容器内没有 Redis 而判 DOWN（服务本身没事，Redis 是懒连接）——已在 test 档关闭该健康检查，否则会误判 profile 拆分失败。
- 本次未提交代码。

## 2026-10-19 本次接力

- 修复雪花 ID 精度丢失（优先级最高项）。根因：`JacksonConfig` 位于 `com.scaffold.common.config`，业务应用只扫自身包，配置从未装配；`user-service` 的 ObjectMapper 一直是 Boot 默认配置。
  - 修复方式：新增 `CommonAutoConfiguration` 显式 `@Import` 公共配置类。注意 `@Import` 会先加载目标类，因此 servlet 专用配置（`CorsConfig implements WebMvcConfigurer`）必须拆到 `CommonServletAutoConfiguration` 并用 `@ConditionalOnWebApplication(SERVLET)` 隔离，否则 WebFlux 的 gateway 启动即 `NoClassDefFoundError`（本轮已踩到，gateway 曾 Restarting 循环）。
  - 序列化策略改为 `SafeLongSerializer`：仅超出 JS 安全整数范围（±2^53-1）的 Long 转字符串，小整数（`code`/`total`/`timestamp`/`parentId=0`）保持数字，避免破坏前端数值比较与分页计算。原 `ToStringSerializer` 全量转字符串会连带把 `code` 变成 `"0"`。
  - 定制方式必须用 `Jackson2ObjectMapperBuilderCustomizer`（Bean 由 `JacksonObjectMapperBuilderConfiguration` 统一应用，顺序有保证），不能改回「对 `Jackson2ObjectMapperBuilder` 做 `@Autowired` 方法注入」：那种写法在自动配置先构建 ObjectMapper 时会被静默跳过，正是本 bug 难以察觉的原因。
  - 双端类型对称：`@fs/types` 新增 `ID = number | string`，各实体主键、`userId`、`parentId` 及 `number[]` 泛型（树选中 id、角色/权限 id 列表）统一改为 `ID`；`@fs/utils` 的 `formatDateTime` 增加空格→`T` 归一化，兼容 Safari 不解析 `yyyy-MM-dd HH:mm:ss`。
- P0-2 登录限流：gateway 增加 `spring-boot-starter-data-redis-reactive`，`RequestRateLimiter` 令牌桶（`LOGIN_RATE_REPLENISH`/`LOGIN_RATE_BURST`，默认 5/s、突发 10）只作用于 `/api/auth/login` 路由（`order=-1`，避免被通配路由截走）；限流键 `loginRateLimitKeyResolver` 取客户端 IP，取法收敛到公共模块 `ClientIpResolver`（默认只信任最后一跳）。429 无响应体，前端在 `@fs/request` 统一补提示文案。
- P0-3 登录失败锁定：`LoginLockoutService` 位于 user-service（Redis 计数 + Lua 原子累加锁定），在 `LoginController` 织入，同时覆盖 JWT / Sa-Token 两条鉴权链路；只按账号锁定，IP 仅记审计；仅统计凭据校验失败（验证码错误在更早一步拦截，不计入）；Redis 不可用时 fail-open 并告警。
- 复核证据：E2E 17/17 全绿；双端 `typecheck` 通过；`/api/user/list` 原始 JSON 中 id 为互不相同的字符串、`createTime` 为 `yyyy-MM-dd HH:mm:ss`；限流 30 次请求 15×429 + 15×200 且静置后恢复；锁定第 6 次返回 40003 且锁键 TTL=897s。
- 环境提醒：验证期间曾遗留一个 `fs-us-probe` 探针容器（同名注册到 Nacos），导致网关在「已修复/未修复」两个实例间轮询、序列化结果时对时错；排查此类「时好时坏」现象时先确认 Nacos 同名实例是否只有一个。
- 本次未提交代码。

## 2026-10-19 接力（续）：P1-6 / P1-7 / P1-8 / P1-10 收口

- 后端（user-service）：操作日志（`sys_operation_log`：`@OperationLog` AOP + 异步落库 + 参数脱敏）、登录日志（`sys_login_log`：登录/登出均写入）、字典管理（`sys_dict_type` / `sys_dict_item`：按类型聚合，删类型在事务内先删项；`/api/dict-item/by-code/{code}` 免权限供下拉取用）、个人中心（`GET`/`PUT /api/user/profile`：user id 由 token 解析，无需权限码）。Flyway V2 / V3 / V4 迁移已随镜像生效。
- 权限种子：`menu:operation-log`、`menu:login-log`、`menu:dict` 三个菜单 + 各自按钮码 + `api:*` 接口码，按 code 反查绑定 ADMIN。补种走**加性 SQL**（`INSERT ... ON DUPLICATE KEY UPDATE`），不跑破坏性重建：`TestDataRebuildRunner` 的 destroy 流程会清库，复核期间不要执行。
- 前端双端对称：新增 `operation-log`、`login-log`、`dict`、`profile` 四个页面与路由（`login-log`/`dict` 带菜单权限码，`profile` 登录即可访问）；`MenuIcon` 映射补 `history`（登录日志）、`book`（字典）；顶栏账号下拉新增「个人中心」入口；字典页左栏选类型 → 右栏列该类型字典项（`.dict-type-selected` 行高亮）；个人中心保存后同步顶栏昵称。
- 复核手段：`/tmp/fs-p1-probe.sh`（24 项，经网关覆盖 4 个模块的权限与增删改查，全绿）+ `/tmp/fs-e2e.sh`（17/17）+ 双端 `typecheck`。脚本在 /tmp，重启会丢，需要时按仓库最新接口重写。
- 未做浏览器 UI 实测：登录验证码需人工识别，且本次浏览器视口高度为 0、无法交互，改以「接口探针 + 镜像产物存在性 + SPA 路由 200」作为复核证据；如需像素级确认，请人工登录后目视 `/dict`、`/login-log`、`/profile` 三页。
- 环境陷阱（新增，勿重复踩）：
  1. 在 `"$( ... )"` 里内联带 `\"` 的 JSON 作参数时，外层双引号会先消费转义，请求体被截断成片段（服务端只会以畸形 JSON 回 50000，极易误判为产品缺陷）。构造请求体一律先经 `python3 -c 'json.dumps(...)'` 生成变量再传。
  2. `mvn -Pdocker` 生命周期命令需读写 `/Users/pe7er/Developer/docker/...` 与 Docker Desktop 日志，**后台执行会 hit restricted**；这类命令要前台执行并关闭沙箱。
  3. 经宿主 `mysql` 客户端写中文需加 `--default-character-set=utf8mb4`，否则双重编码（`menu:operation-log` 的中文名曾被写成乱码，已用 `ON DUPLICATE KEY UPDATE` 一并修正）。
- 本次未提交代码。

## 2026-10-17 本次接力

- 完成双端前端镜像重建、部署及页面/静态资源/API 代理校验，双端类型检查和生产构建通过。构建存在既有大 chunk 警告，不影响产物生成。
- 修复 React 的 ChangePasswordModal / ResetPasswordModal Promise 链：返回接口请求并统一捕获校验/业务失败，避免未处理 rejection；Vue 已有对应错误处理。
- 采用本地 pnpm 构建产物 + nginx:alpine 打包，临时镜像上下文位于 `/tmp/fs-frontend-deploy/{web-react,web-vue}`。部署命令：`docker compose -f docker/docker-compose.yml up -d --no-deps --force-recreate web-react web-vue`。
- 注意从宿主复制产物时权限可能使 nginx 返回 403；打包 Dockerfile 已增加 `RUN chmod -R a+rX /usr/share/nginx/html`，重建后 HTTP 验证通过。
- 用户授权验证码后，双端管理员登录及改密/重置弹窗 UI 冒烟通过，空表单提交均显示必填提示。未在浏览器输入或提交新密码，未重跑后端 E2E；此次无后端改动。未提交代码。
- 新发现（已修复，见 2026-10-19 接力）：双端用户列表多个雪花 ID 显示成相同数字（如 `104879054015991800`），根因是 user-service 未装配 `JacksonConfig`，Long 经 JSON number 解析后丢失精度。
- UI 附带观察（已修复，见 2026-10-19 接力）：Vue 中文界面的弹窗默认按钮仍显示 Cancel / OK，需补齐组件库 locale。

## Maven / Docker 生命周期（2026-10-17）

- [x] 父 POM 管理全栈、后端子 POM 和新增前端部署子模块管理自身；显式 docker profile，普通构建不操作容器。
- [x] 提供 init/migrate/build/create/up/start/stop/restart/delete/remove-image/status/config；删除容器保留外部数据卷，已有卷路径不匹配时报错；修改操作加文件锁。
- [x] 新建四个 bind 命名卷，绑定 `/Users/pe7er/Developer/docker/fullstack-scaffold-data` 下 mysql/redis/nacos-data/nacos-logs。旧卷保留；MySQL 逻辑导入，Redis/Nacos 停服复制。Redis 开启 AOF everysec。
- [x] MySQL 迁移后 12 张含数据表的 INSERT 语句与原备份逐项一致；全部服务已恢复，原卷与物理复制副本保留。
- [x] 16 项自动化测试通过；父 reactor 状态查询通过；Vue / Gateway 镜像经 Maven 从源码构建成功；Vue stop/delete/create/up/restart 各重复两次通过，其他六个服务容器 ID 与启动时间均未改变。
- 构建入口生成无 node_modules/target/AppleDouble 的临时源码上下文，解决 exFAT BuildKit Dockerfile xattr 失败。后端 Dockerfile 同步保留新 reactor 的相对目录，使用 BuildKit 依赖缓存和有限重试。
- 重复 migrate 已验证为无复制操作；`docker.phase=package` 绑定 package 阶段的父 POM 状态查询通过。MySQL 容器重建后用户表数据与迁移备份仍逐项一致。
- 使用方法见 `docs/DOCKER.md`；此次未提交代码。
