package com.scaffold.user.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 集成测试用的独立 MySQL 实例描述。
 *
 * <p>为什么不内置 Testcontainers：本机 Docker 29.x 与 Testcontainers 1.19.x 的
 * docker-java 客户端存在 API 版本协商问题（{@code /_ping} 返回 400），
 * 起不来容器。改为接一个已存在的实例，并把地址做成可覆盖的系统属性，
 * 谁跑测试谁负责提供实例（本地 docker compose 或 CI service container）。
 *
 * <p>连接参数可用系统属性覆盖：
 * {@code -Dit.db.url=... -Dit.db.user=... -Dit.db.password=...}
 *
 * <p>实例不存在时集成测试应当「跳过」而不是「失败」——缺基础设施不是代码错误，
 * 但也不能静悄悄放行，因此日志会明确打印跳过原因。
 */
public final class ItDatabase {

    /** 连接超时（秒）。探测阶段不能拖慢构建。 */
    private static final int LOGIN_TIMEOUT_SECONDS = 3;

    public static final String URL = System.getProperty("it.db.url",
            "jdbc:mysql://127.0.0.1:13306/fs_user"
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
    public static final String USER = System.getProperty("it.db.user", "root");
    public static final String PASSWORD = System.getProperty("it.db.password", "root123");

    private ItDatabase() {
    }

    /**
     * 探测实例是否可用。只建连接再立刻关闭，不做任何读写。
     */
    public static boolean isReachable() {
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        try (Connection ignored = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return true;
        } catch (SQLException e) {
            System.out.println("[it] MySQL 测试实例不可用，集成测试将跳过："
                    + URL + " → " + e.getMessage());
            return false;
        }
    }

    /**
     * 把连接参数注册进 Spring Test 的 {@code DynamicPropertyRegistry}。
     */
    public static void register(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
}
