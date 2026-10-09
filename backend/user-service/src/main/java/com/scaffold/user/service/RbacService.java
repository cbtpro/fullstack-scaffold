package com.scaffold.user.service;

import com.scaffold.user.entity.Permission;
import com.scaffold.user.entity.Role;

import java.util.List;

/**
 * RBAC 领域服务。统一对外暴露角色/权限/菜单的查询入口，
 * 与具体存储（MySQL）解耦，便于后续替换为缓存或远程权限中心。
 *
 * <p>调用方：
 * <ul>
 *   <li>{@code UserServiceImpl#loadUserByUsername} 在登录链路里调 {@link #listRolesByUserId}
 *       与 {@link #listPermissionsByUserId}，把角色/权限塞入 LoginUser → token claims</li>
 *   <li>{@code UserController#menus} 调 {@link #listMenusByUserId} 下发当前用户可见菜单树</li>
 * </ul>
 */
public interface RbacService {

    /** 查询用户全部启用角色编码集合 */
    List<String> listRoleCodesByUserId(Long userId);

    /** 查询用户全部启用角色 */
    List<Role> listRolesByUserId(Long userId);

    /** 查询用户全部启用权限编码集合（含菜单/按钮/接口） */
    List<String> listPermissionCodesByUserId(Long userId);

    /** 查询用户全部启用菜单（type=1），用于侧边栏渲染 */
    List<Permission> listMenusByUserId(Long userId);
}
