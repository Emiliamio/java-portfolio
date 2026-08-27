# AuditVault — 企业级日志审计与安全分析工作台 (SOC Studio)

基于 **Spring Boot 3.2 + JDK 17 + MySQL 8 + Redis 7** 构建的企业级日志采集、审计检索、全景大屏与合规分析工作台，深度对标 Datadog Cloud SIEM 与 Elastic Security 工业级标准。

---

## 🌟 核心功能矩阵

- **100vw × 100vh 全视口 Studio 布局**：自适应大屏控制台，无冗余留白，沉浸式分析体验。
- **多维 Facets 动态聚类侧栏**：实时统计严重级别 (CRITICAL/ERROR/WARN/INFO)、操作类型与 Top IP 聚合计数，支持一键复合下钻。
- **时序直方分布图 (Histogram)**：可视化呈现时间序列上的日志流量走势与异常突增波峰。
- **三模态工作视图**：
  - **表格模式 (Table)**：高密度字段对齐、等宽字体排版、高对比度风险徽标；
  - **控制台流 (Stream)**：紧凑终端流水；
  - **模式聚类 (Pattern Cluster)**：通过算法将相似日志归一化为核心模板，排查重复风暴。
- **上下文日志溯源 (Surrounding Context Trace)**：右侧抽屉支持一键拉取目标事件发生前后 **`±10` 条上下文流水**，还原执行现场。
- **Webhook 异步微服务采集网关**：遵循 RFC 7807 规范，提供轻量令牌鉴权与秒级万条日志吞吐。
- **安全与合规保障**：基于 Redis 7 的 JWT Token 黑名单与滑动窗口限流器，支持 Spring Security RBAC 权限控制。

---

## 🛠️ 技术栈

| 层级 | 技术选型 | 说明 |
|---|---|---|
| **核心框架** | Spring Boot 3.2.0 + JDK 17 | 现代高性能 Java 后端 |
| **持久层** | MyBatis 3.0 + MySQL 8.0 + HikariCP | 联合索引与动态 SQL 优化 |
| **缓存与限流** | Redis 7 + Lettuce | Token 黑名单、IP 滑动窗口限流、HyperLogLog UV 统计 |
| **认证与安全** | Spring Security + JWT (HS256) | RBAC 细粒度角色权限隔离 (ADMIN / USER) |
| **报表导出** | Apache POI 5.2 | 大批量 Excel 审计数据流式导出 |
| **接口规范** | SpringDoc OpenAPI 3.0 | 交互式 Swagger UI 文档 |

---

## 🚀 快速启动

1. **数据库初始化**：
   在 MySQL 8.0 中创建 `log_audit` 数据库并执行 `src/main/resources/sql/schema.sql`
2. **应用配置**：
   检查 `src/main/resources/application.yml` 中的 MySQL 与 Redis 连接密码
3. **打包与运行**：
   ```bash
   mvn clean package -DskipTests
   java -jar target/log-audit-system-1.0.0.jar
   ```
4. **访问系统**：
   - 工作台：`http://localhost:8080` (默认管理员: `admin` / `admin123`)
   - 接口文档：`http://localhost:8080/swagger-ui/index.html`
