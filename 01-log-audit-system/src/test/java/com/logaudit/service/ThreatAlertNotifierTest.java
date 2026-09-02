package com.logaudit.service;

import com.logaudit.dto.WebhookLogDto;
import com.logaudit.websocket.ThreatAlertWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ThreatAlertNotifier 单元测试 — 验证高危日志实时研判与 WebSocket 广播。
 */
@ExtendWith(MockitoExtension.class)
class ThreatAlertNotifierTest {

    @Mock
    private ThreatAlertWebSocketHandler webSocketHandler;

    @InjectMocks
    private ThreatAlertNotifier notifier;

    @Test
    void testCriticalLogTriggersBroadcast() {
        WebhookLogDto dto = new WebhookLogDto();
        dto.setSeverity("CRITICAL");
        dto.setIpAddress("192.168.1.100");
        dto.setUsername("root");
        dto.setOperation("RCE_EXPLOIT");
        dto.setDetail("Remote code execution attempt");

        notifier.notifyIfThreat(dto);

        verify(webSocketHandler, times(1)).broadcast(anyString());
    }

    @Test
    void testSqlInjectionTriggersBroadcast() {
        WebhookLogDto dto = new WebhookLogDto();
        dto.setSeverity("INFO");
        dto.setIpAddress("10.0.0.5");
        dto.setUsername("guest");
        dto.setOperation("LOGIN");
        dto.setDetail("Payload: admin' OR '1'='1");

        notifier.notifyIfThreat(dto);

        verify(webSocketHandler, times(1)).broadcast(anyString());
    }

    @Test
    void testNormalInfoLogDoesNotBroadcast() {
        WebhookLogDto dto = new WebhookLogDto();
        dto.setSeverity("INFO");
        dto.setIpAddress("127.0.0.1");
        dto.setUsername("alice");
        dto.setOperation("QUERY");
        dto.setDetail("Normal query execution");

        notifier.notifyIfThreat(dto);

        verify(webSocketHandler, never()).broadcast(anyString());
    }
}
