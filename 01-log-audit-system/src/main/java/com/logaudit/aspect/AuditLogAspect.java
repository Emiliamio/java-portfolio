package com.logaudit.aspect;

import com.alibaba.fastjson2.JSON;
import com.logaudit.annotation.AuditLog;
import com.logaudit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 审计日志 AOP 切面 (Audit Log Aspect)。
 *
 * 拦截所有标注了 @AuditLog 的方法，实现零侵入全自动审计记录与 TraceId 上下文绑定。
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final AuditLogService auditLogService;

    public AuditLogAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("@annotation(com.logaudit.annotation.AuditLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditLog auditLog = method.getAnnotation(AuditLog.class);

        String operation = auditLog.operation().isBlank() ? method.getName() : auditLog.operation();
        String module = auditLog.module();
        String traceId = MDC.get("traceId");

        String username = resolveCurrentUsername();
        String clientIp = resolveClientIp();

        Object result;
        try {
            result = joinPoint.proceed();
            long costMs = System.currentTimeMillis() - startTime;

            String detail = "module=" + module + ", cost=" + costMs + "ms";
            if (auditLog.recordParams() && joinPoint.getArgs().length > 0) {
                try {
                    detail += ", args=" + Arrays.toString(joinPoint.getArgs());
                } catch (Exception ignored) {}
            }

            log.info("[AUDIT_AOP_SUCCESS] User={}, Op={}, IP={}, Cost={}ms, TraceId={}",
                    username, operation, clientIp, costMs, traceId);

            if (auditLogService != null) {
                auditLogService.recordSuccess(module, operation, detail, clientIp);
            }

            return result;
        } catch (Throwable ex) {
            long costMs = System.currentTimeMillis() - startTime;
            String errorDetail = "module=" + module + ", cost=" + costMs + "ms, error=" + ex.getMessage();

            log.warn("[AUDIT_AOP_FAIL] User={}, Op={}, IP={}, Error={}, TraceId={}",
                    username, operation, clientIp, ex.getMessage(), traceId);

            if (auditLogService != null) {
                auditLogService.recordFail(module, operation, errorDetail, clientIp);
            }
            throw ex;
        }
    }

    private String resolveCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "ANONYMOUS";
    }

    private String resolveClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return "127.0.0.1";
        HttpServletRequest request = attributes.getRequest();

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "127.0.0.1";
    }
}
