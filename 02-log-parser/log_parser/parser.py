"""
日志解析引擎 — 从 CSV 或非结构化文本中提取结构化日志字段。

支持的输入格式：
1. CSV: timestamp,ip_address,username,operation,operation_result,detail,severity,source_file
2. 非结构化文本: 2025-01-15 08:23:45 192.168.1.10 admin LOGIN SUCCESS "User admin logged in"

核心正则：
- 时间戳: ISO / 常见格式
- IP 地址: IPv4
- 键值对提取 (fallback)
"""

import re
import gzip
import logging
from typing import Optional

import pandas as pd

logger = logging.getLogger(__name__)

# ── 正则模式库 ──────────────────────────────────────────────

# Nginx / Apache Combined Log Format: 192.168.1.1 - admin [15/Jan/2025:08:23:45 +0800] "GET /api/users HTTP/1.1" 200 4523 "-" "Mozilla/5.0"
NGINX_COMBINED_PATTERN = re.compile(
    r'^(?P<ip>\S+)\s+\S+\s+(?P<user>\S+)\s+\[(?P<ts>[^\]]+)\]\s+"(?P<method>\S+)(?:\s+(?P<uri>\S+))?(?:\s+(?P<proto>\S+))?"\s+(?P<status>\d{3}|-)\s+(?P<size>\d+|-)(?:\s+"(?P<referrer>[^"]*)"\s+"(?P<ua>[^"]*)")?'
)

# 日志行中常见的日期时间格式
TIMESTAMP_PATTERNS = [
    # 2025-01-15 08:23:45 或 2025-01-15 08:23:45.123
    re.compile(r"(?P<ts>\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d+)?)"),
    # 15/Jan/2025:08:23:45 +0800 (Apache 格式)
    re.compile(r"(?P<ts>\d{2}/[A-Z][a-z]{2}/\d{4}:\d{2}:\d{2}:\d{2}\s+[+-]\d{4})"),
    # Jan 15 08:23:45 (syslog 格式)
    re.compile(r"(?P<ts>[A-Z][a-z]{2}\s+\d{1,2}\s+\d{2}:\d{2}:\d{2})"),
]

# IPv4 地址
IP_PATTERN = re.compile(
    r"(?P<ip>(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}"
    r"(?:25[0-5]|2[0-4]\d|[01]?\d\d?))"
)

# 常见操作类型关键词
OPERATION_KEYWORDS = [
    "LOGIN", "LOGOUT", "QUERY", "DELETE", "UPDATE", "INSERT",
    "EXPORT", "IMPORT", "DOWNLOAD", "UPLOAD", "CREATE", "MODIFY",
    "READ", "WRITE", "EXECUTE", "CONFIG", "RESTART", "SHUTDOWN",
    "ACCESS", "DENIED", "TIMEOUT", "ERROR",
]

# 操作结果关键词
RESULT_PATTERNS = [
    (re.compile(r"(SUCCESS|成功)"), "SUCCESS"),
    (re.compile(r"(FAIL|FAILED|失败|ERROR)"), "FAIL"),
]

# 严重程度关键词
SEVERITY_PATTERNS = [
    (re.compile(r"\b(CRITICAL|FATAL|EMERGENCY)\b", re.IGNORECASE), "CRITICAL"),
    (re.compile(r"\b(ERROR|错误)\b", re.IGNORECASE), "ERROR"),
    (re.compile(r"\b(WARN|WARNING|警告)\b", re.IGNORECASE), "WARN"),
    (re.compile(r"\b(INFO|INFORMATION|信息)\b", re.IGNORECASE), "INFO"),
    (re.compile(r"\b(DEBUG|TRACE)\b", re.IGNORECASE), "DEBUG"),
]


def extract_timestamp(line: str) -> Optional[str]:
    """从日志行中提取时间戳。"""
    for pattern in TIMESTAMP_PATTERNS:
        match = pattern.search(line)
        if match:
            return match.group("ts")
    return None


def extract_ip(line: str) -> Optional[str]:
    """从日志行中提取 IPv4 地址。"""
    match = IP_PATTERN.search(line)
    return match.group("ip") if match else None


def extract_operation(line: str) -> Optional[str]:
    """从日志行中提取操作类型（关键词匹配）。"""
    line_upper = line.upper()
    for keyword in OPERATION_KEYWORDS:
        if keyword in line_upper:
            return keyword
    return "UNKNOWN"


