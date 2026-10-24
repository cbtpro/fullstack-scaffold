package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录日志实体。对应 {@code sys_login_log} 表。
 *
 * <p>审计记录只追加不修改，因此继承仅含主键与创建时间的 {@link BaseEntity}，
 * 登录时间即 {@code createTime}，不再冗余一个时间列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_login_log")
public class LoginLogRecord extends BaseEntity {

    /** 登录账号（登录失败时也记录，便于排查撞库） */
    private String username;

    /** 事件类型：LOGIN 登录 / LOGOUT 登出 */
    private String eventType;

    /** 是否成功：true 成功 */
    private Boolean success;

    /** 失败原因或提示信息 */
    private String message;

    /** 客户端 IP */
    private String clientIp;

    /** User-Agent 原始值 */
    private String userAgent;
}
