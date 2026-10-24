# 全局 Snowflake ID 重构 · 交付报告

环境：JDK 21.0.8 / Maven 3.9.9 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.7 / MySQL 8.0.43（容器 `fs-mysql`，宿主端口 13306，库 `fs_user`）

---

## 一、ID 位分配

| 段 | 位数 | 取值 | 说明 |
|---|---|---|---|
| 符号位 | 1 | 恒 0 | 保证 Java `long` 为正数 |
| 时间戳 | 41 | 毫秒差 | 纪元 `2026-01-01T00:00:00Z` = `1767225600000`，可用约 69.68 年 |
| workerId | 10 | 0–1023 | 最多 1024 个节点 |
| 序列号 | 12 | 0–4095 | 单节点每毫秒 4096 个 |

Java 侧 `long` / `Long`，MySQL 侧 `BIGINT`。全部业务表已去掉 `AUTO_INCREMENT`。

---

## 二、修改清单

### 2.1 新增模块 `backend/id-starter`（19 个文件）

| 文件 | 作用 |
|---|---|
| `SnowflakeIdGenerator.java` | 接口：`nextId()` / `nextIds(int)` / `parse(long)` / `validate(long)` |
| `ParsedSnowflakeId.java` | 解析结果 record：时间戳、workerId、序列号 |
| `DefaultSnowflakeIdGenerator.java` | 核心实现，单 JVM 内 `synchronized` 串行化 |
| `SnowflakeProperties.java` | `scaffold.id.*` 配置项，默认纪元 1767225600000 |
| `SnowflakeIdErrorCode.java` / `SnowflakeIdException.java` | 9 种错误码 + 携带错误码的运行时异常 |
| `Clock.java` / `SystemClock.java` / `MutableClock.java` | 可注入时钟，测试可冻结/回拨 |
| `WorkerIdProvider.java` / `FixedWorkerIdProvider.java` | 固定 workerId，不做集群协调 |
| `WorkerIdLease.java` / `JdbcWorkerIdLease.java` / `LeasedWorkerIdProvider.java` | 可选的数据库租约模式 |
| `GlobalSequenceAllocator.java` / `JdbcSequenceAllocator.java` | 全局严格排序序号分配器 |
| `SnowflakeIdentifierGenerator.java` | MyBatis-Plus `IdentifierGenerator` 桥接 |
| `SnowflakeAutoConfiguration.java` + `AutoConfiguration.imports` | 自动装配 |

### 2.2 业务侧改动（14 个文件修改 + 4 个实体新增）

| 文件 | 改动 |
|---|---|
| `entity/BaseEntity.java` | `IdType.AUTO` → `@TableId(value="id", type=IdType.ASSIGN_ID)` |
| `entity/UserRole.java` 等 4 个 | **新增**关联实体，使关联表走 BaseMapper 而非手写批量 SQL |
| `mapper/{UserRole,RolePermission,PermissionGroupItem,RolePermissionGroup}Mapper.java` | 改为 `extends BaseMapper<X>`，`batchInsert` → `insertBatch`，SQL 显式带 id |
| `service/impl/UserServiceImpl.java` | 改为 `extends ServiceImpl<UserMapper, User>`；`assignRoles` 用 `nextIds(n)` 预分配 |
| `service/impl/RoleServiceImpl.java`、`PermissionGroupServiceImpl.java` | `assignPermissions` / `assignGroups` 预分配 ID |
| `device/UserDevice.java` | 新增 `sortSeq` 字段 |
| `device/UserDeviceRegistry.java` | 新设备注册时从 `GlobalSequenceAllocator` 取全局序号 |
| `device/DeviceServiceImpl.java` | 排序改为 `orderByDesc(lastLoginTime).orderByDesc(sortSeq)` |
| `resources/application.yml` | `id-type: auto` → `assign_id`；新增 `scaffold.id.*` 与 `test-data.*` |
| `backend/pom.xml`、`user-service/pom.xml` | 加入 `id-starter` 模块与依赖 |

### 2.3 数据库

`docker/mysql/init/init.sql` 重写：9 张业务表主键改 `BIGINT NOT NULL`、去掉 `AUTO_INCREMENT`；新增 `idx_*_created (create_time DESC, id DESC)`；`user_device` 增加 `sort_seq BIGINT NOT NULL` + `uk_device_sort_seq`；新增 `sys_sequence`、`sys_worker_node` 两张基础设施表；移除全部种子数据（改由重建程序生成）。

---

## 三、三种排序策略

| 场景 | 排序写法 | 说明 |
|---|---|---|
| 普通列表 | `ORDER BY created_at DESC, id DESC` | 时间相同用 id 兜底，结果稳定 |
| Snowflake 趋势 | `ORDER BY id DESC` | 单节点内严格递增；**不宣称跨节点严格时序** |
| 全局严格顺序 | `ORDER BY sort_seq` | 由 `GlobalSequenceAllocator` 统一分配，仅 `user_device` 使用 |

