package com.scaffold.user.controller;

import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.Result;
import com.scaffold.user.dto.OperationLogQuery;
import com.scaffold.user.entity.OperationLogRecord;
import com.scaffold.user.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询端点。日志只读：写入由切面完成，不对外提供增删改，
 * 避免审计记录被接口层篡改。
 */
@RestController
@RequestMapping("/api/operation-log")
@RequiredArgsConstructor
@Tag(name = "操作日志", description = "操作审计记录查询")
public class OperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping("/list")
    @Operation(summary = "分页查询操作日志")
    @RequirePermission("api:operation-log:list")
    public Result<PageResult<OperationLogRecord>> page(PageQuery pageQuery,
                                                       @ModelAttribute OperationLogQuery condition) {
        return Result.success(operationLogService.page(pageQuery, condition));
    }
}
