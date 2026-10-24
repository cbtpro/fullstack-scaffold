-- 登录日志表：审计记录只追加不修改，登录时间即 create_time
CREATE TABLE IF NOT EXISTS `sys_login_log` (
    `id`         BIGINT       NOT NULL COMMENT '主键（Snowflake）',
    `username`   VARCHAR(64)  NOT NULL COMMENT '登录账号（失败时也记录）',
    `event_type` VARCHAR(16)  NOT NULL DEFAULT 'LOGIN' COMMENT '事件类型：LOGIN 登录 / LOGOUT 登出',
    `success`    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：1成功 0失败',
    `message`    VARCHAR(255) DEFAULT NULL COMMENT '失败原因或提示信息',
    `client_ip`  VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    `user_agent` VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（即登录时间）',
    PRIMARY KEY (`id`),
    KEY `idx_loginlog_created` (`create_time`, `id`),
    KEY `idx_loginlog_username_created` (`username`, `create_time`, `id`),
    KEY `idx_loginlog_ip_created` (`client_ip`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';
