# Java 全栈开发 · 项目作品集

> 日志审计与分析领域 — 4 个项目，覆盖 Java / Python / AI / DevOps / CI/CD 全链路

[![CI/CD Pipeline](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml)
![Tests](https://img.shields.io/badge/Tests-96%20passed-brightgreen)
![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen)
![OpenAPI](https://img.shields.io/badge/OpenAPI%203.0-Swagger%20UI-blue)

---

## 项目总览

| # | 项目 | 技术栈 | 一句话 | 入口 |
|---|---|---|---|---|
| ① | **AuditVault** 日志审计 | Spring Boot + MySQL + Redis | 实时 Webhook 采集、查询、导出，操作审计自闭环 | `:8080` |
| ② | **LogScope** 日志解析器 | Python + Pandas + CLI | CSV/文本日志解析，异常检测，HTML/Excel 可视化报告 | CLI |
| ③ | **Nexus AI** 智能分析 | Spring Boot + Anthropic / DeepSeek | 粘贴日志 → AI 分析风险 + 处置建议 (SSE 打字机) + 跨服务告警 | `:8081` |
| ④ | **技术博客** | Hexo + GitHub Pages | 项目复盘与技术文章 | [emiliamio.github.io](https://emiliamio.github.io) |

---

## 快速开始

唯一依赖：**Docker Desktop**。

```bash
cp .env.example .env            # 首次（可用默认值，AI 分析可选）
docker compose up -d             # MySQL + Redis + AuditVault + Nexus AI
```

| 服务 | URL | 说明 |
|---|---|---|
| AuditVault — 日志检索 | http://localhost:8080 | 含 Webhook 实时采集模拟器与导出 |
| AuditVault — 数据面板 | http://localhost:8080/dashboard.html | 今日事件、异常率与独立 IP 统计 |
| AuditVault — API 文档 | http://localhost:8080/swagger-ui.html | SpringDoc OpenAPI 3.0 交互接口中心 |
| Nexus AI — 智能分析 | http://localhost:8081 | SSE 流式分析、Markdown 导出与跨服务上报 |
| Nexus AI — API 文档 | http://localhost:8081/swagger-ui.html | SpringDoc OpenAPI 3.0 交互接口中心 |
| 技术博客 | https://emiliamio.github.io | 项目复盘与架构技术文章 |

**演示账号**（AuditVault 与 Nexus AI 共用）：

| 账号 | 密码 | 权限 |
|---|---|---|
| `admin` | `admin123` | 管理员：查询 + 导入 + 导出 |
| `user` | `user123` | 普通用户：仅查询 |

日志解析器按需运行（一键导出 HTML 可视化报告、Excel 与 SQL）：

```bash
docker compose --profile tools run --rm log-parser \
  -i sample_logs/access.csv -o /app/output --html --excel --sql
```

---

## 架构

```
┌──────────────────────────────────────────┐
│              浏览器 / 终端                 │
└────┬─────────┬──────────┬────────────────┘
     │         │          │
     ▼         ▼          ▼
┌─────────┐ ┌───────┐ ┌──────────┐
│AuditVault│ │Nexus AI│ │ 技术博客  │
│ :8080    │ │ :8081 │ │ GitHub   │
└────┬─────┘ └──┬────┘ │ Pages    │
     │          │      └──────────┘
     │   POST /api/logs/webhook (Logback 实时采集)
     │◀─────────┤
     ▼          ▼
┌─────────┐ ┌──────────────────┐
│  Redis  │ │     MySQL 8      │
│  :6379  │ │  log_entry       │
└─────────┘ │  audit_log       │
            │  ai_analysis     │
            └────────▲─────────┘
                     │
        ┌────────────┘
        │ LogScope CLI (Python)
        │ 离线解析 → SQL 批量导入
        └────────────
```

---

## 项目结构

```
├── .github/workflows/      # 统一 CI/CD 流水线 (Matrix 多语言测试)
├── 01-log-audit-system/    # Spring Boot · 日志审计查询 + Webhook 采集
├── 02-log-parser/          # Python CLI · 日志解析与异常检测
├── 03-log-ai-assistant/    # Spring Boot + LLM · AI 智能分析 (SSE 流式)
├── 02-tech-blog/           # Hexo · 技术博客
├── docker-compose.yml      # 一键编排全部服务
├── demo.sh                 # 本地演示脚本
├── docs/
│   ├── DEPLOY.md           # 云服务器部署指南
│   └── LOGBACK_INTEGRATION.md # Logback Webhook 采集接入指南
└── .env.example
```

---

## 技术栈

```
Java 17 · Spring Boot 3.2 · MyBatis · MySQL 8 · Redis 7
Spring Security · JWT · BCrypt · httpOnly Cookie · Logback Webhook
Python 3.12 · Pandas · Regex · argparse
Anthropic Messages API / DeepSeek · LLM Prompt Engineering · JSON 容错解析 · SSE Stream
Docker · Docker Compose · 多阶段构建 · HEALTHCHECK
Hexo · GitHub Pages · GitHub Actions CI/CD Pipeline
```


---

## 部署

详细部署指南见 [docs/DEPLOY.md](docs/DEPLOY.md)。

常用命令：

```bash
docker compose ps            # 查看状态
docker compose logs -f       # 查看日志
docker compose down -v       # 停止并重置数据库
```

---

## License

MIT
