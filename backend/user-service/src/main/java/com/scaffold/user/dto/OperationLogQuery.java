package com.scaffold.user.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 操作日志查询条件。时间区间取闭区间，缺省不限制。
 */
@Data
public class OperationLogQuery {

    /** 关键词：匹配模块、操作、说明、操作人、请求路径 */
    private String keyword;

    /** 是否成功，null 表示不限 */
    private Boolean success;

    /** 起始时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /** 结束时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
