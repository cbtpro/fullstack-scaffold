package com.scaffold.user.service;

import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.user.dto.LoginLogQuery;
import com.scaffold.user.entity.LoginLogRecord;

/**
 * 登录日志查询。
 */
public interface LoginLogService {

    /** 按条件分页查询登录日志，按时间倒序 */
    PageResult<LoginLogRecord> page(PageQuery pageQuery, LoginLogQuery condition);
}
