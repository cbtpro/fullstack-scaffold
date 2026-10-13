package com.scaffold.user.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 启用状态：数据库 {@code status} 列（TINYINT）的枚举映射。
 *
 * <p>{@link EnumValue} 让 MyBatis-Plus 负责「枚举 ↔ DB 整型」转换；
 * {@link JsonValue}/{@link JsonCreator} 保持对外 JSON 契约为 {@code 1/0}，
 * 前端无需感知枚举名。
 */
public enum EnableStatus {

    /** 启用 */
    ENABLED(1),
    /** 禁用 */
    DISABLED(0);

    @EnumValue
    @JsonValue
    private final Integer code;

    EnableStatus(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public boolean isEnabled() {
        return this == ENABLED;
    }

    /**
     * 由数据库/请求中的整型值还原枚举，{@code null} 或非法值返回 {@code null}，
     * 避免在部分更新（未传 status）场景抛异常。
     */
    @JsonCreator
    public static EnableStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
