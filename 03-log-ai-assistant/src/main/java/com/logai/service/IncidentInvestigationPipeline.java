package com.logai.service;

import com.alibaba.fastjson2.JSONObject;
import com.logai.engine.FastFeatureEmbeddingEngine;
import com.logai.security.PiiSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * AuditVault 与 AgentForge 双中台跨系统安全协同管道 (Incident Investigation Pipeline)
 * 工业级安全闭环流程：
 * 1. 实时接收 AuditVault 推送的恶意事件 (SQLi/RCE/爆破)；
 * 2. 通过 PiiSanitizer 进行金融级凭据与内网路径脱敏；
 * 3. 通过 FastFeatureEmbeddingEngine 提取 64 维密集特征向量与已知攻击签名比对；
 * 4. 自动关联 MITRE ATT&CK 战术与 CVSS 3.1 评分，生成自动化研判处置工单并对接到 AgentForge 工作流。
 */
@Service
public class IncidentInvestigationPipeline {

    private static final Logger log = LoggerFactory.getLogger(IncidentInvestigationPipeline.class);

    private final PiiSanitizer piiSanitizer;
    private final FastFeatureEmbeddingEngine embeddingEngine;

    public static class InvestigationTicket implements Serializable {
        private static final long serialVersionUID = 1L;

        private String ticketId;
        private String sourceIp;
        private String alertLevel;
        private String mitreTactic;
        private double cvssScore;
        private double attackSimilarity;
        private String sanitizedEvidence;
        private String recommendedPlaybook;
        private JSONObject agentForgePayload;
        private String createdAt;

        public String getTicketId() { return ticketId; }
        public void setTicketId(String ticketId) { this.ticketId = ticketId; }
        public String getSourceIp() { return sourceIp; }
        public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
        public String getAlertLevel() { return alertLevel; }
        public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }
        public String getMitreTactic() { return mitreTactic; }
        public void setMitreTactic(String mitreTactic) { this.mitreTactic = mitreTactic; }
        public double getCvssScore() { return cvssScore; }
        public void setCvssScore(double cvssScore) { this.cvssScore = cvssScore; }
        public double getAttackSimilarity() { return attackSimilarity; }
        public void setAttackSimilarity(double attackSimilarity) { this.attackSimilarity = attackSimilarity; }
        public String getSanitizedEvidence() { return sanitizedEvidence; }
        public void setSanitizedEvidence(String sanitizedEvidence) { this.sanitizedEvidence = sanitizedEvidence; }
        public String getRecommendedPlaybook() { return recommendedPlaybook; }
        public void setRecommendedPlaybook(String recommendedPlaybook) { this.recommendedPlaybook = recommendedPlaybook; }
        public JSONObject getAgentForgePayload() { return agentForgePayload; }
        public void setAgentForgePayload(JSONObject agentForgePayload) { this.agentForgePayload = agentForgePayload; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    public IncidentInvestigationPipeline(PiiSanitizer piiSanitizer, FastFeatureEmbeddingEngine embeddingEngine) {
        this.piiSanitizer = piiSanitizer;
        this.embeddingEngine = embeddingEngine;
    }

    /**
     * 执行全自动化端到端安全协同研判流
     *
     * @param sourceIp   攻击源 IP
     * @param rawLog     原始被捕获攻击日志
     * @param alertLevel 威胁等级 (如 P0_EMERGENCY / P1_HIGH)
     * @return 结构化协同处置工单
     */
    public InvestigationTicket processIncident(String sourceIp, String rawLog, String alertLevel) {
        if (rawLog == null) rawLog = "";

        // 1. 金融级 PII 凭据脱敏
        String sanitized = piiSanitizer != null ? piiSanitizer.sanitize(rawLog) : rawLog;

        // 2. 64 维密集特征向量提取与余弦相似度比对
        double[] featureVector = embeddingEngine.encode(sanitized);
        double[] baselineVector = embeddingEngine.encode("SQL injection attack UNION SELECT password FROM users--");
        double similarity = embeddingEngine.cosineSimilarity(featureVector, baselineVector);

        // 3. 关联 MITRE ATT&CK 与 CVSS 3.1 定级
        String tactic;
        double cvss;
        String playbook;

        String lower = sanitized.toLowerCase();
        if (lower.contains("union") || lower.contains("select") || lower.contains("' or '1'='1")) {
            tactic = "T1190: Exploit Public-Facing Application (SQLi)";
            cvss = 9.8;
            playbook = "PLAYBOOK-SQLI-ISOLATE: 阻断 IP、重置凭证并执行 SQL 审计";
        } else if (lower.contains("passwd") || lower.contains("cmd.exe") || lower.contains("/bin/sh")) {
            tactic = "T1059: Command and Scripting Interpreter (RCE)";
            cvss = 9.9;
            playbook = "PLAYBOOK-RCE-QUARANTINE: 立即隔离宿主机容器并回滚镜像";
        } else {
            tactic = "T1110: Brute Force / Credential Stuffing";
            cvss = 7.5;
            playbook = "PLAYBOOK-BRUTEFORCE-LOCK: 联动 Redis 触发 15 分钟 IP/账号锁定";
        }

        // 4. 构建对接到 AgentForge 的标准工作流载荷
        String ticketId = "TKT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        JSONObject agentForgePayload = new JSONObject();
        agentForgePayload.put("workflowId", "wf_security_auto_triage");
        agentForgePayload.put("executionId", "exec_" + ticketId);
        agentForgePayload.put("sourceSystem", "AuditVault-SOC");
        agentForgePayload.put("incidentIp", sourceIp);
        agentForgePayload.put("tactic", tactic);
        agentForgePayload.put("cvss", cvss);
        agentForgePayload.put("evidence", sanitized);

        InvestigationTicket ticket = new InvestigationTicket();
        ticket.setTicketId(ticketId);
        ticket.setSourceIp(sourceIp);
        ticket.setAlertLevel(alertLevel);
        ticket.setMitreTactic(tactic);
        ticket.setCvssScore(cvss);
        ticket.setAttackSimilarity(similarity);
        ticket.setSanitizedEvidence(sanitized);
        ticket.setRecommendedPlaybook(playbook);
        ticket.setAgentForgePayload(agentForgePayload);
        ticket.setCreatedAt(Instant.now().toString());

        log.warn("🛡️ [INCIDENT_PIPELINE_COMPLETE] 安全协同工单已组装完毕: TicketId={}, IP={}, CVSS={}, Tactic={}",
                ticketId, sourceIp, cvss, tactic);

        return ticket;
    }
}