"""
LogScope mmap 零拷贝与多进程并行解析单元测试
"""

import os
import pytest
import pandas as pd
from log_parser.mmap_parser import parse_log_with_mmap, parse_log_parallel_mmap


@pytest.fixture
def sample_log_file(tmp_path):
    log_content = (
        "2025-01-15 08:00:01 192.168.1.100 User admin LOGIN SUCCESS normal login\n"
        "2025-01-15 08:00:02 192.168.1.101 User zhangsan QUERY SUCCESS select * from user\n"
        "2025-01-15 08:00:03 10.0.0.5 User lisi EXPORT ERROR java.lang.NullPointerException\n"
        "\tat com.example.Service.run(Service.java:42)\n"
        "\tat com.example.Main.main(Main.java:10)\n"
        "2025-01-15 08:00:04 192.168.1.105 User test ATTACK CRITICAL SQL injection: ' OR '1'='1\n"
    )
    file_path = tmp_path / "server_test.log"
    file_path.write_text(log_content, encoding="utf-8")
    return str(file_path)


def test_parse_log_with_mmap(sample_log_file):
    df = parse_log_with_mmap(sample_log_file)
    assert not df.empty
    assert len(df) == 4
    # 验证多行异常堆栈是否被正确合并
    assert "Service.java:42" in df.iloc[2]["detail"]
    assert "Main.java:10" in df.iloc[2]["detail"]
    assert df.iloc[3]["severity"] == "CRITICAL"


def test_parse_log_with_mmap_empty_file(tmp_path):
    empty_file = tmp_path / "empty.log"
    empty_file.write_text("", encoding="utf-8")
    df = parse_log_with_mmap(str(empty_file))
    assert df.empty


def test_parse_log_parallel_mmap(sample_log_file):
    df = parse_log_parallel_mmap(sample_log_file, max_workers=2)
    assert not df.empty
    assert len(df) >= 3
