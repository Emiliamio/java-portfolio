"""
单元测试 — log-parser 各模块测试

运行: pytest tests/ -v
"""

import os
import sys
import pytest
import pandas as pd

# 确保项目根目录在 sys.path 中
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from log_parser.parser import (
    extract_timestamp,
    extract_ip,
    extract_operation,
    extract_result,
    extract_severity,
    extract_username,
    extract_detail,
    extract_source_file,
    parse_line,
    parse_file,
)
from log_parser.anomaly import detect_brute_force, detect_anomalies
from log_parser.exporter import generate_insert_sql, _escape_sql


# ── Parser 单元测试 ──────────────────────────────────────


class TestExtractTimestamp:
    def test_iso_format(self):
        result = extract_timestamp("2025-01-15 08:23:45 User admin LOGIN SUCCESS")
        assert result == "2025-01-15 08:23:45"

    def test_iso_with_ms(self):
        result = extract_timestamp("2025-01-15 08:23:45.123 Some log entry")
        assert result == "2025-01-15 08:23:45.123"

    def test_apache_format(self):
        result = extract_timestamp(
            '15/Jan/2025:08:23:45 +0800 GET /api/users 200'
        )
        assert result == "15/Jan/2025:08:23:45 +0800"

    def test_syslog_format(self):
        result = extract_timestamp("Jan 15 08:23:45 hostname sshd[123]: message")
        assert result == "Jan 15 08:23:45"

    def test_no_timestamp(self):
        assert extract_timestamp("No timestamp here") is None


class TestExtractIP:
    def test_standard_ip(self):
        assert extract_ip("192.168.1.10 User admin LOGIN") == "192.168.1.10"

    def test_private_ip(self):
        assert extract_ip("10.0.0.55 zhangsan LOGIN FAIL") == "10.0.0.55"

    def test_no_ip(self):
        assert extract_ip("LOGIN SUCCESS") is None

    def test_invalid_ip(self):
        assert extract_ip("999.999.999.999 is not valid") is None


class TestExtractOperation:
    def test_login(self):
        assert extract_operation("User admin LOGIN SUCCESS") == "LOGIN"

    def test_unknown(self):
        assert extract_operation("Some random text") == "UNKNOWN"

    def test_case_insensitive(self):
        assert extract_operation("user login success") == "LOGIN"


class TestExtractResult:
    def test_success(self):
        assert extract_result("LOGIN SUCCESS at 08:00") == "SUCCESS"

    def test_fail(self):
        assert extract_result("LOGIN FAIL: wrong password") == "FAIL"

    def test_chinese(self):
        assert extract_result("操作成功完成") == "SUCCESS"

    def test_unknown(self):
        assert extract_result("some text") == "UNKNOWN"


class TestExtractSeverity:
    def test_error(self):
        assert extract_severity("ERROR: connection refused") == "ERROR"

    def test_critical(self):
        assert extract_severity("CRITICAL: disk full") == "CRITICAL"

    def test_default(self):
        assert extract_severity("just a regular log entry") == "INFO"


class TestExtractUsername:
    def test_user_prefix(self):
        assert extract_username("2025-01-15 User admin LOGIN") == "admin"

    def test_username_eq(self):
        assert extract_username("username=zhangsan LOGIN FAIL") == "zhangsan"

    def test_user_colon(self):
        assert extract_username("user:lisi QUERY SUCCESS") == "lisi"

    def test_unknown(self):
        assert extract_username("LOGIN SUCCESS") == "unknown"


class TestExtractDetail:
    def test_quoted_detail(self):
        result = extract_detail('User admin LOGIN SUCCESS "logged in from workstation"')
        assert result == "logged in from workstation"

    def test_no_detail(self):
        assert extract_detail("LOGIN SUCCESS") is None


class TestExtractSourceFile:
    def test_log_file(self):
        assert extract_source_file("Error in auth-service.log: connection refused") == "auth-service.log"

    def test_py_file(self):
        assert extract_source_file("Traceback in app.py line 42") == "app.py"

    def test_no_file(self):
        assert extract_source_file("no file here") is None


