package com.scaffold.user.service.impl;

import com.scaffold.user.entity.Permission;
import com.scaffold.user.entity.Role;
import com.scaffold.user.mapper.PermissionMapper;
import com.scaffold.user.mapper.RoleMapper;
import com.scaffold.user.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RBAC 服务实现。直接走 Mapper 查询，未做缓存。生产环境可在本实现外层加 Redis 缓存装饰器，
 * 不必改动业务调用方。
 */
@Service
@RequiredArgsConstructor
public class RbacServiceImpl implements RbacService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public List<String> listRoleCodesByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return roleMapper.selectByUserId(userId).stream()
                .map(Role::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Role> listRolesByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return roleMapper.selectByUserId(userId);
    }

    @Override
    public List<String> listPermissionCodesByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return permissionMapper.selectByUserId(userId).stream()
                .map(Permission::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Permission> listMenusByUserId(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return permissionMapper.selectMenusByUserId(userId);
    }
}
