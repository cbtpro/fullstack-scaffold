package com.scaffold.user.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限组关联 Mapper。直接操作 {@code sys_role_permission_group} 表，无对应实体（纯关联表）。
 * 动态绑定：角色关联权限组后，解析时 UNION 组内权限（见 PermissionMapper）。
 */
@Mapper
public interface RolePermissionGroupMapper {

    /** 查询角色已绑定的权限组 id 列表 */
    @Select("SELECT group_id FROM sys_role_permission_group WHERE role_id = #{roleId}")
    List<Long> selectGroupIdsByRoleId(@Param("roleId") Long roleId);

    /** 删除角色的全部权限组关联 */
    @Delete("DELETE FROM sys_role_permission_group WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /** 删除指定权限组在全部角色中的关联（权限组删除时清理） */
    @Delete("DELETE FROM sys_role_permission_group WHERE group_id = #{groupId}")
    int deleteByGroupId(@Param("groupId") Long groupId);

    /** 批量插入角色-权限组关联 */
    @Insert("<script>" +
            "INSERT INTO sys_role_permission_group (role_id, group_id) VALUES " +
            "<foreach collection='groupIds' item='groupId' separator=','>" +
            "(#{roleId}, #{groupId})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("roleId") Long roleId, @Param("groupIds") List<Long> groupIds);
}
