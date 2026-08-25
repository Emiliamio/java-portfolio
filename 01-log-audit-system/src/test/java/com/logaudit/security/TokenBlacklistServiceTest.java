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
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testBlacklistTokenSetsKeyWithTTL() {
        tokenBlacklistService.blacklistToken("sample.token.here", 60000);

        verify(valueOperations, times(1)).set(
                eq("auditvault:blacklist:sample.token.here"),
                eq("revoked"),
                eq(Duration.ofMillis(60000))
        );
    }

    @Test
    void testIsBlacklistedReturnsTrue() {
        when(redisTemplate.hasKey("auditvault:blacklist:revoked.token")).thenReturn(true);

        assertTrue(tokenBlacklistService.isBlacklisted("revoked.token"));
    }

    @Test
    void testIsBlacklistedReturnsFalseForValidToken() {
        when(redisTemplate.hasKey("auditvault:blacklist:valid.token")).thenReturn(false);

        assertFalse(tokenBlacklistService.isBlacklisted("valid.token"));
    }

    @Test
    void testFallbackWhenRedisExceptionReturnsFalse() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        assertFalse(tokenBlacklistService.isBlacklisted("any.token"));
    }
}