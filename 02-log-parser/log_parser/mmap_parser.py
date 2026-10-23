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


def _find_chunk_boundaries(filepath: str, workers: int, file_size: int) -> List[tuple]:
    """
    精准对齐多进程分块边界：确保任何多行 Java 异常堆栈 / 续行不会在块边界处被割裂。
    每个 chunk 的边界严格定位到下一条独立日志记录的首行起始字节。
    """
    if workers <= 1 or file_size == 0:
        return [(filepath, 0, file_size)]

    chunk_size = file_size // workers
    split_points = [0]

    with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
        with mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ) as mm:
            for i in range(1, workers):
                raw_offset = i * chunk_size
                if raw_offset >= file_size:
                    break
                mm.seek(raw_offset)
                # 跳过被切断的不完整行
                mm.readline()
                boundary = mm.tell()
                # 若后续行依然是堆栈续行（如 \tat / Caused by 等），向后扫描直至找到非续行的新记录首行
                while boundary < file_size:
                    line_bytes = mm.readline()
                    if not line_bytes:
                        boundary = file_size
                        break
                    line = line_bytes.decode("utf-8", errors="ignore").rstrip("\r\n")
                    if not line:
                        boundary = mm.tell()
                        continue
                    if not is_continuation_line(line):
                        break
                    boundary = mm.tell()

                if boundary not in split_points and boundary < file_size:
                    split_points.append(boundary)

    split_points.append(file_size)
    chunks = []
    for i in range(len(split_points) - 1):
        if split_points[i] < split_points[i + 1]:
            chunks.append((filepath, split_points[i], split_points[i + 1]))
    return chunks


def _process_chunk_range(args) -> List[Dict[str, Any]]:
    """子进程分块解析工作函数，具备跨块多行续行吸收与前向回溯自愈能力"""
    filepath, start_byte, end_byte = args
    if start_byte >= end_byte:
        return []

    file_size = os.path.getsize(filepath)

    def chunk_line_generator():
        with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
            with mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ) as mm:
                mm.seek(start_byte)

                # 非文件首部时，检测是否落在行中或续行中
                if start_byte != 0:
                    mm.seek(max(0, start_byte - 1))
                    prev_b = mm.read(1)
                    mm.seek(start_byte)
                    if prev_b != b"\n":
                        mm.readline()
                    # 持续跳过属于前一 chunk 的续行
                    while mm.tell() < end_byte:
                        pos = mm.tell()
                        line_bytes = mm.readline()
                        if not line_bytes:
                            break
                        line = line_bytes.decode("utf-8", errors="ignore").rstrip("\r\n")
                        if not line:
                            continue
                        if not is_continuation_line(line):
                            mm.seek(pos)
                            break

                # 消费当前块内容
                while mm.tell() < end_byte:
                    line_bytes = mm.readline()
                    if not line_bytes:
                        break
                    yield line_bytes.decode("utf-8", errors="ignore")

                # 若块末尾记录包含跨越到下一 chunk 的续行（例如堆栈延伸），继续吸收直到遇到非续行
                while mm.tell() < file_size:
                    pos = mm.tell()
                    line_bytes = mm.readline()
                    if not line_bytes:
                        break
                    line = line_bytes.decode("utf-8", errors="ignore").rstrip("\r\n")
                    if is_continuation_line(line):
                        yield line_bytes.decode("utf-8", errors="ignore")
                    else:
                        mm.seek(pos)
                        break

    return _collect_records_from_stream(chunk_line_generator())


def parse_log_parallel_mmap(filepath: str, max_workers: Optional[int] = None) -> pd.DataFrame:
    """
    多核并行零拷贝分块解析引擎。
    按安全记录边界划分字节偏移量给多个 CPU Worker 进程并发流式解析。

    顺序语义：跨块合并后会按 timestamp 重新排序（同一秒内多条记录顺序不保证）；
    parse_log_with_mmap 则保持文件原始行序。需要稳定顺序时请对结果自行二次排序。

    Windows 注意：本方法使用 ProcessPoolExecutor，调用方模块必须有
    `if __name__ == "__main__":` 保护，否则 spawn 模式会重复执行模块顶层代码。
    """
    if not os.path.exists(filepath):
        raise FileNotFoundError(f"Log file not found: {filepath}")

    file_size = os.path.getsize(filepath)
    if file_size == 0:
        return pd.DataFrame()

    workers = max_workers or min(os.cpu_count() or 4, 8)

    # 如果文件小于 2MB，单进程 mmap 更快，避免多进程 IPC 序列化开销
    if file_size < 2 * 1024 * 1024 or workers <= 1:
        return parse_log_with_mmap(filepath)

    chunks = _find_chunk_boundaries(filepath, workers, file_size)

    all_records: List[Dict[str, Any]] = []
    with ProcessPoolExecutor(max_workers=len(chunks)) as executor:
        futures = executor.map(_process_chunk_range, chunks)
        for chunk_records in futures:
            all_records.extend(chunk_records)

    df = pd.DataFrame(all_records)
    if not df.empty and "timestamp" in df.columns:
        df = df.sort_values(by="timestamp", ignore_index=True)
    return df
