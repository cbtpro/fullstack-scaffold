package com.scaffold.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应包装。
 *
 * @param <T> 业务数据类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务码，成功为 0 */
    private long code;
    /** 提示信息 */
    private String message;
    /** 业务数据 */
    private T data;
    /** 服务端时间戳（毫秒） */
    private long timestamp = System.currentTimeMillis();

    private Result() {
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return success(data, ResultCode.SUCCESS.getMessage());
    }

    public static <T> Result<T> success(T data, String message) {
        Result<T> r = new Result<>();
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMessage());
    }

    public static <T> Result<T> fail(long code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        Result<T> r = new Result<>();
        r.setCode(resultCode.getCode());
        r.setMessage(message);
        return r;
    }
}
