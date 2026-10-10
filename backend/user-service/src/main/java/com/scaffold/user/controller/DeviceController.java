package com.scaffold.user.controller;

import com.scaffold.auth.AuthManager;
import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.auth.model.UserInfo;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.Result;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.user.device.DeviceService;
import com.scaffold.user.device.UserDevice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录设备管理。自服务模式：用户只能查看和管理自己的设备。
 * 接口权限与用户管理同口径，均经网关 token 校验 + 设备指纹比对。
 */
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Tag(name = "登录设备管理", description = "查看与管理当前用户的登录设备")
public class DeviceController {

    private final AuthManager authManager;
    private final DeviceService deviceService;

    @GetMapping("/list")
    @Operation(summary = "分页查询当前用户设备，支持关键词搜索")
    @RequirePermission("api:device:list")
    public Result<PageResult<UserDevice>> page(PageQuery query, String keyword, HttpServletRequest request) {
        return Result.success(deviceService.page(query, resolveUserId(request), keyword));
    }

    @PutMapping("/{id}/trust")
    @Operation(summary = "将设备设为可信")
    @RequirePermission("api:device:trust")
    public Result<Void> trust(@PathVariable Long id, HttpServletRequest request) {
        deviceService.trust(id, resolveUserId(request));
        return Result.success();
    }

    @PutMapping("/{id}/revoke")
    @Operation(summary = "吊销设备")
    @RequirePermission("api:device:revoke")
    public Result<Void> revoke(@PathVariable Long id, HttpServletRequest request) {
        deviceService.revoke(id, resolveUserId(request));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "移除设备记录")
    @RequirePermission("api:device:remove")
    public Result<Void> remove(@PathVariable Long id, HttpServletRequest request) {
        deviceService.remove(id, resolveUserId(request));
        return Result.success();
    }

    /**
     * 从请求头解析 token 获取当前用户 id。
     * 与 UserController 一致的 token 解析模式。
     */
    private Long resolveUserId(HttpServletRequest request) {
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
