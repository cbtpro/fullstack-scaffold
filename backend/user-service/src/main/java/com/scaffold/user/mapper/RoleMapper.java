package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper。{@link BaseMapper} 提供 CRUD，自定义 SQL 通过 user-role 关联查询用户的角色。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 按 user id 查询其全部启用角色（去重）。
     */
    @Select("""
            SELECT r.* FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.deleted = 0
            ORDER BY r.sort ASC, r.id ASC
            """)
    List<Role> selectByUserId(@Param("userId") Long userId);
}
