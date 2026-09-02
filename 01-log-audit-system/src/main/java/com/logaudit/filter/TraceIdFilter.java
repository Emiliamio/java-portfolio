package com.logaudit.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 分布式全链路追踪过滤器 (W3C TraceContext & OpenTelemetry 双模兼容)。
 *
 * 1. 优先解析 W3C 标准 traceparent: 00-{traceId}-{parentId}-{traceFlags}
 * 2. 次级兼容 X-Trace-Id / X-Request-Id
 * 3. 兜底生成 32 位 Hex TraceId 并注入 SLF4J MDC
 * 4. 响应头双向透传 X-Trace-Id 与 W3C traceparent
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String W3C_TRACEPARENT_HEADER = "traceparent";
    public static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = null;

        // 1. 尝试解析 W3C TraceContext traceparent 标准头 (00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01)
        String traceparent = request.getHeader(W3C_TRACEPARENT_HEADER);
        if (StringUtils.hasText(traceparent)) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 4 && parts[1].length() == 32) {
                traceId = parts[1];
            }
        }

        // 2. 次选 X-Trace-Id / X-Request-Id
        if (!StringUtils.hasText(traceId)) {
            traceId = request.getHeader(TRACE_ID_HEADER);
        }
        if (!StringUtils.hasText(traceId)) {
            traceId = request.getHeader("X-Request-Id");
        }

        // 3. 兜底生成全新 32 位 Hex TraceId
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        String w3cResponseHeader = "00-" + traceId + "-0000000000000001-01";

        try {
            MDC.put(MDC_TRACE_ID_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            response.setHeader(W3C_TRACEPARENT_HEADER, w3cResponseHeader);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }
}
