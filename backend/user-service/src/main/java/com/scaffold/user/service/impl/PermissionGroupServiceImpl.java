package com.scaffold.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.user.entity.PermissionGroup;
import com.scaffold.user.mapper.PermissionGroupItemMapper;
import com.scaffold.user.mapper.PermissionGroupMapper;
import com.scaffold.user.mapper.RolePermissionGroupMapper;
import com.scaffold.user.service.PermissionGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 权限组服务实现。
 * {@link #assignPermissions} 先删后插在同一事务内，避免中间态脏数据。
 * {@link #deleteById} 同步清理组内权限关联与角色-权限组绑定。
 */
@Service
@RequiredArgsConstructor
public class PermissionGroupServiceImpl implements PermissionGroupService {

    private final PermissionGroupMapper permissionGroupMapper;
    private final PermissionGroupItemMapper permissionGroupItemMapper;
    private final RolePermissionGroupMapper rolePermissionGroupMapper;

    @Override
    public PageResult<PermissionGroup> page(PageQuery query, String keyword) {
        Page<PermissionGroup> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<PermissionGroup> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PermissionGroup::getCode, keyword)
                    .or().like(PermissionGroup::getName, keyword);
        }
        wrapper.orderByAsc(PermissionGroup::getSort).orderByAsc(PermissionGroup::getId);
        IPage<PermissionGroup> result = permissionGroupMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public List<PermissionGroup> listEnabled() {
        LambdaQueryWrapper<PermissionGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionGroup::getStatus, 1)
                .orderByAsc(PermissionGroup::getSort).orderByAsc(PermissionGroup::getId);
        return permissionGroupMapper.selectList(wrapper);
    }

    @Override
    public PermissionGroup getById(Long id) {
        PermissionGroup group = permissionGroupMapper.selectById(id);
        if (group == null) {
            throw new BizException(ResultCode.NOT_FOUND, "权限组不存在");
        }
        return group;
    }

    @Override
    public PermissionGroup create(PermissionGroup group) {
        if (group.getCode() == null || group.getCode().isBlank()) {
            throw new BizException(ResultCode.PARAM_INVALID, "权限组编码不能为空");
        }
        if (existsCode(group.getCode(), null)) {
            throw new BizException(ResultCode.PARAM_INVALID, "权限组编码已存在");
        }
        group.setStatus(group.getStatus() == null ? 1 : group.getStatus());
        permissionGroupMapper.insert(group);
        return group;
    }

    @Override
    public PermissionGroup update(PermissionGroup group) {
        if (group.getId() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "权限组 id 不能为空");
        }
        if (group.getCode() != null && existsCode(group.getCode(), group.getId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "权限组编码已存在");
        }
        permissionGroupMapper.updateById(group);
        return getById(group.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteById(Long id) {
        getById(id);
        permissionGroupItemMapper.deleteByGroupId(id);
        rolePermissionGroupMapper.deleteByGroupId(id);
        return permissionGroupMapper.deleteById(id) > 0;
    }

    @Override
    public List<Long> listPermissionIds(Long groupId) {
        if (groupId == null) {
            return Collections.emptyList();
        }
        return permissionGroupItemMapper.selectPermissionIdsByGroupId(groupId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long groupId, List<Long> permissionIds) {
        getById(groupId);
        permissionGroupItemMapper.deleteByGroupId(groupId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            permissionGroupItemMapper.batchInsert(groupId, permissionIds);
        }
    }

    private boolean existsCode(String code, Long excludeId) {
        LambdaQueryWrapper<PermissionGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionGroup::getCode, code);
        if (excludeId != null) {
            wrapper.ne(PermissionGroup::getId, excludeId);
        }
        return permissionGroupMapper.selectCount(wrapper) > 0;
    }
}
