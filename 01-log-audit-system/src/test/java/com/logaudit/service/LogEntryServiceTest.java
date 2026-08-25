package com.logaudit.service;

import com.logaudit.entity.LogEntry;
import com.logaudit.mapper.LogEntryMapper;
import com.logaudit.service.impl.LogEntryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogEntryServiceTest {

    @Mock
    private LogEntryMapper logEntryMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HyperLogLogOperations<String, String> hyperLogLogOperations;

    @InjectMocks
    private LogEntryServiceImpl logEntryService;

    private LogEntry sampleEntry;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);
        sampleEntry = new LogEntry(
                1L,
                LocalDateTime.of(2025, 1, 15, 8, 0, 1),
                "192.168.1.10",
                "admin",
                "LOGIN",
                "SUCCESS",
                "User admin logged in",
                "INFO",
                "auth-service.log",
                LocalDateTime.now()
        );
    }

    @Test
    void testSearchLogsPagination() {
        when(logEntryMapper.countByConditions(any(), any(), any(), any(), any()))
                .thenReturn(45L);
        when(logEntryMapper.findByConditions(any(), any(), any(), any(), any(), eq(20), eq(20)))
                .thenReturn(List.of(sampleEntry));

        Map<String, Object> result = logEntryService.searchLogs(
                null, null, "192.168.1.10", "LOGIN", "INFO", 2, 20
        );

        assertNotNull(result);
        assertEquals(45L, result.get("total"));
        assertEquals(2, result.get("page"));
        assertEquals(20, result.get("pageSize"));
        @SuppressWarnings("unchecked")
        List<LogEntry> records = (List<LogEntry>) result.get("records");
        assertEquals(1, records.size());
        assertEquals("admin", records.get(0).getUsername());
    }

    @Test
    void testGetDetail() {
        when(logEntryMapper.findById(1L)).thenReturn(sampleEntry);

        LogEntry entry = logEntryService.getDetail(1L);
        assertNotNull(entry);
        assertEquals(1L, entry.getId());
        assertEquals("192.168.1.10", entry.getIpAddress());
    }

    @Test
    void testAsyncBatchImport() {
        when(logEntryMapper.batchInsert(anyList())).thenReturn(1);

        logEntryService.asyncBatchImport(List.of(sampleEntry));

        verify(logEntryMapper, times(1)).batchInsert(anyList());
    }

    @Test
    void testTodayStatsDirect() {
        Map<String, Object> todayData = Map.of("total", 50L, "abnormal", 5L, "uniqueIps", 12L);
        when(logEntryMapper.todayStats()).thenReturn(todayData);

        Map<String, Object> stats = logEntryService.todayStats();
        assertNotNull(stats);
        assertEquals(50L, stats.get("total"));
        assertEquals(5L, stats.get("abnormal"));
        assertEquals(12L, stats.get("uniqueIps"));
    }

    @Test
    void testTodayStatsFallbackWhenTodayEmpty() {
        when(logEntryMapper.todayStats()).thenReturn(Map.of("total", 0L, "abnormal", 0L, "uniqueIps", 0L));
        when(logEntryMapper.overallStats()).thenReturn(Map.of("total", 100L, "abnormal", 10L, "uniqueIps", 25L));

        Map<String, Object> stats = logEntryService.todayStats();
        assertNotNull(stats);
        assertEquals(100L, stats.get("total"));
        assertEquals(10L, stats.get("abnormal"));
        assertEquals(25L, stats.get("uniqueIps"));
    }

    @Test
    void testExportLogsGeneratesExcelBytes() {
        when(logEntryMapper.countByConditions(any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(logEntryMapper.findByConditions(any(), any(), any(), any(), any(), eq(0), anyInt()))
                .thenReturn(List.of(sampleEntry));

        byte[] excelBytes = logEntryService.exportLogs(null, null, null, null, null);

        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0, "Excel output byte array should not be empty");
    }
}