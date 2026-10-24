package com.scaffold.user.controller;

import com.scaffold.auth.annotation.RequirePermission;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.Result;
import com.scaffold.common.log.OperationLog;
import com.scaffold.user.dto.DictQuery;
import com.scaffold.user.entity.DictItem;
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
 * 字典项管理。按字典类型 id 归属，更新时不允许改变所属类型。
 */
@RestController
@RequestMapping("/api/dict-item")
@RequiredArgsConstructor
@Tag(name = "字典管理", description = "字典项维护")
public class DictItemController {

    private final DictService dictService;

    @GetMapping("/list")
    @Operation(summary = "分页查询字典项")
    @RequirePermission("api:dict-item:list")
    public Result<PageResult<DictItem>> page(PageQuery pageQuery, @ModelAttribute DictQuery condition) {
        return Result.success(dictService.pageItems(pageQuery, condition));
    }

    @GetMapping("/by-code/{typeCode}")
    @Operation(summary = "按字典类型编码查询启用字典项", description = "供业务侧按编码取值，不需要知道类型 id")
    public Result<List<DictItem>> listByTypeCode(@PathVariable String typeCode) {
        return Result.success(dictService.listItemsByTypeCode(typeCode));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询字典项")
    @RequirePermission("api:dict-item:list")
    public Result<DictItem> getById(@PathVariable Long id) {
        return Result.success(dictService.getItemById(id));
    }

    @PostMapping
    @Operation(summary = "创建字典项")
    @RequirePermission("api:dict-item:create")
    @OperationLog(module = "字典管理", action = "新增", description = "创建字典项")
    public Result<DictItem> create(@RequestBody DictItem dictItem) {
        return Result.success(dictService.createItem(dictItem));
    }

    @PutMapping
    @Operation(summary = "更新字典项")
    @RequirePermission("api:dict-item:update")
    @OperationLog(module = "字典管理", action = "修改", description = "更新字典项")
    public Result<DictItem> update(@RequestBody DictItem dictItem) {
        return Result.success(dictService.updateItem(dictItem));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除字典项")
    @RequirePermission("api:dict-item:delete")
    @OperationLog(module = "字典管理", action = "删除", description = "删除字典项")
    public Result<Void> delete(@PathVariable Long id) {
        dictService.deleteItem(id);
        return Result.success();
    }
}
