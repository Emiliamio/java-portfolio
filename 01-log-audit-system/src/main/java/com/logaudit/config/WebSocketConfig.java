package com.logaudit.config;

import com.logaudit.websocket.ThreatAlertWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置 — 注册实时威胁告警端点 /ws/threat-alerts。
 *
 * <p>安全约束：</p>
 * <ol>
 *   <li>该端点不再匿名放行（见 SecurityConfig），握手请求必须携带有效的 JWT Cookie，
 *       因为推送内容包含内网 IP、操作类型与攻击载荷原文；</li>
 *   <li>跨域来源默认拒绝（同源），避免恶意站点借用受害者 Cookie 建立连接窃听告警。
 *       如确需跨域联调，显式配置 {@code app.websocket.allowed-origins}。</li>
 * </ol>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ThreatAlertWebSocketHandler threatAlertWebSocketHandler;
    private final String allowedOrigins;

    public WebSocketConfig(ThreatAlertWebSocketHandler threatAlertWebSocketHandler,
                           @Value("${app.websocket.allowed-origins:}") String allowedOrigins) {
        this.threatAlertWebSocketHandler = threatAlertWebSocketHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(threatAlertWebSocketHandler, "/ws/threat-alerts");

        // 不调用 setAllowedOriginPatterns 即为「仅同源」。只有显式配置时才开放跨域来源。
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            String[] origins = allowedOrigins.split(",");
            for (int i = 0; i < origins.length; i++) {
                origins[i] = origins[i].trim();
            }
            registration.setAllowedOriginPatterns(origins);
        }
    }
}
