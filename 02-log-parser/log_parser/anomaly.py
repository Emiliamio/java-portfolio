"""
异常检测引擎 — 按 IP 分组统计登录失败次数，标记可疑行为。

核心算法：
1. 筛选 operation_result == "FAIL" 的记录
2. 按 ip_address 分组，统计失败次数
3. 超过阈值的 IP 标记为 "可疑"
4. 输出可疑 IP 列表到文件 + 返回 DataFrame
"""

import logging
from typing import Optional

import pandas as pd

logger = logging.getLogger(__name__)

# 默认异常检测阈值
DEFAULT_THRESHOLD = 5

# 被视为 "敏感" 的操作类型（失败时更值得关注）
SENSITIVE_OPERATIONS = {"LOGIN", "DELETE", "EXECUTE", "CONFIG", "ACCESS"}


def detect_brute_force(
    df: pd.DataFrame,
    threshold: int = DEFAULT_THRESHOLD,
    operation_filter: Optional[list] = None,
) -> pd.DataFrame:
    """
    检测暴力破解 / 高频失败行为。

    筛选出同一 IP 失败次数超过阈值的记录。

    Args:
        df: 解析后的日志 DataFrame
        threshold: 失败次数阈值，超过此值的 IP 视为可疑
        operation_filter: 只统计这些操作类型的失败（默认只统计 LOGIN）

    Returns:
        pd.DataFrame: 可疑 IP 列表，含失败次数、首次/末次时间
    """
    if operation_filter is None:
        operation_filter = ["LOGIN"]

    if df.empty:
        logger.info("Input DataFrame is empty — nothing to analyze.")
        return pd.DataFrame()

    # 只关注失败记录
    fail_df = df[df["operation_result"] == "FAIL"].copy()

    if fail_df.empty:
        logger.info("No failed operations found — nothing to analyze.")
        return pd.DataFrame()

    # 可选：只统计特定操作类型
    if "operation" in fail_df.columns:
        fail_df = fail_df[
            fail_df["operation"].str.upper().isin(
                [op.upper() for op in operation_filter]
            )
        ]

    if fail_df.empty:
        logger.info(
            "No failed operations matching filter %s found.", operation_filter
        )
        return pd.DataFrame()

    # 按 IP 分组统计
    ip_stats = (
        fail_df.groupby("ip_address")
        .agg(
            failed_attempts=("operation_result", "count"),
            first_seen=("timestamp", "min"),
            last_seen=("timestamp", "max"),
            unique_users=("username", "nunique"),
            operations_attempted=("operation", lambda x: list(x.unique())),
        )
        .reset_index()
    )

    # 排序（失败次数多的在前）
    ip_stats.sort_values("failed_attempts", ascending=False, inplace=True)

    # 标记可疑
    ip_stats["is_suspicious"] = ip_stats["failed_attempts"] >= threshold
    ip_stats["risk_level"] = ip_stats["failed_attempts"].apply(
        lambda n: (
            "HIGH"
            if n >= threshold * 3
            else ("MEDIUM" if n >= threshold * 2 else ("LOW" if n >= threshold else "NORMAL"))
        )
    )

    suspicious = ip_stats[ip_stats["is_suspicious"]]

    logger.info(
        "Brute-force analysis complete: %d IPs analyzed, %d suspicious (threshold=%d)",
        len(ip_stats),
        len(suspicious),
        threshold,
    )

    return suspicious


def detect_anomalies(
    df: pd.DataFrame,
    threshold: int = DEFAULT_THRESHOLD,
) -> dict:
    """
    综合异常检测：运行所有检测规则，返回汇总结果。

    Args:
        df: 解析后的日志 DataFrame
        threshold: 失败次数阈值

    Returns:
        dict: {
            "total_records": int,
            "failed_records": int,
            "unique_ips": int,
            "suspicious_ips": pd.DataFrame,
            "summary": str,
        }
    """
    logger.info("Running comprehensive anomaly detection...")

    total = len(df)

    # 失败记录
    failed = df[df["operation_result"] == "FAIL"] if "operation_result" in df.columns else pd.DataFrame()
    fail_count = len(failed)

    # IP 数量
    unique_ips = df["ip_address"].nunique() if "ip_address" in df.columns else 0

    # 暴力破解检测
    suspicious = detect_brute_force(df, threshold=threshold)

    # 失败操作类型分布
    if not failed.empty and "operation" in failed.columns:
        fail_ops = failed["operation"].value_counts().to_dict()
    else:
        fail_ops = {}

    # 严重程度分布
    if "severity" in df.columns:
        severity_dist = df["severity"].value_counts().to_dict()
    else:
        severity_dist = {}

    summary_lines = [
        "=" * 60,
        "          日志异常检测报告",
        "=" * 60,
        f"  总日志条数:         {total}",
        f"  失败操作数:         {fail_count} ({fail_count/total*100:.2f}%)"
        if total > 0
        else "  失败操作数:         0",
        f"  独立 IP 数:         {unique_ips}",
        f"  可疑 IP 数:         {len(suspicious)} (阈值={threshold})",
        "",
    ]

    if fail_ops:
        summary_lines.append("  失败操作分布:")
        for op, cnt in sorted(fail_ops.items(), key=lambda x: -x[1]):
            summary_lines.append(f"    - {op}: {cnt} 次")

    if severity_dist:
        summary_lines.append("")
        summary_lines.append("  严重程度分布:")
        for level in ["CRITICAL", "ERROR", "WARN", "INFO", "DEBUG"]:
            if level in severity_dist:
                summary_lines.append(f"    - {level}: {severity_dist[level]} 条")

    if not suspicious.empty:
        summary_lines.append("")
        summary_lines.append("  可疑 IP 列表:")
        for _, row in suspicious.iterrows():
            summary_lines.append(
                f"    [!] {row['ip_address']} — "
                f"失败 {int(row['failed_attempts'])} 次 "
                f"[{row['risk_level']}]"
            )

    summary_lines.append("")
    summary_lines.append("=" * 60)

    return {
        "total_records": total,
        "failed_records": fail_count,
        "unique_ips": unique_ips,
        "suspicious_ips": suspicious,
        "fail_operations": fail_ops,
        "severity_distribution": severity_dist,
        "summary": "\n".join(summary_lines),
    }
