package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.RolePermissionGroup;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限组关联 Mapper。映射 {@code sys_role_permission_group} 表。
 * 动态绑定：角色关联权限组后，解析时 UNION 组内权限（见 PermissionMapper）。
 *
 * <p>该表不再使用自增主键，批量插入必须显式带 id，
 * id 由调用方用统一 Snowflake 发号器生成后写入实体。
 */
@Mapper
public interface RolePermissionGroupMapper extends BaseMapper<RolePermissionGroup> {

    /** 查询角色已绑定的权限组 id 列表 */
    @Select("SELECT group_id FROM sys_role_permission_group WHERE role_id = #{roleId}")
    List<Long> selectGroupIdsByRoleId(@Param("roleId") Long roleId);

    /** 删除角色的全部权限组关联 */
    @Delete("DELETE FROM sys_role_permission_group WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /** 删除指定权限组在全部角色中的关联（权限组删除时清理） */
    @Delete("DELETE FROM sys_role_permission_group WHERE group_id = #{groupId}")
    int deleteByGroupId(@Param("groupId") Long groupId);

    /** 批量插入角色-权限组关联。id 由调用方预先生成。 */
    @Insert("<script>" +
            "INSERT INTO sys_role_permission_group (id, role_id, group_id) VALUES " +
            "<foreach collection='list' item='e' separator=','>" +
            "(#{e.id}, #{e.roleId}, #{e.groupId})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<RolePermissionGroup> list);
}
