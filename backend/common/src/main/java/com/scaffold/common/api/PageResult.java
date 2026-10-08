package com.scaffold.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果。
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
    /** 当前页 */
    private long page;
    /** 每页大小 */
    private long size;
    /** 当前页记录 */
    private List<T> records;

    public PageResult() {
    }

    public PageResult(long total, long page, long size, List<T> records) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.records = records == null ? Collections.emptyList() : records;
    }

    public static <T> PageResult<T> empty(PageQuery query) {
        return new PageResult<>(0L, query.getPage(), query.getSize(), Collections.emptyList());
    }
}
