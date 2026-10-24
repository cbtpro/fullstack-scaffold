package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志实体。对应 {@code sys_operation_log} 表。
 *
 * <p>审计记录只追加不修改，因此继承仅含主键与创建时间的 {@link BaseEntity}，
 * 不带逻辑删除与启用状态：日志一旦写入即不可掩盖，删除需 DBA 显式物理操作。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_operation_log")
public class OperationLogRecord extends BaseEntity {

    /** 业务模块 */
    private String module;

    /** 操作类型 */
    private String action;

    /** 操作说明 */
    private String description;

    /** HTTP 方法 */
    private String httpMethod;

    /** 请求路径 */
    private String uri;

    /** 请求参数（已脱敏并截断） */
    private String params;

    /** 是否成功：true 成功 */
    private Boolean success;

    /** 失败原因 */
    private String errorMessage;

    /** 操作人 id */
    private Long operatorId;

    /** 操作人用户名 */
    private String operatorName;

    /** 客户端 IP */
    private String clientIp;

    /** 耗时（毫秒） */
    private Long durationMs;
}
