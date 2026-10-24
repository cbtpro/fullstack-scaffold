package com.scaffold.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.user.dto.LoginLogQuery;
import com.scaffold.user.entity.LoginLogRecord;
import com.scaffold.user.mapper.LoginLogMapper;
import com.scaffold.user.service.LoginLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录日志查询实现。
 */
@Service
public class LoginLogServiceImpl implements LoginLogService {

    private final LoginLogMapper loginLogMapper;

    public LoginLogServiceImpl(LoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    @Override
    public PageResult<LoginLogRecord> page(PageQuery pageQuery, LoginLogQuery condition) {
        LambdaQueryWrapper<LoginLogRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(condition.getKeyword())) {
            String keyword = condition.getKeyword().trim();
            // 关键词在多个字段间取「或」，整体与其它条件取「与」
            wrapper.and(w -> w.like(LoginLogRecord::getUsername, keyword)
                    .or().like(LoginLogRecord::getMessage, keyword)
                    .or().like(LoginLogRecord::getClientIp, keyword));
        }
        wrapper.eq(StringUtils.hasText(condition.getEventType()), LoginLogRecord::getEventType, condition.getEventType());
        wrapper.eq(condition.getSuccess() != null, LoginLogRecord::getSuccess, condition.getSuccess());
        wrapper.ge(condition.getStartTime() != null, LoginLogRecord::getCreateTime, condition.getStartTime());
        wrapper.le(condition.getEndTime() != null, LoginLogRecord::getCreateTime, condition.getEndTime());
        wrapper.orderByDesc(LoginLogRecord::getCreateTime, LoginLogRecord::getId);

        Page<LoginLogRecord> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        Page<LoginLogRecord> result = loginLogMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }
}
