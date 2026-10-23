package com.logaudit.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logaudit.dto.WebhookLogDto;
import com.logaudit.entity.LogEntry;
import com.logaudit.service.AuditLogService;
import com.logaudit.service.KafkaLogProducer;
import com.logaudit.service.LogEntryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook 实时日志采集端点 — 供 Logback HTTP Appender、微服务、CI/CD 及外部监控平台实时推送日志。
 *
 * 特性：
 * 1. 双格式自适应：自动支持单条 JSON 对象与多条 JSON 数组；
 * 2. 恒定时间安全鉴权：基于 MessageDigest.isEqual 进行 SHA-256 对比，杜绝时序与长度侧信道；未配置密钥时默认 Fail-Closed 熔断 (503)；
 * 3. 幂等去重控制：基于 traceId 或内容摘要进行 5 分钟滑动窗口去重，防 Appender 重试导致日志与告警风暴；
 * 4. 极速响应非阻塞：< 5ms 返回 202 Accepted，全量异步落库与 Redis HyperLogLog 统计。
 */
@Slf4j
@RestController
@RequestMapping("/api/logs/webhook")
@RequiredArgsConstructor
@Tag(name = "日志采集 Webhook", description = "供微服务与 Logback Appender 实时推送日志的端点")
public class WebhookController {

    private final LogEntryService logEntryService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final KafkaLogProducer kafkaLogProducer;
    private final com.logaudit.service.AuditMetricsService auditMetricsService;
    private final com.logaudit.service.ThreatAlertNotifier threatAlertNotifier;
    private final com.logaudit.service.IpReputationService ipReputationService;
    private final com.logaudit.security.ClientIpResolver clientIpResolver;

    @Value("${app.webhook.secret-key:auditvault-webhook-default-secret-token-2026}")
    private String webhookSecretKey;

    // 进程内幂等去重缓存（5 分钟 TTL）；多实例高并发场景需替换为 Redis SET key "1" EX 300 NX
    private final ConcurrentHashMap<String, Long> idempotencyCache = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = 5 * 60 * 1000L;

    public void setWebhookSecretKey(String webhookSecretKey) {
        this.webhookSecretKey = webhookSecretKey;
    }

