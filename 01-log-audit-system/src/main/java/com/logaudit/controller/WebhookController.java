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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Webhook 实时日志采集端点 — 供 Logback HTTP Appender、微服务、CI/CD 及外部监控平台实时推送日志。
 *
 * 特性：
 * 1. 双格式自适应：自动支持单条 JSON 对象与多条 JSON 数组；
 * 2. 专用令牌安全鉴权：基于 X-Audit-Token 或 Authorization: Bearer；
 * 3. 极速响应非阻塞：< 5ms 返回 202 Accepted，全量异步落库与 Redis HyperLogLog 统计。
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

    @Value("${app.webhook.secret-key:auditvault-webhook-default-secret-token-2026}")
    private String webhookSecretKey;

    @PostMapping
    @Operation(summary = "实时摄入日志", description = "接收单条或批量 JSON 格式日志，经令牌校验后异步批量写入数据库并更新活跃 IP 统计")
    public ResponseEntity<Map<String, Object>> ingestLogs(

            @RequestHeader(value = "X-Audit-Token", required = false) String auditTokenHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) JsonNode payload,
            HttpServletRequest request
    ) {
        String clientIp = resolveClientIp(request);

        // 1. 验证 Webhook 接入 Token
        if (!validateToken(auditTokenHeader, authHeader)) {
            log.warn("Webhook log ingestion rejected: invalid or missing token from IP {}", clientIp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "未授权：无效或缺失 X-Audit-Token 接入令牌"
            ));
        }

        // 2. 校验请求体
        if (payload == null || payload.isNull() || payload.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "请求体不能为空"
            ));
        }

        // 3. 自适应解析（支持 Array 批量与 Object 单条）
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

        // 4. 优先推入 Kafka 分布式削峰队列，若未启用或异常则自动降级为本地线程池
        boolean kafkaQueued = false;
        if (kafkaLogProducer != null && kafkaLogProducer.isAvailable()) {
            kafkaQueued = kafkaLogProducer.sendLogs(dtoList);
        }

        if (!kafkaQueued) {
            // 5. 本地线程池异步提交落库与 Redis HyperLogLog 极速更新
            List<LogEntry> entries = dtoList.stream()
                    .map(dto -> dto.toLogEntry(clientIp))
                    .toList();
            logEntryService.asyncBatchImport(entries);
        }

        // 6. 实时威胁侦测与 WebSocket 广播
        if (threatAlertNotifier != null) {
            for (WebhookLogDto dto : dtoList) {
                threatAlertNotifier.notifyIfThreat(dto);
            }
        }

        // 7. 记录审计轨迹与可观测性度量指标
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

    private boolean validateToken(String auditToken, String authorization) {
        if (webhookSecretKey == null || webhookSecretKey.isBlank()) {
            return true; // 若未配置则放行
        }
        if (auditToken != null && webhookSecretKey.equals(auditToken.trim())) {
            return true;
        }
        if (authorization != null) {
            String token = authorization.trim();
            if (token.startsWith("Bearer ") || token.startsWith("bearer ")) {
                token = token.substring(7).trim();
            }
            return webhookSecretKey.equals(token);
        }
        return false;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
