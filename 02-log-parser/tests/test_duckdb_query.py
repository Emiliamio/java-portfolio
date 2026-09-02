"""
LogScope — DuckDB 嵌入式 OLAP 查询与聚合单元测试
"""

import os
import pytest
import pandas as pd
from log_parser.exporter import export_to_parquet
from log_parser.duckdb_query import query_parquet, analyze_log_summary


@pytest.fixture
def sample_parquet_file(tmp_path):
    df = pd.DataFrame([
        {
            "timestamp": "2026-09-02 15:30:00",
            "ip_address": "192.168.1.10",
            "username": "admin",
            "operation": "LOGIN",
            "operation_result": "SUCCESS",
            "detail": "User login ok",
            "severity": "INFO",
            "source_file": "auth.log"
        },
        {
            "timestamp": "2026-09-02 15:30:01",
            "ip_address": "192.168.1.10",
            "username": "admin",
            "operation": "EXPORT",
            "operation_result": "SUCCESS",
            "detail": "Export report",
            "severity": "INFO",
            "source_file": "auth.log"
        },
        {
            "timestamp": "2026-09-02 15:30:02",
            "ip_address": "198.51.100.77",
            "username": "attacker",
            "operation": "SQLI",
            "operation_result": "FAIL",
            "detail": "' OR '1'='1",
            "severity": "CRITICAL",
            "source_file": "auth.log"
        }
    ])
    parquet_path = tmp_path / "test_data.parquet"
    export_to_parquet(df, str(parquet_path))
    return str(parquet_path)


def test_duckdb_sql_aggregation(sample_parquet_file):
    sql = "SELECT ip_address, count(*) as count FROM logs GROUP BY ip_address ORDER BY count DESC"
    result_df = query_parquet(sample_parquet_file, sql)

    assert not result_df.empty
    assert len(result_df) == 2
    assert result_df.iloc[0]["ip_address"] == "192.168.1.10"
    assert result_df.iloc[0]["count"] == 2


def test_duckdb_analyze_log_summary(sample_parquet_file):
    summary = analyze_log_summary(sample_parquet_file)

    assert summary["total_count"] == 3
    assert summary["unique_ips"] == 2
    assert summary["error_count"] == 1
    assert summary["error_rate"] == 33.33
    assert len(summary["top_ips"]) == 2
    assert summary["top_ips"][0]["ip_address"] == "192.168.1.10"


def test_duckdb_query_non_existent_file():
    with pytest.raises(FileNotFoundError):
        query_parquet("non_existent_file.parquet", "SELECT * FROM logs")
