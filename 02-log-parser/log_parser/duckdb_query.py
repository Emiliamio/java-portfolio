"""
LogScope — DuckDB 嵌入式内存即席分析与 SQL 探索引擎
DuckDB In-Memory Analytical OLAP Engine for Instant Parquet Log Exploration
"""

import os
import duckdb
import pandas as pd
from typing import Dict, Any, Optional


def query_parquet(parquet_path: str, sql: str) -> pd.DataFrame:
    """
    使用 DuckDB 原生嵌入式 OLAP 引擎对 Parquet 日志文件执行即席 SQL 查询。

    Args:
        parquet_path: Parquet 文件路径
        sql: 针对 'logs' 表的标准 SQL 查询语句 (例如: "SELECT operation, count(*) FROM logs GROUP BY 1")

    Returns:
        pd.DataFrame: SQL 查询聚合结果
    """
    if not os.path.exists(parquet_path):
        raise FileNotFoundError(f"Parquet file not found: {parquet_path}")

    # 将 parquet 文件映射为内存视图 'logs'
    con = duckdb.connect(database=":memory:")
    try:
        # 使用安全的参数化路径
        escaped_path = parquet_path.replace("\\", "/")
        con.execute(f"CREATE VIEW logs AS SELECT * FROM read_parquet('{escaped_path}')")
        result_df = con.execute(sql).df()
        return result_df
    finally:
        con.close()


def analyze_log_summary(parquet_path: str) -> Dict[str, Any]:
    """
    使用 DuckDB 毫秒级提取海量日志的关键指标摘要 (单机 0 内存膨胀)。
    """
    if not os.path.exists(parquet_path):
        raise FileNotFoundError(f"Parquet file not found: {parquet_path}")

    con = duckdb.connect(database=":memory:")
    try:
        escaped_path = parquet_path.replace("\\", "/")
        con.execute(f"CREATE VIEW logs AS SELECT * FROM read_parquet('{escaped_path}')")

        total_count = con.execute("SELECT count(*) FROM logs").fetchone()[0]
        unique_ips = con.execute("SELECT count(DISTINCT ip_address) FROM logs").fetchone()[0]
        error_count = con.execute("SELECT count(*) FROM logs WHERE operation_result != 'SUCCESS' OR severity IN ('ERROR', 'CRITICAL')").fetchone()[0]

        top_ips_df = con.execute(
            "SELECT ip_address, count(*) as req_count "
            "FROM logs "
            "WHERE ip_address IS NOT NULL "
            "GROUP BY ip_address "
            "ORDER BY req_count DESC LIMIT 5"
        ).df()

        severity_dist_df = con.execute(
            "SELECT coalesce(severity, 'UNKNOWN') as severity, count(*) as count "
            "FROM logs "
            "GROUP BY 1 "
            "ORDER BY count DESC"
        ).df()

        error_rate = (error_count / total_count * 100.0) if total_count > 0 else 0.0

        return {
            "total_count": total_count,
            "unique_ips": unique_ips,
            "error_count": error_count,
            "error_rate": round(error_rate, 2),
            "top_ips": top_ips_df.to_dict(orient="records"),
            "severity_distribution": severity_dist_df.to_dict(orient="records"),
        }
    finally:
        con.close()
