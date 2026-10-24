package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.RolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关联 Mapper。映射 {@code sys_role_permission} 表。
 *
 * <p>该表不再使用自增主键，批量插入必须显式带 id，
 * id 由调用方用统一 Snowflake 发号器生成后写入实体。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /** 查询角色全部权限 id */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    /** 删除角色全部权限关联 */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /** 删除指定权限的全部角色关联 */
    @Delete("DELETE FROM sys_role_permission WHERE permission_id = #{permissionId}")
    int deleteByPermissionId(@Param("permissionId") Long permissionId);

    /** 批量插入角色-权限关联。id 由调用方预先生成。 */
    @Insert("<script>" +
            "INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES " +
            "<foreach collection='list' item='e' separator=','>" +
            "(#{e.id}, #{e.roleId}, #{e.permissionId})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<RolePermission> list);
}
