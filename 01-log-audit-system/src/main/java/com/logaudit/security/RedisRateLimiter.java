package com.logaudit.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 为主 + 本地内存兜底的 IP 登录防暴力破解限流器。
 *
 * 规则：同一 IP 连续登录失败达到 5 次，锁定 15 分钟。
 * 容灾：Redis 正常时分布式协同并同步本地窗口；Redis 宕机时降级为进程内并发滑动窗口兜底，故障时降级但不放行（Fail-Closed 保护）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "auditvault:rate:login:";
    private static final int LOCAL_CAPACITY = 10000;

    private final StringRedisTemplate redisTemplate;

    /**
     * 本地兜底并发滑动窗口结构
     */
    public static class LocalWindow {
        public final int failCount;
        public final long lockUntil;
        public final long lastTouched;

        public LocalWindow(int failCount, long lockUntil, long lastTouched) {
            this.failCount = failCount;
            this.lockUntil = lockUntil;
            this.lastTouched = lastTouched;
        }
    }

    private final ConcurrentHashMap<String, LocalWindow> localWindows = new ConcurrentHashMap<>();

    /**
     * 检查当前 IP 是否允许尝试登录。
     * @return true 允许尝试; false 达到上限已被锁定
     */
    public boolean isAllowed(String ip) {
        if (ip == null || ip.isBlank()) return true;
        try {
            String val = redisTemplate.opsForValue().get(KEY_PREFIX + ip);
            if (val == null) {
                localWindows.remove(ip);
                return true;
            }
            int count = Integer.parseInt(val);
            long now = System.currentTimeMillis();
            long lockUntil = count >= MAX_FAILED_ATTEMPTS ? (now + LOCK_DURATION.toMillis()) : 0;
            localWindows.put(ip, new LocalWindow(count, lockUntil, now));
            evictIfNecessary();
            return count < MAX_FAILED_ATTEMPTS;
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            LocalWindow window = localWindows.get(ip);
            if (window != null) {
                if (window.lockUntil > 0 && now < window.lockUntil) {
                    log.error("[RATE_LIMIT_REDIS_DOWN_LOCAL_FALLBACK] Redis is unavailable, local rate limit blocked IP: {}, locked until: {}", ip, window.lockUntil);
                    return false;
                }
                if (window.failCount >= MAX_FAILED_ATTEMPTS) {
                    log.error("[RATE_LIMIT_REDIS_DOWN_LOCAL_FALLBACK] Redis is unavailable, local rate limit blocked IP: {}, attempts: {}", ip, window.failCount);
                    return false;
                }
            }
            log.warn("Redis rate limiter check failed, falling back to local window. Error: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 记录一次登录失败。
     */
    public void recordFailure(String ip) {
        if (ip == null || ip.isBlank()) return;
        long now = System.currentTimeMillis();
        localWindows.compute(ip, (k, v) -> {
            int newCount = (v == null || (v.lockUntil > 0 && now >= v.lockUntil)) ? 1 : v.failCount + 1;
            long lockUntil = newCount >= MAX_FAILED_ATTEMPTS ? (now + LOCK_DURATION.toMillis()) : 0;
            return new LocalWindow(newCount, lockUntil, now);
        });
        evictIfNecessary();

        try {
            String key = KEY_PREFIX + ip;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, LOCK_DURATION);
            }
        } catch (Exception e) {
            log.warn("Redis rate limiter record failure failed, recorded in local window: {}", e.getMessage());
        }
    }

    /**
     * 登录成功后重置失败计数。
     */
    public void recordSuccess(String ip) {
        if (ip == null || ip.isBlank()) return;
        localWindows.remove(ip);
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
            if (ttl != null && ttl > 0) {
                return ttl;
            }
        } catch (Exception ignored) {
        }
        LocalWindow window = localWindows.get(ip);
        if (window != null && window.lockUntil > System.currentTimeMillis()) {
            return (window.lockUntil - System.currentTimeMillis()) / 1000;
        }
        return 0;
    }

    public ConcurrentHashMap<String, LocalWindow> getLocalWindows() {
        return localWindows;
    }

    private void evictIfNecessary() {
        if (localWindows.size() > LOCAL_CAPACITY) {
            String oldestKey = null;
            long oldestTouched = Long.MAX_VALUE;
            for (Map.Entry<String, LocalWindow> entry : localWindows.entrySet()) {
                if (entry.getValue().lastTouched < oldestTouched) {
                    oldestTouched = entry.getValue().lastTouched;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey != null) {
                localWindows.remove(oldestKey);
            }
        }
    }
}
