-- fullstack-scaffold 数据库初始化脚本
-- 仅在 MySQL 容器首次启动（数据卷为空）时由 docker-entrypoint-initdb.d 执行一次。
--
-- 职责边界：本脚本只负责建库；表结构自 V1 基线起由 user-service 内 Flyway
-- 统一迁移管理（backend/user-service/src/main/resources/db/migration），
-- 应用启动时自动建表/升级，新增表结构请追加 V2+ 脚本，勿在此处建表。

CREATE DATABASE IF NOT EXISTS `fs_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
