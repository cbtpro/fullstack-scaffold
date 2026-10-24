package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.OperationLogRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。MyBatis-Plus 自动提供基础 CRUD。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogRecord> {
}
