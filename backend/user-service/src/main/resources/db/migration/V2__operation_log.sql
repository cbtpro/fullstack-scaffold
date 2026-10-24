-- 操作日志表：审计记录只追加不修改，故不含 update_time / deleted / status
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
    `id`            BIGINT        NOT NULL COMMENT '主键（Snowflake）',
    `module`        VARCHAR(64)   NOT NULL COMMENT '业务模块',
    `action`        VARCHAR(64)   NOT NULL COMMENT '操作类型',
    `description`   VARCHAR(255)  DEFAULT NULL COMMENT '操作说明',
    `http_method`   VARCHAR(16)   DEFAULT NULL COMMENT 'HTTP 方法',
    `uri`           VARCHAR(255)  DEFAULT NULL COMMENT '请求路径',
    `params`        VARCHAR(2000) DEFAULT NULL COMMENT '请求参数（已脱敏并截断）',
    `success`       TINYINT       NOT NULL DEFAULT 1 COMMENT '是否成功：1成功 0失败',
    `error_message` VARCHAR(500)  DEFAULT NULL COMMENT '失败原因',
    `operator_id`   BIGINT        DEFAULT NULL COMMENT '操作人 id',
    `operator_name` VARCHAR(64)   DEFAULT NULL COMMENT '操作人用户名',
    `client_ip`     VARCHAR(64)   DEFAULT NULL COMMENT '客户端 IP',
    `duration_ms`   BIGINT        DEFAULT NULL COMMENT '耗时（毫秒）',
    `create_time`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_oplog_created` (`create_time`, `id`),
    KEY `idx_oplog_module_created` (`module`, `create_time`, `id`),
    KEY `idx_oplog_operator_created` (`operator_id`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
