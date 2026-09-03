package com.logaudit.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@DisplayName("AuditVault 巅峰天花板深度测试：GeoIP 空间情报 + Prometheus 黄金信号 + SOAR 闭环自愈响应")
class EnterprisePeakAuditVaultTest {

    @Test
    @DisplayName("测试 IP 地理空间情报富化 (GeoIpEnrichmentService)")
    void testGeoIpEnrichmentService() {
        GeoIpEnrichmentService service = new GeoIpEnrichmentService();

        // 1. 测试 RFC 1918 内网局域网识别
        GeoIpEnrichmentService.GeoLocation locLocal = service.resolve("192.168.1.100");
        Assertions.assertTrue(locLocal.isPrivateIp());
        Assertions.assertEquals("局域网私网段 (RFC1918)", locLocal.getCity());

        // 2. 测试公网电信 IP (广州) 空间拓扑解析
        GeoIpEnrichmentService.GeoLocation locGz = service.resolve("183.23.100.55");
        Assertions.assertFalse(locGz.isPrivateIp());
        Assertions.assertEquals("广东省", locGz.getProvince());
        Assertions.assertEquals("广州市", locGz.getCity());
        Assertions.assertEquals("中国电信", locGz.getIsp());
        Assertions.assertTrue(locGz.getLatitude() > 20.0);

        // 3. 测试海外公网 IP (美国 AWS)
        GeoIpEnrichmentService.GeoLocation locUs = service.resolve("54.210.12.8");
        Assertions.assertEquals("美国", locUs.getCountry());
        Assertions.assertEquals("AWS-Cloud", locUs.getIsp());
    }

    @Test
    @DisplayName("测试 Prometheus 黄金四指标生产级深度度量 (PrometheusMetricsService)")
    void testPrometheusMetricsService() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PrometheusMetricsService metricsService = new PrometheusMetricsService(registry);

        // 1. 记录 3 次摄取与耗时
        metricsService.recordIngestion(15);
        metricsService.recordIngestion(25);
        metricsService.recordIngestion(5);
        Assertions.assertEquals(3.0, metricsService.getIngestionCount());

        // 2. 记录告警风暴抑制与恶意 IP 封禁
        metricsService.recordStormSuppressed();
        metricsService.recordStormSuppressed();
        metricsService.recordThreatBanned();
        Assertions.assertEquals(2.0, metricsService.getStormSuppressedCount());
        Assertions.assertEquals(1.0, metricsService.getThreatBannedCount());

        // 3. 设置熔断器状态 Gauge
        metricsService.setCircuitBreakerState(2); // 2=OPEN
        Assertions.assertEquals(2, metricsService.getCircuitBreakerState());
    }

    @Test
    @DisplayName("测试 SOAR 自动化处置与自愈阻断闭环 (SoarAutoRemediationExecutor)")
    void testSoarAutoRemediationExecutor() {
        IpReputationService ipReputationService = Mockito.mock(IpReputationService.class);
        AlertDispatcherService alertDispatcherService = Mockito.mock(AlertDispatcherService.class);

        SoarAutoRemediationExecutor executor = new SoarAutoRemediationExecutor(ipReputationService, alertDispatcherService);

        SoarAutoRemediationExecutor.RemediationOrder order = new SoarAutoRemediationExecutor.RemediationOrder(
                "TKT-AF-998822",
                "183.23.100.55",
                SoarAutoRemediationExecutor.ActionType.AUTO_BAN_IP,
                3600,
                "SQLi injection attack detected by Nexus AI"
        );

        // 执行处置
        SoarAutoRemediationExecutor.RemediationReceipt receipt = executor.executeRemediation(order);

        // 验证执行结果
        Assertions.assertNotNull(receipt);
        Assertions.assertTrue(receipt.isSuccess());
        Assertions.assertEquals("TKT-AF-998822", receipt.getTicketId());
        Assertions.assertEquals("183.23.100.55", receipt.getTargetIp());
        Assertions.assertTrue(receipt.getDigitalSignature().contains("TKT-AF-998822"));

        // 验证反向调用了 IP 封禁与告警通道分发
        verify(ipReputationService, Mockito.times(1)).autoBanIp(eq("183.23.100.55"), eq(3600L), anyString());
        verify(alertDispatcherService, Mockito.times(1)).dispatch(any(), eq(AlertDispatcherService.ChannelType.FEISHU));
    }
}