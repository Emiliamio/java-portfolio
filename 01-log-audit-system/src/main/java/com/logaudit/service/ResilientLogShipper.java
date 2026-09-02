package com.logaudit.service;

import com.logaudit.entity.LogEntry;
import com.logaudit.mapper.LogEntryMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产级高可用日志落盘与熔断分发器 (Resilience4j CircuitBreaker)
 * <p>
 * 当底层 MySQL / ClickHouse 或外部遥测接口发生抖动或持续失败时，
 * 触发滑动窗口熔断，自动降级至本地预写内存队列 (WAL Buffer)，彻底阻断服务雪崩。
 */
@Slf4j
@Service
public class ResilientLogShipper {

    private final LogEntryMapper logEntryMapper;
    private final ConcurrentLinkedQueue<LogEntry> walBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger fallbackCount = new AtomicInteger(0);

    public ResilientLogShipper(LogEntryMapper logEntryMapper) {
        this.logEntryMapper = logEntryMapper;
    }

    /**
     * 具备动态熔断与自动降级保护的日志分发方法
     *
     * @param entry 待落盘的审计日志
     * @return true 表示正常同步落库，false 表示触发熔断降级写入 WAL
     */
    @CircuitBreaker(name = "auditLogShipService", fallbackMethod = "walFallback")
    public boolean shipLog(LogEntry entry) {
        if (entry == null) {
            return false;
        }
        logEntryMapper.batchInsert(Collections.singletonList(entry));
        log.debug("[CIRCUIT_BREAKER_PASS] LogEntry shipped successfully: traceId={}", entry.getTraceId());
        return true;
    }

    /**
     * Resilience4j 熔断降级回调方法
     */
    public boolean walFallback(LogEntry entry, Throwable ex) {
        int count = fallbackCount.incrementAndGet();
        if (entry != null) {
            walBuffer.offer(entry);
        }
        log.warn("[CIRCUIT_BREAKER_FALLBACK] Database/Telemetry down, fallback to WAL. Reason: {}, Total WAL logs: {}",
                ex != null ? ex.getMessage() : "Circuit Open", count);
        return false;
    }

    public int getWalSize() {
        return walBuffer.size();
    }

    public int getFallbackCount() {
        return fallbackCount.get();
    }

    public void clearWal() {
        walBuffer.clear();
        fallbackCount.set(0);
    }
}
