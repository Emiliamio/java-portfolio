package com.logaudit.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SOC Studio 威胁实时广播 WebSocket 处理器。
 *
 * 维护在线前端监控会话，当后端捕获 CRITICAL / HIGH 等级或高危注入日志时，
 * 毫秒级异步广播威胁警报至所有已连接的 SOC 工作台。
 */
@Component
public class ThreatAlertWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ThreatAlertWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("SOC Studio WebSocket connected: session_id={}, active_clients={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("SOC Studio WebSocket closed: session_id={}, active_clients={}", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        log.warn("SOC Studio WebSocket transport error: session_id={}, error={}", session.getId(), exception.getMessage());
    }

    /**
     * 广播威胁警报 JSON 至所有在线 SOC 大屏
     */
    public void broadcast(String alertJson) {
        if (sessions.isEmpty() || alertJson == null) {
            return;
        }

        TextMessage message = new TextMessage(alertJson);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("Failed to push alert to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public int getActiveClientCount() {
        return sessions.size();
    }
}
