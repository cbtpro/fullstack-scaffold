package com.scaffold.user.exception;

import com.scaffold.common.api.Result;
import com.scaffold.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：业务异常转 {@link Result} 结构（HTTP 200 + 业务码），
 * 前端按 code 区分错误类型（如验证码错误、密码错误），与成功响应同一解析路径。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未处理异常", e);
        return Result.fail(50000L, "服务内部错误");
    }
}
