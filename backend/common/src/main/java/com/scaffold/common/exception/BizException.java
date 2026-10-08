package com.scaffold.common.exception;

import com.scaffold.common.api.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。Controller 层统一捕获后转为 {@link com.scaffold.common.api.Result}。
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(long code, String message) {
        super(message);
        this.code = code;
    }
}
