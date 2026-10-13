package com.scaffold.user.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

import java.util.Arrays;

/**
 * 逻辑删除标记：数据库 {@code deleted} 列（TINYINT）的枚举映射。
 *
 * <p>与全局逻辑删除配置 {@code logic-delete-value=1 / logic-not-delete-value=0}
 * 的字面量保持一致，新增记录由 {@code MetaObjectHandler} 填充为 {@link #NOT_DELETED}。
 */
public enum DeleteFlag {

    /** 未删除 */
    NOT_DELETED(0),
    /** 已删除 */
    DELETED(1);

    @EnumValue
    private final Integer code;

    DeleteFlag(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public static DeleteFlag fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}