`sort_seq` 只加在真正需要跨节点严格顺序的表上，不是所有表都加。

---

## 四、三种排序策略的边界（按需求原文）

- **固定 Worker ID 模式不宣称具备自动集群协调能力。**`scaffold.id.worker-id-mode=FIXED`（默认值）下，`FixedWorkerIdProvider` 只读取配置的 workerId，不做任何节点间协商；部署时必须由运维保证节点间 workerId 不重复。
- **分布式协调实现与实际部署条件匹配。**项目当前没有 Redis、没有注册中心，因此可选的 LEASE 模式基于已存在的 MySQL（`sys_worker_node` 表）实现，不引入额外基础设施。
- **重建程序不重置 Worker ID 租约与序号高水位。**`TestDataRebuildRunner` 的清理清单 `PURGE_TABLES` 只含 9 张业务表，`sys_sequence` 与 `sys_worker_node` 不在其中。本次重建后 `sys_sequence.user_device` 从 136 继续（未回到 1），即为此行为的实测证据。

---

## 五、测试结果

### 5.1 单元测试（id-starter）— 22 项全部通过

```
Tests run: 17, Failures: 0, Errors: 0 -- SnowflakeIdGeneratorTest
Tests run:  5, Failures: 0, Errors: 0 -- LeasedWorkerIdProviderTest
Tests run: 22, Failures: 0, Errors: 0
```

覆盖：位分配与解析往返、单节点单调、批量生成、序列号耗尽跨毫秒、时钟回拨（容忍内 / 超容忍）、时间戳溢出、workerId 越界、ID 合法性校验、租约获取/续约/失效停发/重启复用。

### 5.2 集成测试（user-service）— 16 项全部通过

```
Tests run: 16, Failures: 0, Errors: 0 -- IdIntegrationTest
```

接独立 MySQL 实例（`-Dit.db.url=jdbc:mysql://127.0.0.1:13306/fs_user`）。覆盖 `save` / `saveBatch` / `saveOrUpdate` / `updateById` / `removeById` / 逻辑删除 / 分页，以及「已有 id 不被重新生成或覆盖」。

### 5.3 数据重建与校验

```
rebuild  : 3072 ms
  user 23 | sys_role 3 | sys_permission 52 | sys_permission_group 2
  sys_user_role 43 | sys_role_permission 82 | sys_permission_group_item 12
  sys_role_permission_group 3 | user_device 45
verify   : 121 ms  → 全部校验通过
```

`verify` 检查项：ID 合法性、主键重复、8 组外键孤儿、`sort_seq` 唯一且为正、`deleted=0`。

### 5.4 安全护栏实测

| 场景 | 结果 |
|---|---|
| 未传 `test-data.confirm` | `IllegalStateException`，不删除任何数据 |
| profile = `prod` | `IllegalStateException` |
| profile 为空（普通启动） | 不执行任何动作 |

### 5.5 Schema 抽查

9 张业务表主键均为 `bigint`，`information_schema.COLUMNS.EXTRA` 为空，即无 `auto_increment`。

样本：`user.id = 103326213124128771`、`user_device.sort_seq` 区间 [136, 180]，45 行 45 个不同值。

---

## 六、JMH 性能数据（实际运行，非估算）

环境：JDK 21.0.8，1 fork × 3 预热（1s）× 5 测量（2s），堆 512MB。
原始数据：`backend/id-starter/docs/jmh-snowflake-result.txt`

**吞吐量**

| 基准 | 模式 | 得分 | 误差 |
|---|---|---|---|
| `nextIdSingleThread` | 单线程 | 4 081.485 ops/ms | ±102.406 |
| `nextIdMultiThread`（8 线程争用同一生成器） | 多线程 | 4 092.696 ops/ms | ±23.817 |
| `nextIdBatch100`（每次 100 个） | 批量 | 40.871 ops/ms | ±0.266 |
| `nextIdSequenceOverflow`（每次 5000 个） | 序列号耗尽 | 0.819 ops/ms | ±0.006 |
| `nextIdMultiWorker`（8 个独立节点） | 多 Worker | 27 393.270 ops/ms | ±2264.537 |

换算成每毫秒 ID 数：单线程 4081、8 线程争用 4093、批量 4087、耗尽场景 4095。四个场景都收敛在 4096/ms 附近，即 12 位序列号的理论上限。多 Worker 场景 8 个节点各发各的，总量 27 393 ops/ms。

**延迟百分位（ms/op）**

