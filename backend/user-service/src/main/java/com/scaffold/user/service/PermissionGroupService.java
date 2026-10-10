package com.scaffold.user.service;

import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.user.entity.PermissionGroup;

import java.util.List;

/**
 * 权限组服务。提供权限组 CRUD 与组内权限分配接口。
 * 角色 {@link com.scaffold.user.service.RoleService#assignGroups} 绑定权限组后，
 * 解析时 UNION 组内权限（见 {@link com.scaffold.user.mapper.PermissionMapper#selectByUserId}）。
 */
public interface PermissionGroupService {

    /** 分页查询权限组 */
    PageResult<PermissionGroup> page(PageQuery query, String keyword);

    /** 查询全部启用的权限组 */
    List<PermissionGroup> listEnabled();

    /** 按 id 查询权限组 */
    PermissionGroup getById(Long id);

    /** 创建权限组 */
    PermissionGroup create(PermissionGroup group);

    /** 更新权限组 */
    PermissionGroup update(PermissionGroup group);

    /** 删除权限组（同步清理组内权限关联与角色绑定） */
    boolean deleteById(Long id);

    /** 查询权限组内已分配的权限 id 列表 */
    List<Long> listPermissionIds(Long groupId);

    /** 为权限组分配权限（全量替换） */
    void assignPermissions(Long groupId, List<Long> permissionIds);
}