class TestParseLine:
    def test_full_line(self):
        result = parse_line(
            "2025-01-15 08:23:45 192.168.1.10 User admin LOGIN SUCCESS "
            '"logged in successfully" INFO auth-service.log'
        )
        assert result["timestamp"] == "2025-01-15 08:23:45"
        assert result["ip_address"] == "192.168.1.10"
        assert result["username"] == "admin"
        assert result["operation"] == "LOGIN"
        assert result["operation_result"] == "SUCCESS"
        assert result["detail"] == "logged in successfully"
        assert result["severity"] == "INFO"
        assert result["source_file"] == "auth-service.log"


# ── Parser 文件级测试 ────────────────────────────────────

SAMPLES_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "sample_logs",
)


class TestParseCSV:
    def test_parse_csv(self):
        csv_path = os.path.join(SAMPLES_DIR, "access.csv")
        if not os.path.exists(csv_path):
            pytest.skip("sample_logs/access.csv not found")
        df = parse_file(csv_path)
        assert isinstance(df, pd.DataFrame)
        assert len(df) > 0
        assert "ip_address" in df.columns
        assert "operation" in df.columns


class TestParseText:
    def test_parse_text(self):
        log_path = os.path.join(SAMPLES_DIR, "server.log")
        if not os.path.exists(log_path):
            pytest.skip("sample_logs/server.log not found")
        df = parse_file(log_path)
        assert isinstance(df, pd.DataFrame)
        assert len(df) > 0
        assert "ip_address" in df.columns


# ── Anomaly Detection 测试 ──────────────────────────────


class TestDetectBruteForce:
    @pytest.fixture
    def sample_df(self):
        return pd.DataFrame({
            "timestamp": pd.date_range("2025-01-15", periods=10, freq="1min"),
            "ip_address": ["10.0.0.1"] * 6 + ["192.168.1.10"] * 4,
            "username": ["test"] * 10,
            "operation": ["LOGIN"] * 10,
            "operation_result": ["FAIL"] * 6 + ["SUCCESS"] * 4,
        })

    def test_detects_suspicious(self, sample_df):
        result = detect_brute_force(sample_df, threshold=5)
        assert len(result) == 1  # Only 10.0.0.1 has 6 failures
        assert result.iloc[0]["ip_address"] == "10.0.0.1"
        assert result.iloc[0]["failed_attempts"] == 6
        assert result.iloc[0]["risk_level"] == "LOW"

    def test_higher_threshold_excludes(self, sample_df):
        result = detect_brute_force(sample_df, threshold=10)
        assert len(result) == 0  # No IP reaches 10 failures

    def test_empty_input(self):
        result = detect_brute_force(pd.DataFrame(), threshold=5)
        assert result.empty


# ── SQL Exporter 测试 ────────────────────────────────────


class TestEscapeSQL:
    def test_null(self):
        assert _escape_sql(None) == "NULL"

    def test_string(self):
        assert _escape_sql("hello") == "'hello'"

    def test_single_quote(self):
        assert _escape_sql("it's") == "'it\\'s'"

    def test_backslash(self):
        assert _escape_sql("path\\to") == "'path\\\\to'"

    def test_nan(self):
        assert _escape_sql(float("nan")) == "NULL"


class TestGenerateInsertSQL:
    def test_single_row(self):
        df = pd.DataFrame([{
            "timestamp": pd.Timestamp("2025-01-15 08:00:01"),
            "ip_address": "192.168.1.10",
            "username": "admin",
            "operation": "LOGIN",
            "operation_result": "SUCCESS",
            "detail": "logged in",
            "severity": "INFO",
            "source_file": "auth.log",
        }])
        sql = generate_insert_sql(df)
        assert "INSERT INTO log_entry" in sql
        assert "'192.168.1.10'" in sql
        assert "'admin'" in sql
        assert "'LOGIN'" in sql

    def test_empty_df(self):
        df = pd.DataFrame()
        sql = generate_insert_sql(df)
        assert "-- Auto-generated" in sql


# ── Integration 测试 ─────────────────────────────────────


