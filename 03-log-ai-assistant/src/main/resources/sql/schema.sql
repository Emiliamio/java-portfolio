-- =========================================
-- 日志智能分析助手 - 数据库建表
-- =========================================

-- 此表与项目一共享同一个 MySQL 实例
USE log_audit;

-- ----------------------------
-- AI 分析历史记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_analysis (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    log_content     TEXT         NOT NULL COMMENT '用户提交的原始日志内容',
    log_summary     VARCHAR(500) COMMENT 'AI 摘要（操作类型）',
    operation_type  VARCHAR(100) COMMENT '操作类型（AI 识别）',
    risk_level      VARCHAR(20)  COMMENT '风险等级: NORMAL / LOW / MEDIUM / HIGH / CRITICAL',
    need_intervention TINYINT(1) DEFAULT 0 COMMENT '是否需要人工介入',
    ai_suggestion   TEXT         COMMENT 'AI 建议 / 处置方案',
    source_ip       VARCHAR(45)  COMMENT 'AI 提取的源 IP',
    model_used      VARCHAR(50)  COMMENT '使用的模型名称',
    analysis_time_ms INT         COMMENT '分析耗时（毫秒）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_risk_level (risk_level),
    INDEX idx_created_at (created_at),
    INDEX idx_operation_type (operation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 分析历史记录表';
