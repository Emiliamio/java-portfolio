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
import logging
from typing import Optional

import pandas as pd

logger = logging.getLogger(__name__)

# ── 正则模式库 ──────────────────────────────────────────────

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

    Args:
        line: 一行原始日志文本

    Returns:
        dict: 包含所有提取字段的字典
    """
    return {
        "timestamp": extract_timestamp(line),
        "ip_address": extract_ip(line),
        "username": extract_username(line),
        "operation": extract_operation(line),
        "operation_result": extract_result(line),
        "detail": extract_detail(line),
        "severity": extract_severity(line),
        "source_file": extract_source_file(line),
        "raw_line": line.strip(),
    }


def parse_csv(filepath: str) -> pd.DataFrame:
    """
    解析 CSV 格式的结构化日志文件。

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

    df = pd.read_csv(filepath)

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


def parse_text(filepath: str) -> pd.DataFrame:
    """
    解析纯文本格式的非结构化日志文件。

    逐行用正则提取字段，转成结构化 DataFrame。

    Args:
        filepath: 文本日志文件路径

    Returns:
        pd.DataFrame: 解析后的 DataFrame
    """
    logger.info("Parsing text log file: %s", filepath)

    records = []
    with open(filepath, "r", encoding="utf-8", errors="replace") as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            record = parse_line(line)
            record["line_number"] = line_num
            records.append(record)

    df = pd.DataFrame(records)
    logger.info("Text parsed: %d lines → %d records", line_num, len(df))

    # 尝试解析时间戳列为 datetime
    if "timestamp" in df.columns and not df["timestamp"].isna().all():
        df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce")

    return df


def parse_file(filepath: str) -> pd.DataFrame:
    """
    自动检测文件类型并解析日志文件。

    根据扩展名选择 CSV 解析器或文本解析器。

    Args:
        filepath: 日志文件路径 (.csv 或 .log / .txt)

    Returns:
        pd.DataFrame: 结构化日志数据
    """
    if filepath.lower().endswith(".csv"):
        return parse_csv(filepath)
    else:
        return parse_text(filepath)
