#!/usr/bin/env python3
"""
日志解析与异常检测工具 — 命令行入口

用法:
    # 解析 CSV 日志并检测异常
    python -m log_parser.cli -i sample_logs/access.csv -o output/

    # 解析非结构化文本日志
    python -m log_parser.cli -i sample_logs/server.log --type text -o output/

    # 自定义阈值
    python -m log_parser.cli -i sample_logs/access.csv -t 10

    # 同时导出 Excel 和 SQL
    python -m log_parser.cli -i sample_logs/access.csv -o output/ --excel --sql

    # 详细信息（DEBUG 级别日志）
    python -m log_parser.cli -i sample_logs/access.csv -v

功能:
    1. 解析 CSV / 纯文本日志 → 结构化 DataFrame
    2. 按 IP 分组统计失败操作 → 标记可疑 IP
    3. 输出汇总报告（控制台）
    4. 可选导出：Excel 报告 (.xlsx)、可疑 IP CSV、MySQL INSERT SQL

技术栈: Python + Pandas + re + argparse + logging
"""

import argparse
import logging
import os
import sys

# ── Fix for Python installed at drive root (sys.prefix = "E:" breaks site-packages path) ──
_site_packages = os.path.join(os.path.dirname(sys.executable), "Lib", "site-packages")
if os.path.isdir(_site_packages) and _site_packages not in sys.path:
    sys.path.insert(1, _site_packages)  # insert early so user site-packages takes priority
del _site_packages

# ── 日志配置 ──────────────────────────────────────────────


def setup_logging(verbose: bool = False) -> None:
    """配置 logging，替代 print。"""
    level = logging.DEBUG if verbose else logging.INFO
    fmt = "%(asctime)s [%(levelname)-7s] %(name)s — %(message)s"
    logging.basicConfig(level=level, format=fmt, datefmt="%H:%M:%S")


# ── CLI ───────────────────────────────────────────────────


def build_parser() -> argparse.ArgumentParser:
    """构建命令行参数解析器。"""
    parser = argparse.ArgumentParser(
        prog="log-parser",
        description="日志解析与异常检测工具 — 从日志文件中提取结构化数据并检测异常行为",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  %(prog)s -i logs/server.log                          # 解析文本日志，打印报告
  %(prog)s -i logs/access.csv --type csv -o output/    # 解析 CSV，导出结果
  %(prog)s -i logs/audit.log -t 10 --excel --sql       # 阈值 10，导出 Excel + SQL
        """,
    )

    parser.add_argument(
        "-i", "--input",
        required=True,
        help="输入日志文件路径 (.csv / .log / .txt)",
    )
    parser.add_argument(
        "-o", "--output-dir",
        default="output",
        help="输出目录 (默认: output/)",
    )
    parser.add_argument(
        "--type",
        choices=["csv", "text", "auto"],
        default="auto",
        help="日志文件类型 (默认: auto — 根据扩展名自动检测)",
    )
    parser.add_argument(
        "-t", "--threshold",
        type=int,
        default=5,
        help="可疑 IP 判定阈值 — 同一 IP 失败次数超过此值视为可疑 (默认: 5)",
    )
    parser.add_argument(
        "--excel",
        action="store_true",
        help="导出 Excel 格式的汇总报告",
    )
    parser.add_argument(
        "--sql",
        action="store_true",
        help="导出 MySQL INSERT SQL 文件 (可导入项目一的 log_entry 表)",
    )
    parser.add_argument(
        "--csv-output",
        action="store_true",
        help="导出可疑 IP 列表为 CSV 文件",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="显示 DEBUG 级别日志",
    )

    return parser


def main(argv: list = None) -> int:
    """主入口函数。返回 0 表示成功，非 0 表示错误。"""
    parser = build_parser()
    args = parser.parse_args(argv)
    setup_logging(args.verbose)

    logger = logging.getLogger("cli")

    # ── 延迟导入（方便测试） ──
    from log_parser.parser import parse_file
    from log_parser.anomaly import detect_anomalies
    from log_parser.reporter import (
        print_summary,
        export_to_excel,
        export_suspicious_csv,
    )
    from log_parser.exporter import export_to_sql_file

    # ── 1. 检查输入文件 ──
    if not os.path.isfile(args.input):
        logger.error("Input file not found: %s", args.input)
        return 1

    # 安全校验：文件大小上限（防止解析超大文件拖垮内存）
    MAX_FILE_SIZE = 100 * 1024 * 1024  # 100 MB
    try:
        file_size = os.path.getsize(args.input)
        if file_size > MAX_FILE_SIZE:
            logger.error(
                "Input file too large: %.1f MB (limit %d MB). "
                "请先拆分日志文件再解析。",
                file_size / (1024 * 1024), MAX_FILE_SIZE // (1024 * 1024),
            )
            return 3
    except OSError as e:
        logger.error("Failed to stat input file: %s", e)
        return 3

    # 安全校验：阈值必须为正整数
    if args.threshold < 1:
        logger.error("Threshold must be >= 1, got %d", args.threshold)
        return 4

    # 安全校验：输出目录不允许为文件系统根或用户主目录（防止误写）
    output_abs = os.path.abspath(args.output_dir)
    if output_abs in (os.path.abspath(os.sep), os.path.expanduser("~")):
        logger.error("拒绝将输出写入系统根目录或用户主目录: %s", output_abs)
        return 4

    logger.info("=" * 60)
    logger.info("Log Parser v1.0.0 — 日志解析与异常检测工具")
    logger.info("=" * 60)
    logger.info("Input file:  %s", args.input)
    logger.info("Output dir: %s", args.output_dir)
    logger.info("Threshold:  %d", args.threshold)

    # ── 2. 解析日志 ──
    logger.info("Step 1/3: Parsing log file...")
    try:
        df = parse_file(args.input)
    except Exception as e:
        logger.error("Failed to parse log file: %s", e)
        return 2

    if df.empty:
        logger.warning("Parsed 0 records — nothing to analyze. Exiting.")
        return 0

    logger.info("Parsed %d log records.", len(df))

    # ── 3. 异常检测 ──
    logger.info("Step 2/3: Running anomaly detection...")
    report = detect_anomalies(df, threshold=args.threshold)

    # ── 4. 输出报告 ──
    logger.info("Step 3/3: Generating output...")

    # 控制台打印
    print_summary(report)

    # 创建输出目录
    os.makedirs(args.output_dir, exist_ok=True)
    base_name = os.path.splitext(os.path.basename(args.input))[0]

    # 导出可疑 IP CSV
    suspicious_csv = os.path.join(args.output_dir, f"{base_name}_suspicious.csv")
    export_suspicious_csv(report["suspicious_ips"], suspicious_csv)

    # 导出 Excel（可选）
    if args.excel:
        excel_path = os.path.join(args.output_dir, f"{base_name}_report.xlsx")
        export_to_excel(report, excel_path)

    # 导出 SQL（可选）
    if args.sql:
        sql_path = os.path.join(args.output_dir, f"{base_name}_data.sql")
        export_to_sql_file(df, sql_path)

    # 汇总输出文件
    logger.info("")
    logger.info("Output files:")
    if os.path.isfile(suspicious_csv):
        logger.info("  ✓ Suspicious IPs:  %s", suspicious_csv)
    if args.excel:
        logger.info("  ✓ Excel report:    %s", excel_path)
    if args.sql:
        logger.info("  ✓ SQL dump:        %s", sql_path)

    logger.info("Done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