class TestIntegration:
    """端到端集成测试：解析 → 检测 → 导出。"""

    def test_full_pipeline(self):
        """验证从文本日志到 SQL 的完整链路。"""
        log_path = os.path.join(SAMPLES_DIR, "server.log")
        if not os.path.exists(log_path):
            pytest.skip("sample_logs/server.log not found")

        # 1. 解析
        df = parse_file(log_path)
        assert len(df) > 0

        # 2. 异常检测
        report = detect_anomalies(df, threshold=5)
        assert "total_records" in report
        assert "suspicious_ips" in report
        assert report["total_records"] > 0

        # 3. SQL 导出
        sql = generate_insert_sql(df.head(5))
        assert "INSERT INTO log_entry" in sql

        # 4. 可疑 IP 应该包括 10.0.0.55 (zhangsan 6次失败) 和 172.16.0.88 (10次失败)
        suspicious_ips = report["suspicious_ips"]["ip_address"].tolist()
        assert "10.0.0.55" in suspicious_ips
        assert "172.16.0.88" in suspicious_ips


class TestMultiLineParser:
    def test_merge_java_stacktrace(self, tmp_path):
        content = (
            '2025-01-15 08:30:00 192.168.1.50 User admin QUERY ERROR "Database connection timeout"\n'
            '\tat com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure\n'
            '\tat com.zaxxer.hikari.pool.PoolBase.newConnection(PoolBase.java:364)\n'
            'Caused by: java.net.ConnectException: Connection refused: connect\n'
            '2025-01-15 08:30:05 192.168.1.50 User admin QUERY SUCCESS "Query finished after retry"\n'
        )
        log_file = tmp_path / "stacktrace.log"
        log_file.write_text(content, encoding="utf-8")

        df = parse_file(str(log_file), merge_multiline=True)
        assert len(df) == 2
        assert "CommunicationsException" in df.iloc[0]["detail"]
        assert "Caused by" in df.iloc[0]["detail"]
        assert df.iloc[1]["operation_result"] == "SUCCESS"


class TestGzipParser:
    def test_parse_gzip_text_log(self, tmp_path):
        import gzip
        content = (
            '2025-01-15 08:00:00 10.0.0.1 User zhangsan LOGIN SUCCESS "Auth passed"\n'
            '2025-01-15 08:01:00 10.0.0.2 User lisi LOGIN FAIL "Invalid password"\n'
        )
        gz_path = tmp_path / "compressed.log.gz"
        with gzip.open(gz_path, "wt", encoding="utf-8") as f:
            f.write(content)

        df = parse_file(str(gz_path))
        assert len(df) == 2
        assert df.iloc[0]["username"] == "zhangsan"
        assert df.iloc[1]["operation_result"] == "FAIL"

    def test_parse_gzip_csv_log(self, tmp_path):
        import gzip
        csv_content = (
            "timestamp,ip_address,username,operation,operation_result,detail,severity,source_file\n"
            "2025-01-15 08:00:00,192.168.1.1,admin,LOGIN,SUCCESS,ok,INFO,app.log\n"
        )
        gz_csv_path = tmp_path / "test.csv.gz"
        with gzip.open(gz_csv_path, "wt", encoding="utf-8") as f:
            f.write(csv_content)

        df = parse_file(str(gz_csv_path))
        assert len(df) == 1
        assert df.iloc[0]["username"] == "admin"


class TestNginxCombinedLog:
    def test_nginx_200_access(self):
        line = '192.168.1.100 - zhangsan [15/Jan/2025:08:23:45 +0800] "GET /api/v1/users HTTP/1.1" 200 4523 "https://google.com" "Mozilla/5.0"'
        record = parse_line(line)
        assert record["ip_address"] == "192.168.1.100"
        assert record["username"] == "zhangsan"
        assert record["operation"] == "GET"
        assert record["operation_result"] == "SUCCESS"
        assert record["severity"] == "INFO"
        assert "GET /api/v1/users" in record["detail"]

    def test_nginx_500_error(self):
        line = '10.0.0.99 - - [15/Jan/2025:08:24:00 +0800] "POST /api/pay HTTP/1.1" 500 120 "-" "curl/7.68.0"'
        record = parse_line(line)
        assert record["ip_address"] == "10.0.0.99"
        assert record["username"] == "anonymous"
        assert record["operation"] == "POST"
        assert record["operation_result"] == "FAIL"
        assert record["severity"] == "ERROR"


