# Java Full-Stack Development · Flagship Project Portfolio

> A high-performance enterprise ecosystem featuring AI Agent & Hybrid RAG platforms, high-concurrency log audit telemetry, Python FSM log probes, and intelligent Security Copilot Studios.

[![CI/CD Pipeline](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml)
![Tests](https://img.shields.io/badge/Tests-165%20passed%20(100%25)-brightgreen)
![Security](https://img.shields.io/badge/Security-0%20CVEs%20%7C%20A%2B-brightgreen)
![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange)
![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.2-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20pgvector-blue)
![Dual-License](https://img.shields.io/badge/License-MIT%20%7C%20Commercial-blue)

[中文版 (Chinese)](README.md) | [English Documentation](README_EN.md) | [Technical Blog](https://emiliamio.github.io) | [Commercial SLA & License](COMMERCIAL_LICENSE.md)

---

## 🏛️ Flagship Architecture & Portfolio Matrix

| # | Flagship System | Core Technology Stack | Architectural Highlights | Entry / Port |
|---|---|---|---|---|
| 👑 | **AgentForge** · Enterprise AI Platform | **Java 21 (Virtual Threads)** + Spring Boot 3.2 + **PostgreSQL 16 (pgvector)** + **Redis 7** + Vue 3.4 | Pure Java 21 3-Way Hybrid RAG, **Pure Java 8-bit Scalar Quantization (ScalarQuantizationEngine SQ8 75% cut)**, **Distributed Trace Waterfall Gantt Formatter (TraceWaterfallGanttService)**, **Secure Code Sandbox Engine**, **Model Arena Canary Splitter**, **GraphRAG S-P-O Triplet Extraction**, **Tenant Token Quota & RPM Rate Limiter**, **JsqlParser AST Tenant Isolation**, **Kahn DAG Reactive Engine**, **Anthropic MCP Native Client** (46 passing tests) | [GitHub Repository](https://github.com/Emiliamio/agent-forge) / `:80` |
| ① | **AuditVault** · Log Audit & Observability | Spring Boot 3 + MySQL 8 + Redis 7 + **Resilience4j** + **Caffeine L1/L2** + **Kafka** + **ClickHouse** + **K8s Helm** | Datadog-grade SOC Telemetry Studio, **Cryptographic Merkle Tree Root Hash & Inclusion Proof (MerkleAuditTreeService O(log N))**, **3-Sigma Dynamic Baseline Anomaly Detector**, **Cryptographic Tamper-Proof Audit Hash Chain**, **GeoIP Spatial Intelligence Engine**, **Prometheus 4 Golden Signals Telemetry**, **SOAR Automated Remediation**, **PII Data Masking Armor**, **ClickHouse Hourly Pre-Aggregation** (72 passing tests) | `:8080` |
| ② | **LogScope** · Python FSM Anomaly Probe | Python 3.11 + Pandas + Finite State Machine (FSM) + **mmap** + **Parquet** + **DuckDB** | **Multi-Modal Schema Sniffer (SchemaSniffer)**, **Real-Time Streaming Log Watcher Probe (TailWatcher tail -f style incremental stream)**, **Apache Parquet Columnar Storage + DuckDB In-Memory Aggregations**, **Zero-Copy mmap Multi-Core Parser**, Multiline Stacktrace FSM Recovery, **34,317 QPS** (62 passing tests) | CLI |
| ③ | **Nexus AI** · Security Copilot Studio | Spring Boot 3 + **Ollama** / DeepSeek / OpenAI + SSE + **Fast Embedding** | Security Copilot Studio, **Cross-Platform Incident-to-Investigation Pipeline**, **Pure CPU 2ms Dense Vector Embedding Engine**, **Semantic Vector Cache (0 Token 5ms Hit)**, **Industrial Sigma Rule AST Syntax Validator**, **3-Tier Cloud/Local Failover Router**, **PII Data Sanitization Armor** (26 passing tests) | `:8081` |
| ④ | **Sample Order Service** · Microservice Integration | Spring Boot 3 + Spring AOP + JDK HttpClient | **10-Second Non-Invasive Ingestion Sample**: `@AuditLog` non-invasive AOP method interception with asynchronous dispatch to AuditVault (3 passing tests) | `:8085` |
| ⑤ | **@auditvault/sdk** · TypeScript Client SDK | TypeScript + W3C TraceContext + Exponential Backoff | **Node.js / Frontend Type-Safe SDK**: Auto `traceparent` injection, asynchronous non-blocking shipping & retry (2 passing tests) | `sdk/ts` |
| ⑥ | **Architecture Blog** | Hexo + GitHub Pages | 11 In-Depth System Architecture Whitepapers, Interactive FSM Sandbox & 4-Tier Evolution Roadmap | [emiliamio.github.io](https://emiliamio.github.io) |

---

## 🚀 Quick Start in 30 Seconds & Attack Simulation

Prerequisite: **Docker Desktop** (or Docker Engine + Compose).

```bash
# 1. Clone repository
git clone https://github.com/Emiliamio/java-portfolio.git
cd java-portfolio

# 2. Launch all microservices in background
bash demo.sh start

# 3. Run full-fidelity attack simulation & auto-ban verification
bash demo.sh attack-sim
```

### 🌐 Service Endpoints

| Service | URL | Default Credentials / Description |
|---|---|---|
| **AgentForge AI Studio** | `http://localhost:3000` / `http://localhost` | Visual DAG Workflow Builder, Hybrid RAG Knowledge Retrieval & Copilot Portal |
| **AuditVault SOC Telemetry** | `http://localhost:8080` | `admin / admin123` (Admin), `user / user123` (Viewer), Webhook ingestion & SXSSF export |
| **AuditVault Analytics Dashboard** | `http://localhost:8080/dashboard.html` | Real-time events, error distribution & Redis HyperLogLog unique visitor estimation |
| **Nexus AI Copilot Studio** | `http://localhost:8081` | Real-time SSE typewriter analysis, Markdown report generation & rule engine fallback |
| **Sample Order Service** | `http://localhost:8085` | Simulated e-commerce order microservice with `@AuditLog` telemetry |
| **Architecture Blog** | `https://emiliamio.github.io` | Full-stack architectural blueprints, interactive FSM sandbox & production SOPs |

---

## 🛠️ High-Concurrency Distributed Architecture Blueprint

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                 Browser / Enterprise Copilot Portal / CLI Probe              │
└──────────────┬──────────────────┬─────────────────┬─────────────────┬───────┘
               │                  │                 │                 │
               ▼                  ▼                 ▼                 ▼
      ┌─────────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐
      │   AuditVault    │ │   Nexus AI    │ │  AgentForge   │ │ Technical Blog│
      │     :8080       │ │     :8081     │ │  :80 / :3000  │ │ GitHub Pages  │
      └────────┬────────┘ └───────┬───────┘ └───────┬───────┘ └───────────────┘
               │ (TraceId/MDC)    │                 │
               │ POST /api/logs/webhook (Async/Kafka)
               │◀─────────────────┤                 │
               │                  │                 ▼
               ▼                  ▼        ┌──────────────────────────────────┐
      ┌─────────────────┐ ┌──────────────┐ │ PostgreSQL 16 pgvector (HNSW)    │
      │  Redis 7 Cluster│ │ MySQL 8.0    │ │ Redis 7 Semantic Vector Cache    │
      │  - Token Bucket │ │ ClickHouse   │ │ (Cosine Similarity >= 0.95 Hit)  │
      │  - JWT Blacklist│ │ 24.3 OLAP    │ └──────────────────────────────────┘
      │  - HyperLogLog  │ └──────────────┘
      └─────────────────┘
```

---

## 🧪 Comprehensive Automated Test Verification

All modules have 100% test coverage with zero mock illusions:

```bash
# 1. Test AuditVault Backend (72 Tests Passed)
cd 01-log-audit-system && mvn test

# 2. Test LogScope Python FSM Probe (62 Tests Passed)
cd ../02-log-parser && python -m pytest tests/

# 3. Test Nexus AI Copilot (26 Tests Passed)
cd ../03-log-ai-assistant && mvn test

# 4. Test Sample Order & TypeScript SDK (5 Tests Passed)
cd ../04-sample-order-service && mvn test
cd ../sdk/typescript && npm test
# Total: 165 tests 100% green across portfolio (211 tests total across entire ecosystem)
```

---

## 📚 Technical Whitepapers & Engineering Deep Dives

- 🌟 [Pure Java 21 AgentForge Enterprise AI Agent & Hybrid RAG Architecture](https://emiliamio.github.io/2026/08/28/agentforge-pure-java-enterprise-rag-architecture/) —— *AST Tenant Isolation, 3-Way RRF Hybrid Retrieval, Kahn DAG Reactive Engine*
- 🏛️ [From Xinchuang Domestic Compliance to Level-3 Security: Enterprise Private Delivery SOP](https://emiliamio.github.io/2026/08/30/agentforge-xinchuang-and-enterprise-delivery-sop/) —— *Kylin/UOS Compatibility, Bidding Defense, Disaster Recovery*
- 📊 [High-Concurrency Log Telemetry Architecture & SXSSF OOM Prevention](docs/ARCHITECTURE.md) —— *SXSSF Sliding Window, HyperLogLog Bernoulli Estimation, Distributed MDC TraceId*
- 🚀 [Evolution from Monolith to Billion-Scale Kafka + ClickHouse Streaming](https://emiliamio.github.io/2026/08/27/kafka-clickhouse-ollama-enterprise-distributed-architecture/) —— *3-Phase System Scaling Blueprint*

---

## 👤 Author & Digital Ownership

- **Author**: **Zheng Jincheng (Emiliamio)**
- **Email**: `mio2110767128@163.com` / `2110767128@qq.com`
- **GitHub**: [https://github.com/Emiliamio](https://github.com/Emiliamio)
- **Blog**: [https://emiliamio.github.io](https://emiliamio.github.io)

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
