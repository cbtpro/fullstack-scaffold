package com.scaffold.user.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限组-权限关联 Mapper。直接操作 {@code sys_permission_group_item} 表，无对应实体（纯关联表）。
 */
@Mapper
public interface PermissionGroupItemMapper {

    /** 查询权限组内全部权限 id */
    @Select("SELECT permission_id FROM sys_permission_group_item WHERE group_id = #{groupId}")
    List<Long> selectPermissionIdsByGroupId(@Param("groupId") Long groupId);

    /** 删除权限组的全部权限关联 */
    @Delete("DELETE FROM sys_permission_group_item WHERE group_id = #{groupId}")
    int deleteByGroupId(@Param("groupId") Long groupId);

    /** 删除指定权限在全部权限组中的关联 */
    @Delete("DELETE FROM sys_permission_group_item WHERE permission_id = #{permissionId}")
    int deleteByPermissionId(@Param("permissionId") Long permissionId);

    /** 批量插入权限组-权限关联 */
    @Insert("<script>" +
            "INSERT INTO sys_permission_group_item (group_id, permission_id) VALUES " +
            "<foreach collection='permissionIds' item='permissionId' separator=','>" +
            "(#{groupId}, #{permissionId})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("groupId") Long groupId, @Param("permissionIds") List<Long> permissionIds);
}
