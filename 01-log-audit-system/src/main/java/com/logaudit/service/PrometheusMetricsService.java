package com.logaudit.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产级 Prometheus 黄金四指标 (Four Golden Signals) 深度度量服务
 * 对标 Google SRE 生产可观测性标准：
 * 1. 吞吐 (Traffic)：auditvault_ingestion_total 计数器；
 * 2. 时延 (Latency)：auditvault_ingestion_duration_seconds 耗时计时器；
 * 3. 错误与防护 (Errors/Defense)：auditvault_storm_suppressed_total & auditvault_threat_banned_total；
 * 4. 饱和度/熔断器仪表 (Saturation)：auditvault_circuitbreaker_state (0=CLOSED, 1=HALF_OPEN, 2=OPEN)。
 */
@Service
public class PrometheusMetricsService {

    private final Counter ingestionCounter;
    private final Counter stormSuppressedCounter;
    private final Counter threatBannedCounter;
    private final Timer ingestionTimer;
    private final AtomicInteger circuitBreakerStateGauge;

    public PrometheusMetricsService(MeterRegistry registry) {
        this.ingestionCounter = Counter.builder("auditvault_ingestion_total")
                .description("Total number of ingested log audit entries")
                .register(registry);

        this.stormSuppressedCounter = Counter.builder("auditvault_storm_suppressed_total")
                .description("Total number of alert storms suppressed by 5-minute sliding window")
                .register(registry);

        this.threatBannedCounter = Counter.builder("auditvault_threat_banned_total")
                .description("Total number of malicious IPs banned by SOC engine")
                .register(registry);

        this.ingestionTimer = Timer.builder("auditvault_ingestion_duration_seconds")
                .description("Latency distribution of log entry ingestion pipeline")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.circuitBreakerStateGauge = registry.gauge("auditvault_circuitbreaker_state",
                new AtomicInteger(0));
    }

    public void recordIngestion(long durationMs) {
        ingestionCounter.increment();
        ingestionTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordStormSuppressed() {
        stormSuppressedCounter.increment();
    }

    public void recordThreatBanned() {
        threatBannedCounter.increment();
    }

    public void setCircuitBreakerState(int state) {
        if (circuitBreakerStateGauge != null) {
            circuitBreakerStateGauge.set(state);
        }
    }

    public double getIngestionCount() { return ingestionCounter.count(); }
    public double getStormSuppressedCount() { return stormSuppressedCounter.count(); }
    public double getThreatBannedCount() { return threatBannedCounter.count(); }
    public int getCircuitBreakerState() { return circuitBreakerStateGauge.get(); }
}