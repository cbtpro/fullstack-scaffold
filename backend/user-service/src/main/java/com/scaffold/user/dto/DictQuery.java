package com.scaffold.user.dto;

import lombok.Data;

/**
 * 字典查询条件。
 */
@Data
public class DictQuery {

    /** 关键词：字典类型匹配编码/名称，字典项匹配文本/取值 */
    private String keyword;

    /** 所属字典类型 id，仅字典项查询使用 */
    private Long typeId;
}
