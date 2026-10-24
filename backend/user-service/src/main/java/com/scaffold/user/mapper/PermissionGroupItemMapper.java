package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.PermissionGroupItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限组-权限关联 Mapper。映射 {@code sys_permission_group_item} 表。
 *
 * <p>该表不再使用自增主键，批量插入必须显式带 id，
 * id 由调用方用统一 Snowflake 发号器生成后写入实体。
 */
@Mapper
public interface PermissionGroupItemMapper extends BaseMapper<PermissionGroupItem> {

    /** 查询权限组内全部权限 id */
    @Select("SELECT permission_id FROM sys_permission_group_item WHERE group_id = #{groupId}")
    List<Long> selectPermissionIdsByGroupId(@Param("groupId") Long groupId);

    /** 删除权限组的全部权限关联 */
    @Delete("DELETE FROM sys_permission_group_item WHERE group_id = #{groupId}")
    int deleteByGroupId(@Param("groupId") Long groupId);

    /** 删除指定权限在全部权限组中的关联 */
    @Delete("DELETE FROM sys_permission_group_item WHERE permission_id = #{permissionId}")
    int deleteByPermissionId(@Param("permissionId") Long permissionId);

    /** 批量插入权限组-权限关联。id 由调用方预先生成。 */
    @Insert("<script>" +
            "INSERT INTO sys_permission_group_item (id, group_id, permission_id) VALUES " +
            "<foreach collection='list' item='e' separator=','>" +
            "(#{e.id}, #{e.groupId}, #{e.permissionId})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<PermissionGroupItem> list);
}
