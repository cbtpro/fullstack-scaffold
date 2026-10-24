package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户-角色关联实体（{@code sys_user_role}）。
 *
 * <p>纯关联表，主键同样使用统一 Snowflake 发号器，不依赖数据库自增。
 * 业务唯一性由 {@code uk_user_role(user_id, role_id)} 保证。
 */
@Data
@TableName("sys_user_role")
public class UserRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long roleId;

    public UserRole() {
    }

    public UserRole(Long id, Long userId, Long roleId) {
        this.id = id;
        this.userId = userId;
        this.roleId = roleId;
    }
}
