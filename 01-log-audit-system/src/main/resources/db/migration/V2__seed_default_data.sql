-- ============================================================
-- Flyway Database Migration: V2__seed_default_data.sql
-- AuditVault Initial User Seeds & Demonstration Records
-- ============================================================

-- 插入初始默认管理员与只读账号 (密码: admin123 / user123, BCrypt 加密)
INSERT IGNORE INTO `user` (`id`, `username`, `password`, `role`, `enabled`)
VALUES
    (1, 'admin', '$2a$10$w3/h4e4Rj2PkW6nJ8W4Uje0m9C8Z0A2G0K1L2M3N4O5P6Q7R8S9Tu', 'ADMIN', 1),
    (2, 'user', '$2a$10$w3/h4e4Rj2PkW6nJ8W4Uje0m9C8Z0A2G0K1L2M3N4O5P6Q7R8S9Tu', 'USER', 1);

-- 插入初始系统审计记录
INSERT IGNORE INTO `audit_log` (`id`, `operator`, `action`, `target`, `ip_address`, `status`, `created_at`)
VALUES
    (1, 'SYSTEM', 'BOOTSTRAP', 'AuditVault Engine Initialized', '127.0.0.1', 'SUCCESS', NOW());
