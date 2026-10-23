package com.logai;

import com.logai.cache.SemanticDiagnosisCache;
import com.logai.entity.AnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 语义向量与特征缓存单元测试 — 验证相同特征攻击 0 Token 毫秒级命中。
 */
class SemanticDiagnosisCacheTest {

    private SemanticDiagnosisCache cache;

    @BeforeEach
    void setUp() {
        cache = new SemanticDiagnosisCache();
    }

    @Test
    void testSemanticNormalizationAndHit() {
        String log1 = "2026-09-02 15:00:01 192.168.1.50 admin SQLI ' OR '1'='1 --";
        String log2 = "2026-09-02 16:22:45 10.0.0.99 admin SQLI ' OR '1'='1 --";

        AnalysisResult mockResult = new AnalysisResult();
        mockResult.setSummary("SQL Injection Attack");
        mockResult.setRiskLevel("CRITICAL");
        mockResult.setOperationType("SQLI");

        // 写入 log1 的诊断结果
        cache.put(log1, mockResult);

        // log2 具有不同时间戳和 IP，但语义结构完全相同，应当直接命中缓存！
        AnalysisResult cached = cache.get(log2);

        assertNotNull(cached);
        assertEquals("SQL Injection Attack", cached.getSummary());
        assertEquals("CRITICAL", cached.getRiskLevel());
        assertEquals(1, cache.getHitCount());
    }

    @Test
    void testCacheMissOnDifferentPayload() {
        String log1 = "2026-09-02 15:00:01 192.168.1.50 admin SQLI ' OR '1'='1 --";
        String log2 = "2026-09-02 15:00:02 192.168.1.50 admin PATH_TRAVERSAL ../../etc/passwd";

        AnalysisResult mockResult = new AnalysisResult();
        mockResult.setSummary("SQL Injection");

        cache.put(log1, mockResult);

        AnalysisResult cached = cache.get(log2);
        assertNull(cached);
        assertEquals(1, cache.getMissCount());
    }

    @Test
    void testClear() {
        cache.put("sample log", new AnalysisResult());
        assertEquals(1, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0, cache.getHitCount());
    }

    @Test
    void testLruEvictionIsBounded() {
        SemanticDiagnosisCache bounded = new SemanticDiagnosisCache(2, 60_000L);
        bounded.put("log-a", new AnalysisResult());
        bounded.put("log-b", new AnalysisResult());
        bounded.put("log-c", new AnalysisResult());

        assertEquals(2, bounded.size(), "容量上限必须生效，缓存不得无界增长");
        assertEquals(1, bounded.getEvictionCount(), "超出容量应触发一次 LRU 淘汰");
    }

    @Test
    void testExpiredEntryCountsAsMiss() throws Exception {
        SemanticDiagnosisCache shortLived = new SemanticDiagnosisCache(10, 20L);
        AnalysisResult result = new AnalysisResult();
        result.setSummary("expiring");
        shortLived.put("log-x", result);

        Thread.sleep(60L);

        assertNull(shortLived.get("log-x"));
        assertEquals(1, shortLived.getMissCount());
        assertEquals(0, shortLived.getHitCount());
    }
}
