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
     * 两条路径取并集：
     * 1. 直接分配：sys_user_role → sys_role_permission → sys_permission
     * 2. 权限组动态绑定：sys_user_role → sys_role_permission_group → sys_permission_group_item → sys_permission
     *    （权限组须启用且未逻辑删除）
     * 两条路径都要求角色本身启用且未逻辑删除：停用角色与 {@code listRoleCodesByUserId} 的口径必须一致，
     * 否则界面停用了角色，权限却仍在解析结果里、继续写进登录 token。
     * 角色启用判断写成 EXISTS 子查询而非 JOIN，避免与下方 {@code ORDER BY sort ASC, id ASC}
     * 的列名（sys_role 同有 sort / id）产生歧义。
     */
    @Select("""
            SELECT DISTINCT p.* FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
              AND p.status = 1
              AND p.deleted = 0
              AND EXISTS (SELECT 1 FROM sys_role r
                          WHERE r.id = rp.role_id AND r.status = 1 AND r.deleted = 0)

            UNION

            SELECT DISTINCT p.* FROM sys_permission p
            INNER JOIN sys_permission_group_item pgi ON pgi.permission_id = p.id
            INNER JOIN sys_role_permission_group rpg ON rpg.group_id = pgi.group_id
            INNER JOIN sys_user_role ur ON ur.role_id = rpg.role_id
            INNER JOIN sys_permission_group pg ON pg.id = pgi.group_id
            WHERE ur.user_id = #{userId}
              AND p.status = 1
              AND p.deleted = 0
              AND pg.status = 1
              AND pg.deleted = 0
              AND EXISTS (SELECT 1 FROM sys_role r
                          WHERE r.id = rpg.role_id AND r.status = 1 AND r.deleted = 0)

            ORDER BY sort ASC, id ASC
            """)
    List<Permission> selectByUserId(@Param("userId") Long userId);

    /**
     * 按 user id 查询其全部启用菜单（type=1），用于侧边栏渲染。
     * 同 {@link #selectByUserId} 双路径 UNION，额外限定 type=1。
     */
    @Select("""
            SELECT DISTINCT p.* FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
              AND p.type = 1
              AND p.status = 1
              AND p.deleted = 0
              AND EXISTS (SELECT 1 FROM sys_role r
                          WHERE r.id = rp.role_id AND r.status = 1 AND r.deleted = 0)

            UNION

            SELECT DISTINCT p.* FROM sys_permission p
            INNER JOIN sys_permission_group_item pgi ON pgi.permission_id = p.id
            INNER JOIN sys_role_permission_group rpg ON rpg.group_id = pgi.group_id
            INNER JOIN sys_user_role ur ON ur.role_id = rpg.role_id
            INNER JOIN sys_permission_group pg ON pg.id = pgi.group_id
            WHERE ur.user_id = #{userId}
              AND p.type = 1
              AND p.status = 1
              AND p.deleted = 0
              AND pg.status = 1
              AND pg.deleted = 0
              AND EXISTS (SELECT 1 FROM sys_role r
                          WHERE r.id = rpg.role_id AND r.status = 1 AND r.deleted = 0)

            ORDER BY sort ASC, id ASC
            """)
    List<Permission> selectMenusByUserId(@Param("userId") Long userId);
}
