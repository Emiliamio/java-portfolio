package com.logaudit.service;

import com.logaudit.security.AuditLogTamperProofChain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@DisplayName("AuditVault 终极工业天花板测试：时序动态基线 3-Sigma 离群检测 + 密码学区块链式防篡改哈希审计链")
class EnterpriseApexAuditVaultTest {

    @Test
    @DisplayName("测试动态时序基线与 3-Sigma 突发异动检测 (DynamicBaselineAnomalyDetector)")
    void testDynamicBaselineAnomalyDetector() {
        DynamicBaselineAnomalyDetector detector = new DynamicBaselineAnomalyDetector();

        // 1. 喂入 20 个平稳基线样本 (均值约 100，轻微在 95~105 波动)
        for (int i = 0; i < 20; i++) {
            detector.recordSample(100.0 + (i % 5));
        }

        // 2. 正常波动样本点 (103.0) -> 不应触发异动
        DynamicBaselineAnomalyDetector.AnomalyReport normalReport = detector.evaluate(103.0);
        Assertions.assertFalse(normalReport.isAnomaly());
        Assertions.assertTrue(Math.abs(normalReport.getZScore()) < 3.0);

        // 3. 注入 3-Sigma 突变样本点 (如突然暴增至 600.0 流量突发)
        DynamicBaselineAnomalyDetector.AnomalyReport spikeReport = detector.evaluate(600.0);
        Assertions.assertTrue(spikeReport.isAnomaly());
        Assertions.assertTrue(spikeReport.getZScore() > 3.0);
        Assertions.assertTrue(spikeReport.getMessage().contains("3-Sigma 突变异动告警"));
    }

    @Test
    @DisplayName("测试区块链式 SHA-256 防篡改哈希审计链与防伪自检 (AuditLogTamperProofChain)")
    void testAuditLogTamperProofChain() {
        AuditLogTamperProofChain chainService = new AuditLogTamperProofChain();

        // 1. 连续追加 3 条正常审计日志块
        AuditLogTamperProofChain.AuditBlock b0 = chainService.appendEntry(1001L, System.currentTimeMillis(), "LOGIN", "user=admin;ip=183.23.1.1");
        AuditLogTamperProofChain.AuditBlock b1 = chainService.appendEntry(1002L, System.currentTimeMillis() + 10, "TRANSFER", "amount=50000;target=622202");
        AuditLogTamperProofChain.AuditBlock b2 = chainService.appendEntry(1003L, System.currentTimeMillis() + 20, "LOGOUT", "user=admin");

        List<AuditLogTamperProofChain.AuditBlock> chain = chainService.getChainSnapshot();
        Assertions.assertEquals(3, chain.size());

        // 2. 初始完整性自检 -> 必须通过
        AuditLogTamperProofChain.VerificationResult cleanResult = chainService.verifyChainIntegrity(chain);
        Assertions.assertTrue(cleanResult.isValid());

        // 3. 模拟内鬼 DBA 物理篡改第 2 个区块的转账金额 (从 50000 改为 500)
        List<AuditLogTamperProofChain.AuditBlock> tamperedChain = new ArrayList<>(chain);
        AuditLogTamperProofChain.AuditBlock tamperedBlock = new AuditLogTamperProofChain.AuditBlock(
                b1.getBlockIndex(),
                b1.getLogId(),
                b1.getTimestamp(),
                b1.getOperation(),
                "amount=500;target=622202", // 被非法篡改
                b1.getPrevHash(),
                b1.getCurrentHash() // 哈希与篡改后内容对不上
        );
        tamperedChain.set(1, tamperedBlock);

        // 4. 再次执行防伪自检 -> 必须瞬间检出篡改并精确指认区块索引 1
        AuditLogTamperProofChain.VerificationResult tamperedResult = chainService.verifyChainIntegrity(tamperedChain);
        Assertions.assertFalse(tamperedResult.isValid());
        Assertions.assertEquals(1, tamperedResult.getBrokenAtIndex());
        Assertions.assertTrue(tamperedResult.getReason().contains("tampered at block index 1"));
    }
}