def extract_result(line: str) -> str:
    """从日志行中提取操作结果。"""
    for pattern, label in RESULT_PATTERNS:
        if pattern.search(line):
            return label
    return "UNKNOWN"


def extract_severity(line: str) -> str:
    """从日志行中提取严重程度。"""
    for pattern, level in SEVERITY_PATTERNS:
        if pattern.search(line):
            return level
    return "INFO"


def extract_username(line: str) -> str:
    """
    从日志行中尝试提取用户名。
    策略：找 IP 后面的下一个单词/标识符，或匹配 "user=" 模式。
    """
    user_pattern = re.compile(
        r"(?:user(name)?[=:]\s*|User\s+)(?P<user>[^\s,;\)]+)", re.IGNORECASE
    )
    match = user_pattern.search(line)
    if match:
        return match.group("user").strip('"\'')
    return "unknown"


def extract_detail(line: str) -> Optional[str]:
    """提取引号内或冒号后的详细信息。"""
    # 尝试双引号内的内容
    quote_match = re.search(r'"([^"]*)"', line)
    if quote_match:
        return quote_match.group(1)
    # 尝试单引号
    quote_match = re.search(r"'([^']*)'", line)
    if quote_match:
        return quote_match.group(1)
    return None


def extract_source_file(line: str) -> Optional[str]:
    """提取文件名（.log / .py / .java 后缀）。"""
    file_match = re.search(r"(?P<file>[^\s]+\.(?:log|py|java|go|js|ts))", line)
    return file_match.group("file") if file_match else None


def parse_line(line: str) -> dict:
    """
    解析单行非结构化日志，返回结构化的字段字典。
    自动适配 Nginx/Apache Combined Log 格式与通用应用日志。

    Args:
        line: 一行原始日志文本

    Returns:
        dict: 包含所有提取字段的字典
    """
    stripped = line.strip()
    # 1. 尝试 Nginx / Apache Combined 日志格式快速解析
    nginx_match = NGINX_COMBINED_PATTERN.match(stripped)
    if nginx_match:
        status = nginx_match.group("status")
        status_num = int(status) if status and status.isdigit() else 200
        severity = "ERROR" if status_num >= 500 else ("WARN" if status_num >= 400 else "INFO")
        op_result = "FAIL" if status_num >= 400 else "SUCCESS"
        method = nginx_match.group("method") or "HTTP"
        uri = nginx_match.group("uri") or "/"
        user = nginx_match.group("user")
        username = user if user and user != "-" else "anonymous"

        detail_parts = [f"{method} {uri} -> {status}"]
        if nginx_match.group("referrer") and nginx_match.group("referrer") != "-":
            detail_parts.append(f"ref: {nginx_match.group('referrer')}")
        if nginx_match.group("ua") and nginx_match.group("ua") != "-":
            detail_parts.append(f"ua: {nginx_match.group('ua')}")

        return {
            "timestamp": nginx_match.group("ts"),
            "ip_address": nginx_match.group("ip"),
            "username": username,
            "operation": method.upper(),
            "operation_result": op_result,
            "detail": " | ".join(detail_parts),
            "severity": severity,
            "source_file": "nginx-access.log",
            "raw_line": stripped,
        }

    # 2. 标准通用日志回退提取
    return {
        "timestamp": extract_timestamp(line),
        "ip_address": extract_ip(line),
        "username": extract_username(line),
        "operation": extract_operation(line),
        "operation_result": extract_result(line),
        "detail": extract_detail(line),
        "severity": extract_severity(line),
        "source_file": extract_source_file(line),
        "raw_line": stripped,
    }


def parse_csv(filepath: str) -> pd.DataFrame:
    """
    解析 CSV 格式的结构化日志文件（支持 .csv 与 .csv.gz 压缩流）。

    CSV 期望列：timestamp, ip_address, username, operation,
                operation_result, detail, severity, source_file

    Args:
        filepath: CSV 文件路径

    Returns:
        pd.DataFrame: 解析后的 DataFrame
    """
    logger.info("Parsing CSV file: %s", filepath)

    # 标准化列名映射
    column_mapping = {
        "timestamp": "timestamp",
        "ip_address": "ip_address",
        "ip": "ip_address",
        "username": "username",
        "user": "username",
        "operation": "operation",
        "action": "operation",
        "operation_result": "operation_result",
        "result": "operation_result",
        "detail": "detail",
        "message": "detail",
        "severity": "severity",
        "level": "severity",
        "source_file": "source_file",
        "file": "source_file",
    }

    compression = "gzip" if filepath.lower().endswith((".gz", ".gzip")) else None
    df = pd.read_csv(filepath, compression=compression)

    # 重命名列为标准名
    df.rename(columns=column_mapping, inplace=True)

    # 确保必要列存在
    required_cols = [
        "timestamp", "ip_address", "username", "operation",
        "operation_result", "severity",
    ]
    for col in required_cols:
        if col not in df.columns:
            df[col] = None

    # 保留出现的其他列
    logger.info("CSV parsed: %d rows, %d columns", len(df), len(df.columns))
    return df


