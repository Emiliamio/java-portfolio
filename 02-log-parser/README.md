# 日志解析与异常检测工具 (Log Parser)

> 项目二 — Python 数据处理 · 命令行工具 · 安全异常检测

一个用 Python 编写的命令行工具，能够解析 CSV 和纯文本格式的日志文件，提取结构化字段，按 IP 统计失败操作并标记可疑行为。

---

## 功能概览

| 功能 | 说明 |
|------|------|
| **日志解析** | 支持 CSV（结构化）和纯文本（非结构化）两种格式，自动检测 |
| **字段提取** | 从原始日志行中用正则提取时间戳、IP、用户、操作类型、结果、严重程度等 |
| **异常检测** | 按 IP 分组统计失败次数，超过阈值 → 标记可疑 + 风险等级 |
| **控制台报告** | 汇总统计 + 失败操作分布 + 严重程度分布 → 直接打印 |
| **CSV 导出** | 可疑 IP 列表导出为 CSV |
| **Excel 导出** | 多 Sheet 汇总报告（Summary / Suspicious IPs / Fail Distribution） |
| **SQL 导出** | 生成 MySQL INSERT 语句 → 可直接导入项目一的 `log_entry` 表 |

---

## 技术栈

| 技术 | 用途 |
|------|------|
| **Python 3.10+** | 主语言 |
| **Pandas** | 数据读取、清洗、分组统计 |
| **re (正则)** | 从非结构化文本中提取字段 |
| **argparse** | 命令行参数解析 |
| **logging** | 工程级日志输出（替代 print） |
| **openpyxl** | Excel 文件写入 |
| **pytest** | 单元测试 |

---

## 项目结构

```
02-log-parser/
├── log_parser/                 # 核心代码包
│   ├── __init__.py
│   ├── cli.py                  # 命令行入口 + argparse
│   ├── parser.py               # 日志解析引擎（CSV + 文本 + 正则提取）
│   ├── anomaly.py              # 异常检测引擎（暴力破解 / 高频失败）
│   ├── reporter.py             # 报告生成（控制台 / Excel / CSV）
│   └── exporter.py             # SQL 导出器（→ 项目一的 MySQL）
├── sample_logs/                # 示例日志文件
│   ├── access.csv              # CSV 结构化日志（50 条）
│   └── server.log              # 纯文本非结构化日志（48 条）
├── tests/
│   ├── __init__.py
│   └── test_log_parser.py      # 单元测试 + 集成测试
├── output/                     # 输出目录（gitignore）
├── requirements.txt
├── .gitignore
└── README.md
```

---

## 快速开始

### 1. 环境准备

```bash
# 克隆仓库
git clone <your-repo-url>
cd 02-log-parser

# 创建虚拟环境（推荐）
python -m venv venv
source venv/bin/activate        # Linux / macOS
# 或
venv\Scripts\activate           # Windows

# 安装依赖
pip install -r requirements.txt
```

### 2. 运行示例

```bash
# 解析 CSV 日志并检测异常
python -m log_parser.cli -i sample_logs/access.csv -o output/

# 解析非结构化文本日志
python -m log_parser.cli -i sample_logs/server.log -o output/

# 自定义阈值（默认 5）
python -m log_parser.cli -i sample_logs/access.csv -t 10

# 导出 Excel + SQL（完整输出）
python -m log_parser.cli -i sample_logs/access.csv -o output/ --excel --sql

# Debug 模式（显示详细日志）
python -m log_parser.cli -i sample_logs/access.csv -v
```

### 3. 运行测试

```bash
pytest tests/ -v
```

---

## 命令行参数

| 参数 | 简写 | 说明 | 默认值 |
|------|------|------|--------|
| `--input` | `-i` | 输入日志文件路径（必填） | — |
| `--output-dir` | `-o` | 输出目录 | `output/` |
| `--type` | — | 日志类型：`csv` / `text` / `auto` | `auto` |
| `--threshold` | `-t` | 可疑 IP 判定阈值 | `5` |
| `--excel` | — | 导出 Excel 报告 | 否 |
| `--sql` | — | 导出 MySQL INSERT SQL | 否 |
| `--csv-output` | — | 导出可疑 IP CSV | 否 |
| `--verbose` | `-v` | DEBUG 级别日志 | 否 |

