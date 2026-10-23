package com.logaudit.security;

import com.logaudit.mapper.UserMapper;
import com.logaudit.service.AuditLogService;
import com.logaudit.service.ClickHouseAnalyticsService;
import com.logaudit.service.KafkaLogProducer;
import com.logaudit.service.LogEntryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.health.db.enabled=false",
        "management.health.redis.enabled=false",
        "management.health.diskspace.enabled=false"
})
@AutoConfigureMockMvc
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private LogEntryService logEntryService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private KafkaLogProducer kafkaLogProducer;

    @MockBean
    private ClickHouseAnalyticsService clickHouseAnalyticsService;

    @Test
    void testActuatorHealthIsPublic() throws Exception {
        // /actuator/health must be public for container health probes
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void testActuatorMetricsRequiresAdminAnonymous() throws Exception {
        // Anonymous request to /actuator/metrics should be rejected (401 Unauthorized)
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "normal_user", roles = {"USER"})
    void testActuatorMetricsRequiresAdminUserForbidden() throws Exception {
        // USER role should be forbidden from accessing /actuator/metrics (403 Forbidden)
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void testActuatorMetricsRequiresAdminSuccess() throws Exception {
        // ADMIN role should be granted access to /actuator/metrics (200 OK)
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }
}