| 基准 | p50 | p90 | p99 | p99.99 | p100 |
|---|---|---|---|---|---|
| `nextIdSingleThread` | ≈1e-4 | ≈1e-4 | ≈1e-4 | 0.805 | 1.804 |
| `nextIdMultiThread` | ≈1e-4 | 0.001 | 0.159 | 1.609 | 5.890 |
| `nextIdBatch100` | 0.002 | 0.002 | 0.906 | 1.037 | 16.482 |
| `nextIdMultiWorker` | ≈1e-4 | ≈1e-3 | 0.001 | 2.182 | 86.770 |
| `nextIdSequenceOverflow` | 1.023 | 1.917 | 1.917 | 8.110 | 8.110 |

p99.99 一带的 ~1ms 尖刺对应「序列号用完、等到下一毫秒」的等待，与 12 位序列号上限一致。`nextIdSequenceOverflow` 每次调用生成 5000 个 ID，必然跨毫秒，因此整体落在毫秒量级。

---

## 七、剩余风险

1. **FIXED 模式依赖人工保证 workerId 唯一。**当前默认 `worker-id-mode=FIXED`，`worker-id=0`。多节点部署时若两个节点配了同一个 workerId，会产生重复主键。上线多节点前需改为 `LEASE`，或为每个实例显式配置不同的 `SNOWFLAKE_WORKER_ID`。
2. **LEASE 模式未做生产压测。**`JdbcWorkerIdLease` 的正确性有单元测试覆盖（内存桩），但没有在真实 MySQL 多实例并发下验证过租约竞争与续约。续约间隔 10s、TTL 30s 这组参数是默认值，未针对具体网络抖动调优。
3. **时钟回拨容忍窗口是配置值，不是兜底方案。**`clock-rollback-tolerance-ms=1000`，即回拨 1 秒内等待追平、超过 1 秒抛 `CLOCK_MOVED_BACKWARDS`。如果宿主 NTP 发生大步长校正，发号会中断。生产环境建议配 NTP 单调时钟或 chrony 的平滑校正。
4. **`sys_sequence` 只保证唯一与单调，允许空洞。**这是有意为之（避免回退带来的锁竞争），但如果把 `sort_seq` 当成「连续编号」展示给用户，会出现跳号。
5. **集成测试依赖外部 MySQL 实例。**Testcontainers 与本机 Docker 29.8.2 不兼容（ping 返回 400），已改为接独立实例，通过 `-Dit.db.url` 指定。CI 环境需要自行提供同版本 MySQL，否则这 16 项会失败。
6. **排序索引依赖 `create_time`。**新增的 `idx_*_created` 是针对 `ORDER BY create_time DESC, id DESC` 建的，若后续业务改用其他排序字段，需要同步补索引。
7. **前端未适配 `bigint`。**ID 从 32 位自增变成 64 位 Snowflake 后，若前端用 JavaScript `Number` 接收，超过 `2^53` 会丢精度。本次生成的 ID 在 1.03e17 量级，已超过 `Number.MAX_SAFE_INTEGER`（9.007e15），前端需要改成字符串接收。

---

## 八、五项风险的修复（第二轮）

需求评审列出的 7 项风险里，第 1、2 项（前端丢精度、多节点部署前需改 LEASE）是上线前的人工决策项，不在本次代码修复范围。其余 5 项已用代码 + 测试闭环修复。

### 8.1 时钟回拨兜底策略与水位持久化（原风险 3）

原先回拨处理只是「容忍窗口内等待、超窗抛异常」。现在：

- 新增 `ClockRollbackStrategy` 三态：`WAIT_THEN_THROW`（默认）/ `WAIT_FOREVER` / `THROW`，由 `scaffold.id.clock-rollback-strategy` 配置。
- 新增 `TimestampBookkeeper` 水位记账接口 + `InMemoryTimestampBookkeeper`（测试用）+ `JdbcTimestampBookkeeper`（持久化到 `sys_id_watermark`）。
  发号器启动时即读取持久水位，因此**跨进程重启的时钟回拨**也能被检测到（内存水位随进程消失，这是原先唯一的检测盲区）。
- 后台快照调度器 `SnowflakeWatermarkSnapshotter` 按 `watermark-snapshot-interval-ms`（默认 5s）把水位写入库；只在水位前进时写、空闲不写，发号主路径零开销。
- 新增 `SnowflakeIdHealthIndicator`（Actuator），暴露 `clockLagMs` / `lastRollbackMs` / `workerValid` / `rollbackStrategy`，使时钟回拨与租约丢失能被监控发现。实测 `/actuator/health` 返回 `{"status":"UP"}`，`sys_id_watermark` 出现 `worker-0` 行。

测试：`ClockRollbackTest` 11 项，覆盖三态行为、持久水位检出重启回拨、无水位时重启回拨不可检（证明该配置确实起作用）、水位单调、快照节流、健康状态。

### 8.2 LEASE 模式真实 MySQL 并发压测与参数校验（原风险 1）

原先只有内存桩验证。现在 `WorkerIdLeaseIntegrationTest` 11 项跑在真实 MySQL 上：

