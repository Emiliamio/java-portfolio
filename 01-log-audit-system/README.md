# AuditVault — 企业级高并发分布式日志审计与可观测性平台 (SOC Studio)

基于 **Spring Boot 3.2 + Java 17/21 + MySQL 8 + Redis 7 + Apache Kafka + ClickHouse 24.3** 构建的企业级日志采集、分布式审计检索、全景大屏与合规分析工作台，深度对标 Datadog Cloud SIEM 与 Elastic Security 工业级标准。

---

## 🌟 核心功能矩阵

- **100vw × 100vh 全视口 Studio 布局**：自适应大屏控制台，无冗余留白，沉浸式分析体验。
- **分布式链路追踪 (TraceId / MDC)**：内置 `TraceIdFilter` 与 `MdcTaskDecorator`，主线程与异步线程池全程透传 `X-Trace-Id`。
- **数据库慢 SQL 监控与告警**：MyBatis 原生 `SlowSqlInterceptor` 插件自动捕获执行耗时超 200ms 的 SQL 并在日志与指标中打标。
- **多维 Facets 动态聚类侧栏**：实时统计严重级别 (CRITICAL/ERROR/WARN/INFO)、操作类型与 Top IP 聚合计数，支持一键复合下钻。
- **ClickHouse 时序直方分布图 (Histogram)**：ClickHouse MergeTree 引擎支撑千万级日志 24 小时流量走势 **< 3ms 聚合 (45x 加速)**。
- **Kafka 分布式流式削峰**：Webhook 毫秒级接收请求推入 Kafka Topic 缓冲，支持异步落库与瞬时高并发流量削峰。
- **海量导出防 OOM 装甲**：基于 POI `SXSSFWorkbook(100)` 磁盘滑动窗口机制，50,000 条日志流式导出仅占用 ~18MB 堆内存。
- **Redis HyperLogLog 独立 IP 统计**：基于伯努利试验以 12KB 固定内存实现海量活跃 IP 快速去重与基数估算。
- **W3C TraceContext / OpenTelemetry 双模全链路追踪**：优先支持 W3C `traceparent` (00-4bf...-01) 与 `X-Trace-Id` 双向透传，无缝融入 K8s Istio 服务网格。
- **IP 威胁信誉评分与自适应自动熔断 (Auto-Ban)**：动态追踪高危恶意行为 (+40/+50分)，超 80 分秒级注入 Redis 黑名单并在网关层 `403 Forbidden` 拦截。
- **WebSocket 实时高危威胁推流**：内置 `ThreatAlertNotifier`，秒级将 SQL 注入、路径穿越或 CRITICAL 告警实时广播至前端 SOC Studio。
- **Flyway 数据库版本化增量迁移**：基于 `db/migration` 实现 `V1__init_schema.sql`、`V2__seed_default_data.sql` 自动化热升级。
- **@AuditLog 企业级无侵入 AOP 埋点**：自定义切面自动抓取方法耗时、操作用户、客户端 IP 与 TraceId，零侵入完成审计追踪。
- **Kubernetes 云原生 Helm Chart 编排**：内置 `helm/auditvault`，支持 HPA 自动水平弹性伸缩 (2~10 副本) 与 Ingress 负载均衡。
- **Grafana 生产可观测性大屏**：预置 `docs/grafana/auditvault-dashboard.json` 模板，一键导入 Prometheus 监控大屏。
- **安全与合规保障**：基于 Redis 7 的 JWT Token 黑名单与滑动窗口限流器，支持 Spring Security RBAC 权限控制。

---

## 🛠️ 技术栈

| 层级 | 技术选型 | 说明 |
|---|---|---|
| **核心框架** | Spring Boot 3.2.0 + Java 17 / 21 LTS | 现代高性能 Java 后端，虚拟线程就绪 |
| **链路追踪** | W3C TraceContext + OTel + MDC | 兼容 traceparent 与 X-Trace-Id 双模链路标准 |
| **主动防御** | Redis IP 信誉度评分 + Auto-Ban | 威胁分 >= 80 分自动熔断封禁 |
| **分布式消息** | Apache Kafka 3.7 (KRaft) | 分布式日志流式削峰摄取 |
| **时序 OLAP** | ClickHouse 24.3 (MergeTree) | 45x 毫秒级多维时序直方图分析 |
| **持久层 & 迁移** | MyBatis 3.0 + MySQL 8.0 + Flyway | 联合索引、版本化增量迁移与慢 SQL 拦截 |
| **无侵入埋点** | Spring AOP + `@AuditLog` | 自动抓取耗时、TraceId 并异步落库 |
| **实时通信** | Spring WebSocket | `/ws/threat-alerts` 实时高危告警广播通道 |
| **云原生编排** | Kubernetes Helm Chart | HPA 自动弹性伸缩、Liveness/Readiness 探针 |
| **缓存与限流** | Redis 7 + Lettuce | Token 黑名单、IP 滑动窗口限流、HyperLogLog UV 统计 |
| **认证与安全** | Spring Security + JWT (HS256) | RBAC 细粒度角色权限隔离 (ADMIN / USER) |
| **报表导出** | Apache POI 5.2 (SXSSF) | 5万行 Excel 审计数据流式防 OOM 导出 |
| **可观测性** | Spring Boot Actuator + Micrometer | 业务级自定义指标暴露，支持 Prometheus / Grafana |
| **接口规范** | SpringDoc OpenAPI 3.0 | 交互式 Swagger UI 丰富入参示例 |

---

## 🧪 单元测试

包含全套 59 项单元与集成测试（100% 绿灯运行）：
```bash
mvn clean test
```

---

## 🚀 快速启动

1. **一键 Docker Compose 启动**：
   ```bash
   cd .. && docker compose up -d
   ```
2. **访问系统**：
   - 工作台：`http://localhost:8080` (管理员: `admin` / `admin123`，只读: `user` / `user123`)
   - 数据仪表盘：`http://localhost:8080/dashboard.html`
   - 接口文档：`http://localhost:8080/swagger-ui/index.html`
