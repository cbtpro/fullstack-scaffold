-- fullstack-scaffold 初始化脚本
-- 建库 fs_user 与示例 user 表

CREATE DATABASE IF NOT EXISTS `fs_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fs_user`;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码（加密存储）',
    `nickname`    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone`       VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入示例数据（密码 = BCrypt("123456")，可用 BCryptPasswordEncoder.matches 验证）
INSERT INTO `user` (`username`, `password`, `nickname`, `email`, `status`)
VALUES ('admin', '$2a$10$kFQj2cTQ4Cx8PgguNyqcfO3zfFQuEzf5im9Jov4XgGfU0RAT0X1wq', '管理员', 'admin@fullstack-scaffold.local', 1),
       ('guest', '$2a$10$kFQj2cTQ4Cx8PgguNyqcfO3zfFQuEzf5im9Jov4XgGfU0RAT0X1wq', '访客', 'guest@fullstack-scaffold.local', 1);

-- ============================================================================
-- RBAC 权限模型：角色 + 权限（菜单/按钮/接口）+ 用户-角色 + 角色-权限 关联
-- ============================================================================

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(64)  NOT NULL COMMENT '角色编码（业务唯一）',
    `name`        VARCHAR(64)  NOT NULL COMMENT '显示名',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 权限表：菜单(type=1) + 按钮/操作(type=2) + 接口(type=3) 三类混合
CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_id`  BIGINT       NOT NULL DEFAULT 0 COMMENT '父节点 id，0 为根',
    `code`       VARCHAR(128) NOT NULL COMMENT '权限编码：menu:user / user:create / api:user:list',
    `name`       VARCHAR(64)  NOT NULL COMMENT '显示名',
    `type`       TINYINT      NOT NULL COMMENT '1菜单 2按钮 3接口',
    `path`       VARCHAR(255) DEFAULT NULL COMMENT '前端路由 path（菜单用）',
    `component`  VARCHAR(255) DEFAULT NULL COMMENT '前端组件相对路径（菜单用）',
    `icon`       VARCHAR(64)  DEFAULT NULL COMMENT '图标名（菜单用）',
    `sort`       INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 用户-角色关联
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户 id',
    `role_id` BIGINT NOT NULL COMMENT '角色 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联';

-- 角色-权限关联
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT NOT NULL COMMENT '角色 id',
    `permission_id` BIGINT NOT NULL COMMENT '权限 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联';

-- 角色：ADMIN（全部权限）+ USER（仅查看）
INSERT INTO `sys_role` (`code`, `name`, `description`, `sort`) VALUES
    ('ADMIN', '管理员',   '系统超级管理员，拥有全部权限', 1),
    ('USER',  '普通用户', '仅查看权限',                2);

-- 权限：菜单 + 按钮 + 接口（编码与前端按钮指令、后端 @RequirePermission 一一对应）
--   菜单 menu:user（type=1） → 渲染侧边栏菜单项
--   按钮 user:view/create/update/delete（type=2） → 控制按钮可见性
--   接口 api:user:*（type=3） → 后端 @RequirePermission 校验
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    (0, 'menu:user',       '用户管理', 1, '/user', 'user-list/index', 'team',  10),
    (1, 'user:view',        '查看',     2, NULL,    NULL,              NULL,     1),
    (1, 'user:create',      '新增',     2, NULL,    NULL,              NULL,     2),
    (1, 'user:update',      '编辑',     2, NULL,    NULL,              NULL,     3),
    (1, 'user:delete',      '删除',     2, NULL,    NULL,              NULL,     4),
    (0, 'api:user:list',    '用户列表接口', 3, NULL, NULL,              NULL,     1),
    (0, 'api:user:create',  '用户创建接口', 3, NULL, NULL,              NULL,     2),
    (0, 'api:user:update',  '用户更新接口', 3, NULL, NULL,              NULL,     3),
    (0, 'api:user:delete',  '用户删除接口', 3, NULL, NULL,              NULL,     4);

-- ADMIN 角色绑定全部权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p WHERE r.`code` = 'ADMIN';

-- USER 角色仅绑定 user:view 与 api:user:list
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE r.`code` = 'USER' AND p.`code` IN ('user:view', 'api:user:list');

-- admin 用户绑定 ADMIN 角色
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `user` u CROSS JOIN `sys_role` r WHERE u.`username` = 'admin' AND r.`code` = 'ADMIN';

-- guest 用户绑定 USER 角色
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `user` u CROSS JOIN `sys_role` r WHERE u.`username` = 'guest' AND r.`code` = 'USER';

