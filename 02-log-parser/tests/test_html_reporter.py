"""
HTML 报告生成器单元测试。
"""

import os
import pandas as pd
import pytest

from log_parser.anomaly import detect_anomalies
from log_parser.cli import main
from log_parser.parser import parse_file
from log_parser.reporter_html import export_to_html


@pytest.fixture
def sample_report():
    suspicious_data = pd.DataFrame([
        {
            "ip_address": "192.168.1.100",
            "fail_count": 12,
            "first_seen": "2025-01-15 08:00:00",
            "last_seen": "2025-01-15 08:05:00",
        },
        {
            "ip_address": "10.0.0.50",
            "fail_count": 6,
            "first_seen": "2025-01-15 09:00:00",
            "last_seen": "2025-01-15 09:02:00",
        }
    ])

    return {
        "total_records": 100,
        "failed_records": 18,
        "unique_ips": 15,
        "suspicious_ips": suspicious_data,
        "fail_operations": {"LOGIN": 12, "ACCESS": 6},
        "severity_distribution": {"INFO": 80, "WARN": 10, "ERROR": 8, "CRITICAL": 2},
    }


def test_export_to_html_creates_valid_file(sample_report, tmp_path):
    output_file = tmp_path / "test_report.html"
    export_to_html(sample_report, str(output_file))

    assert output_file.exists()
    content = output_file.read_text(encoding="utf-8")
    assert "<!DOCTYPE html>" in content
    assert "LogScope" in content
    assert "192.168.1.100" in content
    assert "10.0.0.50" in content
    assert "100" in content
    assert "18" in content
    assert "高危异常" in content


def test_export_to_html_empty_report(tmp_path):
    output_file = tmp_path / "empty_report.html"
    empty_report = {
        "total_records": 0,
        "failed_records": 0,
        "unique_ips": 0,
        "suspicious_ips": pd.DataFrame(),
        "fail_operations": {},
        "severity_distribution": {},
    }
    export_to_html(empty_report, str(output_file))

    assert output_file.exists()
    content = output_file.read_text(encoding="utf-8")
    assert "<!DOCTYPE html>" in content
    assert "未发现超过阈值的可疑 IP" in content


def test_cli_with_html_flag(tmp_path):
    access_csv = os.path.join(os.path.dirname(__file__), "..", "sample_logs", "access.csv")
    out_dir = str(tmp_path / "out")

    exit_code = main(["-i", access_csv, "-o", out_dir, "--html"])
    assert exit_code == 0

    html_report = os.path.join(out_dir, "access_report.html")
    assert os.path.isfile(html_report)
    with open(html_report, "r", encoding="utf-8") as f:
        html_text = f.read()
    assert "LogScope" in html_text
    assert "Security Report" in html_text
