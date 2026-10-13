package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体。对应 {@code sys_role} 表。
 * 公共字段（id/status/createTime/updateTime/deleted）见 {@link BaseLogicEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class Role extends BaseLogicEntity {

    /** 角色编码，业务侧引用此值（如 ADMIN） */
    private String code;

    private String name;

    private Integer sort;

    private String description;
}
