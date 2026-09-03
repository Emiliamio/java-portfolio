package com.logai;

import com.logai.engine.FastFeatureEmbeddingEngine;
import com.logai.security.PiiSanitizer;
import com.logai.service.IncidentInvestigationPipeline;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Nexus AI 与 AgentForge 跨中台安全协同流水线测试")
class IncidentInvestigationPipelineTest {

    private final PiiSanitizer piiSanitizer = new PiiSanitizer();
    private final FastFeatureEmbeddingEngine embeddingEngine = new FastFeatureEmbeddingEngine();
    private final IncidentInvestigationPipeline pipeline = new IncidentInvestigationPipeline(piiSanitizer, embeddingEngine);

    @Test
    @DisplayName("测试 SQL 注入高危事件自动化研判与 AgentForge 工单联动")
    void testSqlInjectionIncident() {
        String rawLog = "2026-09-03 12:00:00 WARN Login failed with password=SecretPassword123! and payload: admin' UNION SELECT * FROM users-- from IP 183.23.100.55";

        IncidentInvestigationPipeline.InvestigationTicket ticket =
                pipeline.processIncident("183.23.100.55", rawLog, "P0_EMERGENCY");

        Assertions.assertNotNull(ticket);
        Assertions.assertTrue(ticket.getTicketId().startsWith("TKT-"));
        Assertions.assertEquals("183.23.100.55", ticket.getSourceIp());
        Assertions.assertEquals("P0_EMERGENCY", ticket.getAlertLevel());
        Assertions.assertTrue(ticket.getMitreTactic().contains("T1190"));
        Assertions.assertEquals(9.8, ticket.getCvssScore());

        // 验证敏感口令已被脱敏
        Assertions.assertFalse(ticket.getSanitizedEvidence().contains("SecretPassword123!"));
        Assertions.assertTrue(ticket.getSanitizedEvidence().contains("[REDACTED_SECRET]"));

        // 验证 AgentForge 对接载荷
        Assertions.assertNotNull(ticket.getAgentForgePayload());
        Assertions.assertEquals("wf_security_auto_triage", ticket.getAgentForgePayload().getString("workflowId"));
        Assertions.assertEquals("AuditVault-SOC", ticket.getAgentForgePayload().getString("sourceSystem"));
    }

    @Test
    @DisplayName("测试 RCE 远程命令执行高危事件研判与隔离处置")
    void testRceIncident() {
        String rawLog = "POST /api/cmd HTTP/1.1 attempt to read /etc/passwd and execute /bin/sh";

        IncidentInvestigationPipeline.InvestigationTicket ticket =
                pipeline.processIncident("10.0.0.99", rawLog, "P0_EMERGENCY");

        Assertions.assertNotNull(ticket);
        Assertions.assertTrue(ticket.getMitreTactic().contains("T1059"));
        Assertions.assertEquals(9.9, ticket.getCvssScore());
        Assertions.assertTrue(ticket.getRecommendedPlaybook().contains("PLAYBOOK-RCE-QUARANTINE"));
    }
}