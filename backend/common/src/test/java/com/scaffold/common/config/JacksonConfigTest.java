package com.scaffold.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 序列化约定测试：验证 {@link JacksonConfig} 定制的 ObjectMapper 行为。
 *
 * <p>用例直接构造 Builder 并用生产同一份 Customizer 定制，不启动 Spring 上下文——
 * 这里要守的是「配置生效后的序列化结果」，而不是「配置类是否被扫描到」，
 * 后者由各业务服务的接口验证覆盖。
 *
 * <p>雪花主键精度问题曾真实发生过：Long 按 JSON number 下发，超过 2^53 后
 * 不同主键在浏览器被舍入成同一个数字。本类是该缺陷的回归护栏。
 */
class JacksonConfigTest {

    private static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().scaffoldJacksonCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("超出 JS 安全整数范围的 Long 序列化为字符串")
    void snowflakeIdIsWrittenAsString() throws Exception {
        ObjectMapper objectMapper = mapper();
        // 19 位雪花主键，两个相邻值仅末位不同
        String json = objectMapper.writeValueAsString(new IdPayload(104879054015991814L, 104879054015991815L));

        assertThat(json).contains("\"id\":\"104879054015991814\"");
        assertThat(json).contains("\"otherId\":\"104879054015991815\"");
    }

    @Test
    @DisplayName("小整数保持 JSON number，不破坏前端数值比较与分页计算")
    void smallNumbersStayNumeric() throws Exception {
        ObjectMapper objectMapper = mapper();
        String json = objectMapper.writeValueAsString(new IdPayload(0L, 9007199254740991L));

        assertThat(json).contains("\"id\":0");
        // 2^53-1 是 JS 仍能精确表示的上界，因此不转字符串
        assertThat(json).contains("\"otherId\":9007199254740991");
    }

    @Test
    @DisplayName("负数越界同样转字符串")
    void negativeOverflowIsWrittenAsString() throws Exception {
        ObjectMapper objectMapper = mapper();

        assertThat(objectMapper.writeValueAsString(new IdPayload(-9007199254740992L, -1L)))
                .isEqualTo("{\"id\":\"-9007199254740992\",\"otherId\":-1}");
    }

    @Test
    @DisplayName("null 仍序列化为 null，不写成字符串")
    void nullStaysNull() throws Exception {
        ObjectMapper objectMapper = mapper();

        assertThat(objectMapper.writeValueAsString(new IdPayload(null, null)))
                .isEqualTo("{\"id\":null,\"otherId\":null}");
    }

    @Test
    @DisplayName("LocalDateTime 按 yyyy-MM-dd HH:mm:ss 输出，且与前端解析格式一致")
    void localDateTimeUsesAgreedPattern() throws Exception {
        ObjectMapper objectMapper = mapper();

        assertThat(objectMapper.writeValueAsString(LocalDateTime.of(2026, 10, 19, 8, 5, 3)))
                .isEqualTo("\"2026-10-19 08:05:03\"");
        // 反序列化用同一格式，缺了它前端回传时间会解析失败
        assertThat(objectMapper.readValue("\"2026-10-19 08:05:03\"", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 10, 19, 8, 5, 3));
    }

    /** 最小载体：只需两个 Long 字段，避免依赖业务实体 */
    private static class IdPayload {
        private final Long id;
        private final Long otherId;

        IdPayload(Long id, Long otherId) {
            this.id = id;
            this.otherId = otherId;
        }

        public Long getId() {
            return id;
        }

        public Long getOtherId() {
            return otherId;
        }
    }
}
