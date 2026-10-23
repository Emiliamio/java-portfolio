package com.logaudit.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis + 进程内写穿缓存的 JWT 登出黑名单管理。
 *
 * 当用户登出时，将剩余有效期的 Token 写入 Redis 黑名单，并同步写穿至本地哈希缓存。
 * 安全规范：本地黑名单以 SHA-256 摘要作为 Key 存储，杜绝内存中留存 JWT 明文令牌。
 *
 * 容灾与残余风险说明：
 * Redis 正常时作为分布式权威共享层；Redis 故障时回退本地写穿黑名单判定，绝不无条件放行（避免 Fail-Open）。
 * 残余风险：多实例部署时，若 Redis 发生故障，A 实例登出的 Token 仅在 A 实例本地即时吊销，B 实例在 Redis 恢复前可能仍视为有效。
 * 这是在共享存储不可用时权衡单实例防御与业务可用性的接受边界。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "auditvault:blacklist:";
    private static final int LOCAL_CAPACITY = 5000;

    private final StringRedisTemplate redisTemplate;

    /**
     * 进程内写穿黑名单：Key = SHA-256(token)，Value = 过期时间戳（毫秒）
     */
    private final ConcurrentHashMap<String, Long> localBlacklist = new ConcurrentHashMap<>();

    /**
     * 将 Token 加入黑名单，过期时间为 Token 剩余有效期。同时写穿本地与 Redis。
     */
    public void blacklistToken(String token, long remainingMs) {
        if (token == null || token.isBlank() || remainingMs <= 0) {
            return;
        }

        // 1. 写穿本地缓存（SHA-256 存储）
        long expireAt = System.currentTimeMillis() + remainingMs;
        String hashedKey = hashToken(token);
        localBlacklist.put(hashedKey, expireAt);
        evictIfNecessary();

        // 2. 写入 Redis
        try {
            String key = BLACKLIST_PREFIX + token;
            redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(remainingMs));
            log.info("Token added to Redis and local blacklist, TTL: {}ms", remainingMs);
        } catch (Exception e) {
            log.warn("Failed to add token to Redis blacklist, retained in local blacklist: {}", e.getMessage());
        }
    }

    /**
     * 校验 Token 是否已被吊销。
     * 策略：先查本地 -> 命中且未过期即返回 true -> 查 Redis -> Redis 异常时回退本地结果（不无条件放行）。
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String hashedKey = hashToken(token);
        long now = System.currentTimeMillis();

        // 1. 先查本地写穿缓存
        Long localExpire = localBlacklist.get(hashedKey);
        if (localExpire != null) {
            if (localExpire > now) {
                return true;
            } else {
                localBlacklist.remove(hashedKey);
            }
        }

        // 2. 查询 Redis 分布式黑名单
        try {
            String key = BLACKLIST_PREFIX + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("[BLACKLIST_REDIS_DOWN] Failed to check Redis token blacklist, fallback to local blacklist: {}", e.getMessage());
            // Redis 异常时以本地检查结果为准，未命中本地则为 false（绝不能在无条件场景抛错或无根据判定）
            return false;
        }
    }

    public String hashToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public ConcurrentHashMap<String, Long> getLocalBlacklist() {
        return localBlacklist;
    }

    private void evictIfNecessary() {
        long now = System.currentTimeMillis();
        // 清理已过期数据
        localBlacklist.entrySet().removeIf(entry -> entry.getValue() <= now);

        // 超出容量上限按最快过期时间淘汰
        if (localBlacklist.size() > LOCAL_CAPACITY) {
            String minKey = null;
            long minExpiry = Long.MAX_VALUE;
            for (Map.Entry<String, Long> entry : localBlacklist.entrySet()) {
                if (entry.getValue() < minExpiry) {
                    minExpiry = entry.getValue();
                    minKey = entry.getKey();
                }
            }
            if (minKey != null) {
                localBlacklist.remove(minKey);
            }
        }
    }
}