---

## 示例输出

运行 `python -m log_parser.cli -i sample_logs/access.csv -o output/ --excel --sql` 后：

```
============================================================
          日志异常检测报告
============================================================
  总日志条数:         50
  失败操作数:         24 (48.00%)
  独立 IP 数:         12
  可疑 IP 数:         3 (阈值=5)

  失败操作分布:
    - LOGIN: 18 次
    - DELETE: 2 次
    - QUERY: 4 次

  严重程度分布:
    - CRITICAL: 3 条
    - ERROR: 8 条
    - WARN: 16 条
    - INFO: 20 条

  可疑 IP 列表:
    ⚠ 172.16.0.88 — 失败 10 次 [HIGH]
    ⚠ 10.0.0.55 — 失败 6 次 [LOW]
    ⚠ 10.0.0.100 — 失败 4 次 [NORMAL]
============================================================
```

---

## 核心知识点（面试用）

### 1. 正则表达式设计

`parser.py` 中的每条正则都有明确的**职责边界**：

- **时间戳**：3 种常见格式（ISO / Apache / syslog），`(?P<ts>...)` 命名组便于提取
- **IP 地址**：严格限制 0-255 范围，不匹配 `999.999.999.999`
- **操作类型**：关键词白名单匹配（`LOGIN`、`DELETE` 等），其余归为 `UNKNOWN`
- **严重程度**：优先级从高到低（CRITICAL → DEBUG），先匹配到先返回

面试可能问：**为什么不用一个大正则一次匹配所有字段？**
→ 每个字段独立匹配，方便容错。某一行没有 IP 不影响其他字段的提取。

### 2. Pandas 数据处理

- `groupby().agg()` 按 IP 聚合，一次完成 count/min/max/nunique
- `pd.to_datetime(errors="coerce")` 容错解析时间戳
- `pd.ExcelWriter` 多 Sheet 写入

面试可能问：**为什么不用纯 Python 循环？**
→ Pandas 的 groupby 是 C 实现的向量化操作，几万条数据比 Python 循环快 10-100 倍。

### 3. 异常检测算法

核心逻辑：`operation_result == "FAIL"` → 按 `ip_address` 分组 → `count >= threshold` → 标记可疑。

**风险等级分三档**：
- `NORMAL`：失败 < 阈值
- `LOW`：阈值 ≤ 失败 < 2×阈值
- `MEDIUM`：2×阈值 ≤ 失败 < 3×阈值
- `HIGH`：失败 ≥ 3×阈值

面试可能问：**阈值为什么是 5？**
→ 这是**经验值**，可以调节。生产环境中应该根据业务日志量设：日均 1 万条日志设 50，日均 100 条设 3。这个工具通过 `--threshold` 参数让使用者灵活配置。

### 4. argparse 设计

- `required=True` 确保必填参数
- `formatter_class=RawDescriptionHelpFormatter` 保留示例格式
- `choices` 限定可选值（`--type`）
- `action="store_true"` 做布尔开关（`--excel`、`--sql`）

### 5. logging vs print

| 维度 | logging | print |
|------|---------|-------|
| 级别控制 | DEBUG/INFO/WARN/ERROR | 无 |
| 时间戳 | 自动 | 需手动 |
| 输出目标 | 文件/控制台/网络 | 只能控制台 |
| 生产环境 | ✅ 标准做法 | ❌ 不规范 |

项目中使用 `logger.info()` 替代 `print()`，通过 `-v` 切换 DEBUG 级别。

---

## 与项目一的联动

这个工具的 **SQL 导出功能** (`--sql`) 可以生成与项目一 `log_entry` 表结构完全匹配的 INSERT 语句：

```bash
# 把解析结果导出为 SQL
python -m log_parser.cli -i sample_logs/access.csv -o output/ --sql

# 导入项目一的 MySQL 数据库
mysql -u root -p log_audit < output/access_data.sql
```

然后项目一的 Spring Boot 后端就能查询到这些日志了——**Python 做离线批量解析 + 异常检测，Java 做在线查询 + 展示**，形成完整的日志分析链路。

---

## License

MIT
