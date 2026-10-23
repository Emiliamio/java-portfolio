package com.logai.engine;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 嵌入式极速特征向量化与余弦相似度计算引擎 (Edge Fast Embedding Engine)
 * <p>
 * 专为离线物理隔离与轻量化边缘 CPU 设计：
 * 将攻击日志与错误堆栈映射为 64 维词袋哈希特征向量（Hashing Trick），可在毫秒级完成余弦相似度对比，
 * 用于攻击聚类与相似度排序，无需外接大模型。
 * <p>
 * 注意：这是词面重叠度度量，不是语义嵌入；不作为诊断结果缓存的键（缓存见 SemanticDiagnosisCache）。
 */
@Component
public class FastFeatureEmbeddingEngine {

    private static final int VECTOR_DIMENSIONS = 64;

    /** 线程内复用的 MD5 摘要器，避免每个 token 重新 getInstance */
    private static final ThreadLocal<MessageDigest> MD5_DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    });

    /**
     * 将日志文本转换为归一化的 64 维特征向量
     */
    public double[] encode(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new double[VECTOR_DIMENSIONS];
        }

        double[] vector = new double[VECTOR_DIMENSIONS];
        String[] tokens = text.toLowerCase().split("[\\s,;:'\"(){}\\[\\]=<>]+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            int hash = hashToken(token);
            int index = Math.abs(hash % VECTOR_DIMENSIONS);
            vector[index] += 1.0;
        }

        double norm = 0.0;
        for (double val : vector) {
            norm += val * val;
        }
        norm = Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < VECTOR_DIMENSIONS; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }

    /**
     * 计算两个特征向量的余弦相似度 (Cosine Similarity: 0.0 ~ 1.0)
     */
    public double cosineSimilarity(double[] vecA, double[] vecB) {
        if (vecA == null || vecB == null || vecA.length != vecB.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, dotProduct / (Math.sqrt(normA) * Math.sqrt(normB))));
    }

    private int hashToken(String token) {
        // 复用线程内的 MessageDigest：每次 getInstance 会重建算法上下文，
        // 数千 token 的日志会放大成数千次对象创建，是“2ms 级”延迟的真实瓶颈。
        MessageDigest md = MD5_DIGEST.get();
        md.reset();
        byte[] bytes = md.digest(token.getBytes(StandardCharsets.UTF_8));
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }
}
