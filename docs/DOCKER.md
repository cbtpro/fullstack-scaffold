# Maven 管理 Docker 部署

面向单机服务器：MySQL、Redis、Nacos、网关、用户服务及 React/Vue 统一由 Compose 编排。Maven `docker` profile 将操作绑定到 `validate` 阶段，普通 `mvn test/package/clean` 不操作容器。父 POM 管理全栈，独立运行子 POM 管理自身。共享库没有独立镜像。

前置条件：服务器安装 Maven 3.9+、JDK 17+、bash（macOS 自带 3.2 即可）、支持 BuildKit 的 Docker Engine/Desktop 和 Compose v2（支持 `up --wait`）；通过本机 Unix socket 访问 Docker。部署脚本只用 bash 与 Docker CLI，不需要 Python 或 jq。所有命令在仓库根执行；无需额外手动运行 Docker 命令。

## 整套服务

```sh
# 查看状态；也可省略 docker.action（默认 status）
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=status

# 首次初始化宿主数据目录（检测到历史命名卷时必须先 migrate）
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=init

# 将历史 Compose 卷迁移到指定宿主目录，保留原卷
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=migrate

# 构建全部应用镜像；中间件使用官方镜像
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=build

# 新建容器但不启动
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=create

# 启动并等待就绪；已存在的容器按配置收敛，不强制重建
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=up

# 构建并启动（一条命令部署）
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=deploy

mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=stop
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=restart

# 删除容器及项目网络，保留所有数据卷和镜像
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=delete

# 单独删除应用镜像；仍在使用的镜像会报错，不强删
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=remove-image
```

MySQL/Redis/Nacos/用户服务使用各自健康检查；网关检查本地 HTTP 服务已监听，双端 Nginx 检查首页响应。业务接口另行执行冒烟验证。

`start` 是 `up` 的别名，即使容器尚不存在也能新建并启动。可用 `-Ddocker.wait.timeout=360` 调整等待秒数。`config` 校验 Compose 配置，不打印可能包含凭据的完整配置。

需要挂入标准打包阶段时，显式指定执行阶段，例如：

```sh
mvn -N -f backend/pom.xml -Pdocker package -Ddocker.phase=package -Ddocker.action=build
```

构建入口自动准备不含 node_modules/target/._* 的临时源码上下文，避免外置 exFAT 卷的 xattr 问题。这里镜像的后端编译、前端依赖安装与打包在各 Dockerfile 的构建阶段完成；`-N` 仅避免宿主重复编译，不影响镜像内编译。普通 `mvn clean` 不删除镜像、容器或数据。推荐使用 `-N -f`，不要使用 `-pl` 选择 Docker 目标；未加 `-N` 的完整 reactor 会由父 POM 统一操作一次，子模块跳过，避免重复操作。

## 单模块

```sh
mvn -N -f backend/user-service/pom.xml -Pdocker validate -Ddocker.action=build
mvn -N -f backend/user-service/pom.xml -Pdocker validate -Ddocker.action=restart
mvn -N -f backend/gateway-service/pom.xml -Pdocker validate -Ddocker.action=up
mvn -N -f docker/web-react/pom.xml -Pdocker validate -Ddocker.action=deploy
mvn -N -f docker/web-vue/pom.xml -Pdocker validate -Ddocker.action=stop
```

应用模块的 create/up/restart 不启动或重建依赖容器，依赖先由父 POM 启动。独立构建后端子模块镜像时，Dockerfile 会构建其所需共享模块。前端 Maven 模块只管理镜像，源码仍位于 `frontend/apps/`。中间件容器及宿主持久化目录只由父 POM 的动作管理。

## 持久化与幂等语义

默认数据根目录：`/Volumes/WD_BLACK/docker/docker_files`（外置盘）。四个数据目录都在 [docker-compose.yml](../docker/docker-compose.yml) 里用同一个 `${FS_DATA_PATH}` 声明，分别为 `fs_mysql_data/`、`fs_redis_data/`、`fs_nacos_data/`、`fs_nacos_logs/`；改数据根只需改一处。数据目录优先级：`-Ddocker.data.dir` > 环境文件里的 `FS_DATA_PATH` > 上面的默认值。容器与 Docker 虚拟磁盘不保存业务数据，`delete` 只删容器和网络，宿主目录原样保留。Redis 使用 AOF everysec + RDB，仍可能丢失断电前约一秒的写入，持久化不能替代异机备份。

迁移前停止全部写入服务，Redis 先执行 SAVE，MySQL 使用 mysqldump 逻辑备份导入，避免 macOS 宿主目录大小写规则与旧数据字典不兼容；Redis/Nacos 通过暂存目录复制，成功后写入标记，重复 migrate 不再复制。MySQL 备份保存在数据目录的 `backups/mysql-migration.sql`，只迁移本项目 `fs_user` 库，不迁移其他库和 MySQL 系统账户。旧卷不删除，可以作为迁移时点的回退副本。失败时返回非零且保留原卷、备份和暂存目录，服务可能仅部分启动；检查原因后再恢复，禁止直接覆盖非空目标目录。MySQL 导入失败可使用已记录的 pending 备份重试；其他卷部分复制需人工检查暂存目录。

