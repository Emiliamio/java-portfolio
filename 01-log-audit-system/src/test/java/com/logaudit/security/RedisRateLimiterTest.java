package com.logaudit.security;

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

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testIsAllowedWhenNoFailures() {
        when(valueOperations.get("auditvault:rate:login:127.0.0.1")).thenReturn(null);

        assertTrue(rateLimiter.isAllowed("127.0.0.1"));
    }

    @Test
    void testIsAllowedWhenUnderLimit() {
        when(valueOperations.get("auditvault:rate:login:127.0.0.1")).thenReturn("3");

        assertTrue(rateLimiter.isAllowed("127.0.0.1"));
    }

    @Test
    void testIsBlockedWhenReachedLimit() {
        when(valueOperations.get("auditvault:rate:login:127.0.0.1")).thenReturn("5");

        assertFalse(rateLimiter.isAllowed("127.0.0.1"));
    }

    @Test
    void testRecordFailureIncrementsCount() {
        when(valueOperations.increment("auditvault:rate:login:127.0.0.1")).thenReturn(1L);

        rateLimiter.recordFailure("127.0.0.1");

        verify(valueOperations, times(1)).increment("auditvault:rate:login:127.0.0.1");
        verify(redisTemplate, times(1)).expire(eq("auditvault:rate:login:127.0.0.1"), any(Duration.class));
    }

    @Test
    void testRecordSuccessClearsCount() {
        when(redisTemplate.delete("auditvault:rate:login:127.0.0.1")).thenReturn(true);

        rateLimiter.recordSuccess("127.0.0.1");

        verify(redisTemplate, times(1)).delete("auditvault:rate:login:127.0.0.1");
    }

    @Test
    void testFallbackWhenRedisException() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis connection lost"));

        // Should gracefully fallback to allowed (Fail-Open)
        assertTrue(rateLimiter.isAllowed("127.0.0.1"));
    }
}