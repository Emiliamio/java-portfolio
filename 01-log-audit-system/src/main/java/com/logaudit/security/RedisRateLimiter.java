package com.logaudit.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的 IP 登录防暴力破解限流器。
 *
 * 规则：同一 IP 连续登录失败达到 5 次，锁定 15 分钟。
 * 容灾：Redis 不可用时自动降级放行（Fail-Open），不影响业务正常运转。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "auditvault:rate:login:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 检查当前 IP 是否允许尝试登录。
     * @return true 允许尝试; false 达到上限已被锁定
     */
    public boolean isAllowed(String ip) {
        if (ip == null || ip.isBlank()) return true;
        try {
            String val = redisTemplate.opsForValue().get(KEY_PREFIX + ip);
            if (val == null) return true;
            int count = Integer.parseInt(val);
            return count < MAX_FAILED_ATTEMPTS;
        } catch (Exception e) {
            log.warn("Redis rate limiter check failed, falling back to allow. Error: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 记录一次登录失败。
     */
    public void recordFailure(String ip) {
        if (ip == null || ip.isBlank()) return;
        try {
            String key = KEY_PREFIX + ip;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, LOCK_DURATION);
            }
        } catch (Exception e) {
            log.warn("Redis rate limiter record failure failed: {}", e.getMessage());
        }
    }

    /**
     * 登录成功后重置失败计数。
     */
    public void recordSuccess(String ip) {
        if (ip == null || ip.isBlank()) return;
        try {
            redisTemplate.delete(KEY_PREFIX + ip);
        } catch (Exception e) {
            log.warn("Redis rate limiter reset failed: {}", e.getMessage());
        }
    }

    /**
     * 获取剩余锁定秒数（用于前端提示）。
     */
    public long getRemainingLockSeconds(String ip) {
        if (ip == null || ip.isBlank()) return 0;
        try {
            Long ttl = redisTemplate.getExpire(KEY_PREFIX + ip);
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}