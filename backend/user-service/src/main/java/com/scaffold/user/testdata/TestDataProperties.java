package com.scaffold.user.testdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试数据重建程序配置，前缀 {@code test-data}。
 */
@ConfigurationProperties(prefix = "test-data")
public class TestDataProperties {

    /**
     * 执行动作：{@code inspect} / {@code rebuild} / {@code verify}。
     * 留空表示不执行 —— 重建程序绝不随普通应用启动自动运行。
     */
    private String action = "";

    /**
     * 显式授权标记。rebuild 必须同时传入 {@link TestDataRebuildRunner#CONFIRM_TOKEN}，
     * 仅配置 action 不足以触发删除。
     */
    private String confirm = "";

    /** 允许执行重建的数据库名白名单 */
    private List<String> allowedDatabases = new ArrayList<>(List.of("fs_user", "fs_user_test"));

    /** 生成的用户数量（不含 admin / guest / operator 三个固定账号） */
    private int userCount = 20;

    /** 单个动作执行完毕后是否退出进程（命令行一次性执行场景） */
    private boolean exitAfterRun = true;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getConfirm() {
        return confirm;
    }

    public void setConfirm(String confirm) {
        this.confirm = confirm;
    }

    public List<String> getAllowedDatabases() {
        return allowedDatabases;
    }

    public void setAllowedDatabases(List<String> allowedDatabases) {
        this.allowedDatabases = allowedDatabases;
    }

    public int getUserCount() {
        return userCount;
    }

    public void setUserCount(int userCount) {
        this.userCount = userCount;
    }

    public boolean isExitAfterRun() {
        return exitAfterRun;
    }

    public void setExitAfterRun(boolean exitAfterRun) {
        this.exitAfterRun = exitAfterRun;
    }
}
