package com.sample.order.aspect;

import com.alibaba.fastjson2.JSONObject;
import com.sample.order.annotation.AuditLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 示例微服务无侵入 AOP 审计上报切面 (Demonstrating AuditVault Starter Pattern)。
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    @Value("${auditvault.enabled:true}")
    private boolean enabled;

    @Value("${auditvault.webhook-url:http://localhost:8080/api/logs/webhook}")
    private String webhookUrl;

    @Value("${auditvault.token:auditvault-webhook-default-secret-token-2026}")
    private String token;

    @Value("${auditvault.service-name:ORDER_SERVICE}")
    private String serviceName;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Around("@annotation(com.sample.order.annotation.AuditLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        AuditLog annotation = method.getAnnotation(AuditLog.class);

        String op = annotation.operation().isBlank() ? method.getName() : annotation.operation();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        Object result;
        try {
            result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            if (enabled) {
                dispatchAuditLog(op, "SUCCESS", "Execution took " + cost + "ms", annotation.severity(), traceId);
            }
            return result;
        } catch (Throwable ex) {
            long cost = System.currentTimeMillis() - start;
            if (enabled) {
                dispatchAuditLog(op, "FAIL", "Error: " + ex.getMessage() + ", cost=" + cost + "ms", "ERROR", traceId);
            }
            throw ex;
        }
    }

    public void dispatchAuditLog(String operation, String result, String detail, String severity, String traceId) {
        CompletableFuture.runAsync(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                payload.put("ipAddress", "192.168.1.50");
                payload.put("username", "order_operator");
                payload.put("operation", "[" + serviceName + "] " + operation);
                payload.put("operationResult", result);
                payload.put("detail", detail);
                payload.put("severity", severity);
                payload.put("sourceFile", serviceName + ".java");
                payload.put("traceId", traceId);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .header("X-Audit-Token", token)
                        .header("X-Trace-Id", traceId)
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
                        .build();

                httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                        .thenAccept(res -> log.debug("[AUDIT_SHIP_SUCCESS] Response code: {}", res.statusCode()));
            } catch (Exception ex) {
                log.warn("[AUDIT_SHIP_FAIL] Could not ship audit log to AuditVault: {}", ex.getMessage());
            }
        });
    }
}
