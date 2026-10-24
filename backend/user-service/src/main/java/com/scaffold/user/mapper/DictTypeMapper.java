package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.DictType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 Mapper。MyBatis-Plus 自动提供基础 CRUD。
 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictType> {
}
