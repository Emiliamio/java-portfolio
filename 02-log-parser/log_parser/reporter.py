"""
报告生成器 — 输出汇总统计数据，支持控制台打印和 Excel 导出。
"""

import logging
import os
import sys
from datetime import datetime

import pandas as pd

logger = logging.getLogger(__name__)


def print_summary(report: dict) -> None:
    """在控制台打印汇总报告（处理 Windows GBK 编码问题）。"""
    text = report["summary"]
    try:
        print(text)
    except UnicodeEncodeError:
        # Windows GBK 编码不支持某些 Unicode 符号，回退为 ASCII 安全输出
        print(text.encode(sys.stdout.encoding or "utf-8", errors="replace").decode(
            sys.stdout.encoding or "utf-8", errors="replace"
        ))


def export_to_excel(report: dict, output_path: str) -> None:
    """
    将检测结果导出为 Excel 文件。

    Excel 包含三个 Sheet：
    - Summary: 汇总统计
    - Suspicious IPs: 可疑 IP 详情
    - Fail Distribution: 失败操作分布

    Args:
        report: detect_anomalies() 返回的结果字典
        output_path: 输出 Excel 文件路径 (.xlsx)
    """
    logger.info("Exporting report to Excel: %s", output_path)

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)

    with pd.ExcelWriter(output_path, engine="openpyxl") as writer:
        # Sheet 1: Summary
        summary_data = {
            "指标": [
                "总日志条数", "失败操作数", "独立 IP 数",
                "可疑 IP 数", "导出时间",
            ],
            "值": [
                report["total_records"],
                report["failed_records"],
                report["unique_ips"],
                len(report["suspicious_ips"]),
                datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            ],
        }
        pd.DataFrame(summary_data).to_excel(
            writer, sheet_name="Summary", index=False
        )

        # Sheet 2: Suspicious IPs
        if not report["suspicious_ips"].empty:
            report["suspicious_ips"].to_excel(
                writer, sheet_name="Suspicious_IPs", index=False
            )
        else:
            pd.DataFrame({"信息": ["未发现可疑 IP"]}).to_excel(
                writer, sheet_name="Suspicious_IPs", index=False
            )

        # Sheet 3: Fail Operations
        if report.get("fail_operations"):
            fail_df = pd.DataFrame(
                list(report["fail_operations"].items()),
                columns=["操作类型", "失败次数"],
            )
            fail_df.sort_values("失败次数", ascending=False, inplace=True)
            fail_df.to_excel(writer, sheet_name="Fail_Distribution", index=False)
        else:
            pd.DataFrame({"信息": ["无失败操作"]}).to_excel(
                writer, sheet_name="Fail_Distribution", index=False
            )

        # Sheet 4: Severity Distribution
        if report.get("severity_distribution"):
            sev_df = pd.DataFrame(
                list(report["severity_distribution"].items()),
                columns=["严重程度", "数量"],
            )
            sev_df.to_excel(writer, sheet_name="Severity", index=False)

    logger.info("Excel report written successfully.")


def export_suspicious_csv(suspicious_df: pd.DataFrame, output_path: str) -> None:
    """
    将可疑 IP 列表导出为 CSV 文件。

    Args:
        suspicious_df: detect_brute_force() 返回的可疑 IP DataFrame
        output_path: 输出 CSV 文件路径
    """
    if suspicious_df.empty:
        logger.info("No suspicious IPs to export.")
        return

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    suspicious_df.to_csv(output_path, index=False, encoding="utf-8-sig")
    logger.info(
        "Exported %d suspicious IPs to %s", len(suspicious_df), output_path
    )
