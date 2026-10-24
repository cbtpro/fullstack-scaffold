package com.scaffold.user.service;

import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.user.dto.OperationLogQuery;
import com.scaffold.user.entity.OperationLogRecord;

/**
 * 操作日志查询。
 */
public interface OperationLogService {

    /** 按条件分页查询操作日志，按时间倒序 */
    PageResult<OperationLogRecord> page(PageQuery pageQuery, OperationLogQuery condition);
}
