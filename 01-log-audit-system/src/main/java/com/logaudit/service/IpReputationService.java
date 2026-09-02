package com.logaudit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 动态 IP 威胁信誉度评分与自动熔断黑名单引擎 (Dynamic IP Threat Reputation & Auto-Ban Armor)。
 *
 * 核心机制：
 * 1. 动态评分累加：不同攻击类型增加不同威胁分 (SQLi: +40, RCE: +50, BruteForce: +25, PathTraversal: +35)
 * 2. 威胁分窗口：Redis Key 默认保留 10 分钟滑动窗口
 * 3. 自动熔断封禁 (Auto-Ban)：当威胁分累积 >= 80 分时，秒级自动熔断注入黑名单，TTL 1小时
 * 4. 容灾降级：Redis 异常时采用 Fail-Open，避免误伤业务
 */
@Service
public class IpReputationService {

    private static final Logger log = LoggerFactory.getLogger(IpReputationService.class);

    private static final String PREFIX_SCORE = "audit:threat:score:";
    private static final String PREFIX_BANNED = "audit:threat:banned:";
    private static final int AUTO_BAN_THRESHOLD = 80;
    private static final long BAN_DURATION_SECONDS = 3600; // 1 小时自动熔断

    private final StringRedisTemplate redisTemplate;

    public IpReputationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 记录威胁事件并累加威胁信誉分
     *
     * @return 是否触发了自动封禁
     */
    public boolean recordThreatIncident(String ip, int scoreToAdd, String reason) {
        if (ip == null || ip.isBlank() || "127.0.0.1".equals(ip) || "localhost".equalsIgnoreCase(ip)) {
            return false;
        }

        try {
            if (redisTemplate == null) return false;

            String scoreKey = PREFIX_SCORE + ip;
            Long currentScore = redisTemplate.opsForValue().increment(scoreKey, scoreToAdd);
            redisTemplate.expire(scoreKey, Duration.ofMinutes(10));

            log.warn("[THREAT_REPUTATION] IP={}, AddedScore={}, TotalScore={}, Reason={}",
                    ip, scoreToAdd, currentScore, reason);

            if (currentScore != null && currentScore >= AUTO_BAN_THRESHOLD) {
                autoBanIp(ip, BAN_DURATION_SECONDS, "Exceeded threat threshold (" + currentScore + " >= " + AUTO_BAN_THRESHOLD + ")");
                return true;
            }
        } catch (Exception ex) {
            log.warn("[THREAT_REPUTATION_FAIL_OPEN] Failed to update threat score for IP {}: {}", ip, ex.getMessage());
        }
        return false;
    }

    /**
     * 校验当前 IP 是否被自动熔断黑名单拦截
     */
    public boolean isIpBanned(String ip) {
        if (ip == null || ip.isBlank()) return false;
        try {
            if (redisTemplate == null) return false;
            Boolean banned = redisTemplate.hasKey(PREFIX_BANNED + ip);
            return Boolean.TRUE.equals(banned);
        } catch (Exception ex) {
            log.warn("[THREAT_REPUTATION_FAIL_OPEN] Check banned status failed for IP {}: {}", ip, ex.getMessage());
            return false;
        }
    }

    /**
     * 自动封禁 IP
     */
    public void autoBanIp(String ip, long durationSeconds, String reason) {
        try {
            if (redisTemplate == null) return;
            String banKey = PREFIX_BANNED + ip;
            redisTemplate.opsForValue().set(banKey, reason, Duration.ofSeconds(durationSeconds));
            log.error("[SOC_AUTO_BAN_ACTIVE] IP {} is AUTO-BANNED for {}s! Reason: {}", ip, durationSeconds, reason);
        } catch (Exception ex) {
            log.warn("[THREAT_REPUTATION_FAIL_OPEN] Auto ban failed for IP {}: {}", ip, ex.getMessage());
        }
    }

    /**
     * 手动解封 IP
     */
    public void unbanIp(String ip) {
        try {
            if (redisTemplate == null) return;
            redisTemplate.delete(PREFIX_BANNED + ip);
            redisTemplate.delete(PREFIX_SCORE + ip);
            log.info("[SOC_MANUAL_UNBAN] IP {} unbanned manually.", ip);
        } catch (Exception ex) {
            log.warn("Unban failed for IP {}: {}", ip, ex.getMessage());
        }
    }

    /**
     * 获取当前 IP 累积分数
     */
    public int getIpThreatScore(String ip) {
        try {
            if (redisTemplate == null) return 0;
            String val = redisTemplate.opsForValue().get(PREFIX_SCORE + ip);
            return val != null ? Integer.parseInt(val) : 0;
        } catch (Exception ex) {
            return 0;
        }
    }
}
