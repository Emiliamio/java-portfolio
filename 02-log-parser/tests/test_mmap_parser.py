"""
LogScope mmap 零拷贝与多进程并行解析单元测试
"""

import os
import pytest
import pandas as pd
from unittest import mock
from log_parser.mmap_parser import parse_log_with_mmap, parse_log_parallel_mmap

# parse_log_parallel_mmap 在文件小于 2MB 时会短路回退单线程，
# 因此跨块一致性测试必须构造超过该阈值的样本，否则测的是两次单线程结果。
PARALLEL_THRESHOLD_BYTES = 2 * 1024 * 1024


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


def test_cross_chunk_multiline_stacktrace_consistency(tmp_path):
    """
    验证跨分块边界的多行 Java 堆栈不会被割裂，多进程结果与单进程完全一致。

    注意：文件必须大于 2MB，否则 parse_log_parallel_mmap 会短路回退到单线程，
    该测试就退化成「单线程 vs 单线程」，永远通过但毫无验证力。
    """
    lines = []
    # 构造跨越数兆字节的大文件，穿插深层堆栈与异常续行
    total_records = 40000
    for i in range(total_records):
        if i % 250 == 0:
            lines.append(f"2026-09-02 12:{i%60:02d}:00 ERROR 192.168.1.{i%250} admin NullPointerException: fail_{i}\n")
            lines.append(f"\tat com.logaudit.service.DatabaseService.connect(DatabaseService.java:{i})\n")
            lines.append(f"\tat com.logaudit.controller.AuditController.query(AuditController.java:{i+1})\n")
            lines.append(f"Caused by: java.io.IOException: socket closed at {i}\n")
            lines.append(f"\tat java.base/java.net.Socket.connect(Socket.java:100)\n")
        else:
            lines.append(f"2026-09-02 12:{i%60:02d}:00 INFO 192.168.1.{i%250} user{i%10} LOGIN SUCCESS user logged in {i}\n")

    content = "".join(lines)
    test_file = tmp_path / "cross_chunk_test.log"
    test_file.write_text(content, encoding="utf-8")

    file_size = os.path.getsize(str(test_file))
    assert file_size > PARALLEL_THRESHOLD_BYTES, (
        f"样本仅 {file_size} 字节，未超过并行阈值 {PARALLEL_THRESHOLD_BYTES}，"
        "测试会静默回退单线程而失去意义"
    )

    df_single = parse_log_with_mmap(str(test_file))

    # 关键：把单线程入口 patch 成直接失败，一旦发生回退，测试立即炸出来
    with mock.patch(
        "log_parser.mmap_parser.parse_log_with_mmap",
        side_effect=AssertionError("parallel path silently fell back to single-thread"),
    ):
        df_parallel = parse_log_parallel_mmap(str(test_file), max_workers=4)

    assert len(df_single) == total_records
    assert len(df_parallel) == len(df_single)

    # 并行版会按 timestamp 排序（跨块合并后恢复时间序），单线程版保持文件原序，
    # 且同一秒内的多条记录排序不稳定，因此按「内容多重集」比对，而不是按行号逐条比对。
    single_details = sorted(str(x) for x in df_single.detail.fillna(""))
    parallel_details = sorted(str(x) for x in df_parallel.detail.fillna(""))
    assert parallel_details == single_details, "并行与单线程解析出的记录内容必须完全一致"

    # 每条多行堆栈记录都必须保留完整尾部，不能因跨块被割裂
    err_parallel = df_parallel[df_parallel.severity == "ERROR"]
    assert len(err_parallel) == total_records // 250
    assert all("Socket.java:100" in str(d) for d in err_parallel.detail)
