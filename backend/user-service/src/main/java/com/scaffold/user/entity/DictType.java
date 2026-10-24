package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型实体。对应 {@code sys_dict_type} 表。
 * 一类可枚举的业务取值（如「用户状态」）由若干字典项组成。
 * 公共字段（id/status/createTime/updateTime/deleted）见 {@link BaseLogicEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class DictType extends BaseLogicEntity {

    /** 字典类型编码，业务侧引用此值 */
    private String code;

    /** 显示名 */
    private String name;

    /** 描述 */
    private String description;

    private Integer sort;
}