def is_continuation_line(line: str) -> bool:
    """
    判断一行是否为上一条日志的延续行（如 Java 异常堆栈、换行数据等）。
    """
    # 堆栈特征前缀
    if line.startswith(("\tat ", "at ", "Caused by: ", "... ", "Suppressed: ")):
        return True
    # 缩进行且不以时间戳开头
    if (line.startswith("  ") or line.startswith("\t")) and extract_timestamp(line) is None:
        return True
    # 没有时间戳且没有 IP 的普通延续行
    if extract_timestamp(line) is None and extract_ip(line) is None:
        return True
    return False


def open_log_file(filepath: str):
    """透明支持普通文本与 .gz 压缩日志流。"""
    if filepath.lower().endswith((".gz", ".gzip")):
        return gzip.open(filepath, "rt", encoding="utf-8", errors="replace")
    return open(filepath, "r", encoding="utf-8", errors="replace")


def parse_text(filepath: str, merge_multiline: bool = True) -> pd.DataFrame:
    """
    解析纯文本格式的非结构化日志文件，支持多行堆栈（StackTrace）自动聚合与 .gz 流式解压。

    逐行用正则提取字段，遇到异常堆栈行自动追加到上一条记录的 detail 中。

    Args:
        filepath: 文本日志文件路径（支持 .log, .txt, .log.gz 等）
        merge_multiline: 是否开启多行日志/堆栈合并

    Returns:
        pd.DataFrame: 解析后的 DataFrame
    """
    logger.info("Parsing text log file: %s (merge_multiline=%s)", filepath, merge_multiline)

    records = []
    current_record = None
    multiline_buffer = []

    def commit_current():
        nonlocal current_record, multiline_buffer
        if current_record:
            if multiline_buffer:
                stack_trace = "\n".join(multiline_buffer)
                if current_record.get("detail"):
                    current_record["detail"] += "\n" + stack_trace
                else:
                    current_record["detail"] = stack_trace
                current_record["raw_line"] += "\n" + stack_trace
            records.append(current_record)
            current_record = None
            multiline_buffer = []

    with open_log_file(filepath) as f:
        for line_num, raw_line in enumerate(f, 1):
            line = raw_line.rstrip("\r\n")
            if not line.strip():
                continue

            if merge_multiline and current_record is not None and is_continuation_line(line):
                # 延续行：聚合到当前日志记录的 detail 中
                multiline_buffer.append(line.strip())
            else:
                # 遇到新记录（含有时间戳或非延续行）
                commit_current()
                current_record = parse_line(line)
                current_record["line_number"] = line_num

        commit_current()

    df = pd.DataFrame(records)
    logger.info("Text parsed: %d records generated", len(df))

    # 尝试解析时间戳列为 datetime
    if not df.empty and "timestamp" in df.columns and not df["timestamp"].isna().all():
        df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce")

    return df


def parse_file(filepath: str, merge_multiline: bool = True) -> pd.DataFrame:
    """
    自动检测文件类型并解析日志文件（透明支持普通文件与 .gz 压缩归档）。

    根据扩展名选择 CSV 解析器或文本解析器。

    Args:
        filepath: 日志文件路径 (.csv, .log, .csv.gz, .log.gz)
        merge_multiline: 是否开启多行日志/堆栈合并 (默认开启)

    Returns:
        pd.DataFrame: 结构化日志数据
    """
    clean_path = filepath.lower()
    if clean_path.endswith((".gz", ".gzip")):
        clean_path = clean_path.rsplit(".", 1)[0]

    if clean_path.endswith(".csv"):
        return parse_csv(filepath)
    else:
        return parse_text(filepath, merge_multiline=merge_multiline)

