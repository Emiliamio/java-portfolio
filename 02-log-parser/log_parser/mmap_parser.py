"""
LogScope — 零拷贝内存映射 (mmap) 与多核并行分块日志解析引擎
Zero-Copy Memory-Mapped IO & Multi-Core Chunk Parser for High-Throughput Log Ingestion
"""

import os
import mmap
import pandas as pd
from typing import List, Dict, Any, Optional
from concurrent.futures import ProcessPoolExecutor
from .parser import parse_line, is_continuation_line


def _collect_records_from_stream(lines_iterable) -> List[Dict[str, Any]]:
    """状态机多行堆栈合并核心收集器"""
    records = []
    current_entry = None

    def commit_current():
        nonlocal current_entry
        if current_entry:
            records.append(current_entry)
            current_entry = None

    for line in lines_iterable:
        line = line.rstrip("\r\n")
        if not line:
            continue

        if is_continuation_line(line):
            if current_entry is not None:
                current_entry["detail"] = (current_entry["detail"] or "") + "\n" + line
            else:
                entry = parse_line(line)
                if entry:
                    current_entry = entry
        else:
            commit_current()
            entry = parse_line(line)
            if entry:
                current_entry = entry

    commit_current()
    return records


def parse_log_with_mmap(filepath: str) -> pd.DataFrame:
    """
    基于 OS 底层 mmap 零拷贝机制逐行扫描并解析日志。
    避免传统 readlines() 将 GB 级文本全量驻留用户态堆内存，大幅降低 GC 压力并提升吞吐。
    """
    if not os.path.exists(filepath):
        raise FileNotFoundError(f"Log file not found: {filepath}")

    file_size = os.path.getsize(filepath)
    if file_size == 0:
        return pd.DataFrame()

    def mmap_line_generator():
        with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
            with mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ) as mm:
                while True:
                    line_bytes = mm.readline()
                    if not line_bytes:
                        break
                    yield line_bytes.decode("utf-8", errors="ignore")

    records = _collect_records_from_stream(mmap_line_generator())
    return pd.DataFrame(records)


def _process_chunk_range(args) -> List[Dict[str, Any]]:
    """子进程分块解析工作函数"""
    filepath, start_byte, end_byte = args

    def chunk_line_generator():
        with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
            with mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ) as mm:
                mm.seek(start_byte)
                # 如果不是从文件开头开始，跳过第一行不完整的残缺字符（由前一个 chunk 消费）
                if start_byte != 0:
                    mm.readline()

                while mm.tell() < end_byte:
                    line_bytes = mm.readline()
                    if not line_bytes:
                        break
                    yield line_bytes.decode("utf-8", errors="ignore")

    return _collect_records_from_stream(chunk_line_generator())


def parse_log_parallel_mmap(filepath: str, max_workers: Optional[int] = None) -> pd.DataFrame:
    """
    多核并行零拷贝分块解析引擎。
    将超大文件按字节偏移量划分给多个 CPU Worker 进程并发流式解析。
    """
    if not os.path.exists(filepath):
        raise FileNotFoundError(f"Log file not found: {filepath}")

    file_size = os.path.getsize(filepath)
    if file_size == 0:
        return pd.DataFrame()

    workers = max_workers or min(os.cpu_count() or 4, 8)
    chunk_size = file_size // workers

    # 如果文件小于 2MB，单进程 mmap 更快，避免多进程 IPC 序列化开销
    if file_size < 2 * 1024 * 1024 or workers <= 1:
        return parse_log_with_mmap(filepath)

    chunks = []
    for i in range(workers):
        start = i * chunk_size
        end = file_size if i == workers - 1 else (i + 1) * chunk_size
        chunks.append((filepath, start, end))

    all_records: List[Dict[str, Any]] = []
    with ProcessPoolExecutor(max_workers=workers) as executor:
        futures = executor.map(_process_chunk_range, chunks)
        for chunk_records in futures:
            all_records.extend(chunk_records)

    df = pd.DataFrame(all_records)
    if not df.empty and "timestamp" in df.columns:
        df = df.sort_values(by="timestamp", ignore_index=True)
    return df
