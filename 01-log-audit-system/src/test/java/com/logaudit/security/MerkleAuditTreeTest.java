package com.logaudit.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@DisplayName("AuditVault 极境天花板测试：密码学 Merkle Tree 默克尔根哈希与对数级包含证明")
class MerkleAuditTreeTest {

    @Test
    @DisplayName("测试构建默克尔树与根哈希确定性 (buildMerkleTree)")
    void testBuildMerkleTreeAndRootConsistency() {
        MerkleAuditTreeService service = new MerkleAuditTreeService();

        List<String> leaves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            leaves.add(MerkleAuditTreeService.sha256("log_record_" + i));
        }

        // 构建 4 个叶子节点的树，深度应为 3
        MerkleAuditTreeService.MerkleTreeResult res = service.buildMerkleTree(leaves);
        Assertions.assertNotNull(res.getRootHash());
        Assertions.assertEquals(4, res.getLeafCount());
        Assertions.assertEquals(3, res.getTreeDepth());

        // 验证奇数个叶子节点 (5个) 也能正常处理
        leaves.add(MerkleAuditTreeService.sha256("log_record_4"));
        MerkleAuditTreeService.MerkleTreeResult oddRes = service.buildMerkleTree(leaves);
        Assertions.assertNotNull(oddRes.getRootHash());
        Assertions.assertEquals(5, oddRes.getLeafCount());
        Assertions.assertEquals(4, oddRes.getTreeDepth());
    }

    @Test
    @DisplayName("测试 O(log N) 默克尔包含证明生成与防伪核验 (generateProof & verifyProof)")
    void testGenerateAndVerifyProof() {
        MerkleAuditTreeService service = new MerkleAuditTreeService();

        // 构造 8 条日志哈希
        List<String> leaves = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            leaves.add(MerkleAuditTreeService.sha256("financial_tx_" + i));
        }

        MerkleAuditTreeService.MerkleTreeResult tree = service.buildMerkleTree(leaves);
        String rootHash = tree.getRootHash();

        // 1. 为第 3 个交易生成默克尔证明 (Proof 大小应为 log2(8) = 3)
        int targetIndex = 3;
        String targetLeaf = leaves.get(targetIndex);
        List<MerkleAuditTreeService.ProofNode> proof = service.generateProof(targetIndex, leaves);
        Assertions.assertEquals(3, proof.size());

        // 2. 正常核验 -> 必须为 true
        boolean valid = service.verifyProof(targetLeaf, proof, rootHash);
        Assertions.assertTrue(valid);

        // 3. 篡改叶子内容 -> 必须为 false
        String tamperedLeaf = MerkleAuditTreeService.sha256("tampered_tx_3");
        boolean tamperedValid = service.verifyProof(tamperedLeaf, proof, rootHash);
        Assertions.assertFalse(tamperedValid);
    }
}