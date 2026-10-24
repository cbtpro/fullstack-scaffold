package com.scaffold.user.controller;

import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.Result;
import com.scaffold.common.log.OperationLog;
import com.scaffold.user.dto.DictQuery;
import com.scaffold.user.entity.DictItem;
import com.scaffold.user.entity.DictType;
import com.scaffold.user.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典类型管理。字典项由 {@link DictItemController} 单独维护，
 * 类型删除会连同其下所有项一并清理。
 */
@RestController
@RequestMapping("/api/dict-type")
@RequiredArgsConstructor
@Tag(name = "字典管理", description = "字典类型维护")
public class DictTypeController {

    private final DictService dictService;

    @GetMapping("/list")
    @Operation(summary = "分页查询字典类型")
    @RequirePermission("api:dict-type:list")
    public Result<PageResult<DictType>> page(PageQuery pageQuery, @ModelAttribute DictQuery condition) {
        return Result.success(dictService.pageTypes(pageQuery, condition));
    }

    @GetMapping("/all")
    @Operation(summary = "查询全部启用字典类型")
    @RequirePermission("api:dict-type:list")
    public Result<List<DictType>> listEnabled() {
        return Result.success(dictService.listEnabledTypes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询字典类型")
    @RequirePermission("api:dict-type:list")
    public Result<DictType> getById(@PathVariable Long id) {
        return Result.success(dictService.getTypeById(id));
    }

    @PostMapping
    @Operation(summary = "创建字典类型")
    @RequirePermission("api:dict-type:create")
    @OperationLog(module = "字典管理", action = "新增", description = "创建字典类型")
    public Result<DictType> create(@RequestBody DictType dictType) {
        return Result.success(dictService.createType(dictType));
    }

    @PutMapping
    @Operation(summary = "更新字典类型")
    @RequirePermission("api:dict-type:update")
    @OperationLog(module = "字典管理", action = "修改", description = "更新字典类型")
    public Result<DictType> update(@RequestBody DictType dictType) {
        return Result.success(dictService.updateType(dictType));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除字典类型（连同其下字典项）")
    @RequirePermission("api:dict-type:delete")
    @OperationLog(module = "字典管理", action = "删除", description = "删除字典类型")
    public Result<Void> delete(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.success();
    }
}
