package com.logaudit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logaudit.entity.LogEntry;
import com.logaudit.mapper.UserMapper;
import com.logaudit.security.TokenBlacklistService;
import com.logaudit.service.AuditLogService;
import com.logaudit.service.LogEntryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerTest {

    private static final String DEFAULT_TOKEN = "auditvault-webhook-default-secret-token-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LogEntryService logEntryService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        doNothing().when(logEntryService).asyncBatchImport(anyList());
    }

    @Test
    void testIngestSingleLogWithTokenSuccess() throws Exception {
        Map<String, Object> logPayload = Map.of(
                "level", "ERROR",
                "logger", "com.example.OrderService",
                "message", "Database connection timed out",
                "ip", "10.0.0.88",
                "user", "order-system"
        );

        mockMvc.perform(post("/api/logs/webhook")
                        .header("X-Audit-Token", DEFAULT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logPayload)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.message").value("日志已接收并在后台异步处理"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(logEntryService, times(1)).asyncBatchImport(captor.capture());

        List<LogEntry> captured = captor.getValue();
        assertEquals(1, captured.size());
        LogEntry entry = captured.get(0);
        assertEquals("ERROR", entry.getSeverity());
        assertEquals("FAIL", entry.getOperationResult());
        assertEquals("10.0.0.88", entry.getIpAddress());
        assertEquals("order-system", entry.getUsername());
        assertEquals("com.example.OrderService", entry.getSourceFile());
        assertTrue(entry.getDetail().contains("Database connection timed out"));
    }

    @Test
    void testIngestBatchLogsWithBearerAuthSuccess() throws Exception {
        List<Map<String, Object>> batchPayload = List.of(
                Map.of("level", "INFO", "message", "User logged in", "user", "alice"),
                Map.of("level", "WARN", "message", "Rate limit approaching", "user", "bob")
        );

        mockMvc.perform(post("/api/logs/webhook")
                        .header("Authorization", "Bearer " + DEFAULT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchPayload)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accepted").value(2));

        verify(logEntryService, times(1)).asyncBatchImport(anyList());
    }

    @Test
    void testIngestWithoutTokenReturns401() throws Exception {
        Map<String, Object> logPayload = Map.of("level", "INFO", "message", "test");

        mockMvc.perform(post("/api/logs/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logPayload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("未授权")));

        verify(logEntryService, never()).asyncBatchImport(anyList());
    }

    @Test
    void testIngestWithInvalidTokenReturns401() throws Exception {
        Map<String, Object> logPayload = Map.of("level", "INFO", "message", "test");

        mockMvc.perform(post("/api/logs/webhook")
                        .header("X-Audit-Token", "invalid-secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logPayload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(logEntryService, never()).asyncBatchImport(anyList());
    }

    @Test
    void testIngestEmptyPayloadReturns400() throws Exception {
        mockMvc.perform(post("/api/logs/webhook")
                        .header("X-Audit-Token", DEFAULT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testIngestLogWithLogbackFieldsAutoMapping() throws Exception {
        Map<String, Object> logbackJson = Map.of(
                "@timestamp", "2026-08-25T12:00:00.000Z",
                "logLevel", "ERROR",
                "loggerName", "com.logai.service.LlmService",
                "formattedMessage", "DeepSeek API 500 internal error",
                "thread", "http-nio-8081-exec-2",
                "stack_trace", "java.net.SocketTimeoutException: Read timed out\n\tat java.net.http.HttpClient.send(HttpClient.java:550)"
        );

        mockMvc.perform(post("/api/logs/webhook")
                        .header("X-Audit-Token", DEFAULT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logbackJson)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accepted").value(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(logEntryService, times(1)).asyncBatchImport(captor.capture());

        LogEntry entry = captor.getValue().get(0);
        assertEquals("ERROR", entry.getSeverity());
        assertEquals("FAIL", entry.getOperationResult());
        assertEquals("com.logai.service.LlmService", entry.getSourceFile());
        assertTrue(entry.getDetail().contains("DeepSeek API 500 internal error"));
        assertTrue(entry.getDetail().contains("[http-nio-8081-exec-2]"));
        assertTrue(entry.getDetail().contains("SocketTimeoutException"));
    }
}
