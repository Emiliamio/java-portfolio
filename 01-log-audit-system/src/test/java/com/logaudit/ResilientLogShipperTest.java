package com.logaudit;

import com.logaudit.entity.LogEntry;
import com.logaudit.mapper.LogEntryMapper;
import com.logaudit.service.ResilientLogShipper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResilientLogShipperTest {

    private LogEntryMapper logEntryMapper;
    private ResilientLogShipper shipper;

    @BeforeEach
    void setUp() {
        logEntryMapper = mock(LogEntryMapper.class);
        shipper = new ResilientLogShipper(logEntryMapper);
    }

    @Test
    @DisplayName("正常场景：数据库可用时，日志正常持久化成功")
    void testShipLogSuccess() {
        LogEntry entry = LogEntry.builder()
                .username("alice")
                .operation("USER_QUERY")
                .operationResult("SUCCESS")
                .severity("INFO")
                .timestamp(LocalDateTime.now())
                .traceId("trace-test-1001")
                .build();

        boolean result = shipper.shipLog(entry);

        assertTrue(result);
        verify(logEntryMapper, times(1)).batchInsert(Collections.singletonList(entry));
        assertEquals(0, shipper.getWalSize());
    }

    @Test
    @DisplayName("熔断降级场景：底层数据库抛出异常时，自动降级写入本地 WAL 缓冲池")
    void testShipLogFallbackToWal() {
        LogEntry entry = LogEntry.builder()
                .username("bob")
                .operation("PAYMENT")
                .operationResult("FAIL")
                .severity("CRITICAL")
                .timestamp(LocalDateTime.now())
                .traceId("trace-test-1002")
                .build();

        // 模拟调用 fallback 方法
        boolean result = shipper.walFallback(entry, new RuntimeException("ClickHouse Connection Refused"));

        assertFalse(result);
        assertEquals(1, shipper.getWalSize());
        assertEquals(1, shipper.getFallbackCount());
    }
}
