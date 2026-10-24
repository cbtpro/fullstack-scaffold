package com.scaffold.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典项实体。对应 {@code sys_dict_item} 表。
 * 通过 {@code typeId} 归属到字典类型，不使用冗余的类型编码，避免类型改码后项失联。
 * 公共字段（id/status/createTime/updateTime/deleted）见 {@link BaseLogicEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class DictItem extends BaseLogicEntity {

    /** 所属字典类型 id */
    private Long typeId;

    /** 显示文本 */
    private String label;

    /** 取值 */
    private String value;

    /** 备注 */
    private String remark;

    private Integer sort;
}
