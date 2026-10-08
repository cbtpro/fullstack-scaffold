package com.scaffold.common.api;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询入参。当前页从 1 起，每页大小默认 10。
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页，从 1 起 */
    private long page = 1L;
    /** 每页大小 */
    private long size = 10L;
    /** 排序字段（可选） */
    private String sortField;
    /** 排序方向：asc/desc */
    private String sortOrder = "asc";

    public long getOffset() {
        return Math.max(0L, (page - 1L) * size);
    }
}
