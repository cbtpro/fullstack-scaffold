package com.scaffold.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.user.dto.OperationLogQuery;
import com.scaffold.user.entity.OperationLogRecord;
import com.scaffold.user.mapper.OperationLogMapper;
import com.scaffold.user.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志查询实现。
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public PageResult<OperationLogRecord> page(PageQuery pageQuery, OperationLogQuery condition) {
        LambdaQueryWrapper<OperationLogRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            // 关键词在多个字段间取「或」，整体与其它条件取「与」
            wrapper.and(w -> w.like(OperationLogRecord::getModule, keyword)
                    .or().like(OperationLogRecord::getAction, keyword)
                    .or().like(OperationLogRecord::getDescription, keyword)
                    .or().like(OperationLogRecord::getOperatorName, keyword)
                    .or().like(OperationLogRecord::getUri, keyword));
        }
        wrapper.eq(condition.getSuccess() != null, OperationLogRecord::getSuccess, condition.getSuccess());
        wrapper.ge(condition.getStartTime() != null, OperationLogRecord::getCreateTime, condition.getStartTime());
        wrapper.le(condition.getEndTime() != null, OperationLogRecord::getCreateTime, condition.getEndTime());
        wrapper.orderByDesc(OperationLogRecord::getCreateTime, OperationLogRecord::getId);

        Page<OperationLogRecord> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        Page<OperationLogRecord> result = operationLogMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }
}
