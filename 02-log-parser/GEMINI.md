# GEMINI.md - log-parser

> 通用规范：~/.dsh/AGENTS.md（§0 为冲突仲裁序）
> 环境事实：读 D:\Antigravity\env-snapshot.json，不在本文件写死

## 技术栈
- Python 3.12, pandas (>=2.0.0), openpyxl (>=3.1.0)
- pyarrow (>=15.0.0), duckdb (>=1.0.0), pytest

## 目录职责
- `log_parser/`：核心解析引擎与工具库
  - `parser.py` / `mmap_parser.py`：日志解析与 mmap 零拷贝多核分块
  - `anomaly.py`：滑动窗口异常检测与 FSM 多行堆栈状态机
  - `duckdb_query.py` / `exporter.py`：DuckDB 即席查询与 Parquet/Excel 导出
  - `schema_sniffer.py` / `tail_watcher.py`：Schema 推断与实时流式监听
  - `cli.py` / `reporter.py` / `reporter_html.py`：CLI 入口与报表渲染
- `tests/`：自动化单元与集成测试用例
- `sample_logs/`：基准测试与样本日志数据
- `web/`：可视化报告模板与前端页面

## 命令
- 测试：`python -m pytest -q`
- 运行：`python benchmark.py` 或 `python -m log_parser.cli`

## 禁区
- 凭据只读 ~/.dsh/private.env.md，绝不写进本仓库
- 唯一署名 Emiliamio <mio2110767128@163.com>
- 改完必须 `python -m pytest -q` 全绿
