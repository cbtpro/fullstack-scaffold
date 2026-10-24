package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.LoginLogRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper。MyBatis-Plus 自动提供基础 CRUD。
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLogRecord> {
}
