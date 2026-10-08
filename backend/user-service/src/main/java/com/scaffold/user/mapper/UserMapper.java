package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。MyBatis-Plus 自动提供基础 CRUD。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
