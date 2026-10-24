package com.scaffold.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import com.scaffold.user.dto.DictQuery;
import com.scaffold.user.entity.DictItem;
import com.scaffold.user.entity.DictType;
import com.scaffold.user.enums.EnableStatus;
import com.scaffold.user.mapper.DictItemMapper;
import com.scaffold.user.mapper.DictTypeMapper;
import com.scaffold.user.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 字典管理实现。
 *
 * <p>唯一性在应用层按「未删除记录」判定，与数据库唯一索引配合：
 * 数据库侧约束保证并发下的最终一致，应用层负责给出可读的报错。
 */
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;

    // ------------------------------------------------------------------ 字典类型

    @Override
    public PageResult<DictType> pageTypes(PageQuery pageQuery, DictQuery condition) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            wrapper.and(w -> w.like(DictType::getCode, keyword).or().like(DictType::getName, keyword));
        }
        wrapper.orderByAsc(DictType::getSort).orderByAsc(DictType::getId);

        Page<DictType> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        Page<DictType> result = dictTypeMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public List<DictType> listEnabledTypes() {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictType::getStatus, EnableStatus.ENABLED)
                .orderByAsc(DictType::getSort).orderByAsc(DictType::getId);
        return dictTypeMapper.selectList(wrapper);
    }

    @Override
    public DictType getTypeById(Long id) {
        DictType dictType = dictTypeMapper.selectById(id);
        if (dictType == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典类型不存在");
        }
        return dictType;
    }

    @Override
    public DictType createType(DictType dictType) {
        if (!StringUtils.hasText(dictType.getCode())) {
            throw new BizException(ResultCode.PARAM_INVALID, "字典类型编码不能为空");
        }
        if (existsTypeCode(dictType.getCode(), null)) {
            throw new BizException(ResultCode.PARAM_INVALID, "字典类型编码已存在");
        }
        dictType.setStatus(dictType.getStatus() == null ? EnableStatus.ENABLED : dictType.getStatus());
        dictTypeMapper.insert(dictType);
        return dictType;
    }

    @Override
    public DictType updateType(DictType dictType) {
        if (dictType.getId() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "字典类型 id 不能为空");
        }
        if (StringUtils.hasText(dictType.getCode()) && existsTypeCode(dictType.getCode(), dictType.getId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "字典类型编码已存在");
        }
        dictTypeMapper.updateById(dictType);
        return getTypeById(dictType.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        getTypeById(id);
        // 先清理子项再删类型，避免留下无法再从界面进入的孤儿字典项
        dictItemMapper.delete(new LambdaQueryWrapper<DictItem>().eq(DictItem::getTypeId, id));
        dictTypeMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 字典项

    @Override
    public PageResult<DictItem> pageItems(PageQuery pageQuery, DictQuery condition) {
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            wrapper.and(w -> w.like(DictItem::getLabel, keyword).or().like(DictItem::getValue, keyword));
        }
        wrapper.eq(condition.getTypeId() != null, DictItem::getTypeId, condition.getTypeId());
        wrapper.orderByAsc(DictItem::getSort).orderByAsc(DictItem::getId);

        Page<DictItem> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        Page<DictItem> result = dictItemMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public List<DictItem> listItemsByTypeCode(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            return List.of();
        }
        LambdaQueryWrapper<DictType> typeWrapper = new LambdaQueryWrapper<>();
        typeWrapper.eq(DictType::getCode, typeCode);
        DictType dictType = dictTypeMapper.selectOne(typeWrapper);
        if (dictType == null || dictType.getStatus() == EnableStatus.DISABLED) {
            return List.of();
        }
        return listEnabledItems(dictType.getId());
    }

    @Override
    public DictItem getItemById(Long id) {
        DictItem dictItem = dictItemMapper.selectById(id);
        if (dictItem == null) {
            throw new BizException(ResultCode.NOT_FOUND, "字典项不存在");
        }
        return dictItem;
    }

    @Override
    public DictItem createItem(DictItem dictItem) {
        validateItem(dictItem);
        if (existsItemValue(dictItem.getTypeId(), dictItem.getValue(), null)) {
            throw new BizException(ResultCode.PARAM_INVALID, "同一字典类型下取值已存在");
        }
        dictItem.setStatus(dictItem.getStatus() == null ? EnableStatus.ENABLED : dictItem.getStatus());
        dictItemMapper.insert(dictItem);
        return dictItem;
    }

    @Override
    public DictItem updateItem(DictItem dictItem) {
        if (dictItem.getId() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "字典项 id 不能为空");
        }
        DictItem existing = getItemById(dictItem.getId());
        // 类型归属不允许在更新时改变：项一旦挂到别的类型下，原类型的取值含义就变了
        dictItem.setTypeId(existing.getTypeId());
        validateItem(dictItem);
        if (existsItemValue(dictItem.getTypeId(), dictItem.getValue(), dictItem.getId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "同一字典类型下取值已存在");
        }
        dictItemMapper.updateById(dictItem);
        return getItemById(dictItem.getId());
    }

    @Override
    public void deleteItem(Long id) {
        getItemById(id);
        dictItemMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 内部

    private List<DictItem> listEnabledItems(Long typeId) {
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictItem::getTypeId, typeId)
                .eq(DictItem::getStatus, EnableStatus.ENABLED)
                .orderByAsc(DictItem::getSort).orderByAsc(DictItem::getId);
        return dictItemMapper.selectList(wrapper);
    }

    private void validateItem(DictItem dictItem) {
        if (dictItem.getTypeId() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "所属字典类型不能为空");
        }
        getTypeById(dictItem.getTypeId());
        if (!StringUtils.hasText(dictItem.getLabel())) {
            throw new BizException(ResultCode.PARAM_INVALID, "字典项文本不能为空");
        }
        if (!StringUtils.hasText(dictItem.getValue())) {
            throw new BizException(ResultCode.PARAM_INVALID, "字典项取值不能为空");
        }
    }

    private boolean existsTypeCode(String code, Long excludeId) {
        LambdaQueryWrapper<DictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictType::getCode, code);
        if (excludeId != null) {
            wrapper.ne(DictType::getId, excludeId);
        }
        return dictTypeMapper.selectCount(wrapper) > 0;
    }

    private boolean existsItemValue(Long typeId, String value, Long excludeId) {
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictItem::getTypeId, typeId).eq(DictItem::getValue, value);
        if (excludeId != null) {
            wrapper.ne(DictItem::getId, excludeId);
        }
        return dictItemMapper.selectCount(wrapper) > 0;
    }
}
