package com.scaffold.user.controller;

import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.Result;
import com.scaffold.user.dto.LoginLogQuery;
import com.scaffold.user.entity.LoginLogRecord;
import com.scaffold.user.service.LoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志查询端点。日志只读：写入由登录入口完成，不对外提供增删改，
 * 避免审计记录被接口层篡改。
 */
@RestController
@RequestMapping("/api/login-log")
@RequiredArgsConstructor
@Tag(name = "登录日志", description = "登录/登出审计记录查询")
public class LoginLogController {

    private final LoginLogService loginLogService;

    @GetMapping("/list")
    @Operation(summary = "分页查询登录日志")
    @RequirePermission("api:login-log:list")
    public Result<PageResult<LoginLogRecord>> page(PageQuery pageQuery,
                                                   @ModelAttribute LoginLogQuery condition) {
        return Result.success(loginLogService.page(pageQuery, condition));
    }
}