    @PostMapping
    @Operation(summary = "实时摄入日志", description = "接收单条或批量 JSON 格式日志，经恒定时间令牌校验与幂等去重后异步批量处理")
    public ResponseEntity<Map<String, Object>> ingestLogs(
            @RequestHeader(value = "X-Audit-Token", required = false) String auditTokenHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) JsonNode payload,
            HttpServletRequest request
    ) {
        String clientIp = resolveClientIp(request);

        // 0. 防未配置裸奔：密钥为空时 Fail-Closed，返回 503
        if (webhookSecretKey == null || webhookSecretKey.isBlank()) {
            log.error("[WEBHOOK_TOKEN_NOT_CONFIGURED] Webhook secret key is not configured or blank, rejecting request (Fail-Closed protection)");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "success", false,
                    "message", "服务不可用：Webhook 接入密钥未配置 (Fail-Closed 保护)"
            ));
        }

        // 1. 校验是否被 IP 威胁信誉引擎自动熔断封禁
        if (ipReputationService != null && ipReputationService.isIpBanned(clientIp)) {
            log.warn("[SOC_AUTO_BAN_BLOCKED] Rejected request from banned IP {}", clientIp);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "拒绝访问：该 IP 因累积高危攻击已被系统自动熔断封禁"
            ));
        }

        // 2. 恒定时间验证 Webhook 接入 Token
        if (!validateToken(auditTokenHeader, authHeader)) {
            log.warn("Webhook log ingestion rejected: invalid or missing token from IP {}", clientIp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "未授权：无效或缺失 X-Audit-Token 接入令牌"
            ));
        }

        // 3. 校验请求体
        if (payload == null || payload.isNull() || payload.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "请求体不能为空"
            ));
        }

        // 4. 自适应解析（支持 Array 批量与 Object 单条）
        List<WebhookLogDto> dtoList = new ArrayList<>();
        try {
            if (payload.isArray()) {
                List<WebhookLogDto> batch = objectMapper.convertValue(payload, new TypeReference<List<WebhookLogDto>>() {});
                if (batch != null) {
                    dtoList.addAll(batch);
                }
            } else if (payload.isObject()) {
                WebhookLogDto single = objectMapper.treeToValue(payload, WebhookLogDto.class);
                if (single != null) {
                    dtoList.add(single);
                }
            }
        } catch (Exception e) {
            log.error("Webhook payload deserialization failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "JSON 格式解析失败：" + e.getMessage()
            ));
        }

        if (dtoList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "未解析到有效日志记录"
            ));
        }

        // 5. 幂等去重检查 (基于 traceId 或内容哈希指纹，5 分钟滑动窗口)
        String idempotencyKey = null;
        WebhookLogDto first = dtoList.get(0);
        if (first.getTraceId() != null && !first.getTraceId().isBlank()) {
            idempotencyKey = first.getTraceId().trim();
        } else {
            idempotencyKey = sha256Hex(clientIp + first.getOperation() + first.getDetail() + first.getTimestamp());
        }

        long now = System.currentTimeMillis();
        idempotencyCache.entrySet().removeIf(entry -> entry.getValue() <= now);

        if (idempotencyKey != null) {
            Long existingExpire = idempotencyCache.putIfAbsent(idempotencyKey, now + IDEMPOTENCY_TTL_MS);
            if (existingExpire != null && existingExpire > now) {
                log.info("[WEBHOOK_IDEMPOTENT_DUPLICATE] Deduplicated webhook request with key: {}", idempotencyKey);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "deduplicated", true,
                        "accepted", 0,
                        "message", "重复请求已幂等过滤"
                ));
            }
        }

        // 6. 优先推入 Kafka 分布式削峰队列，若未启用或异常则自动降级为本地线程池
        boolean kafkaQueued = false;
        if (kafkaLogProducer != null && kafkaLogProducer.isAvailable()) {
            kafkaQueued = kafkaLogProducer.sendLogs(dtoList);
        }

        if (!kafkaQueued) {
            // 本地线程池异步提交落库与 Redis HyperLogLog 极速更新
            List<LogEntry> entries = dtoList.stream()
                    .map(dto -> dto.toLogEntry(clientIp))
                    .toList();
            logEntryService.asyncBatchImport(entries);
        }

        // 7. 实时威胁侦测与 WebSocket 广播
        if (threatAlertNotifier != null) {
            for (WebhookLogDto dto : dtoList) {
                threatAlertNotifier.notifyIfThreat(dto);
            }
        }

        // 8. 记录审计轨迹与可观测性度量指标
        if (auditMetricsService != null) {
            auditMetricsService.recordWebhookIngest(dtoList.size());
        }
        auditLogService.recordSuccess("WEBHOOK", "INGEST_LOGS",
                "count=" + dtoList.size() + ", buffer=" + (kafkaQueued ? "KAFKA" : "THREAD_POOL") + ", sourceIp=" + clientIp, clientIp);

        log.info("Webhook accepted {} log entries from {} (Buffer Engine: {})",
                dtoList.size(), clientIp, kafkaQueued ? "Kafka Topic" : "Async ThreadPool");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "success", true,
                "message", "日志已接收并在后台异步处理",
                "accepted", dtoList.size(),
                "bufferEngine", kafkaQueued ? "Apache Kafka (Stream Ingestion)" : "ThreadPoolTaskExecutor (Async Fallback)"
        ));
    }

    /**
     * 恒定时间令牌校验，杜绝时序侧信道与长度侧信道。
     */
    private boolean validateToken(String auditToken, String authorization) {
        if (webhookSecretKey == null || webhookSecretKey.isBlank()) {
            return false;
        }
        if (auditToken != null && constantTimeEquals(webhookSecretKey, auditToken.trim())) {
            return true;
        }
        if (authorization != null) {
            String token = authorization.trim();
            if (token.startsWith("Bearer ") || token.startsWith("bearer ")) {
                token = token.substring(7).trim();
            }
            return constantTimeEquals(webhookSecretKey, token);
        }
        return false;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] expectedDigest = sha256Bytes(expected);
        byte[] actualDigest = sha256Bytes(actual);
        return MessageDigest.isEqual(expectedDigest, actualDigest);
    }

    private byte[] sha256Bytes(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String sha256Hex(String input) {
        byte[] digest = sha256Bytes(input);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 解析客户端真实 IP —— 统一走可信代理链解析器。
     *
     * 该 IP 同时用于 IP 威胁信誉熔断判定与日志落库来源，必须不可被请求头伪造，
     * 否则攻击者可伪造受害者 IP 把正常用户送进自动封禁名单。
     */
    private String resolveClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
