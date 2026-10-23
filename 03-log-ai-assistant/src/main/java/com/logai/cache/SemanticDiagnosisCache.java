package com.logai.cache;

import com.logai.entity.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Nexus AI 攻击模式指纹缓存 (Semantic Diagnosis Cache)。
 *
 * <p><b>实现边界（与实现保持一致，不做超出实现的宣称）：</b></p>
 * <ul>
 *   <li>键是「归一化后的 SHA-256 精确指纹」：先抹掉时间戳、IP、UUID 等瞬态噪声，再整体哈希。
 *       因此它是<b>结构相同的同型攻击才命中</b>，不是向量语义相似度检索。</li>
 *   <li>淘汰策略是<b>有界 LRU（访问序 LinkedHashMap）+ TTL 双约束</b>：容量默认 1000，
 *       超限淘汰最久未访问项；条目 30 分钟过期。</li>
 *   <li>命中即跳过云端/本地大模型调用，实现 0 Token 复判，延迟来自一次正则归一化 + 一次哈希。</li>
 * </ul>
 *
 * <p>说明：本模块另有 64 维哈希特征向量引擎 {@code FastFeatureEmbeddingEngine} 用于相似度聚类，
 * 但那套词袋哈希向量无法保证「不同攻击类型不误命中」，不适合作为诊断结果缓存键，故此处未采用。</p>
 */
@Component
public class SemanticDiagnosisCache {

    private static final Logger log = LoggerFactory.getLogger(SemanticDiagnosisCache.class);

    private static final Pattern TS_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?");
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** 默认容量上限，超出后按 LRU 淘汰 */
    private static final int DEFAULT_CAPACITY = 1000;
    /** 默认条目存活时间：30 分钟 */
    private static final long DEFAULT_TTL_MS = 1800_000L;

    private final int capacity;
    private final long ttlMs;
    private final Map<String, CacheEntry> cache;

    private long hitCount = 0L;
    private long missCount = 0L;
    private long evictionCount = 0L;

    public static class CacheEntry {
        public final AnalysisResult result;
        public final long expireAt;

        public CacheEntry(AnalysisResult result, long ttlMs) {
            this.result = result;
            this.expireAt = System.currentTimeMillis() + ttlMs;
        }

        public boolean isExpired(long now) {
            return now > expireAt;
        }
    }

    public SemanticDiagnosisCache() {
        this(DEFAULT_CAPACITY, DEFAULT_TTL_MS);
    }

    public SemanticDiagnosisCache(int capacity, long ttlMs) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive: " + capacity);
        }
        if (ttlMs <= 0) {
            throw new IllegalArgumentException("Cache TTL must be positive: " + ttlMs);
        }
        this.capacity = capacity;
        this.ttlMs = ttlMs;
        // accessOrder=true：每次 get 会把条目挪到队尾，队首即最久未访问项（LRU 淘汰依据）
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                if (size() > SemanticDiagnosisCache.this.capacity) {
                    evictionCount++;
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * 提取日志的结构指纹：抹掉时间戳 / IP / UUID 等瞬态噪声后取 SHA-256。
     *
     * 同一类攻击的不同时间、不同来源 IP 会得到相同指纹，从而命中同一条诊断结论。
     */
    public String computeSemanticSignature(String logContent) {
        if (logContent == null || logContent.isBlank()) {
            return "";
        }

        String normalized = TS_PATTERN.matcher(logContent).replaceAll("<TIMESTAMP>");
        normalized = IP_PATTERN.matcher(normalized).replaceAll("<IP>");
        normalized = UUID_PATTERN.matcher(normalized).replaceAll("<UUID>");
        normalized = normalized.trim().toLowerCase();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            return String.valueOf(normalized.hashCode());
        }
    }

    /**
     * 查询缓存：未命中或已过期一律计 miss，过期项立即剔除。
     */
    public synchronized AnalysisResult get(String logContent) {
        String signature = computeSemanticSignature(logContent);
        if (signature.isEmpty()) {
            missCount++;
            return null;
        }

        CacheEntry entry = cache.get(signature);
        if (entry == null) {
            missCount++;
            return null;
        }

        if (entry.isExpired(System.currentTimeMillis())) {
            cache.remove(signature);
            missCount++;
            return null;
        }

        hitCount++;
        log.info("[SEMANTIC_CACHE_HIT] Signature: {}, TotalHits: {}",
                signature.substring(0, Math.min(8, signature.length())), hitCount);
        return entry.result;
    }

    /**
     * 写入缓存：写前做一次过期清扫，避免过期项长期占用容量导致有效命中被挤出。
     */
    public synchronized void put(String logContent, AnalysisResult result) {
        if (logContent == null || result == null) {
            return;
        }
        String signature = computeSemanticSignature(logContent);
        if (signature.isEmpty()) {
            return;
        }

        evictExpired();
        cache.put(signature, new CacheEntry(result, ttlMs));
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CacheEntry>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    /** 清空缓存并重置全部统计计数（便于测试与运维手动复位）。 */
    public synchronized void clear() {
        cache.clear();
        hitCount = 0L;
        missCount = 0L;
        evictionCount = 0L;
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized long getHitCount() {
        return hitCount;
    }

    public synchronized long getMissCount() {
        return missCount;
    }

    public synchronized long getEvictionCount() {
        return evictionCount;
    }
}
