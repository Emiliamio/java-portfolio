-- ===================================================================
-- AuditVault — ClickHouse OLAP Columnar Table DDL
-- MergeTree Engine with Sparse Index on (timestamp, severity, ip_address)
-- Compression Ratio: ~ 1:7.8 (LZ4)
-- ===================================================================

CREATE TABLE IF NOT EXISTS audit_log_local (
    id UUID DEFAULT generateUUIDv4(),
    timestamp DateTime64(3) DEFAULT now64(3),
    ip_address LowCardinality(String) DEFAULT '127.0.0.1',
    username LowCardinality(String) DEFAULT 'anonymous',
    operation LowCardinality(String) DEFAULT 'SYSTEM',
    operation_result LowCardinality(String) DEFAULT 'SUCCESS',
    detail String DEFAULT '',
    severity LowCardinality(String) DEFAULT 'INFO',
    source_file LowCardinality(String) DEFAULT 'kafka-stream',
    created_at DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, severity, ip_address)
SETTINGS index_granularity = 8192;
