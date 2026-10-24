package com.scaffold.common;

import com.scaffold.common.config.CorsConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * 公共模块的 servlet 专用自动装配。
 *
 * <p>用 {@code @ConditionalOnWebApplication(SERVLET)} 在装配层面隔离：
 * WebFlux 环境（gateway-service）条件不成立，整个自动配置被跳过，
 * 其 {@code @Import} 的 servlet 配置类不会被加载。
 *
 * <p>不能改成在 {@link CorsConfig} 上加 {@code @ConditionalOnClass} 来实现隔离：
 * {@code @Import} 要求先加载目标类，而该类实现了 WebFlux 下不存在的
 * {@code WebMvcConfigurer}，加载阶段即失败。
 */
@AutoConfiguration(after = CommonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(CorsConfig.class)
public class CommonServletAutoConfiguration {
}
