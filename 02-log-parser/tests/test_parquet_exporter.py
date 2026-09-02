"""
LogScope — Parquet 列式存储导出单元测试
"""

import os
import tempfile
import pytest
import pandas as pd
from log_parser.exporter import export_to_parquet


@pytest.fixture
def sample_dataframe():
    return pd.DataFrame([
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
            "ip_address": "10.0.0.5",
            "username": "guest",
            "operation": "QUERY",
            "operation_result": "FAIL",
            "detail": "Unauthorized access",
            "severity": "WARN",
            "source_file": "auth.log"
        }
    ])


def test_export_to_parquet_creates_valid_file(sample_dataframe, tmp_path):
    output_parquet = tmp_path / "output_test.parquet"
    out_path = export_to_parquet(sample_dataframe, str(output_parquet))

    assert os.path.exists(out_path)
    assert os.path.getsize(out_path) > 0

    # 读取并验证数据完整性
    read_df = pd.read_parquet(out_path)
    assert len(read_df) == 2
    assert "ip_address" in read_df.columns
    assert read_df.iloc[0]["username"] == "admin"
    assert read_df.iloc[1]["severity"] == "WARN"


def test_export_to_parquet_empty_df(tmp_path):
    empty_df = pd.DataFrame(columns=["timestamp", "ip_address", "username"])
    output_parquet = tmp_path / "empty.parquet"
    out_path = export_to_parquet(empty_df, str(output_parquet))

    assert os.path.exists(out_path)
    read_df = pd.read_parquet(out_path)
    assert read_df.empty
