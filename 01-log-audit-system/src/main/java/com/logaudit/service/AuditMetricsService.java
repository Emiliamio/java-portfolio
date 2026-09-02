package com.logaudit.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 生产级可观测性业务度量服务
 * 注册业务核心指标至 Micrometer，供 Prometheus / Grafana 采集
 */
@Service
public class AuditMetricsService {

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private Counter webhookIngestCounter;
    private Counter batchIngestCounter;
    private Counter slowQueryCounter;
    private Timer exportLatencyTimer;

    @PostConstruct
    public void init() {
        if (meterRegistry != null) {
            webhookIngestCounter = Counter.builder("auditvault.logs.ingested")
                    .tag("source", "webhook")
                    .description("通过 Webhook 实时摄取的日志总条数")
                    .register(meterRegistry);

            batchIngestCounter = Counter.builder("auditvault.logs.ingested")
                    .tag("source", "batch")
                    .description("通过批量导入摄取的日志总条数")
                    .register(meterRegistry);

            slowQueryCounter = Counter.builder("auditvault.slow_queries.total")
                    .description("系统检测到的慢 SQL 查询总次数")
                    .register(meterRegistry);

            exportLatencyTimer = Timer.builder("auditvault.export.latency")
                    .description("SXSSF 流式导出 Excel 耗时统计")
                    .register(meterRegistry);
        }
    }

    public void recordWebhookIngest(int count) {
        if (webhookIngestCounter != null) {
            webhookIngestCounter.increment(count);
        }
    }

    public void recordBatchIngest(int count) {
        if (batchIngestCounter != null) {
            batchIngestCounter.increment(count);
        }
    }

    public void recordSlowQuery() {
        if (slowQueryCounter != null) {
            slowQueryCounter.increment();
        }
    }

    public void recordExportLatency(long durationMs) {
        if (exportLatencyTimer != null) {
            exportLatencyTimer.record(durationMs, TimeUnit.MILLISECONDS);
        }
    }
}
