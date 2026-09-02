package com.logaudit.interceptor;

import com.logaudit.service.AuditMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.util.Properties;

/**
 * MyBatis 慢 SQL 监控与预警拦截器
 * 自动拦截执行耗时超过阈值的 SQL 语句，记录告警日志并递增 Prometheus 慢查询度量指标
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class SlowSqlInterceptor implements Interceptor {

    @Value("${app.mybatis.slow-sql-threshold-ms:200}")
    private long slowSqlThresholdMs;

    @Autowired(required = false)
    private AuditMetricsService auditMetricsService;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            if (costTime >= slowSqlThresholdMs) {
                try {
                    StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
                    String boundSql = statementHandler.getBoundSql().getSql();
                    String cleanSql = boundSql.replaceAll("\\s+", " ").trim();
                    log.warn("[SLOW_SQL_ALERT] SQL 执行耗时过长: cost={}ms, threshold={}ms, sql={}", 
                            costTime, slowSqlThresholdMs, cleanSql);
                    if (auditMetricsService != null) {
                        auditMetricsService.recordSlowQuery();
                    }
                } catch (Exception e) {
                    log.debug("提取慢 SQL 详情失败: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
