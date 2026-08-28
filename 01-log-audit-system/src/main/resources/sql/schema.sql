-- =========================================
-- 日志审计系统 - 数据库建表脚本
-- =========================================

-- CREATE DATABASE IF NOT EXISTS log_audit DEFAULT CHARACTER SET utf8mb4;
USE log_audit;

-- ----------------------------
-- 1. 用户表
-- ----------------------------
CREATE TABLE IF NOT EXISTS user (
                                    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 2. 日志记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS log_entry (
                                         id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         timestamp       DATETIME      NOT NULL COMMENT '日志时间',
                                         ip_address      VARCHAR(45)   NOT NULL COMMENT '来源IP',
    username        VARCHAR(50)   NOT NULL COMMENT '操作用户',
    operation       VARCHAR(100)  NOT NULL COMMENT '操作类型',
    operation_result VARCHAR(20)  NOT NULL COMMENT '操作结果：SUCCESS/FAIL',
    detail          TEXT          COMMENT '操作详情',
    severity        VARCHAR(20)   NOT NULL DEFAULT 'INFO' COMMENT '严重程度',
    source_file     VARCHAR(255)  COMMENT '来源文件',
    trace_id        VARCHAR(64)   COMMENT '分布式链路追踪ID',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_timestamp (timestamp),
    INDEX idx_ip_address (ip_address),
    INDEX idx_operation (operation),
    INDEX idx_severity (severity),
    INDEX idx_timestamp_ip (timestamp, ip_address),
    INDEX idx_username (username),
    INDEX idx_trace_id (trace_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日志记录表';

-- ----------------------------
-- 3. 操作审计表
-- ----------------------------
CREATE TABLE IF NOT EXISTS audit_log (
                                         id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         operator        VARCHAR(50)  NOT NULL COMMENT '操作者',
    action          VARCHAR(50)  NOT NULL COMMENT '操作类型',
    target          VARCHAR(255) COMMENT '操作目标',
    ip_address      VARCHAR(45)  COMMENT '操作者IP',
    status          VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_operator (operator),
    INDEX idx_action (action),
    INDEX idx_created_at (created_at),
    INDEX idx_operator_time (operator, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计表';