package com.scaffold.user.controller;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.auth.model.UserInfo;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.Result;
import com.scaffold.user.dto.MenuNode;
import com.scaffold.user.entity.Permission;
import com.scaffold.user.entity.User;
import com.scaffold.user.service.RbacService;
import com.scaffold.user.service.UserService;
import com.scaffold.user.util.MenuTreeBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户 CRUD + 当前用户菜单。受 auth-starter 鉴权保护：未放行路径需带 token。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户增删改查与菜单下发")
public class UserController {

    private final UserService userService;
    private final AuthManager authManager;
    private final RbacService rbacService;

    @GetMapping("/info")
    @Operation(summary = "当前登录用户信息", description = "按请求头 token 解析")
    public Result<UserInfo> info(HttpServletRequest request) {
        String header = request.getHeader(authManager.getTokenHeaderName());
        String token = authManager.parseToken(header);
        if (token == null) {
            return Result.fail(com.scaffold.common.api.ResultCode.UNAUTHORIZED);
        }
        return Result.success(authManager.getLoginUser(token));
    }

    @GetMapping("/menus")
    @Operation(summary = "当前登录用户菜单树", description = "按 token 解析 user id，下发其可见菜单")
    public Result<List<MenuNode>> menus(HttpServletRequest request) {
        String header = request.getHeader(authManager.getTokenHeaderName());
        String token = authManager.parseToken(header);
        if (token == null) {
            return Result.fail(com.scaffold.common.api.ResultCode.UNAUTHORIZED);
        }
        UserInfo info = authManager.getLoginUser(token);
        if (info == null || info.getId() == null) {
            return Result.fail(com.scaffold.common.api.ResultCode.UNAUTHORIZED);
        }
        List<Permission> flat = rbacService.listMenusByUserId(info.getId());
        return Result.success(MenuTreeBuilder.build(flat));
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询用户")
    public Result<PageResult<User>> page(PageQuery query,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(userService.page(query, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询用户")
    public Result<User> get(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建用户")
    @RequirePermission("user:create")
    public Result<User> create(@RequestBody User user) {
        return Result.success(userService.create(user));
    }

    @PutMapping
    @Operation(summary = "更新用户")
    @RequirePermission("user:update")
    public Result<User> update(@RequestBody User user) {
        return Result.success(userService.update(user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @RequirePermission("user:delete")
    public Result<Void> delete(@PathVariable Long id) {
        return userService.deleteById(id) ? Result.success() : Result.fail(com.scaffold.common.api.ResultCode.FAIL);
    }

    @GetMapping("/{id}/roles")
    @Operation(summary = "查询用户已分配的角色 id 列表")
    @RequirePermission("user:assign-role")
    public Result<List<Long>> listRoles(@PathVariable("id") Long userId) {
        return Result.success(userService.listRoleIds(userId));
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "为用户分配角色（全量替换）")
    @RequirePermission("user:assign-role")
    public Result<Void> assignRoles(@PathVariable("id") Long userId, @RequestBody List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
        return Result.success();
    }
}
