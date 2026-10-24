package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 权限组-权限关联实体（{@code sys_permission_group_item}）。
 * 主键使用统一 Snowflake 发号器；业务唯一性由 {@code uk_group_perm} 保证。
 */
@Data
@TableName("sys_permission_group_item")
public class PermissionGroupItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long groupId;

    private Long permissionId;

    public PermissionGroupItem() {
    }

    public PermissionGroupItem(Long id, Long groupId, Long permissionId) {
        this.id = id;
        this.groupId = groupId;
        this.permissionId = permissionId;
    }
}
