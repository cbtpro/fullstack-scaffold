package com.scaffold.user.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 登录日志查询条件。时间区间取闭区间，缺省不限制。
 */
@Data
public class LoginLogQuery {

    /** 关键词：匹配账号、提示信息、客户端 IP */
    private String keyword;

    /** 事件类型：LOGIN / LOGOUT，null 表示不限 */
    private String eventType;

    /** 是否成功，null 表示不限 */
    private Boolean success;

    /** 起始时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /** 结束时间（含） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
