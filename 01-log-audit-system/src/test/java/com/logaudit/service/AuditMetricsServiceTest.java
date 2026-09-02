package com.logaudit.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class AuditMetricsServiceTest {

    private MeterRegistry meterRegistry;
    private AuditMetricsService auditMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        auditMetricsService = new AuditMetricsService();
        ReflectionTestUtils.setField(auditMetricsService, "meterRegistry", meterRegistry);
        auditMetricsService.init();
    }

    @Test
    void testRecordMetrics() {
        auditMetricsService.recordWebhookIngest(5);
        auditMetricsService.recordBatchIngest(10);
        auditMetricsService.recordSlowQuery();
        auditMetricsService.recordExportLatency(150);

        assertEquals(5.0, meterRegistry.get("auditvault.logs.ingested").tag("source", "webhook").counter().count());
        assertEquals(10.0, meterRegistry.get("auditvault.logs.ingested").tag("source", "batch").counter().count());
        assertEquals(1.0, meterRegistry.get("auditvault.slow_queries.total").counter().count());
        assertNotNull(meterRegistry.get("auditvault.export.latency").timer());
    }
}
