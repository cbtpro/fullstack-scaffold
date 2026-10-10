package com.scaffold.user.service;

import com.scaffold.user.entity.Permission;

import java.util.List;

/**
 * 权限服务。提供权限 CRUD 与树形查询。
 * 权限分三类：1菜单、2按钮、3接口，菜单型权限通过 parentId 构成树。
 */
public interface PermissionService {

    /** 查询全部权限（按 sort 排序），前端自行构建树 */
    List<Permission> listAll();

    /** 按 id 查询 */
    Permission getById(Long id);

    /** 创建权限 */
    Permission create(Permission permission);

    /** 更新权限 */
    Permission update(Permission permission);

    /** 删除权限（同步清理角色-权限关联） */
    boolean deleteById(Long id);
}
