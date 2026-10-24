package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色-权限关联实体（{@code sys_role_permission}）。
 * 主键使用统一 Snowflake 发号器；业务唯一性由 {@code uk_role_perm} 保证。
 */
@Data
@TableName("sys_role_permission")
public class RolePermission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long roleId;

    private Long permissionId;

    public RolePermission() {
    }

    public RolePermission(Long id, Long roleId, Long permissionId) {
        this.id = id;
        this.roleId = roleId;
        this.permissionId = permissionId;
    }
}
