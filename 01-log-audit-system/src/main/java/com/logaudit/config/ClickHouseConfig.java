package com.logaudit.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * ClickHouse 列式分析引擎数据源配置与自动表结构初始化
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

    @PostConstruct
    public void initTable() {
        if (!enabled) {
            log.info("ClickHouse OLAP engine is disabled in configuration.");
            return;
        }
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            String ddl = """
                CREATE TABLE IF NOT EXISTS audit_log_local (
                    id UUID DEFAULT generateUUIDv4(),
                    timestamp DateTime64(3) DEFAULT now64(3),
                    ip_address LowCardinality(String) DEFAULT '127.0.0.1',
                    username LowCardinality(String) DEFAULT 'anonymous',
                    operation LowCardinality(String) DEFAULT 'SYSTEM',
                    operation_result LowCardinality(String) DEFAULT 'SUCCESS',
                    detail String DEFAULT '',
                    severity LowCardinality(String) DEFAULT 'INFO',
                    source_file LowCardinality(String) DEFAULT 'kafka-stream',
                    trace_id String DEFAULT '',
                    created_at DateTime DEFAULT now()
                ) ENGINE = MergeTree()
                PARTITION BY toYYYYMM(timestamp)
                ORDER BY (timestamp, severity, ip_address)
                SETTINGS index_granularity = 8192
            """;
            stmt.execute(ddl);
            log.info("ClickHouse audit_log_local table initialized successfully.");
        } catch (Exception e) {
            log.warn("ClickHouse table initialization skipped: {}", e.getMessage());
        }
    }

    public Connection getConnection() throws Exception {
        if (!enabled) {
            throw new IllegalStateException("ClickHouse is not enabled in application configuration.");
        }
        return DriverManager.getConnection(url, user, password);
    }
}
