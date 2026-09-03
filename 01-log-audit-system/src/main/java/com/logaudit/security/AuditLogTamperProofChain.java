package com.logaudit.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 区块链式防篡改哈希审计链 (Cryptographic Tamper-Proof Audit Hash Chain)
 * 对标 AWS CloudTrail Log File Integrity 与金融等保三级最高防篡改抵赖标准：
 * 1. 采用 SHA-256 密码学单向哈希链式单向锁结构；
 * 2. CurrentHash = SHA-256(PrevHash + ":" + LogId + ":" + Timestamp + ":" + Operation + ":" + Payload)；
 * 3. 一旦数据库底层单行数据被 DBA 内鬼或黑客物理篡改、删除或重新排序，整条哈希链即刻熔断报警。
 */
@Service
public class AuditLogTamperProofChain {

    private static final Logger log = LoggerFactory.getLogger(AuditLogTamperProofChain.class);

    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    public static class AuditBlock implements Serializable {
        private final int blockIndex;
        private final Long logId;
        private final long timestamp;
        private final String operation;
        private final String payload;
        private final String prevHash;
        private final String currentHash;

        public AuditBlock(int blockIndex, Long logId, long timestamp, String operation, String payload, String prevHash, String currentHash) {
            this.blockIndex = blockIndex;
            this.logId = logId;
            this.timestamp = timestamp;
            this.operation = operation != null ? operation : "";
            this.payload = payload != null ? payload : "";
            this.prevHash = prevHash != null ? prevHash : GENESIS_HASH;
            this.currentHash = currentHash != null ? currentHash : "";
        }

        public int getBlockIndex() { return blockIndex; }
        public Long getLogId() { return logId; }
        public long getTimestamp() { return timestamp; }
        public String getOperation() { return operation; }
        public String getPayload() { return payload; }
        public String getPrevHash() { return prevHash; }
        public String getCurrentHash() { return currentHash; }
    }

    public static class VerificationResult implements Serializable {
        private final boolean valid;
        private final int brokenAtIndex;
        private final String reason;

        public VerificationResult(boolean valid, int brokenAtIndex, String reason) {
            this.valid = valid;
            this.brokenAtIndex = brokenAtIndex;
            this.reason = reason;
        }

        public boolean isValid() { return valid; }
        public int getBrokenAtIndex() { return brokenAtIndex; }
        public String getReason() { return reason; }
    }

    private final List<AuditBlock> memoryChain = Collections.synchronizedList(new ArrayList<>());

    /**
     * 计算密码学 SHA-256 签名
     */
    public static String calculateHash(String prevHash, Long logId, long timestamp, String operation, String payload) {
        String dataToHash = String.format("%s:%d:%d:%s:%s",
                prevHash != null ? prevHash : GENESIS_HASH,
                logId != null ? logId : 0L,
                timestamp,
                operation != null ? operation : "",
                payload != null ? payload : "");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    /**
     * 向哈希审计链追加新日志块
     */
    public synchronized AuditBlock appendEntry(Long logId, long timestamp, String operation, String payload) {
        String prevHash = memoryChain.isEmpty() ? GENESIS_HASH : memoryChain.get(memoryChain.size() - 1).getCurrentHash();
        int newIndex = memoryChain.size();
        String currentHash = calculateHash(prevHash, logId, timestamp, operation, payload);

        AuditBlock block = new AuditBlock(newIndex, logId, timestamp, operation, payload, prevHash, currentHash);
        memoryChain.add(block);
        return block;
    }

    /**
     * 校验整条哈希链的完整性与防篡改证明
     */
    public VerificationResult verifyChainIntegrity(List<AuditBlock> chain) {
        if (chain == null || chain.isEmpty()) {
            return new VerificationResult(true, -1, "Empty chain");
        }

        for (int i = 0; i < chain.size(); i++) {
            AuditBlock current = chain.get(i);

            // 1. 验证前置哈希链接
            if (i == 0) {
                if (!GENESIS_HASH.equals(current.getPrevHash())) {
                    return new VerificationResult(false, 0, "Genesis block prevHash mismatch");
                }
            } else {
                AuditBlock prev = chain.get(i - 1);
                if (!Objects.equals(current.getPrevHash(), prev.getCurrentHash())) {
                    log.error("🚨 [TAMPER_DETECTED] 审计哈希链断裂! 索引 {} 处的 prevHash 与前一区块哈希不匹配", i);
                    return new VerificationResult(false, i, "Hash chain link broken at index " + i);
                }
            }

            // 2. 验证当前块内容重新计算哈希是否一致
            String expectedHash = calculateHash(current.getPrevHash(), current.getLogId(),
                    current.getTimestamp(), current.getOperation(), current.getPayload());

            if (!expectedHash.equals(current.getCurrentHash())) {
                log.error("🚨 [TAMPER_DETECTED] 审计日志内容被篡改! 索引 {} 处的真实内容签名与登记签名不符", i);
                return new VerificationResult(false, i, "Data payload tampered at block index " + i);
            }
        }

        log.info("🛡️ [CHAIN_VERIFIED] 审计哈希链完整性自检通过，已核验 {} 个数据区块", chain.size());
        return new VerificationResult(true, -1, "Integrity verified successfully");
    }

    public List<AuditBlock> getChainSnapshot() {
        return new ArrayList<>(memoryChain);
    }
}