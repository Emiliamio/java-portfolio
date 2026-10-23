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

        // 构建 4 个叶子节点的树，深度应为 3 (叶子层1 -> 内部层2 -> 根节点3)
        MerkleAuditTreeService.MerkleTreeResult res = service.buildMerkleTree(leaves);
        Assertions.assertNotNull(res.getRootHash());
        Assertions.assertEquals(4, res.getLeafCount());
        Assertions.assertEquals(3, res.getTreeDepth());

        // 验证奇数个叶子节点 (5个) 提升处理，深度应为 4
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

    @Test
    @DisplayName("抗第二原像与消除 CVE-2012-2459：内容前缀相同但叶子数不同的树根哈希必不相同")
    void testOddPromotionPreventsCve20122459Collision() {
        MerkleAuditTreeService service = new MerkleAuditTreeService();
        List<String> leaves3 = List.of("tx_0", "tx_1", "tx_2");
        List<String> leaves4Duplicated = List.of("tx_0", "tx_1", "tx_2", "tx_2");

        MerkleAuditTreeService.MerkleTreeResult tree3 = service.buildMerkleTree(leaves3);
        MerkleAuditTreeService.MerkleTreeResult tree4 = service.buildMerkleTree(leaves4Duplicated);

        Assertions.assertNotEquals(tree3.getRootHash(), tree4.getRootHash(),
                "奇数提升机制必须消除 CVE-2012-2459 同型碰撞：[A,B,C] 与 [A,B,C,C] 根哈希必须不同");
    }

    @Test
    @DisplayName("奇数叶子提升场景下，每个叶子的包含证明都必须校验通过")
    void testProofValidForEveryLeafInOddTree() {
        MerkleAuditTreeService service = new MerkleAuditTreeService();
        List<String> leaves = List.of("odd_0", "odd_1", "odd_2", "odd_3", "odd_4");

        String rootHash = service.buildMerkleTree(leaves).getRootHash();
        Assertions.assertNotNull(rootHash);

        // 奇数树存在「提升节点」，其证明链层级比兄弟节点少一层，必须逐叶验证而非只测一层
        for (int i = 0; i < leaves.size(); i++) {
            List<MerkleAuditTreeService.ProofNode> proof = service.generateProof(i, leaves);
            Assertions.assertTrue(service.verifyProof(leaves.get(i), proof, rootHash),
                    "第 " + i + " 个叶子的包含证明校验失败（提升节点场景）");
        }

        // 提升节点（最后一个叶子）的证明链不应包含冗余的自我复制节点
        List<MerkleAuditTreeService.ProofNode> lastProof = service.generateProof(4, leaves);
        Assertions.assertEquals(1, lastProof.size(),
                "末尾提升叶子的证明链只应剩最顶层的唯一兄弟节点");
    }
}
