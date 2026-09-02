package com.logaudit.service;

import com.alibaba.fastjson2.JSONObject;
import com.logaudit.dto.WebhookLogDto;
import com.logaudit.entity.LogEntry;
import com.logaudit.websocket.ThreatAlertWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 实时威胁告警通知服务。
 *
 * 对摄取的日志进行威胁等级研判：
 * 若日志级别为 CRITICAL 或 HIGH，或 detail 包含 SQL 注入 / 路径穿越等恶意攻击特征，
 * 自动组装富文本威胁告警卡片并通过 WebSocket 广播给前端 SOC Studio。
 */
@Service
public class ThreatAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(ThreatAlertNotifier.class);

    private final ThreatAlertWebSocketHandler webSocketHandler;
    private final IpReputationService ipReputationService;

    public ThreatAlertNotifier(ThreatAlertWebSocketHandler webSocketHandler, IpReputationService ipReputationService) {
        this.webSocketHandler = webSocketHandler;
        this.ipReputationService = ipReputationService;
    }

    /**
     * 针对 Webhook 摄取的日志进行威胁侦测与实时告警，并联动 IP 信誉自动熔断引擎
     */
    public void notifyIfThreat(WebhookLogDto dto) {
        if (dto == null) return;

        boolean isCritical = "CRITICAL".equalsIgnoreCase(dto.getSeverity());
        boolean isHigh = "HIGH".equalsIgnoreCase(dto.getSeverity());
        boolean hasAttackPayload = isAttackPayload(dto.getDetail());

        if (isCritical || isHigh || hasAttackPayload) {
            int threatScore = hasAttackPayload ? 45 : (isCritical ? 35 : 20);
            boolean autoBanned = false;
            if (ipReputationService != null && dto.getIpAddress() != null) {
                autoBanned = ipReputationService.recordThreatIncident(
                        dto.getIpAddress(),
                        threatScore,
                        "Triggered " + (hasAttackPayload ? "Attack Payload" : dto.getSeverity()) + " in " + dto.getOperation()
                );
            }

            JSONObject alert = new JSONObject();
            alert.put("eventType", "THREAT_ALERT");
            alert.put("timestamp", dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            alert.put("ipAddress", dto.getIpAddress());
            alert.put("username", dto.getUsername());
            alert.put("operation", dto.getOperation());
            alert.put("severity", dto.getSeverity() != null ? dto.getSeverity().toUpperCase() : (hasAttackPayload ? "CRITICAL" : "HIGH"));
            alert.put("detail", dto.getDetail());
            alert.put("threatCategory", hasAttackPayload ? "INJECTION_ATTACK" : "SECURITY_ANOMALY");
            alert.put("alertLevel", isCritical ? "P0_EMERGENCY" : "P1_HIGH");
            alert.put("autoBanned", autoBanned);

            String alertJson = alert.toJSONString();
            log.warn("[SOC_REALTIME_THREAT_ALERT] Pushing alert: IP={}, Level={}, Op={}, AutoBanned={}",
                    dto.getIpAddress(), alert.getString("alertLevel"), dto.getOperation(), autoBanned);
            webSocketHandler.broadcast(alertJson);
        }
    }

    private boolean isAttackPayload(String detail) {
        if (detail == null || detail.isBlank()) return false;
        String lower = detail.toLowerCase();
        return lower.contains("' or '1'='1")
                || lower.contains("union select")
                || lower.contains("../")
                || lower.contains("<script>")
                || lower.contains("/etc/passwd")
                || lower.contains("cmd.exe");
    }
}
