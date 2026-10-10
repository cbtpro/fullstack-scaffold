package com.scaffold.user.service;

import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.user.entity.Role;

import java.util.List;

/**
 * 角色服务。提供角色 CRUD 与权限分配。
 */
public interface RoleService {

    /** 分页查询角色 */
    PageResult<Role> page(PageQuery query, String keyword);

    /** 查询全部启用角色（用于下拉选择） */
    List<Role> listEnabled();

    /** 按 id 查询 */
    Role getById(Long id);

    /** 创建角色 */
    Role create(Role role);

    /** 更新角色 */
    Role update(Role role);

    /** 删除角色（同步清理角色-权限关联） */
    boolean deleteById(Long id);

    /** 查询角色已分配的权限 id 列表 */
    List<Long> listPermissionIds(Long roleId);

    /** 为角色分配权限（全量替换，先删后插） */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /** 查询角色已绑定的权限组 id 列表 */
    List<Long> listGroupIds(Long roleId);

    /** 为角色分配权限组（全量替换，先删后插） */
    void assignGroups(Long roleId, List<Long> groupIds);
}
