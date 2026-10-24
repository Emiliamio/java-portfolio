# GEMINI.md - log-audit-system

> 通用规范：~/.dsh/AGENTS.md（§0 为冲突仲裁序）
> 环境事实：读 D:\Antigravity\env-snapshot.json，不在本文件写死

## 技术栈
- Java 17, Spring Boot 3.5.7, Spring Web, Spring AOP, Spring Security + JJWT 0.11.5
- MyBatis 3.0.3, MySQL Connector/J, Flyway (MySQL), ClickHouse JDBC 0.6.0
- Spring Data Redis, Caffeine, Resilience4j 2.2.0, Spring Kafka
- SpringDoc OpenAPI 2.3.0, WebSocket, Actuator, Micrometer Prometheus, Fastjson2, POI 5.2.5
- Test: Spring Boot Starter Test, Security Test, Testcontainers 1.19.7 (MySQL)

## 目录职责
- `src/main/java/com/logaudit/`：核心应用源码（含 LogAuditApplication 启动入口）
  - `annotation`/`aspect`：无侵入审计埋点注解与 AOP 切面
  - `config`/`filter`/`security`：安全链、JWT 认证与系统中间件配置
  - `controller`/`dto`：RESTful 审计查询接口与请求响应模型
  - `entity`/`mapper`：数据持久层与 MyBatis 映射器
  - `service`：Merkle 存证、动态基线、SOAR 自愈、告警与威胁分析
  - `handler`/`interceptor`/`websocket`：全局异常、慢 SQL 拦截与实时威胁推流
- `src/main/resources/`：应用配置与 Flyway 增量迁移脚本
- `src/test/java/`：全套单元与集成测试套件

## 命令
- 测试：`mvn -B test`
- 启动：`mvn spring-boot:run` 或运行 `com.logaudit.LogAuditApplication`

## 禁区
- 凭据只读 ~/.dsh/private.env.md，绝不写进本仓库
- 唯一署名 Emiliamio <mio2110767128@163.com>
- 改完必须 `mvn -B test` 全绿
