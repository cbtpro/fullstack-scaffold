package com.scaffold.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果。字段名与前端 {@code @fs/types PageResult} 契约对齐：
 * {@code list / total / pageNum / pageSize}，避免前端再做字段映射。
 *
 * @param <T> 记录类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;
    /** 当前页码 */
    private long pageNum;
    /** 每页大小 */
    private long pageSize;
    /** 当前页记录 */
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long total, long pageNum, long pageSize, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.list = list == null ? Collections.emptyList() : list;
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(0L, query.getPage(), query.getSize(), Collections.emptyList());
    }
}
