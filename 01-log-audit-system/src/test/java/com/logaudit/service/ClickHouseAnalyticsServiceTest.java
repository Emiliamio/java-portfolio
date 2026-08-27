package com.logaudit.service;

import com.logaudit.config.ClickHouseConfig;
import com.logaudit.dto.WebhookLogDto;
import com.logaudit.mapper.LogEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClickHouseAnalyticsServiceTest {

    private ClickHouseConfig clickHouseConfig;
    private LogEntryMapper logEntryMapper;
    private ClickHouseAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        clickHouseConfig = mock(ClickHouseConfig.class);
        logEntryMapper = mock(LogEntryMapper.class);
        analyticsService = new ClickHouseAnalyticsService(clickHouseConfig, logEntryMapper);
    }

    @Test
    void testFallbackToMySQLWhenClickHouseDisabled() {
        when(clickHouseConfig.isEnabled()).thenReturn(false);

        assertFalse(analyticsService.isAvailable());

        Map<String, Object> histogram = analyticsService.getTimeSeriesHistogram("clickhouse");
        assertNotNull(histogram);
        assertTrue(histogram.get("engine").toString().contains("MySQL"));
        assertTrue(histogram.containsKey("buckets"));
        assertTrue(histogram.containsKey("latencyMs"));

        List<?> buckets = (List<?>) histogram.get("buckets");
        assertEquals(12, buckets.size());
    }

    @Test
    void testFacetDistributionFallback() {
        when(clickHouseConfig.isEnabled()).thenReturn(false);

        Map<String, Object> facets = analyticsService.getFacetDistribution("mysql");
        assertNotNull(facets);
        assertTrue(facets.get("engine").toString().contains("MySQL"));
        assertTrue(facets.containsKey("severities"));
        assertTrue(facets.containsKey("topServices"));
    }

    @Test
    void testBatchInsertDoesNotThrowWhenDisabled() {
        when(clickHouseConfig.isEnabled()).thenReturn(false);

        WebhookLogDto dto = new WebhookLogDto();
        dto.setSeverity("ERROR");
        dto.setDetail("Test message");

        assertDoesNotThrow(() -> analyticsService.batchInsert(List.of(dto)));
    }
}
