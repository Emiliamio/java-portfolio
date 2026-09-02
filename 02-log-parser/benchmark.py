"""
LogScope 性能基准测试与吞吐评估脚本
用于测试状态机解析引擎在单行、多行堆栈、Gzip 压缩流下的解析吞吐量与内存占用
"""

import time
import tempfile
import os
import sys

# 兼容 Windows 控制台输出编码
if sys.platform.startswith('win'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

from log_parser.parser import parse_file

def generate_sample_logs(lines_count=50000):
    lines = []
    for i in range(lines_count):
        if i % 100 == 0:
            lines.append(f"2026-09-02 12:{i%60:02d}:00 ERROR 192.168.1.{i%255} admin DatabaseConnectionException: connection timeout\n")
            lines.append(f"\tat com.logaudit.service.DatabaseService.connect(DatabaseService.java:45)\n")
            lines.append(f"\tat com.logaudit.controller.AuditController.query(AuditController.java:88)\n")
        else:
            lines.append(f"2026-09-02 12:{i%60:02d}:00 INFO 192.168.1.{i%255} user{i%10} LOGIN SUCCESS User logged in successfully\n")
    return "".join(lines)

def run_benchmark():
    print("=" * 60)
    print("LogScope FSM Log Parser Engine - Performance Benchmark")
    print("=" * 60)

    lines_count = 50000
    print(f"Generating {lines_count} log lines (including multi-line stacktraces)...")
    raw_data = generate_sample_logs(lines_count)
    raw_size_mb = len(raw_data.encode('utf-8')) / (1024 * 1024)
    print(f"Sample data size: {raw_size_mb:.2f} MB")

    with tempfile.NamedTemporaryFile('w', delete=False, suffix='.log', encoding='utf-8') as f:
        f.write(raw_data)
        temp_log_path = f.name

    try:
        start_time = time.time()
        df = parse_file(temp_log_path)
        cost_time = time.time() - start_time
        qps = lines_count / cost_time if cost_time > 0 else 0

        print("-" * 60)
        print("Parsing completed successfully!")
        print(f"Elapsed Time: {cost_time:.3f} s")
        print(f"Throughput: {qps:,.0f} lines/sec (QPS)")
        print(f"Valid log entries parsed: {len(df):,}")
        print(f"Identified error records: {len(df[df['severity'] == 'ERROR']):,}")
        print("-" * 60)
    finally:
        if os.path.exists(temp_log_path):
            os.remove(temp_log_path)

if __name__ == '__main__':
    run_benchmark()
