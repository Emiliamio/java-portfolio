package com.logaudit.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的 JWT 登出黑名单管理。
 *
 * 当用户登出时，将剩余有效期的 Token 写入 Redis 黑名单，解决无状态 JWT 无法即时吊销的安全隐患。
 * 容灾：Redis 宕机时自动降级放行，保证基础认证可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "auditvault:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 将 Token 加入黑名单，过期时间为 Token 剩余有效期。
     */
    public void blacklistToken(String token, long remainingMs) {
        if (token == null || token.isBlank() || remainingMs <= 0) {
            return;
        }
        try {
            String key = BLACKLIST_PREFIX + token;
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(remainingMs));
            log.info("Token added to Redis blacklist, TTL: {}ms", remainingMs);
        } catch (Exception e) {
            log.warn("Failed to add token to Redis blacklist: {}", e.getMessage());
        }
    }

    /**
     * 校验 Token 是否已被吊销。
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String key = BLACKLIST_PREFIX + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Failed to check Redis token blacklist, fallback to valid: {}", e.getMessage());
            return false;
        }
    }
}