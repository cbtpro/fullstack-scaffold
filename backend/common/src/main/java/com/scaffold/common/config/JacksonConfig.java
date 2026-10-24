package com.scaffold.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 序列化基础配置：
 * <ul>
 *   <li>时区统一 Asia/Shanghai</li>
 *   <li>LocalDateTime 统一 yyyy-MM-dd HH:mm:ss</li>
 *   <li>超出 JS 安全整数范围的 Long → String，避免主键精度丢失</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 以 {@link Jackson2ObjectMapperBuilderCustomizer} 扩展 Jackson。
     *
     * <p>不能改用「对 {@code Jackson2ObjectMapperBuilder} 做 {@code @Autowired} 方法注入」的写法：
     * 那种方式依赖 Bean 创建顺序，一旦自动配置先构建出 ObjectMapper，定制就会被跳过，
     * 表现为雪花主键仍按数字下发、时间仍为 ISO 格式。本 Bean 由
     * {@code JacksonObjectMapperBuilderConfiguration} 在创建 Builder 时统一应用，顺序有保证。
     *
     * <p>用 {@code modulesToInstall}（追加）而非 {@code modules}（整体替换）：
     * 保留自动配置注册的 {@code ParameterNamesModule} 等模块，只叠加自定义序列化器。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer scaffoldJacksonCustomizer() {
        return builder -> {
            builder.timeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            builder.modulesToInstall(localDateTimeModule(), safeLongModule());
        };
    }

    /** LocalDateTime 统一按 yyyy-MM-dd HH:mm:ss 输出 */
    private JavaTimeModule localDateTimeModule() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
        return module;
    }

    /** 仅超出 JS 安全整数范围的 Long 转字符串，小整数保持数字（见 SafeLongSerializer） */
    private SimpleModule safeLongModule() {
        SafeLongSerializer serializer = new SafeLongSerializer();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, serializer);
        module.addSerializer(Long.TYPE, serializer);
        return module;
    }
}
