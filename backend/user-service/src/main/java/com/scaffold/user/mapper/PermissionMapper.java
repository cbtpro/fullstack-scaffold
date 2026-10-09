package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限 Mapper。{@link BaseMapper} 提供 CRUD，自定义 SQL 通过 user-role-permission 三表关联
 * 查询用户的全部启用权限。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    /**
     * 按 user id 查询其全部启用权限（去重）。
     * 通过 sys_user_role → sys_role_permission → sys_permission 三表关联。
     */
    @Select("""
            SELECT DISTINCT p.* FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
              AND p.status = 1
              AND p.deleted = 0
            ORDER BY p.sort ASC, p.id ASC
            """)
    List<Permission> selectByUserId(@Param("userId") Long userId);

    /**
     * 按 user id 查询其全部启用菜单（type=1），用于侧边栏渲染。
     */
    @Select("""
            SELECT DISTINCT p.* FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
              AND p.type = 1
              AND p.status = 1
              AND p.deleted = 0
            ORDER BY p.sort ASC, p.id ASC
            """)
    List<Permission> selectMenusByUserId(@Param("userId") Long userId);
}