- init：四个数据目录已存在则不改动；检测到旧命名卷且未迁移则报错。
- create/up：目标存在则复用，配置/镜像变化时由 Compose 重建。
- stop/delete：目标不存在也成功；delete 不清理数据、不删除镜像。
- restart：保证目标最终运行；每次调用都会实际停止/启动，无法保证进程本身不重复执行。
- build/deploy：重复执行可复用构建缓存；基础镜像/依赖变化可能产生新镜像，不保证镜像摘要相同。
- 所有修改操作使用宿主目录锁（原子 `mkdir` + pid 判活，陈旧锁自动清理）串行执行；错误不会被忽略。

Linux 服务器可用 `-Ddocker.data.dir=/srv/fullstack-scaffold-data` 覆盖默认目录。初始化后每次命令都应使用同一目录；换目录等于换一份空数据，工具不会自动搬运或合并已有数据。目录路径指 Docker 所在服务器，不支持在客户端通过 SSH Docker context 初始化远端目录。Docker Desktop 需要允许共享该宿主目录。

## 服务器端口与配置

用户服务仅在容器网络提供 8001。MySQL（默认 13306）、Redis、Nacos 管理端口默认绑定 127.0.0.1。公开入口为网关 8080、React 5173、Vue 5174；可由服务器反向代理统一接入域名/TLS。

Compose 环境变量可覆盖端口、`FS_DATA_PATH`、`AUTH_JWT_SECRET`、`AUTH_GATEWAY_SECRET`、`IMAGE_TAG`。先复制模板再按平台改名，两份都可以被 Git 忽略：

```sh
cp docker/env.template docker/.env.unix    # macOS / Linux / WSL
cp docker/env.template docker/.env.window   # Windows
```

脚本按当前平台优先加载 `docker/.env.$PLATFORM`（`unix` 或 `window`），找不到再退回 `docker/.env`；`docker-compose` 直接跑时用 `--env-file docker/.env.unix`。模板里的默认值是开发口径，切 `prod` 前须替换密钥。`docker-compose.dev.yml` 和 `docker-compose.test.yml` 是独立开发/测试档，不属于本生命周期，禁止与全栈档同时创建同名开发容器。

## 环境档（Spring Profile）

后端两个模块各拆为 `application.yml`（通用）+ `application-dev.yml` / `application-test.yml` / `application-prod.yml`，由 `SPRING_PROFILES_ACTIVE` 选择，缺省 **dev**：

- **dev**：外部依赖默认指向 `127.0.0.1`、内置默认密钥、打印 SQL。本地与默认 Compose 部署走这一档，无环境变量即可启动。
- **test**：数据源用 `it.db.url` 等系统属性（与集成测试同一组名字，`-Dit.db.url=…` 可切换实例），注册中心与 Redis 均不参与，`login-lockout` 关闭。CI 的 `backend-test.yml` 已按此约定传参。
- **prod**：外部依赖与密钥**无默认值**，另在 `scaffold.required-properties` 里声明必需项，缺失即启动失败并报出具体键名；同时关闭接口文档与 SQL 日志，`scaffold.id.worker-id` 须显式指定（多实例互不相同，否则雪花发号撞号）。

切到生产档：在 `docker/.env` 写入 `SPRING_PROFILES_ACTIVE=prod` 并补齐 `MYSQL_HOST` / `MYSQL_USER` / `MYSQL_PASSWORD` / `REDIS_HOST` / `NACOS_ADDR` / `SNOWFLAKE_WORKER_ID` / `AUTH_JWT_SECRET` / `AUTH_GATEWAY_SECRET`。未补齐时容器启动即退出，不会带默认密钥对外服务——生产安全配置因此不再依赖人工记得改，而由启动校验兜住。

## 验证

```sh
bash docker/tests/manage_test.sh
mvn -N -f backend/pom.xml -Pdocker validate -Ddocker.action=config
```

设计参考：[Exec Maven Plugin](https://www.mojohaus.org/exec-maven-plugin/exec-mojo.html)、[Compose 绑定挂载](https://docs.docker.com/engine/storage/bind-mounts/)。

已在当前环境验证：19 组共 63 项脚本测试全部通过、父 reactor 统一执行、package 阶段绑定、Vue/Gateway 子模块源码镜像构建、Vue 五种操作各重复两次、兄弟容器不受影响、`delete` 后数据目录与镜像保留、迁移重复执行不再复制。未重启整台 Docker daemon，以免影响同机其他项目。
