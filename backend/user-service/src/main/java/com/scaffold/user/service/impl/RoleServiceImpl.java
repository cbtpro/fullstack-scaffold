package com.scaffold.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.user.entity.Role;
import com.scaffold.user.mapper.RoleMapper;
import com.scaffold.user.mapper.RolePermissionGroupMapper;
import com.scaffold.user.mapper.RolePermissionMapper;
import com.scaffold.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 角色服务实现。
 * {@link #assignPermissions} 先删后插在同一事务内，避免中间态脏数据。
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RolePermissionGroupMapper rolePermissionGroupMapper;

    @Override
    public PageResult<Role> page(PageQuery query, String keyword) {
        Page<Role> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Role::getCode, keyword)
                    .or().like(Role::getName, keyword);
        }
        wrapper.orderByAsc(Role::getSort).orderByAsc(Role::getId);
        IPage<Role> result = roleMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public List<Role> listEnabled() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getStatus, 1)
                .orderByAsc(Role::getSort).orderByAsc(Role::getId);
        return roleMapper.selectList(wrapper);
    }

    @Override
    public Role getById(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    @Override
    public Role create(Role role) {
        if (role.getCode() == null || role.getCode().isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "角色编码不能为空");
        }
        if (existsCode(role.getCode(), null)) {
            throw new BizException(ResultCode.PARAM_INVALID, "角色编码已存在");
        }
        role.setStatus(role.getStatus() == null ? 1 : role.getStatus());
        roleMapper.insert(role);
        return role;
    }

    @Override
    public Role update(Role role) {
        if (role.getId() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "角色 id 不能为空");
        }
        if (role.getCode() != null && existsCode(role.getCode(), role.getId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "角色编码已存在");
        }
        roleMapper.updateById(role);
        return getById(role.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteById(Long id) {
        rolePermissionMapper.deleteByRoleId(id);
        rolePermissionGroupMapper.deleteByRoleId(id);
        return roleMapper.deleteById(id) > 0;
    }

    @Override
    public List<Long> listPermissionIds(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        return rolePermissionMapper.selectPermissionIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        getById(roleId);
        rolePermissionMapper.deleteByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            rolePermissionMapper.batchInsert(roleId, permissionIds);
        }
    }

    @Override
    public List<Long> listGroupIds(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        return rolePermissionGroupMapper.selectGroupIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignGroups(Long roleId, List<Long> groupIds) {
        getById(roleId);
        rolePermissionGroupMapper.deleteByRoleId(roleId);
        if (groupIds != null && !groupIds.isEmpty()) {
            rolePermissionGroupMapper.batchInsert(roleId, groupIds);
        }
    }

    private boolean existsCode(String code, Long excludeId) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getCode, code);
        if (excludeId != null) {
            wrapper.ne(Role::getId, excludeId);
        }
        return roleMapper.selectCount(wrapper) > 0;
    }
}
