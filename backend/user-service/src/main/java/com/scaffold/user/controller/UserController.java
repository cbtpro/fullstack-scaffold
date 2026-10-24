package com.scaffold.user.controller;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.auth.model.UserInfo;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.Result;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.common.log.OperationLog;
import com.scaffold.user.dto.MenuNode;
import com.scaffold.user.dto.ProfileUpdateRequest;
import com.scaffold.user.dto.ResetPasswordRequest;
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

    @GetMapping("/profile")
    @Operation(summary = "本人资料", description = "按 token 解析 user id，无需额外权限")
    public Result<User> profile(HttpServletRequest request) {
        return Result.success(userService.getProfile(currentUserId(request)));
    }

    @PutMapping("/profile")
    @Operation(summary = "维护本人资料", description = "仅允许修改昵称、邮箱、手机")
    @OperationLog(module = "个人中心", action = "修改资料", description = "维护本人资料")
    public Result<User> updateProfile(HttpServletRequest request,
                                      @RequestBody ProfileUpdateRequest body) {
        return Result.success(userService.updateProfile(currentUserId(request), body));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询用户")
    public Result<User> get(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建用户")
    @RequirePermission("user:create")
    @OperationLog(module = "用户管理", action = "新增", description = "创建用户")
    public Result<User> create(@RequestBody User user) {
        return Result.success(userService.create(user));
    }

    @PutMapping
    @Operation(summary = "更新用户")
    @RequirePermission("user:update")
    @OperationLog(module = "用户管理", action = "修改", description = "更新用户")
    public Result<User> update(@RequestBody User user) {
        return Result.success(userService.update(user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @RequirePermission("user:delete")
    @OperationLog(module = "用户管理", action = "删除", description = "删除用户")
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
    @OperationLog(module = "用户管理", action = "分配角色", description = "为用户分配角色")
    public Result<Void> assignRoles(@PathVariable("id") Long userId, @RequestBody List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
        return Result.success();
    }

    @PutMapping("/{id}/password/reset")
    @Operation(summary = "重置用户密码（管理员，无需原密码）")
    @RequirePermission("user:reset-password")
    @OperationLog(module = "用户管理", action = "重置密码", description = "管理员重置用户密码")
    public Result<Void> resetPassword(@PathVariable("id") Long userId,
                                      @RequestBody ResetPasswordRequest body) {
        userService.resetPassword(userId, body.newPassword());
        return Result.success();
    }

    /**
     * 从请求头 token 解析当前登录用户 id，未认证直接抛业务异常。
     * 本人相关接口一律走此方法取 id，不接受请求体/路径传入的 id。
     */
    private Long currentUserId(HttpServletRequest request) {
        String header = request.getHeader(authManager.getTokenHeaderName());
        String token = authManager.parseToken(header);
        if (token == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        UserInfo info = authManager.getLoginUser(token);
        if (info == null || info.getId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return info.getId();
    }
}
