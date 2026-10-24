-- 字典类型表：一类可枚举业务取值。逻辑删除后编码不可再被占用（唯一索引在 code 上），
-- 需要「删除后复用同一编码」时请改用启用/禁用（status）而非删除。
CREATE TABLE IF NOT EXISTS `sys_dict_type` (
    `id`          BIGINT      NOT NULL COMMENT '主键（Snowflake）',
    `code`        VARCHAR(64) NOT NULL COMMENT '字典类型编码',
    `name`        VARCHAR(64) NOT NULL COMMENT '显示名',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序，升序',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '启用状态：1启用 0禁用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type_code` (`code`),
    KEY `idx_dict_type_sort` (`sort`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

-- 字典项表：通过 type_id 归属类型；同一类型内取值唯一
CREATE TABLE IF NOT EXISTS `sys_dict_item` (
    `id`          BIGINT       NOT NULL COMMENT '主键（Snowflake）',
    `type_id`     BIGINT       NOT NULL COMMENT '所属字典类型 id',
    `label`       VARCHAR(64)  NOT NULL COMMENT '显示文本',
    `value`       VARCHAR(128) NOT NULL COMMENT '取值',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序，升序',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态：1启用 0禁用',
    `create_time` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3)  DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_item_type_value` (`type_id`, `value`),
    KEY `idx_dict_item_type_sort` (`type_id`, `sort`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项表';
