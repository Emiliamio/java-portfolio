package com.logaudit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logaudit.entity.LogEntry;
import com.logaudit.mapper.UserMapper;
import com.logaudit.security.TokenBlacklistService;
import com.logaudit.service.AuditLogService;
import com.logaudit.service.LogEntryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LogEntryControllerTest {

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

    private LogEntry sampleEntry;

    @BeforeEach
    void setUp() {
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        sampleEntry = new LogEntry(
                1L,
                LocalDateTime.of(2025, 1, 15, 8, 0, 1),
                "192.168.1.10",
                "admin",
                "LOGIN",
                "SUCCESS",
                "User admin logged in",
                "INFO",
                "auth-service.log",
                LocalDateTime.now()
        );
    }

    @Test
    void testUnauthenticatedAccessLogsReturns401() throws Exception {
        mockMvc.perform(get("/api/logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void testUserCanSearchLogs() throws Exception {
        when(logEntryService.searchLogs(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Map.of("total", 1, "page", 1, "pageSize", 20, "records", List.of(sampleEntry)));

        mockMvc.perform(get("/api/logs?page=1&pageSize=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].username").value("admin"));
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void testUserGetDetail() throws Exception {
        when(logEntryService.getDetail(1L)).thenReturn(sampleEntry);

        mockMvc.perform(get("/api/logs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ipAddress").value("192.168.1.10"));
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void testUserGetTodayStats() throws Exception {
        when(logEntryService.todayStats()).thenReturn(Map.of("total", 50, "abnormal", 3, "uniqueIps", 8));

        mockMvc.perform(get("/api/logs/today-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(50))
                .andExpect(jsonPath("$.uniqueIps").value(8));
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void testUserCannotExportLogsReturns403() throws Exception {
        mockMvc.perform(get("/api/logs/export"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void testUserCannotBatchImportReturns403() throws Exception {
        mockMvc.perform(post("/api/logs/batch-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(sampleEntry))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testAdminCanExportLogs() throws Exception {
        byte[] dummyExcel = new byte[]{1, 2, 3, 4};
        when(logEntryService.exportLogs(any(), any(), any(), any(), any())).thenReturn(dummyExcel);

        mockMvc.perform(get("/api/logs/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=logs.xlsx"))
                .andExpect(content().bytes(dummyExcel));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testAdminCanBatchImport() throws Exception {
        doNothing().when(logEntryService).asyncBatchImport(anyList());

        mockMvc.perform(post("/api/logs/batch-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(sampleEntry))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("导入任务已提交")));
    }
}