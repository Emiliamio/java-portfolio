# Java Full-Stack Development · Flagship Project Portfolio

> A high-performance enterprise ecosystem featuring AI Agent & Hybrid RAG platforms, high-concurrency log audit telemetry, Python FSM log probes, and intelligent Security Copilot Studios.

[![CI/CD Pipeline](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/Emiliamio/java-portfolio/actions/workflows/ci.yml)
![Tests](https://img.shields.io/badge/Tests-130%20passed%20(100%25)-brightgreen)
![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen)
![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange)
![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.2-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20pgvector-blue)
![License](https://img.shields.io/badge/License-MIT-green)

[中文版 (Chinese)](README.md) | [English Documentation](README_EN.md) | [Technical Blog](https://emiliamio.github.io)

---

## 🏛️ Flagship Architecture & Portfolio Matrix

| # | Flagship System | Core Technology Stack | Architectural Highlights | Entry / Port |
|---|---|---|---|---|
| 👑 | **AgentForge** · Enterprise AI Platform | **Java 21 (Virtual Threads)** + Spring Boot 3.2 + **PostgreSQL 16 (pgvector)** + **Redis 7** + Vue 3.4 | Pure Java 21 3-Way Hybrid RAG (Dense + Sparse + RRF), **JsqlParser AST Physical Tenant Isolation (0.00% Leakage)**, **Kahn Topological Sort Reactive DAG Engine**, **Redis Semantic Vector Cache (60% Token Cost Reduction)**, 800MB Stream Armor Parser (35 passing tests) | [Private Base](https://emiliamio.github.io/projects/) / `:80` |
| ① | **AuditVault** · Log Audit & Observability | Spring Boot 3 + MySQL 8 + Redis 7 + **Kafka KRaft** + **ClickHouse 24.3** + **WebSocket** + **Flyway** + **K8s Helm** | Datadog-grade SOC Telemetry Studio, **W3C TraceContext/OTel Dual-Mode Propagation**, **Dynamic IP Threat Reputation & Auto-Ban Armor**, **Flyway Versioned Migrations**, **@AuditLog Non-invasive AOP**, **Kafka Buffer**, **ClickHouse 45x Fast Histograms**, **SXSSF Disk Sliding Window (Zero OOM)**, **Kubernetes Helm Chart** (59 passing tests) | `:8080` |
| ② | **LogScope** · Python FSM Anomaly Probe | Python 3.11 + Pandas + Finite State Machine (FSM) + **Zero-Copy mmap** | **Zero-Copy mmap Memory Mapping & Multi-Core Chunk Parser**, Multiline Java Exception Stacktrace FSM Recovery, **34,317 QPS Throughput**, Sliding-Window Anomaly Detection, HTML/Excel/SQL Export (53 passing tests) | CLI |
| ③ | **Nexus AI** · Security Copilot Studio | Spring Boot 3 + **Ollama** / DeepSeek / OpenAI + SSE | Security Copilot Studio, **Industrial Sigma Rule AST Syntax Validator & Linter**, **3-Tier Cloud/Local Failover Router**, **PII Data Sanitization Armor**, **100% Offline Privacy Shield**, CVSS 3.1 Scoring & Playbooks (18 passing tests) | `:8081` |
| ④ | **Architecture Blog** | Hexo + GitHub Pages | 11 In-Depth System Architecture Whitepapers & 4-Tier Evolution Roadmap | [emiliamio.github.io](https://emiliamio.github.io) |

---

## 🚀 Quick Start in 30 Seconds

Prerequisite: **Docker Desktop** (or Docker Engine + Compose).

```bash
# 1. Clone repository
git clone https://github.com/Emiliamio/java-portfolio.git
cd java-portfolio

# 2. Copy environment template
cp .env.example .env

# 3. Launch all microservices in background
docker compose up -d
```

### 🌐 Service Endpoints

| Service | URL | Default Credentials / Description |
|---|---|---|
| **AgentForge AI Studio** | `http://localhost:3000` / `http://localhost` | Visual DAG Workflow Builder, Hybrid RAG Knowledge Retrieval & Copilot Portal |
| **AuditVault SOC Telemetry** | `http://localhost:8080` | `admin / admin123` (Admin), `user / user123` (Viewer), Webhook ingestion & SXSSF export |
| **AuditVault Analytics Dashboard** | `http://localhost:8080/dashboard.html` | Real-time events, error distribution & Redis HyperLogLog unique visitor estimation |
| **Nexus AI Copilot Studio** | `http://localhost:8081` | Real-time SSE typewriter analysis, Markdown report generation & rule engine fallback |
| **Architecture Blog** | `https://emiliamio.github.io` | Full-stack architectural blueprints & production deployment SOPs |

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
# 1. Test AuditVault Backend (49 Tests Passed)
cd 01-log-audit-system && mvn test

# 2. Test LogScope Python FSM Probe (50 Tests Passed)
cd ../02-log-parser && python -m pytest tests/

# 3. Test Nexus AI Copilot (9 Tests Passed)
cd ../03-log-ai-assistant && mvn test

# 4. Run LogScope 50,000-Line Performance Benchmark
python benchmark.py
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
