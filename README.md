# Java 全栈开发 · 项目作品集

> 日志审计与分析领域 — 4 个项目，覆盖 Java / Python / AI / DevOps 全链路

---

## 项目总览

| # | 项目 | 技术栈 | 一句话 | 入口 |
|---|---|---|---|---|
| ① | **AuditVault** 日志审计 | Spring Boot + MySQL + Redis | 日志采集、查询、导入、导出，操作审计自闭环 | `:8080` |
| ② | **LogScope** 日志解析器 | Python + Pandas + CLI | CSV/文本日志解析，异常检测，多格式报告 | CLI |
| ③ | **Nexus AI** 智能分析 | Spring Boot + Anthropic API | 粘贴日志 → AI 分析风险 + 处置建议 | `:8081` |
| ④ | **技术博客** | Hexo + GitHub Pages | 项目复盘与技术文章 | [emiliamio.github.io](https://emiliamio.github.io) |

---

## 快速开始

唯一依赖：**Docker Desktop**。

```bash
cp .env.example .env            # 首次（可用默认值，AI 分析可选）
docker compose up -d             # MySQL + Redis + AuditVault + Nexus AI
```

| 服务 | URL |
|---|---|
| AuditVault — 日志查询 | http://localhost:8080 |
| AuditVault — 数据面板 | http://localhost:8080/dashboard.html |
| Nexus AI — 智能分析 | http://localhost:8081 |
| 技术博客 | https://emiliamio.github.io |

**演示账号**（AuditVault 与 Nexus AI 共用）：

| 账号 | 密码 | 权限 |
|---|---|---|
| `admin` | `admin123` | 管理员：查询 + 导入 + 导出 |
| `user` | `user123` | 普通用户：仅查询 |

日志解析器按需运行：

```bash
docker compose --profile tools run --rm log-parser \
  -i sample_logs/access.csv -o /app/output --excel --sql
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
├── 01-log-audit-system/    # Spring Boot · 日志审计查询
├── 02-log-parser/          # Python CLI · 日志解析与异常检测
├── 03-log-ai-assistant/    # Spring Boot + LLM · AI 智能分析
├── 02-tech-blog/           # Hexo · 技术博客
├── docker-compose.yml      # 一键编排全部服务
├── demo.sh                 # 本地演示脚本
├── docs/
│   └── DEPLOY.md           # 云服务器部署指南
└── .env.example
```

---

## 技术栈

```
Java 17 · Spring Boot 3.2 · MyBatis · MySQL 8 · Redis 7
Spring Security · JWT · BCrypt · httpOnly Cookie
Python 3.12 · Pandas · Regex · argparse
Anthropic Messages API / DeepSeek · LLM Prompt Engineering · JSON 容错解析
Docker · Docker Compose · 多阶段构建 · HEALTHCHECK
Hexo · GitHub Pages · GitHub Actions
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
