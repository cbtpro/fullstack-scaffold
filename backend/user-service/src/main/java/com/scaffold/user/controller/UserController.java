package com.scaffold.user.controller;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.model.LoginUser;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.Result;
import com.scaffold.user.entity.User;
import com.scaffold.user.service.UserService;
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

/**
 * 用户 CRUD。受 auth-starter 鉴权保护：未放行路径需带 token。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户增删改查")
public class UserController {

    private final UserService userService;
    private final AuthManager authManager;

    @GetMapping("/info")
    @Operation(summary = "当前登录用户信息", description = "按请求头 token 解析")
    public Result<LoginUser> info(HttpServletRequest request) {
        String token = authManager.parseToken(request);
        if (token == null) {
            return Result.fail(com.scaffold.common.api.ResultCode.UNAUTHORIZED);
        }
        return Result.success(authManager.getLoginUser(token));
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
    public Result<User> create(@RequestBody User user) {
        return Result.success(userService.create(user));
    }

    @PutMapping
    @Operation(summary = "更新用户")
    public Result<User> update(@RequestBody User user) {
        return Result.success(userService.update(user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        return userService.deleteById(id) ? Result.success() : Result.fail(com.scaffold.common.api.ResultCode.FAIL);
    }
}
