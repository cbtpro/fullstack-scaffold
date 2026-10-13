package com.scaffold.user.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.scaffold.user.enums.DeleteFlag;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器。
 * 实体 {@code @TableField(fill = FieldFill.INSERT/INSERT_UPDATE)} 标注的字段在此自动赋值，
 * 避免数据库 DEFAULT 不生效（MyBatis-Plus insert 显式传 null 覆盖 DEFAULT）。
 *
 * <p>{@code deleted} 以枚举 {@link DeleteFlag#NOT_DELETED} 填充；
 * 不含该字段的实体（如 UserDevice）因无对应 setter，{@code strictInsertFill} 自动跳过。
 */
@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "deleted", DeleteFlag.class, DeleteFlag.NOT_DELETED);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
