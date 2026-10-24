package com.scaffold.common;

import com.scaffold.common.config.JacksonConfig;
import com.scaffold.common.config.OpenApiConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 公共模块自动装配入口（servlet 与 reactive 通用）。
 *
 * <p>{@code com.scaffold.common.config} 下的配置类不会被业务应用的组件扫描覆盖，
 * 必须在此显式装配。这里只装配两种运行环境都能加载的配置：
 * <ul>
 *   <li>{@link JacksonConfig}：依赖 spring-web 的 {@code Jackson2ObjectMapperBuilder}，两端均可用</li>
 *   <li>{@link OpenApiConfig}：自带 {@code @ConditionalOnClass}，未引入 springdoc 时自动跳过</li>
 * </ul>
 *
 * <p>仅 servlet 可用的配置（如 {@link com.scaffold.common.config.CorsConfig} 实现
 * {@code WebMvcConfigurer}）不能放这里：{@code @Import} 需要先加载目标类，
 * 在 WebFlux 环境下会因父类型缺失直接 {@code NoClassDefFoundError}，
 * 其自身的 {@code @ConditionalOnClass} 来不及生效。这类配置见
 * {@link CommonServletAutoConfiguration}。
 */
@AutoConfiguration
@Import({JacksonConfig.class, OpenApiConfig.class})
public class CommonAutoConfiguration {
}