- 16 个节点用同一时刻并发申请，**结果拿到 0..15 互不重复**（靠 `worker_id` 主键 + `node_key` 唯一键做物理冲突约束）。
- 同一 nodeKey 重启复用同一 workerId；续约成功；租约被他人接管后原持有者续约失败；过期租约被回收后 workerId 复用；release 后立即可复用；耗尽抛 `WORKER_ID_UNAVAILABLE`；空 nodeKey 拒绝。
- 新增时序参数校验：续约间隔必须 `<= TTL/2`，否则启动即失败——单次续约失败不会立刻丢租约。自动装配 `SnowflakeAutoConfiguration` 里加了 `assertLeaseTiming`。

### 8.3 sort_seq 空洞可观测 + compact（原风险 4）

- `verify` 新增空洞统计：行数 / 区间 / 跨度 / 空洞数 / 最大单段空洞，只记录不判失败，让跳号「设计使然」可被观测。
- 新增 `compact` 动作（同样受 profile + confirm + 库名三道护栏）：按现有顺序把 `sort_seq` 重排为 1..N 并同步重置 `sys_sequence` 高水位。实测 45 条重排为 1..45，空洞归零。
- 文档明确：序号允许空洞是刻意的取舍，只有业务确实要连续编号才执行 compact。

### 8.4 集成测试脱离固定外部 MySQL（原风险 5）

- `IdIntegrationTest` 与新增 `WorkerIdLeaseIntegrationTest` 加 `@EnabledIf("databaseReachable")`：实例不可达时**整类跳过**，而不是判失败（缺基础设施不是代码缺陷，但也不能静悄悄放行，跳过原因打印到标准输出）。
- 探测逻辑 `ItDatabase.isReachable()` 仅建连即关，3s 超时，不影响构建速度。
- 新增 `docker/docker-compose.test.yml`：只起一个 MySQL（端口 13307，独立卷），不依赖 Nacos/Redis/网关。
- 新增 `.github/workflows/backend-test.yml`：用 GitHub Actions service container 起 MySQL，跑 `mvn -B verify -Dit.db.url=...`，CI 自给自足。

### 8.5 排序索引与查询字段对齐并纳入校验（原风险 6）

核查实际排序字段后发现：角色/权限/权限组的列表**按 `sort ASC, id ASC` 排序**，但之前只建了 `idx_*_created`。已补：

- `sys_role.idx_role_sort`、`sys_permission.idx_perm_sort`、`sys_permission_group.idx_group_sort`（`sort, id`）
- `sys_permission.idx_perm_type_status`（`type, status, deleted`，覆盖菜单查询）
- `user_device` 全局列表排序已由 `idx_device_user_login (user_id, last_login_time, sort_seq)` 覆盖（设备查询恒带 user_id 过滤）

`verify` 新增 `checkExpectedIndexes`：断言每张表的索引存在且列顺序与查询一致，后续排序字段改了而索引没跟上会直接判失败。

### 8.6 构建稳定性（顺带修复）

外置卷（exFAT）的 `._*` AppleDouble 副本此前会触发 surefire "Unable to create test class" 中断构建。在父 pom 的 `maven-surefire-plugin` 配置里加了 `<excludes><exclude>**/._*</exclude></excludes>`（并显式声明 `maven-surefire-plugin.version`），从根上消除该问题，不再需要每次手工 `find -delete`。

---

## 九、修复后测试总览

| 测试类 | 项数 | 范围 |
|---|---|---|
| `SnowflakeIdGeneratorTest` | 17 | 发号核心：位结构、单调、批量、序列号耗尽、时间戳溢出、workerId 越界、校验 |
| `LeasedWorkerIdProviderTest` | 5 | 租约获取/续约/失效/重启复用（内存桩） |
| `ClockRollbackTest` | 11 | 时钟回拨三态 + 持久水位 + 健康检查 + 快照节流 |
| `IdIntegrationTest` | 16 | 独立 MySQL：主键策略、CRUD、主外键、排序、序号分配、重建（实例不可达则跳过） |
| `WorkerIdLeaseIntegrationTest` | 11 | 真实 MySQL：并发争抢、续约、接管、过期回收、耗尽、时序参数（实例不可达则跳过） |

**合计 60 项，全部通过。**

执行数据（同一 MySQL 实例）：

```
rebuild : user 23 | sys_role 3 | sys_permission 52 | sys_user_role 43 | sys_role_permission 82
         | sys_permission_group 2 | sys_permission_group_item 12 | sys_role_permission_group 3 | user_device 45
verify  : 全部校验通过（含索引对齐、sort_seq 空洞统计）
compact : 45 条 sort_seq 重排为 1..45，空洞归零
health  : /actuator/health -> {"status":"UP"}；sys_id_watermark 写入 worker-0 行
```

