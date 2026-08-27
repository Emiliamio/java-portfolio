package com.logaudit.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * ClickHouse 列式分析引擎数据源配置与初始化
 */
@Configuration
public class ClickHouseConfig {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseConfig.class);

    @Value("${app.clickhouse.enabled:false}")
    private boolean enabled;

    @Value("${app.clickhouse.url:jdbc:ch://localhost:8123/default}")
    private String url;

    @Value("${app.clickhouse.user:default}")
    private String user;

    @Value("${app.clickhouse.password:}")
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public Connection getConnection() throws Exception {
        if (!enabled) {
            throw new IllegalStateException("ClickHouse is not enabled in application configuration.");
        }
        return DriverManager.getConnection(url, user, password);
    }
}
