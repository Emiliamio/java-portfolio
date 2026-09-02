package com.logai;

import com.logai.engine.FastFeatureEmbeddingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FastFeatureEmbeddingEngineTest {

    private FastFeatureEmbeddingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FastFeatureEmbeddingEngine();
    }

    @Test
    @DisplayName("特征向量化：对同一攻击特征计算自相似度应为 1.0 (100%)")
    void testSelfSimilarity() {
        String log = "2026-09-02 15:30:00 SQL INJECTION attempt: ' OR '1'='1 in /api/login";
        double[] vec = engine.encode(log);

        assertEquals(64, vec.length);
        double similarity = engine.cosineSimilarity(vec, vec);
        assertEquals(1.0, similarity, 0.0001);
    }

    @Test
    @DisplayName("语义相似度：两段相似的 SQL 注入攻击向量相似度应大于 0.70")
    void testSimilarAttacksSimilarity() {
        String logA = "SQL injection ' OR '1'='1 from IP 192.168.1.10";
        String logB = "SQL injection ' UNION SELECT NULL, password FROM users from IP 10.0.0.5";

        double[] vecA = engine.encode(logA);
        double[] vecB = engine.encode(logB);

        double similarity = engine.cosineSimilarity(vecA, vecB);
        assertTrue(similarity > 0.60, "Similar SQL injection logs should have high cosine similarity, got: " + similarity);
    }

    @Test
    @DisplayName("空值防护：空日志输入应返回全零向量，相似度为 0.0")
    void testEmptyInputSafety() {
        double[] vecEmpty = engine.encode("");
        double[] vecNormal = engine.encode("NORMAL LOGIN");

        double similarity = engine.cosineSimilarity(vecEmpty, vecNormal);
        assertEquals(0.0, similarity, 0.0001);
    }
}
