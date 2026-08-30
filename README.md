# Java 全栈开发 · 旗舰项目作品集

> 涵盖企业级 AI Agent & 混合 RAG 中台、高并发分布式日志审计、Python 状态机探针与智能研判 Studio 全链路

[![CI/CD Pipeline](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml)
![Tests](https://img.shields.io/badge/Tests-140%20passed%20(100%25)-brightgreen)
![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen)
![OpenAPI](https://img.shields.io/badge/OpenAPI%203.0-Swagger%20UI-blue)
![Actuator](https://img.shields.io/badge/Prometheus-Actuator%20Ready-orange)

---

## 🏛️ 旗舰项目矩阵与全景总览

| # | 旗舰项目 | 核心技术栈 | 一句话核心亮点 | 入口 / 源码 |
|---|---|---|---|---|
| 👑 | **AgentForge (灵眸智枢)** · 企业级 AI 中台 | **Java 21 (虚拟线程)** + Spring Boot 3.2 + **PostgreSQL 16 (pgvector)** + **Redis 7** + Vue 3.4 | 纯血 Java 21 三路混合 RAG (Dense+Sparse+RRF)、**JsqlParser AST 租户强隔离 (0%越权)**、**Kahn 拓扑 DAG 响应式引擎**、**Redis 向量语义降本 60%**、800MB 装甲流式解析器 (35项单测全通) | [私有商业底座](https://emiliamio.github.io/projects/) / `:80` |
| ① | **AuditVault** · 日志审计中台 | Spring Boot 3 + MySQL 8 + Redis 7 + **Kafka KRaft** + **ClickHouse 24.3** | Datadog/SigNoz 级 SOC 遥测大屏、**Kafka 分布式流式削峰摄取**、**ClickHouse 45x 毫秒级 OLAP 直方图**、SXSSF 磁盘滑动窗口防 OOM 导出 | `:8080` |
| ② | **LogScope** · 日志解析探针 | Python 3.11 + Pandas + 有限状态机 (FSM) | 多行 Java 异常堆栈 FSM 状态机精准还原、时序滚动窗口异常检测、HTML/Excel/SQL 多管道输出 | CLI |
| ③ | **Nexus AI** · 安全研判 Copilot | Spring Boot 3 + **Ollama** / DeepSeek / OpenAI + SSE | Security Copilot 研判工作台、**云端/本地私有化三级热备路由**、**100% 离线隐私盾**、CVSS 3.1 评分与 WAF 剧本生成 | `:8081` |
| ④ | **技术博客** | Hexo + GitHub Pages | 架构深度复盘、避坑指南与全景演进文章 | [emiliamio.github.io](https://emiliamio.github.io) |

---

## 🚀 快速开始

唯一依赖：**Docker Desktop**。

```bash
cp .env.example .env            # 首次（可用默认值，AI 分析可选）
docker compose up -d             # MySQL + Redis + AuditVault + Nexus AI
```

| 服务 | URL | 说明 |
|---|---|---|
| AgentForge — 企业级 AI 工作台 | http://localhost:3000 / http://localhost | 智能体 DAG 画布、知识库三路召回与极简员工 Copilot 门户 |
| AuditVault — 日志检索与分析 | http://localhost:8080 | 含 Webhook 实时采集模拟器、SXSSF 导出与 ClickHouse 45x 聚合 |
| AuditVault — 数据仪表盘 | http://localhost:8080/dashboard.html | 今日事件、异常率与 Redis HyperLogLog 独立基数统计 |
| Nexus AI — 智能研判 Studio | http://localhost:8081 | SSE 实时打字机流式研判、Markdown 导出与跨服务上报 |
| 技术博客与演开展厅 | https://emiliamio.github.io | 全栈项目深度复盘与核心系统演进长文 |

---

## 🛠️ 核心架构演进路径

```
┌──────────────────────────────────────────────────────────┐
│              浏览器 / 极简员工 Copilot 门户 / 终端         │
└────┬─────────┬──────────────┬──────────────┬─────────────┘
     │         │              │              │
     ▼         ▼              ▼              ▼
┌─────────┐ ┌───────┐ ┌──────────────┐ ┌──────────┐
│AuditVault│ │Nexus AI│ │  AgentForge  │ │ 技术博客  │
│ :8080    │ │ :8081 │ │  :80 / :8080 │ │ GitHub   │
└────┬─────┘ └──┬────┘ └──────┬───────┘ │ Pages    │
     │          │             │         └──────────┘
     │   POST /api/logs/webhook (Logback 实时采集)
     │◀─────────┤             │
     │          │             ▼
     ▼          ▼      ┌────────────────────────────┐
┌─────────┐ ┌────────┐ │ pgvector HNSW + Redis 7   │
│  Redis  │ │MySQL 8 │ │ (向量语义降本 60% 缓存)    │
│  :6379  │ │ClickHouse│ └────────────────────────────┘
└─────────┘ └────────┘
```

---

## 📚 详细技术架构与演进文档

- 🌟 [纯血 Java 21 AgentForge 企业级 AI Agent & 混合 RAG 全栈架构实践](https://emiliamio.github.io/2026/08/28/agentforge-pure-java-enterprise-rag-architecture/) —— *AST 租户强隔离、三路混合 RRF 检索、Kahn DAG 响应式调度、Redis 语义降本 60%*
- 🏛️ [从信创国产化到等保三级：AgentForge 政企私有化交付与高可用容灾全流程实战](https://emiliamio.github.io/2026/08/30/agentforge-xinchuang-and-enterprise-delivery-sop/) —— *信创全栈兼容矩阵、招投标答辩20问、一键自动化巡检与秒级灾备SOP*
- 🏛️ [企业级高并发日志架构设计与系统深度剖析](docs/ARCHITECTURE.md) —— *SXSSFWorkbook 内存防爆机制、HyperLogLog 伯努利试验基数估算、JWT 登出黑名单、亿级流量 Kafka+ClickHouse 架构演进*
- 🚀 [从单机吞吐到亿级日志与混合 RAG 架构演进](https://emiliamio.github.io/2026/08/27/kafka-clickhouse-ollama-enterprise-distributed-architecture/) —— *系统三阶段全景演进矩阵*
- 🚢 [云服务器生产部署指南](docs/DEPLOY.md) —— *Docker Compose 一键启动与生产安全最佳实践*

---

## License

MIT
