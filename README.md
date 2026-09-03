# Java 全栈开发 · 旗舰项目作品集

> 涵盖企业级 AI Agent & 混合 RAG 中台、高并发分布式日志审计、Python 状态机探针与智能研判 Studio 全链路

[![CI/CD Pipeline](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml)
![Tests](https://img.shields.io/badge/Tests-165%20passed%20(100%25)-brightgreen)
![Security](https://img.shields.io/badge/Security-0%20CVEs%20%7C%20A%2B-brightgreen)
![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange)
![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.2-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20pgvector-blue)
![Dual-License](https://img.shields.io/badge/License-MIT%20%7C%20Commercial-blue)

[中文版文档 (Chinese)](README.md) | [English Documentation](README_EN.md) | [技术博客](https://emiliamio.github.io) | [商业授权 & SLA](COMMERCIAL_LICENSE.md)

---

## 🏛️ 旗舰项目矩阵与全景总览

| # | 旗舰项目 | 核心技术栈 | 一句话核心亮点 | 入口 / 源码 |
|---|---|---|---|---|
| 👑 | **AgentForge (灵眸智枢)** · 企业级 AI 中台 | **Java 21 (虚拟线程)** + Spring Boot 3.2 + **PostgreSQL 16 (pgvector)** + **Redis 7** + Vue 3.4 | 纯血 Java 21 三路混合 RAG、**纯 Java 8-bit 标量量化压缩 (ScalarQuantizationEngine SQ8 内存降低 75%)**、**分布式 Trace 拓扑甘特瀑布流 (TraceWaterfallGanttService)**、**受限安全代码沙箱与看门狗**、**多模型金丝雀分流竞技场**、**GraphRAG 实体两跳扩散**、**多租户 Token 预算限流熔断**、**JsqlParser AST 租户强隔离**、**Kahn DAG 响应式引擎**、**Anthropic MCP 原生客户端** (46项单测全通) | [GitHub 官方仓库](https://github.com/Emiliamio/agent-forge) / `:80` |
| ① | **AuditVault** · 日志审计中台 | Spring Boot 3 + MySQL 8 + Redis 7 + **Resilience4j 熔断** + **Caffeine L1/L2** + **Kafka** + **ClickHouse** + **K8s Helm** | Datadog 级 SOC 遥测大屏、**密码学 Merkle Tree 树根哈希防伪存证 (MerkleAuditTreeService O(log N) 包含证明)**、**3-Sigma 时序动态基线异动检测**、**区块链式防篡改哈希审计链**、**IP 地理空间情报富化**、**Prometheus 黄金四指标深度度量**、**SOAR 闭环自愈响应**、**金融级 PII 实时脱敏装甲**、**ClickHouse 小时级预聚合直方图** (72项单测全通) | `:8080` |
| ② | **LogScope** · 日志解析探针 | Python 3.11 + Pandas + 有限状态机 (FSM) + **mmap 零拷贝** + **Parquet** + **DuckDB** | **多模态 Schema 智能嗅探 (SchemaSniffer)**、**实时流式日志监听探针 (TailWatcher 类 tail -f 增量监听)**、**Apache Parquet 列存 + DuckDB 内存即席分析**、**零拷贝 mmap 多核并行解析**、多行 Java 堆栈 FSM 状态机还原、**实测 34,317 QPS** (62项测试全通) | CLI |
| ③ | **Nexus AI** · 安全研判 Copilot | Spring Boot 3 + **Ollama** / DeepSeek / OpenAI + SSE + **边缘极速向量化** | Security Copilot 研判工作台、**双中台跨系统协同工单流水线**、**纯 CPU 2ms 边缘密集特征向量化**、**语义特征向量缓存 (0 Token 5ms 命中)**、**工业级 Sigma 规则 AST 校验器**、**三级热备路由**、**PII 脱敏装甲** (26项单测全通) | `:8081` |
| ④ | **Sample Order Service** · 微服务接入示例 | Spring Boot 3 + Spring AOP + JDK 原生 HttpClient | **10 秒无侵入接入示范工程**：通过 `@AuditLog` 注解自动抓取方法耗时与 TraceId 并异步回传 AuditVault (3项单测全通) | `:8085` |
| ⑤ | **@auditvault/sdk** · TypeScript 客户端 SDK | TypeScript + W3C TraceContext + 指数退避重试 | **Node.js / 前端全类型安全 SDK**：自动注入 `traceparent` 标头与异步上报 (2项单测全通) | `sdk/ts` |
| ⑥ | **技术博客** | Hexo + GitHub Pages | 11 篇架构深度复盘、避坑指南、交互式 FSM 演练沙盒与四大阶梯演进路线图 | [emiliamio.github.io](https://emiliamio.github.io) |

---

## 🚀 30 秒极速启动与一键攻防演练

唯一依赖：**Docker Desktop**。

```bash
# 1. 克隆代码仓库
git clone https://github.com/Emiliamio/java-portfolio.git
cd java-portfolio

# 2. 一键启动全套微服务环境
bash demo.sh start

# 3. 运行一键全仿真攻防演练与 IP 威胁自动熔断 (Auto-Ban)
bash demo.sh attack-sim
```

| 服务 | URL | 默认凭据 / 说明 |
|---|---|---|
| **AgentForge — 企业级 AI 工作台** | `http://localhost:3000` / `http://localhost` | 智能体 DAG 画布、知识库三路召回与极简员工 Copilot 门户 |
| **AuditVault — 日志检索与分析** | `http://localhost:8080` | `admin / admin123` (管理员), `user / user123` (只读用户) |
| **AuditVault — 数据仪表盘** | `http://localhost:8080/dashboard.html` | 今日事件、异常率与 Redis HyperLogLog 独立基数统计 |
| **Nexus AI — 智能研判 Studio** | `http://localhost:8081` | SSE 实时打字机流式研判、Markdown 导出与规则引擎降级 |
| **Sample Order Service — 示例服务** | `http://localhost:8085` | 模拟电商微服务下单与 `@AuditLog` 自动上报 |
| **技术博客与演开展厅** | `https://emiliamio.github.io` | 全栈项目深度复盘、交互式沙盒与核心系统演进长文 |

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
# 1. 验证 AuditVault 核心后端 (72 项测试通过)
cd 01-log-audit-system && mvn test

# 2. 验证 LogScope Python 探针 (62 项测试通过)
cd ../02-log-parser && python -m pytest tests/

# 3. 验证 Nexus AI 智能研判 (26 项测试通过)
cd ../03-log-ai-assistant && mvn test

# 4. 验证 Sample Order 示例服务与 TypeScript SDK (5 项测试通过)
cd ../04-sample-order-service && mvn test
cd ../sdk/typescript && npm test
# 作品集模块总计 165 项自动化测试 100% 绿灯通过 (全生态包含 AgentForge 共 211 项通过)
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
