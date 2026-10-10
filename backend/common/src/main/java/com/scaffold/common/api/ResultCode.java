package com.scaffold.common.api;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 业务码常量。
 */
public enum ResultCode {

    /** 成功 */
    SUCCESS(0L, "操作成功"),
    /** 失败 */
    FAIL(1L, "操作失败"),

    /** 参数非法 */
    PARAM_INVALID(40000L, "参数非法"),
    /** 未登录 */
    UNAUTHORIZED(40001L, "未登录或登录已过期"),
    /** 无权限 */
    FORBIDDEN(40003L, "无访问权限"),
    /** 资源不存在 */
    NOT_FOUND(40004L, "资源不存在"),
    /** 验证码错误或已过期 */
    CAPTCHA_INVALID(40005L, "验证码错误或已过期"),

    /** 服务端内部错误 */
    INTERNAL_ERROR(50000L, "服务内部错误"),
    /** 服务降级 */
    SERVICE_UNAVAILABLE(50003L, "服务暂不可用");

    private final long code;
    private final String message;

    ResultCode(long code, String message) {
        this.code = code;
        this.message = message;
    }

    public long getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
