package com.scaffold.user.service;

import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.user.dto.DictQuery;
import com.scaffold.user.entity.DictItem;
import com.scaffold.user.entity.DictType;

import java.util.List;

/**
 * 字典管理。类型与项分两个聚合写入，删除类型时连同其下所有项一并清理，
 * 避免残留下属项成为孤儿数据。
 */
public interface DictService {

    // ------------------------------------------------------------------ 字典类型

    PageResult<DictType> pageTypes(PageQuery pageQuery, DictQuery condition);

    /** 全部启用字典类型（供下拉选择） */
    List<DictType> listEnabledTypes();

    DictType getTypeById(Long id);

    DictType createType(DictType dictType);

    DictType updateType(DictType dictType);

    /** 删除字典类型，同时删除其下所有字典项 */
    void deleteType(Long id);

    // ------------------------------------------------------------------ 字典项

    PageResult<DictItem> pageItems(PageQuery pageQuery, DictQuery condition);

    /** 按字典类型编码查询启用项，供业务侧按编码取值 */
    List<DictItem> listItemsByTypeCode(String typeCode);

    DictItem getItemById(Long id);

    DictItem createItem(DictItem dictItem);

    DictItem updateItem(DictItem dictItem);

    void deleteItem(Long id);
}
