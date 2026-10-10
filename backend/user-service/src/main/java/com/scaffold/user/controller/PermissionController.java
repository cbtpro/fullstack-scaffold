package com.scaffold.user.controller;

import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.common.api.Result;
import com.scaffold.user.entity.Permission;
import com.scaffold.user.service.PermissionService;
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
 * 权限管理。提供权限 CRUD 接口，列表返回全部权限供前端构建树。
 */
@RestController
@RequestMapping("/api/permission")
@RequiredArgsConstructor
@Tag(name = "权限管理")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping("/list")
    @Operation(summary = "查询全部权限（扁平列表，前端构建树）")
    @RequirePermission("api:permission:list")
    public Result<List<Permission>> list() {
        return Result.success(permissionService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询权限")
    @RequirePermission("api:permission:list")
    public Result<Permission> getById(@PathVariable Long id) {
        return Result.success(permissionService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建权限")
    @RequirePermission("api:permission:create")
    public Result<Permission> create(@RequestBody Permission permission) {
        return Result.success(permissionService.create(permission));
    }

    @PutMapping
    @Operation(summary = "更新权限")
    @RequirePermission("api:permission:update")
    public Result<Permission> update(@RequestBody Permission permission) {
        return Result.success(permissionService.update(permission));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除权限")
    @RequirePermission("api:permission:delete")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.deleteById(id);
        return Result.success();
    }
}
