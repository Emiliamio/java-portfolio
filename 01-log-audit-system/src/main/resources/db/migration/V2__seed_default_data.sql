-- ============================================================
-- Flyway Database Migration: V2__seed_default_data.sql
-- AuditVault Initial User Seeds & Demonstration Records
-- ============================================================

-- 插入初始默认管理员与只读账号 (密码: admin123 / user123, BCrypt 加密)
INSERT IGNORE INTO `user` (`id`, `username`, `password`, `role`, `enabled`)
VALUES
    (1, 'admin', '$2b$10$Qa4pHGIGBVi.rf5CQRQLVemQd18o8MoBnaFP8RvvVR1omgbUXAiDO', 'ADMIN', 1),
    (2, 'user', '$2a$10$ev8OMFN786yRCWL0diOqx..JCfA0bW3Vlhx6mwlW2mNTBX3WarG7y', 'USER', 1);

-- 插入初始系统审计记录
INSERT IGNORE INTO `audit_log` (`id`, `operator`, `action`, `target`, `ip_address`, `status`, `created_at`)
VALUES
    (1, 'SYSTEM', 'BOOTSTRAP', 'AuditVault Engine Initialized', '127.0.0.1', 'SUCCESS', NOW());
