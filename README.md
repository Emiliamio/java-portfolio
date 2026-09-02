# Java 全栈开发 · 旗舰项目作品集

> 涵盖企业级 AI Agent & 混合 RAG 中台、高并发分布式日志审计、Python 状态机探针与智能研判 Studio 全链路

[![CI/CD Pipeline](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml)
![Tests](https://img.shields.io/badge/Tests-108%20passed%20(100%25)-brightgreen)
![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen)
![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange)
![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.2-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20pgvector-blue)
![License](https://img.shields.io/badge/License-MIT-green)

[中文版文档 (Chinese)](README.md) | [English Documentation](README_EN.md) | [技术博客](https://emiliamio.github.io)

---

## 🏛️ 旗舰项目矩阵与全景总览

| # | 旗舰项目 | 核心技术栈 | 一句话核心亮点 | 入口 / 源码 |
|---|---|---|---|---|
| 👑 | **AgentForge (灵眸智枢)** · 企业级 AI 中台 | **Java 21 (虚拟线程)** + Spring Boot 3.2 + **PostgreSQL 16 (pgvector)** + **Redis 7** + Vue 3.4 | 纯血 Java 21 三路混合 RAG (Dense+Sparse+RRF)、**JsqlParser AST 租户强隔离 (0%越权)**、**Kahn 拓扑 DAG 响应式引擎**、**Redis 向量语义降本 60%**、800MB 装甲流式解析器 (35项单测全通) | [私有商业底座](https://emiliamio.github.io/projects/) / `:80` |
| ① | **AuditVault** · 日志审计中台 | Spring Boot 3 + MySQL 8 + Redis 7 + **Kafka KRaft** + **ClickHouse 24.3** | Datadog/SigNoz 级 SOC 遥测大屏、**Kafka 分布式流式削峰摄取**、**ClickHouse 45x 毫秒级 OLAP 直方图**、**SXSSF 磁盘滑动窗口防 OOM**、分布式 MDC TraceId 全链路追踪、MyBatis 慢 SQL 自动预警 (49项单测全通) | `:8080` |
| ② | **LogScope** · 日志解析探针 | Python 3.11 + Pandas + 有限状态机 (FSM) | 多行 Java 异常堆栈 FSM 状态机精准还原、**实测 34,000+ QPS 高吞吐**、时序滚动窗口异常检测、HTML/Excel/SQL 多管道输出 (50项测试全通) | CLI |
| ③ | **Nexus AI** · 安全研判 Copilot | Spring Boot 3 + **Ollama** / DeepSeek / OpenAI + SSE | Security Copilot 研判工作台、**云端/本地私有化三级热备路由**、**100% 离线隐私盾**、CVSS 3.1 评分与 WAF 剧本生成 (9项单测全通) | `:8081` |
| ④ | **技术博客** | Hexo + GitHub Pages | 10 篇架构深度复盘、避坑指南与全景演进文章 | [emiliamio.github.io](https://emiliamio.github.io) |

---

## 🚀 30 秒极速启动

唯一依赖：**Docker Desktop**。

```bash
# 1. 克隆代码仓库
git clone https://github.com/Emiliamio/java-portfolio.git
cd java-portfolio

# 2. 复制环境变量配置模版
cp .env.example .env

# 3. 后台一键拉起全微服务集群
docker compose up -d
```

| 服务 | URL | 默认凭据 / 说明 |
|---|---|---|
| **AgentForge — 企业级 AI 工作台** | `http://localhost:3000` / `http://localhost` | 智能体 DAG 画布、知识库三路召回与极简员工 Copilot 门户 |
| **AuditVault — 日志检索与分析** | `http://localhost:8080` | `admin / admin123` (管理员), `user / user123` (只读用户) |
| **AuditVault — 数据仪表盘** | `http://localhost:8080/dashboard.html` | 今日事件、异常率与 Redis HyperLogLog 独立基数统计 |
| **Nexus AI — 智能研判 Studio** | `http://localhost:8081` | SSE 实时打字机流式研判、Markdown 导出与规则引擎降级 |
| **技术博客与演开展厅** | `https://emiliamio.github.io` | 全栈项目深度复盘与核心系统演进长文 |

---

## 🛠️ 高并发分布式架构全景蓝图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                 浏览器 / 极简员工 Copilot 门户 / 探针 CLI 终端               │
└──────────────┬──────────────────┬─────────────────┬─────────────────┬───────┘
               │                  │                 │                 │
               ▼                  ▼                 ▼                 ▼
      ┌─────────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐
      │   AuditVault    │ │   Nexus AI    │ │  AgentForge   │ │  技术博客     │
      │     :8080       │ │     :8081     │ │  :80 / :3000  │ │  GitHub Pages │
      └────────┬────────┘ └───────┬───────┘ └───────┬───────┘ └───────────────┘
               │ (TraceId/MDC)    │                 │
               │ POST /api/logs/webhook (Async/Kafka)
               │◀─────────────────┤                 │
               │                  │                 ▼
               ▼                  ▼        ┌──────────────────────────────────┐
      ┌─────────────────┐ ┌──────────────┐ │ PostgreSQL 16 pgvector (HNSW)    │
      │  Redis 7 集群   │ │ MySQL 8.0    │ │ Redis 7 向量语义缓存             │
      │  - 令牌桶限流   │ │ ClickHouse   │ │ (提问余弦相似度 >= 0.95 秒级命中) │
      │  - JWT 登出黑名单│ │ 24.3 OLAP   │ └──────────────────────────────────┘
      │  - HyperLogLog  │ └──────────────┘
      └─────────────────┘
```

---

## 🧪 自动化测试全量验证

所有子模块均包含完备的自动化测试，杜绝任何假功能与空壳实现：

```bash
# 1. 验证 AuditVault 核心后端 (49 项测试通过)
cd 01-log-audit-system && mvn test

# 2. 验证 LogScope Python 探针 (50 项测试通过)
cd ../02-log-parser && python -m pytest tests/

# 3. 验证 Nexus AI 智能研判 (9 项测试通过)
cd ../03-log-ai-assistant && mvn test

# 4. 运行 LogScope 50,000 行性能基准测试
python benchmark.py
```

---

## 📚 详细技术架构与演进长文

- 🌟 [纯血 Java 21 AgentForge 企业级 AI Agent & 混合 RAG 全栈架构实践](https://emiliamio.github.io/2026/08/28/agentforge-pure-java-enterprise-rag-architecture/) —— *AST 租户强隔离、三路混合 RRF 检索、Kahn DAG 响应式调度、Redis 语义降本 60%*
- 🏛️ [从信创国产化到等保三级：AgentForge 政企私有化交付与高可用容灾全流程实战](https://emiliamio.github.io/2026/08/30/agentforge-xinchuang-and-enterprise-delivery-sop/) —— *信创全栈兼容矩阵、招投标答辩20问、一键自动化巡检与秒级灾备SOP*
- 📊 [企业级高并发日志架构设计与系统深度剖析](docs/ARCHITECTURE.md) —— *SXSSFWorkbook 内存防爆机制、HyperLogLog 伯努利试验基数估算、分布式 MDC TraceId*
- 🚀 [从单机吞吐到亿级日志与混合 RAG 架构演进](https://emiliamio.github.io/2026/08/27/kafka-clickhouse-ollama-enterprise-distributed-architecture/) —— *系统三阶段全景演进矩阵*
- 🚢 [云服务器生产部署指南](docs/DEPLOY.md) —— *Docker Compose 一键启动与生产安全最佳实践*

---

## 👤 作者与数字所有权

- **作者**：**郑锦城 (Emiliamio)**
- **邮箱**：`mio2110767128@163.com` / `2110767128@qq.com`
- **GitHub**：[https://github.com/Emiliamio](https://github.com/Emiliamio)
- **技术博客**：[https://emiliamio.github.io](https://emiliamio.github.io)

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。
