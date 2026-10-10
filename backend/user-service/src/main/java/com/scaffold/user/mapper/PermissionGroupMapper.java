package com.scaffold.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.user.entity.PermissionGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限组 Mapper。{@link BaseMapper} 提供标准 CRUD。
 */
@Mapper
public interface PermissionGroupMapper extends BaseMapper<PermissionGroup> {
}
