package com.scaffold.user;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * user-service 启动类。
 *
 * <p>排除 {@link SecurityAutoConfiguration} 与 {@link UserDetailsServiceAutoConfiguration}：
 * <ul>
 *   <li>JWT 模式：避免 Security 默认 FilterChain（form login）干扰，{@code JwtSecurityConfig} 自定义唯一链</li>
 *   <li>SA_TOKEN 模式：避免 Security 链与 sa-token {@code SaInterceptor} 冲突</li>
 * </ul>
 * 切换 {@code auth.type} 无需改代码，仅改 application.yml。
 */
@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
// 按注解扫描 Mapper：新增子包（如 device）下的 Mapper 接口无需回改此处
@MapperScan(basePackages = "com.scaffold.user", annotationClass = Mapper.class)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
