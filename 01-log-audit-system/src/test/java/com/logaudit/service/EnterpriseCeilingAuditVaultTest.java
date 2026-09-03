package com.logaudit.service;

import com.alibaba.fastjson2.JSONObject;
import com.logaudit.mapper.LogEntryMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditVault 行业天花板套件测试：多通道告警分发与风暴抑制 + 数据冷热分层生命周期")
class EnterpriseCeilingAuditVaultTest {

    @Mock
    private LogEntryMapper logEntryMapper;

    @InjectMocks
    private DataLifecycleService dataLifecycleService;

    @Test
    @DisplayName("测试企业级多通道告警分发与风暴收敛 (AlertDispatcherService)")
    void testAlertDispatcherAndAntiStorm() {
        AlertDispatcherService dispatcher = new AlertDispatcherService();
        dispatcher.setStormWindowMs(5000); // 测试使用 5 秒窗口

        JSONObject alert = new JSONObject();
        alert.put("ipAddress", "183.23.100.55");
        alert.put("alertLevel", "P0_EMERGENCY");
        alert.put("threatCategory", "SQL_INJECTION");
        alert.put("detail", "admin' UNION SELECT * FROM users--");
        alert.put("autoBanned", true);

        // 1. 第一次推送飞书卡片 -> 应当成功分发
        AlertDispatcherService.AlertDispatchResult result1 = dispatcher.dispatch(alert, AlertDispatcherService.ChannelType.FEISHU);
        Assertions.assertTrue(result1.isDispatched());
        Assertions.assertFalse(result1.isStormSuppressed());
        Assertions.assertNotNull(result1.getFormattedPayload());
        Assertions.assertTrue(result1.getFormattedPayload().contains("interactive"));
        Assertions.assertTrue(result1.getFormattedPayload().contains("183.23.100.55"));

        // 2. 相同 IP 在 5 秒窗口内连续攻击第 2 次 -> 应当触发风暴抑制
        AlertDispatcherService.AlertDispatchResult result2 = dispatcher.dispatch(alert, AlertDispatcherService.ChannelType.FEISHU);
        Assertions.assertFalse(result2.isDispatched());
        Assertions.assertTrue(result2.isStormSuppressed());
        Assertions.assertEquals(1, result2.getSuppressedCount());

        // 3. 相同 IP 连续攻击第 3 次 -> 累加聚合计数为 2
        AlertDispatcherService.AlertDispatchResult result3 = dispatcher.dispatch(alert, AlertDispatcherService.ChannelType.DINGTALK);
        Assertions.assertFalse(result3.isDispatched());
        Assertions.assertTrue(result3.isStormSuppressed());
        Assertions.assertEquals(2, result3.getSuppressedCount());

        // 4. 不同 IP 的告警 -> 应当不受影响，正常分发
        JSONObject alertDifferentIp = new JSONObject();
        alertDifferentIp.put("ipAddress", "220.181.38.148");
        alertDifferentIp.put("alertLevel", "P1_HIGH");
        alertDifferentIp.put("threatCategory", "RCE_ATTACK");
        alertDifferentIp.put("detail", "cat /etc/passwd");
        alertDifferentIp.put("autoBanned", false);

        AlertDispatcherService.AlertDispatchResult resultDiff = dispatcher.dispatch(alertDifferentIp, AlertDispatcherService.ChannelType.DINGTALK);
        Assertions.assertTrue(resultDiff.isDispatched());
        Assertions.assertFalse(resultDiff.isStormSuppressed());
        Assertions.assertTrue(resultDiff.getFormattedPayload().contains("markdown"));
    }

    @Test
    @DisplayName("测试冷热分层数据生命周期与物理归档淘汰 (DataLifecycleService)")
    void testDataLifecycleService() {
        // 1. 测试冷热存储分层评估
        Map<String, Long> tiers = dataLifecycleService.evaluateStorageTiers(100_000L);
        Assertions.assertEquals(40_000L, tiers.get("HOT_TIER_0_7D"));
        Assertions.assertEquals(35_000L, tiers.get("WARM_TIER_8_30D"));
        Assertions.assertEquals(25_000L, tiers.get("COLD_TIER_31_180D"));

        // 2. Mock 物理删除过期日志
        when(logEntryMapper.deleteBefore(any(LocalDateTime.class))).thenReturn(1500);

        // 3. 执行数据生命周期保留清理 (保留 180 天)
        DataLifecycleService.LifecycleReport report = dataLifecycleService.executeLifecycleRetention(180);
        Assertions.assertNotNull(report);
        Assertions.assertEquals(180, report.getDefaultRetentionDays());
        Assertions.assertEquals(1500, report.getActualPurgedCount());
        Assertions.assertTrue(report.getDurationMs() >= 1);

        verify(logEntryMapper, times(1)).deleteBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("测试入库级金融合规 PII 敏感数据脱敏装甲 (PiiDataMasker)")
    void testPiiDataMasker() {
        com.logaudit.security.PiiDataMasker masker = new com.logaudit.security.PiiDataMasker();

        String raw = "Login attempt with password=SuperSecretPassword99! by user phone=13912345678, idCard=440106199505051234, bank=6222021234567890123 on file /home/app/config/secret.key";
        String masked = masker.mask(raw);

        // 验证敏感信息均被脱敏替换
        Assertions.assertFalse(masked.contains("SuperSecretPassword99!"));
        Assertions.assertTrue(masked.contains("[REDACTED_SECRET]"));
        Assertions.assertFalse(masked.contains("13912345678"));
        Assertions.assertTrue(masked.contains("139****5678"));
        Assertions.assertFalse(masked.contains("440106199505051234"));
        Assertions.assertTrue(masked.contains("440106********1234"));
        Assertions.assertFalse(masked.contains("6222021234567890123"));
        Assertions.assertTrue(masked.contains("622202********0123"));
        Assertions.assertTrue(masked.contains("[INTERNAL_PATH]"));
    }

    @Test
    @DisplayName("测试 ClickHouse 小时级预聚合时序直方图查询与降级")
    void testClickHousePreAggregatedHourlyStats() {
        com.logaudit.config.ClickHouseConfig chConfig = mock(com.logaudit.config.ClickHouseConfig.class);
        when(chConfig.isEnabled()).thenReturn(false); // 模拟 ClickHouse 未启用触发安全降级

        ClickHouseAnalyticsService analyticsService = new ClickHouseAnalyticsService(chConfig, logEntryMapper);
        Map<String, Object> result = analyticsService.getPreAggregatedHourlyStats(12);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(12, result.get("lookbackHours"));
        Assertions.assertTrue(result.get("sourceEngine").toString().contains("Fallback"));
        List<?> buckets = (List<?>) result.get("buckets");
        Assertions.assertEquals(12, buckets.size());
    }
}