package com.scaffold.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.user.entity.Permission;
import com.scaffold.user.mapper.PermissionMapper;
import com.scaffold.user.mapper.RolePermissionMapper;
import com.scaffold.user.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 权限服务实现。
 * 删除权限时同步清理 {@code sys_role_permission} 关联，避免脏数据。
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public List<Permission> listAll() {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Permission::getSort).orderByAsc(Permission::getId);
        return permissionMapper.selectList(wrapper);
    }

    @Override
    public Permission getById(Long id) {
        Permission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BizException(ResultCode.NOT_FOUND, "权限不存在");
        }
        return permission;
    }

    @Override
    public Permission create(Permission permission) {
        if (permission.getCode() == null || permission.getCode().isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "权限编码不能为空");
        }
        if (existsCode(permission.getCode(), null)) {
            throw new BizException(ResultCode.PARAM_INVALID, "权限编码已存在");
        }
        permission.setStatus(permission.getStatus() == null ? 1 : permission.getStatus());
        permissionMapper.insert(permission);
        return permission;
    }

    @Override
    public Permission update(Permission permission) {
        if (permission.getId() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "权限 id 不能为空");
        }
        if (permission.getCode() != null && existsCode(permission.getCode(), permission.getId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "权限编码已存在");
        }
        permissionMapper.updateById(permission);
        return getById(permission.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteById(Long id) {
        // 先清理角色-权限关联，再删权限
        rolePermissionMapper.deleteByPermissionId(id);
        return permissionMapper.deleteById(id) > 0;
    }

    private boolean existsCode(String code, Long excludeId) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getCode, code);
        if (excludeId != null) {
            wrapper.ne(Permission::getId, excludeId);
        }
        return permissionMapper.selectCount(wrapper) > 0;
    }
}
