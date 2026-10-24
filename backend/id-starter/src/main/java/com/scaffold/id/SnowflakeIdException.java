package com.scaffold.id;

/**
 * Snowflake 发号异常。携带 {@link SnowflakeIdErrorCode} 便于调用方分类处理。
 */
public class SnowflakeIdException extends RuntimeException {

    private final SnowflakeIdErrorCode errorCode;

    public SnowflakeIdException(SnowflakeIdErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SnowflakeIdException(SnowflakeIdErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public SnowflakeIdErrorCode getErrorCode() {
        return errorCode;
    }
}
