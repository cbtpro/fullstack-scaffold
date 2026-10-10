package com.scaffold.user.controller;

import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.Result;
import com.scaffold.user.entity.Role;
import com.scaffold.user.service.RoleService;
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
 * 角色管理。提供角色 CRUD 与权限分配接口。
 */
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
@Tag(name = "角色管理")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/list")
    @Operation(summary = "分页查询角色")
    @RequirePermission("api:role:list")
    public Result<PageResult<Role>> page(PageQuery query, String keyword) {
        return Result.success(roleService.page(query, keyword));
    }

    @GetMapping("/all")
    @Operation(summary = "查询全部启用角色")
    @RequirePermission("api:role:list")
    public Result<List<Role>> listEnabled() {
        return Result.success(roleService.listEnabled());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询角色")
    @RequirePermission("api:role:list")
    public Result<Role> getById(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建角色")
    @RequirePermission("api:role:create")
    public Result<Role> create(@RequestBody Role role) {
        return Result.success(roleService.create(role));
    }

    @PutMapping
    @Operation(summary = "更新角色")
    @RequirePermission("api:role:update")
    public Result<Role> update(@RequestBody Role role) {
        return Result.success(roleService.update(role));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色")
    @RequirePermission("api:role:delete")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteById(id);
        return Result.success();
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "查询角色已分配的权限 id 列表")
    @RequirePermission("api:role:list")
    public Result<List<Long>> listPermissions(@PathVariable("id") Long roleId) {
        return Result.success(roleService.listPermissionIds(roleId));
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "为角色分配权限（全量替换）")
    @RequirePermission("api:role:assign")
    public Result<Void> assignPermissions(@PathVariable("id") Long roleId, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(roleId, permissionIds);
        return Result.success();
    }

    @GetMapping("/{id}/groups")
    @Operation(summary = "查询角色已绑定的权限组 id 列表")
    @RequirePermission("api:role:assign-group")
    public Result<List<Long>> listGroups(@PathVariable("id") Long roleId) {
        return Result.success(roleService.listGroupIds(roleId));
    }

    @PutMapping("/{id}/groups")
    @Operation(summary = "为角色分配权限组（全量替换）")
    @RequirePermission("api:role:assign-group")
    public Result<Void> assignGroups(@PathVariable("id") Long roleId, @RequestBody List<Long> groupIds) {
        roleService.assignGroups(roleId, groupIds);
        return Result.success();
    }
}