-- ============================================================================
-- 登录设备表：token 与设备指纹绑定（登录时登记，请求时比对 X-Device-Fp）
-- 后续"可信登录列表"按 risk_level / 新表白名单在 TrustedDevicePolicy 处扩展
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_device` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT      NOT NULL COMMENT '用户 id',
    `device_id`       VARCHAR(64) NOT NULL COMMENT '设备标识（UUID），token claims 携带',
    `fingerprint`     CHAR(64)    NOT NULL COMMENT '设备指纹（sha256 hex）',
    `ip`              VARCHAR(64) DEFAULT NULL COMMENT '最近登录 IP',
    `risk_level`      VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '风险等级：TRUSTED/NORMAL/RISKY/REVOKED',
    `last_login_time` DATETIME    DEFAULT NULL COMMENT '最近登录时间',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_id` (`device_id`),
    UNIQUE KEY `uk_user_fp` (`user_id`, `fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录设备表';

-- ============================================================================
-- 登录设备管理权限：菜单 + 按钮 + 接口（自服务，ADMIN/USER 均绑定全部）
-- ============================================================================
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    (0, 'menu:device', '登录设备', 1, '/device', 'device-list/index', 'smartphone', 20);
SET @deviceMenuId = LAST_INSERT_ID();
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    (@deviceMenuId, 'device:view',   '查看',     2, NULL, NULL, NULL, 1),
    (@deviceMenuId, 'device:trust',   '设为可信', 2, NULL, NULL, NULL, 2),
    (@deviceMenuId, 'device:revoke',  '吊销',     2, NULL, NULL, NULL, 3),
    (@deviceMenuId, 'device:remove',  '移除',     2, NULL, NULL, NULL, 4),
    (0, 'api:device:list',   '设备列表接口',     3, NULL, NULL, NULL, 1),
    (0, 'api:device:trust',  '设备设为可信接口', 3, NULL, NULL, NULL, 2),
    (0, 'api:device:revoke', '设备吊销接口',     3, NULL, NULL, NULL, 3),
    (0, 'api:device:remove', '设备移除接口',     3, NULL, NULL, NULL, 4);

-- ADMIN 绑定全部设备权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE r.`code` = 'ADMIN' AND (p.`code` = 'menu:device' OR p.`code` LIKE 'device:%' OR p.`code` LIKE 'api:device:%');

-- USER 绑定全部设备权限（自服务：管理自己的设备）
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE r.`code` = 'USER' AND (p.`code` = 'menu:device' OR p.`code` LIKE 'device:%' OR p.`code` LIKE 'api:device:%');

-- ============================================================================
-- 角色管理权限（仅 ADMIN 拥有全部，USER 仅查看）
-- ============================================================================
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    (0, 'menu:role', '角色管理', 1, '/role', 'role-list/index', 'shield', 30);
SET @roleMenuId = LAST_INSERT_ID();
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    (@roleMenuId, 'role:view',      '查看',   2, NULL, NULL, NULL, 1),
    (@roleMenuId, 'role:create',    '新增',   2, NULL, NULL, NULL, 2),
    (@roleMenuId, 'role:update',    '编辑',   2, NULL, NULL, NULL, 3),
    (@roleMenuId, 'role:delete',    '删除',   2, NULL, NULL, NULL, 4),
    (@roleMenuId, 'role:assign',    '分配权限', 2, NULL, NULL, NULL, 5),
    (0, 'api:role:list',   '角色列表接口',     3, NULL, NULL, NULL, 1),
    (0, 'api:role:create', '角色创建接口',     3, NULL, NULL, NULL, 2),
    (0, 'api:role:update', '角色更新接口',     3, NULL, NULL, NULL, 3),
    (0, 'api:role:delete', '角色删除接口',     3, NULL, NULL, NULL, 4),
    (0, 'api:role:assign', '角色分配权限接口', 3, NULL, NULL, NULL, 5);

-- ADMIN 绑定全部角色权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE r.`code` = 'ADMIN' AND (p.`code` = 'menu:role' OR p.`code` LIKE 'role:%' OR p.`code` LIKE 'api:role:%');

-- USER 仅绑定角色查看
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE r.`code` = 'USER' AND p.`code` IN ('menu:role', 'role:view', 'api:role:list');

-- ============================================================================
-- 权限管理权限（仅 ADMIN 拥有全部）
-- ============================================================================
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    (0, 'menu:permission', '权限管理', 1, '/permission', 'permission-list/index', 'key', 40);
SET @permMenuId = LAST_INSERT_ID();
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    (@permMenuId, 'permission:view',   '查看', 2, NULL, NULL, NULL, 1),
    (@permMenuId, 'permission:create', '新增', 2, NULL, NULL, NULL, 2),
    (@permMenuId, 'permission:update', '编辑', 2, NULL, NULL, NULL, 3),
    (@permMenuId, 'permission:delete', '删除', 2, NULL, NULL, NULL, 4),
    (0, 'api:permission:list',   '权限列表接口', 3, NULL, NULL, NULL, 1),
    (0, 'api:permission:create', '权限创建接口', 3, NULL, NULL, NULL, 2),
    (0, 'api:permission:update', '权限更新接口', 3, NULL, NULL, NULL, 3),
    (0, 'api:permission:delete', '权限删除接口', 3, NULL, NULL, NULL, 4);

-- ADMIN 绑定全部权限管理权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE r.`code` = 'ADMIN' AND (p.`code` = 'menu:permission' OR p.`code` LIKE 'permission:%' OR p.`code` LIKE 'api:permission:%');

-- USER 绑定 user:assign-role 权限（用户管理-分配角色）
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    ((SELECT id FROM sys_permission WHERE code = 'menu:user' LIMIT 1), 'user:assign-role', '分配角色', 2, NULL, NULL, NULL, 5);
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE r.`code` = 'ADMIN' AND p.`code` = 'user:assign-role';

-- ============================================================================
-- 权限组：将一组权限打包成可复用单元，角色动态绑定后自动同步组内权限
-- 角色 → sys_role_permission_group → sys_permission_group_item → sys_permission
-- 解析时与 sys_role_permission 取并集去重（见 PermissionMapper.selectByUserId）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_permission_group` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(64)  NOT NULL COMMENT '权限组编码（业务唯一）',
    `name`        VARCHAR(64)  NOT NULL COMMENT '显示名',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限组表';

-- 权限组-权限关联
CREATE TABLE IF NOT EXISTS `sys_permission_group_item` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_id`      BIGINT NOT NULL COMMENT '权限组 id',
    `permission_id` BIGINT NOT NULL COMMENT '权限 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_perm` (`group_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限组-权限关联';

-- 角色-权限组关联（动态绑定：角色关联组后，解析时 UNION 组内权限）
CREATE TABLE IF NOT EXISTS `sys_role_permission_group` (
    `id`       BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`  BIGINT NOT NULL COMMENT '角色 id',
    `group_id` BIGINT NOT NULL COMMENT '权限组 id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_group` (`role_id`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限组关联';

-- ============================================================================
-- 权限组管理权限（仅 ADMIN 拥有全部）
-- ============================================================================
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    (0, 'menu:permission-group', '权限组管理', 1, '/permission-group', 'permission-group-list/index', 'layers', 50);
SET @groupMenuId = LAST_INSERT_ID();
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    (@groupMenuId, 'permission-group:view',   '查看',     2, NULL, NULL, NULL, 1),
    (@groupMenuId, 'permission-group:create', '新增',     2, NULL, NULL, NULL, 2),
    (@groupMenuId, 'permission-group:update', '编辑',     2, NULL, NULL, NULL, 3),
    (@groupMenuId, 'permission-group:delete', '删除',     2, NULL, NULL, NULL, 4),
    (@groupMenuId, 'permission-group:assign',  '分配权限', 2, NULL, NULL, NULL, 5),
    (0, 'api:permission-group:list',   '权限组列表接口',     3, NULL, NULL, NULL, 1),
    (0, 'api:permission-group:create', '权限组创建接口',     3, NULL, NULL, NULL, 2),
    (0, 'api:permission-group:update', '权限组更新接口',     3, NULL, NULL, NULL, 3),
    (0, 'api:permission-group:delete', '权限组删除接口',     3, NULL, NULL, NULL, 4),
    (0, 'api:permission-group:assign', '权限组分配权限接口', 3, NULL, NULL, NULL, 5);

-- ADMIN 绑定全部权限组管理权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE r.`code` = 'ADMIN' AND (p.`code` = 'menu:permission-group' OR p.`code` LIKE 'permission-group:%' OR p.`code` LIKE 'api:permission-group:%');

-- 角色分配权限组按钮（挂在 menu:role 下）+ 接口权限
-- 注意：MySQL 不允许 INSERT INTO sys_permission 的 VALUES 子查询引用 sys_permission 自身，须先用变量暂存
SELECT id INTO @roleMenuIdForGroup FROM sys_permission WHERE code = 'menu:role' LIMIT 1;
INSERT INTO `sys_permission` (`parent_id`, `code`, `name`, `type`, `path`, `component`, `icon`, `sort`) VALUES
    (@roleMenuIdForGroup, 'role:assign-group', '分配权限组', 2, NULL, NULL, NULL, 6),
    (0, 'api:role:assign-group', '角色分配权限组接口', 3, NULL, NULL, NULL, 6);
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE r.`code` = 'ADMIN' AND p.`code` IN ('role:assign-group', 'api:role:assign-group');
