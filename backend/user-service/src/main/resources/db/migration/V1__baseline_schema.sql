-- ============================================================================
-- V1 基线：fullstack-scaffold 初始表结构（Snowflake 主键版）
--
-- 本脚本为历史 docker/mysql/init/init.sql 的等价基线，建库职责已交给容器
-- （MYSQL_DATABASE / init.sql 仅 CREATE DATABASE），此处只建表。
-- 主键统一 BIGINT，由应用侧 Snowflake 发号器分配，数据库不使用 AUTO_INCREMENT。
-- 表结构只建不填：测试数据由 TestDataRebuildRunner 生成。
--
-- 兼容性：存量库（由旧 init.sql 建表）首次接入 Flyway 时，应用在 v0 打基线后
-- 补跑本脚本，全部 CREATE TABLE IF NOT EXISTS 均为幂等空操作，不影响存量数据。
-- 后续表结构演进一律新增 V2+ 脚本，禁止修改本文件。
-- ============================================================================

-- ============================================================================
-- 用户表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL COMMENT '主键（Snowflake）',
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码（加密存储）',
    `nickname`    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone`       VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_user_created` (`create_time` DESC, `id` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================================
-- RBAC：角色 / 权限 / 权限组 + 关联表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          BIGINT       NOT NULL COMMENT '主键（Snowflake）',
    `code`        VARCHAR(64)  NOT NULL COMMENT '角色编码（业务唯一）',
    `name`        VARCHAR(64)  NOT NULL COMMENT '显示名',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `create_time` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`),
    -- 列表查询实际按 sort ASC, id ASC 排序（RoleServiceImpl），索引必须与之一致
    KEY `idx_role_sort` (`sort` ASC, `id` ASC),
    KEY `idx_role_created` (`create_time` DESC, `id` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id`          BIGINT       NOT NULL COMMENT '主键（Snowflake）',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父节点 id，0 为根',
    `code`        VARCHAR(128) NOT NULL COMMENT '权限编码：menu:user / user:create / api:user:list',
    `name`        VARCHAR(64)  NOT NULL COMMENT '显示名',
    `type`        TINYINT      NOT NULL COMMENT '1菜单 2按钮 3接口',
    `path`        VARCHAR(255) DEFAULT NULL COMMENT '前端路由 path（菜单用）',
    `component`   VARCHAR(255) DEFAULT NULL COMMENT '前端组件相对路径（菜单用）',
    `icon`        VARCHAR(64)  DEFAULT NULL COMMENT '图标名（菜单用）',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`code`),
    KEY `idx_perm_parent` (`parent_id`),
    -- 列表查询按 sort ASC, id ASC 排序（PermissionServiceImpl / PermissionMapper）
    KEY `idx_perm_sort` (`sort` ASC, `id` ASC),
    -- 菜单查询按 type=1 + status + deleted 过滤后取排序，覆盖 selectMenusByUserId
    KEY `idx_perm_type_status` (`type`, `status`, `deleted`),
    KEY `idx_perm_created` (`create_time` DESC, `id` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE IF NOT EXISTS `sys_permission_group` (
    `id`          BIGINT       NOT NULL COMMENT '主键（Snowflake）',
    `code`        VARCHAR(64)  NOT NULL COMMENT '权限组编码（业务唯一）',
    `name`        VARCHAR(64)  NOT NULL COMMENT '显示名',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_code` (`code`),
    -- 列表查询按 sort ASC, id ASC 排序（PermissionGroupServiceImpl）
    KEY `idx_group_sort` (`sort` ASC, `id` ASC),
    KEY `idx_group_created` (`create_time` DESC, `id` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限组表';

-- 用户-角色关联
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id`      BIGINT NOT NULL COMMENT '主键（Snowflake）',
    `user_id` BIGINT NOT NULL COMMENT '用户 id',
    `role_id` BIGINT NOT NULL COMMENT '角色 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_ur_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联';

-- 角色-权限关联
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id`            BIGINT NOT NULL COMMENT '主键（Snowflake）',
    `role_id`       BIGINT NOT NULL COMMENT '角色 id',
    `permission_id` BIGINT NOT NULL COMMENT '权限 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
    KEY `idx_rp_perm` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联';

-- 权限组-权限关联
CREATE TABLE IF NOT EXISTS `sys_permission_group_item` (
    `id`            BIGINT NOT NULL COMMENT '主键（Snowflake）',
    `group_id`      BIGINT NOT NULL COMMENT '权限组 id',
    `permission_id` BIGINT NOT NULL COMMENT '权限 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_perm` (`group_id`, `permission_id`),
    KEY `idx_pgi_perm` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限组-权限关联';

-- 角色-权限组关联
CREATE TABLE IF NOT EXISTS `sys_role_permission_group` (
    `id`       BIGINT NOT NULL COMMENT '主键（Snowflake）',
    `role_id`  BIGINT NOT NULL COMMENT '角色 id',
    `group_id` BIGINT NOT NULL COMMENT '权限组 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_group` (`role_id`, `group_id`),
    KEY `idx_rpg_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限组关联';

-- ============================================================================
-- 登录设备表：token 与设备指纹绑定
-- sort_seq 为全局严格递增序号（由 sys_sequence 分配），代表设备注册顺序
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_device` (
    `id`              BIGINT      NOT NULL COMMENT '主键（Snowflake）',
    `sort_seq`        BIGINT      NOT NULL COMMENT '全局严格递增序号（注册顺序）',
    `user_id`         BIGINT      NOT NULL COMMENT '用户 id',
    `device_id`       VARCHAR(64) NOT NULL COMMENT '设备标识（UUID），token claims 携带',
    `fingerprint`     CHAR(64)    NOT NULL COMMENT '设备指纹（sha256 hex）',
    `ip`              VARCHAR(64) DEFAULT NULL COMMENT '最近登录 IP',
    `risk_level`      VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '风险等级：TRUSTED/NORMAL/RISKY/REVOKED',
    `last_login_time` DATETIME(3) DEFAULT NULL COMMENT '最近登录时间',
    `create_time`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_id` (`device_id`),
    UNIQUE KEY `uk_user_fp` (`user_id`, `fingerprint`),
    UNIQUE KEY `uk_device_sort_seq` (`sort_seq`),
    KEY `idx_device_user_login` (`user_id`, `last_login_time` DESC, `sort_seq` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录设备表';

-- ============================================================================
-- 全局序号分配器：为需要严格排序的业务表分配单调递增序号
-- 重建测试数据时不重置此表，保证序号高水位不回退
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_sequence` (
    `biz_key`       VARCHAR(64) NOT NULL COMMENT '业务键，通常对应一张表',
    `current_value` BIGINT      NOT NULL COMMENT '当前已分配到的最大值',
    `update_time`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`biz_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局序号分配表';

-- ============================================================================
-- 时间戳水位表：scaffold.id.persist-timestamp-watermark=true 时使用
-- 记录每个节点已发号到的最大时间戳，用于检测跨进程重启的时钟回拨。
-- 内存水位随进程消失；没有这张表，节点重启后若时钟被回拨将检测不出来。
-- 写入走 GREATEST(last_timestamp, ?)，水位只前进不回退。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_id_watermark` (
    `node_key`       VARCHAR(128) NOT NULL COMMENT '节点标识：scaffold.id.node-key，缺省为 worker-<workerId>',
    `worker_id`      INT          NOT NULL COMMENT 'Worker ID',
    `last_timestamp` BIGINT       NOT NULL COMMENT '已发号到的最大时间戳（毫秒绝对值，非纪元偏移）',
    `update_time`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`node_key`),
    KEY `idx_wm_worker` (`worker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发号器时间戳水位表';

-- ============================================================================
-- Worker ID 租约表：scaffold.id.worker-id-mode=LEASE 时使用
-- 本表不由测试数据重建程序清理，避免重置节点租约与高水位
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_worker_node` (
    `worker_id`       INT          NOT NULL COMMENT 'Worker ID（0..1023）',
    `node_key`        VARCHAR(128) NOT NULL COMMENT '节点标识',
    `lease_expire_at` DATETIME(3)  NOT NULL COMMENT '租约到期时间',
    `heartbeat_at`    DATETIME(3)  NOT NULL COMMENT '最近心跳时间',
    PRIMARY KEY (`worker_id`),
    UNIQUE KEY `uk_node_key` (`node_key`),
    KEY `idx_wn_expire` (`lease_expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Worker ID 租约表';
