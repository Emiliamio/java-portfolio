package com.logaudit.config;

import com.logaudit.websocket.ThreatAlertWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置 — 注册实时威胁告警端点 /ws/threat-alerts。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ThreatAlertWebSocketHandler threatAlertWebSocketHandler;

    public WebSocketConfig(ThreatAlertWebSocketHandler threatAlertWebSocketHandler) {
        this.threatAlertWebSocketHandler = threatAlertWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(threatAlertWebSocketHandler, "/ws/threat-alerts")
                .setAllowedOriginPatterns("*");
    }
}
