package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.UserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联 Mapper。映射 {@code sys_user_role} 表。
 *
 * <p>该表不再使用自增主键，批量插入必须显式带 id，
 * id 由调用方用统一 Snowflake 发号器生成后写入实体。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /** 查询用户全部角色 id */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /** 删除用户全部角色关联 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /** 批量插入用户-角色关联。id 由调用方预先生成，一条 SQL 完成。 */
    @Insert("<script>" +
            "INSERT INTO sys_user_role (id, user_id, role_id) VALUES " +
            "<foreach collection='list' item='e' separator=','>" +
            "(#{e.id}, #{e.userId}, #{e.roleId})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<UserRole> list);
}
