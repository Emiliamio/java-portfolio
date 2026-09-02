package com.logaudit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IpReputationService 单元测试 — 验证 IP 威胁信誉度累加与自动熔断封禁机制。
 */
@ExtendWith(MockitoExtension.class)
class IpReputationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private IpReputationService ipReputationService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testRecordThreatIncidentBelowThreshold() {
        when(valueOperations.increment(eq("audit:threat:score:192.168.10.5"), eq(40L))).thenReturn(40L);

        boolean banned = ipReputationService.recordThreatIncident("192.168.10.5", 40, "SQL Injection Attempt");

        assertFalse(banned);
        verify(redisTemplate, times(1)).expire(eq("audit:threat:score:192.168.10.5"), any(Duration.class));
        verify(valueOperations, never()).set(startsWith("audit:threat:banned:"), anyString(), any(Duration.class));
    }

    @Test
    void testRecordThreatIncidentTriggersAutoBan() {
        // 累积分数达到 85 (>= 80 阈值)
        when(valueOperations.increment(eq("audit:threat:score:192.168.10.99"), eq(50L))).thenReturn(85L);

        boolean banned = ipReputationService.recordThreatIncident("192.168.10.99", 50, "Repeated RCE Exploit");

        assertTrue(banned);
        verify(valueOperations, times(1)).set(eq("audit:threat:banned:192.168.10.99"), anyString(), any(Duration.class));
    }

    @Test
    void testIsIpBanned() {
        when(redisTemplate.hasKey("audit:threat:banned:192.168.10.99")).thenReturn(true);
        when(redisTemplate.hasKey("audit:threat:banned:192.168.10.1")).thenReturn(false);

        assertTrue(ipReputationService.isIpBanned("192.168.10.99"));
        assertFalse(ipReputationService.isIpBanned("192.168.10.1"));
    }

    @Test
    void testUnbanIp() {
        ipReputationService.unbanIp("192.168.10.99");
        verify(redisTemplate, times(1)).delete("audit:threat:banned:192.168.10.99");
        verify(redisTemplate, times(1)).delete("audit:threat:score:192.168.10.99");
    }
}
