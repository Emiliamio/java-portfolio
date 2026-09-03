# LogScope CLI — 高性能离线日志解析与异常探针

> 项目二 — Python 数据处理 · 命令行工具 · 有限状态机 (FSM) · 安全异常检测

基于 **Python 3.11 + Pandas + 正则表达式 + 有限状态机 (FSM)** 构建的高性能命令行日志探针，支持单行/多行 Java 异常堆栈拼接还原、实测 **34,000+ QPS** 高吞吐解析与多端报表导出。

---

## 🌟 功能概览

| 功能 | 说明 |
|------|------|
| **多模态 Schema 智能嗅探** | 内置 `SchemaSniffer`，自动前 50 行特征打分识别 Logback、Nginx、JSON Lines 与 Syslog，免配置即席解析 |
| **实时流式日志监听探针** | 内置 `TailWatcher`，支持类 `tail -f` 实时增量变动监听，边解析 FSM 状态机边流式推送至 AuditVault |
| **DuckDB 内存即席分析** | 嵌入式 DuckDB OLAP 引擎，对 Parquet 日志文件执行 0 内存暴涨毫秒级纯 SQL 聚合与特征钻取 |
| **Apache Parquet 列存导出** | 导出压缩比高达 85% 的 Parquet 列式存储文件，无缝对接 DuckDB / ClickHouse 秒级 SQL 过滤 |
| **零拷贝 mmap 内存映射** | 基于 OS 底层 `mmap` 零拷贝与多进程分块并行解析，GB 级大文件内存零暴涨 |
| **FSM 状态机多行解析** | 针对 Java 异常堆栈（`Caused by`、`\tat ...`），通过有限状态机实现单遍扫描精准合并还原 |
| **高吞吐性能** | 实测 **34,317 行/秒 (QPS)** 解析速率，50,000 条日志 1.45 秒完成全量抽取 |
| **双格式自适应** | 支持 CSV（结构化）和纯文本（非结构化）以及 `.gz` 压缩流文件自动解压解析 |
| **滑动窗口异常检测** | 基于 Pandas Rolling Window 模型，按 IP 统计失败操作，超过阈值自动标记暴力破解风险 |
| **多管道导出** | 格式化多 Sheet Excel 报表 / 交互式 HTML 动态仪表盘 / Parquet 列存 / 标准 MySQL INSERT SQL |

---

## 🛠️ 技术栈

- **Python 3.11+**：主开发语言
- **SchemaSniffer**：未知日志格式多模态自动嗅探与置信度推导
- **TailWatcher**：实时流式日志文件监听与增量解析
- **DuckDB & Apache Parquet**：高性能列式压缩存储与嵌入式即席分析引擎
- **mmap & multiprocessing**：零拷贝内存映射与多核并行分块解析
- **Pandas**：时序数据清洗与滑动窗口分析
- **有限状态机 (FSM)**：多行异常堆栈无损合并算法
- **openpyxl**：带样式与公式的 Excel 报表生成
- **pytest**：全套 62 项自动化单元测试

---

## 📁 项目结构

```
02-log-parser/
├── log_parser/                 # 核心代码包
│   ├── __init__.py
│   ├── cli.py                  # 命令行入口 + argparse
│   ├── parser.py               # 日志解析引擎（CSV + 文本 + FSM 状态机）
│   ├── schema_sniffer.py       # 多模态日志格式自动嗅探与智能类型推导
│   ├── tail_watcher.py         # 实时流式日志监听与增量探针
│   ├── mmap_parser.py          # 零拷贝 mmap 与多核分块解析引擎
│   ├── anomaly.py              # 异常检测引擎（滑动窗口 + 暴力破解识别）
│   ├── duckdb_query.py         # DuckDB 嵌入式即席分析与 SQL 探索引擎
│   ├── reporter.py             # 报告生成（控制台 / Excel / HTML）
│   └── exporter.py             # SQL & Parquet 导出器（→ AuditVault 数据库）
├── sample_logs/                # 示例日志文件
├── tests/                      # 单元测试 (62/62 Passed)
│   ├── __init__.py
│   ├── test_duckdb_query.py
│   ├── test_html_reporter.py
│   ├── test_log_parser.py
│   ├── test_mmap_parser.py
│   ├── test_parquet_exporter.py
│   ├── test_schema_sniffer.py
│   └── test_tail_watcher.py
├── benchmark.py                # 50,000 行性能基准测试脚本
├── requirements.txt
└── README.md
```

---

## 🧪 测试与性能压测

```bash
# 1. 运行 58 项单元测试
python -m pytest tests/
```

# 2. 运行性能基准压测
python benchmark.py
```
