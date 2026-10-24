package com.scaffold.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.List;

/**
 * 必需配置项启动校验（fail-fast），在 Environment 准备完成后、任何 Bean 实例化之前执行。
 *
 * <p>为什么需要它：Spring Boot 对 {@code @ConfigurationProperties} 的占位符是「非严格解析」，
 * {@code host: ${REDIS_HOST}} 在变量缺失时会把字面量 {@code ${REDIS_HOST}} 原样绑定进去，
 * 于是故障被推迟到首次连接（Redis 是懒连接，可能直到首次限流才暴露），
 * 共享密钥更是退化成仓库里公开的字面量、让网关信任校验静默通过。
 * 这里改用 {@code Environment.getProperty} 的严格占位符解析（解析不到直接抛
 * {@code Could not resolve placeholder}），把问题挡在启动阶段。
 *
 * <p>必需项清单由配置声明（{@code scaffold.required-properties}），只在真正要求外部输入的环境档
 * （如 {@code application-prod.yml}）出现；校验逻辑与环境无关，增删必需项只改配置，
 * 也不限制取值来源——环境变量、{@code --spring.datasource.url=...} 或配置中心均可。
 */
public class RequiredPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 清单所在配置键 */
    private static final String REQUIRED_PROPERTIES_KEY = "scaffold.required-properties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> required = Binder.get(environment)
                .bind(REQUIRED_PROPERTIES_KEY, Bindable.listOf(String.class))
                .orElseGet(List::of);
        if (required.isEmpty()) {
            return;
        }
        for (String key : required) {
            String value = environment.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("缺少必需配置项 " + key
                        + "（清单见 " + REQUIRED_PROPERTIES_KEY + "），请注入后再启动");
            }
        }
    }
}
