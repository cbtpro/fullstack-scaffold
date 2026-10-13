package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限组实体。对应 {@code sys_permission_group} 表。
 * 权限组将一组权限打包成可复用单元，角色动态绑定后解析时 UNION 组内权限。
 * 公共字段（id/status/createTime/updateTime/deleted）见 {@link BaseLogicEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission_group")
public class PermissionGroup extends BaseLogicEntity {

    /** 权限组编码，业务侧引用此值 */
    private String code;

    private String name;

    private String description;

    private Integer sort;
}
