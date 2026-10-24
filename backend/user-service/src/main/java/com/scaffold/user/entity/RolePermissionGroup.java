package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色-权限组关联实体（{@code sys_role_permission_group}）。
 * 主键使用统一 Snowflake 发号器；业务唯一性由 {@code uk_role_group} 保证。
 */
@Data
@TableName("sys_role_permission_group")
public class RolePermissionGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long roleId;

    private Long groupId;

    public RolePermissionGroup() {
    }

    public RolePermissionGroup(Long id, Long roleId, Long groupId) {
        this.id = id;
        this.roleId = roleId;
        this.groupId = groupId;
    }
}
