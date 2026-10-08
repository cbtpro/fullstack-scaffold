package com.scaffold.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 序列化基础配置：
 * <ul>
 *   <li>时区统一 Asia/Shanghai</li>
 *   <li>LocalDateTime 统一 yyyy-MM-dd HH:mm:ss</li>
 *   <li>Long → String，避免前端 JS Number 精度丢失</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 通过 {@link Jackson2ObjectMapperBuilder} 定制 Spring 容器内的 ObjectMapper，
     * 避免直接覆盖自动配置的 {@code ObjectMapper} Bean 造成未知副作用。
     */
    @Autowired
    public void configureBuilder(Jackson2ObjectMapperBuilder builder) {
        TimeZone tz = TimeZone.getTimeZone("Asia/Shanghai");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));

        // Long 转 String，规避 JS Number 53 位精度上限（用 Jackson 自带 ToStringSerializer）
        SimpleModule longModule = new SimpleModule();
        longModule.addSerializer(Long.class, ToStringSerializer.instance);
        longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

        builder.timeZone(tz);
        builder.modules(javaTimeModule, longModule);
    }
}
