package com.logaudit.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.logaudit.entity.LogEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Webhook 日志采集 DTO — 适配 Logback/Logstash/SLF4J 及标准 JSON 日志推送格式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Webhook 日志摄入请求体 (支持单对象或数组)")
public class WebhookLogDto {

    @Schema(description = "事件时间戳", example = "2026-09-02 14:30:00")
    @JsonAlias({"time", "logTime", "@timestamp"})
    private String timestamp;

    @Schema(description = "日志严重级别 (INFO/WARN/ERROR/CRITICAL)", example = "WARN")
    @JsonAlias({"level", "logLevel"})
    private String severity;

    @Schema(description = "客户端或来源 IP 地址", example = "192.168.1.108")
    @JsonAlias({"clientIp", "ip", "host"})
    private String ipAddress;

    @Schema(description = "操作用户名 / 认证主体", example = "admin")
    @JsonAlias({"user", "operator", "principal"})
    private String username;

    @Schema(description = "操作类型或事件名称", example = "LOGIN_ATTEMPT")
    @JsonAlias({"action", "event"})
    private String operation;

    @Schema(description = "操作执行结果 (SUCCESS/FAIL)", example = "FAIL")
    @JsonAlias({"result", "status"})
    private String operationResult;

    @Schema(description = "日志详细消息或错误内容", example = "User admin login failed: invalid password credentials")
    @JsonAlias({"message", "content", "msg", "formattedMessage"})
    private String detail;

    @Schema(description = "微服务来源或日志记录器", example = "order-payment-service.log")
    @JsonAlias({"logger", "loggerName", "service", "serviceName", "app", "source"})
    private String sourceFile;

    @Schema(description = "全链路分布式追踪 ID", example = "tr-8f4b1c2e9a0d3f7b")
    @JsonAlias({"traceId", "trace_id", "traceID", "spanId", "trace"})
    private String traceId;

    @Schema(description = "执行线程名", example = "http-nio-8080-exec-1")
    private String thread;

    @Schema(description = "异常堆栈详情 (可选)", example = "java.lang.SecurityException: Invalid token")
    @JsonAlias({"exception", "stack_trace", "throwable", "error"})
    private String stackTrace;

    private static final DateTimeFormatter STANDARD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * 将 DTO 转换为系统标准实体 LogEntry，具备字段容错与智能推导能力。
     */
    public LogEntry toLogEntry(String defaultClientIp) {
        LogEntry entry = new LogEntry();

        // 1. 时间解析与容错
        entry.setTimestamp(parseTimestamp(this.timestamp));

        // 2. IP 地址推导（优先使用 payload 中的 IP，若无则回退到 HTTP 真实客户端 IP）
        String resolvedIp = (this.ipAddress != null && !this.ipAddress.isBlank())
                ? this.ipAddress.trim()
                : (defaultClientIp != null && !defaultClientIp.isBlank() ? defaultClientIp : "127.0.0.1");
        entry.setIpAddress(resolvedIp);

        // 3. 用户名
        entry.setUsername((this.username != null && !this.username.isBlank()) ? this.username.trim() : "SYSTEM");

        // 4. 严重级别（统一转为大写）
        String sev = (this.severity != null && !this.severity.isBlank()) ? this.severity.trim().toUpperCase() : "INFO";
        entry.setSeverity(sev);

        // 5. 操作类型
        if (this.operation != null && !this.operation.isBlank()) {
            entry.setOperation(this.operation.trim());
        } else if (this.sourceFile != null && !this.sourceFile.isBlank()) {
            entry.setOperation(this.sourceFile.trim());
        } else {
            entry.setOperation("LOG_APPENDER");
        }

        // 6. 操作结果智能推导
        if (this.operationResult != null && !this.operationResult.isBlank()) {
            entry.setOperationResult(this.operationResult.trim().toUpperCase());
        } else {
            entry.setOperationResult(("ERROR".equals(sev) || "FATAL".equals(sev) || "WARN".equals(sev)) ? "FAIL" : "SUCCESS");
        }

        // 7. 来源标记
        entry.setSourceFile((this.sourceFile != null && !this.sourceFile.isBlank()) ? this.sourceFile.trim() : "webhook");

        // 8. 分布式链路追踪 ID
        if (this.traceId != null && !this.traceId.isBlank()) {
            entry.setTraceId(this.traceId.trim());
        } else {
            entry.setTraceId("tr-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }

        // 9. 详情与堆栈拼接
        StringBuilder sb = new StringBuilder();
        if (this.detail != null && !this.detail.isBlank()) {
            sb.append(this.detail.trim());
        }
        if (this.thread != null && !this.thread.isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("[").append(this.thread.trim()).append("]");
        }
        if (this.stackTrace != null && !this.stackTrace.isBlank()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Stack Trace: ").append(this.stackTrace.trim());
        }
        entry.setDetail(sb.length() > 0 ? sb.toString() : "No message provided");

        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }

    private LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        raw = raw.trim();

        // 尝试时间戳毫秒/秒解析
        try {
            if (raw.matches("^\\d+$")) {
                long epoch = Long.parseLong(raw);
                if (raw.length() == 10) { // 秒级时间戳
                    epoch *= 1000;
                }
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
            }
        } catch (Exception ignored) {
        }

        // 尝试 yyyy-MM-dd HH:mm:ss 解析
        try {
            return LocalDateTime.parse(raw, STANDARD_FORMATTER);
        } catch (DateTimeParseException ignored) {
        }

        // 尝试 ISO 格式解析 (如 2026-08-25T19:00:00.000Z)
        try {
            if (raw.endsWith("Z")) {
                return LocalDateTime.ofInstant(Instant.parse(raw), ZoneId.systemDefault());
            }
            return LocalDateTime.parse(raw, ISO_FORMATTER);
        } catch (Exception ignored) {
        }

        return LocalDateTime.now();
    }
}
