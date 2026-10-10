package com.scaffold.user.controller;

import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.Result;
import com.scaffold.user.entity.PermissionGroup;
import com.scaffold.user.service.PermissionGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限组管理。提供权限组 CRUD 与组内权限分配接口。
 * 角色绑定权限组后，解析时自动 UNION 组内权限（动态同步）。
 */
@RestController
@RequestMapping("/api/permission-group")
@RequiredArgsConstructor
@Tag(name = "权限组管理")
public class PermissionGroupController {

    private final PermissionGroupService permissionGroupService;

    @GetMapping("/list")
    @Operation(summary = "分页查询权限组")
    @RequirePermission("api:permission-group:list")
    public Result<PageResult<PermissionGroup>> page(PageQuery query, String keyword) {
        return Result.success(permissionGroupService.page(query, keyword));
    }

    @GetMapping("/all")
    @Operation(summary = "查询全部启用权限组")
    @RequirePermission("api:permission-group:list")
    public Result<List<PermissionGroup>> listEnabled() {
        return Result.success(permissionGroupService.listEnabled());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询权限组")
    @RequirePermission("api:permission-group:list")
    public Result<PermissionGroup> getById(@PathVariable Long id) {
        return Result.success(permissionGroupService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建权限组")
    @RequirePermission("api:permission-group:create")
    public Result<PermissionGroup> create(@RequestBody PermissionGroup group) {
        return Result.success(permissionGroupService.create(group));
    }

    @PutMapping
    @Operation(summary = "更新权限组")
    @RequirePermission("api:permission-group:update")
    public Result<PermissionGroup> update(@RequestBody PermissionGroup group) {
        return Result.success(permissionGroupService.update(group));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除权限组")
    @RequirePermission("api:permission-group:delete")
    public Result<Void> delete(@PathVariable Long id) {
        permissionGroupService.deleteById(id);
        return Result.success();
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "查询权限组已分配的权限 id 列表")
    @RequirePermission("api:permission-group:list")
    public Result<List<Long>> listPermissions(@PathVariable("id") Long groupId) {
        return Result.success(permissionGroupService.listPermissionIds(groupId));
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "为权限组分配权限（全量替换）")
    @RequirePermission("api:permission-group:assign")
    public Result<Void> assignPermissions(@PathVariable("id") Long groupId, @RequestBody List<Long> permissionIds) {
        permissionGroupService.assignPermissions(groupId, permissionIds);
        return Result.success();
    }
}
