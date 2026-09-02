# Sample Order Service — 外部微服务 10 秒无侵入接入 AuditVault 示范工程

> 本子工程演示外部电商/业务微服务如何通过 `@AuditLog` 注解在 10 秒内零侵入接入 AuditVault 审计遥测中枢。

---

## 🌟 核心接入步骤 (10 秒极速接入)

1. **引入注解与切面**：将 `@AuditLog` 与 `AuditLogAspect` 复制到工程或通过 Starter 依赖引入；
2. **在业务方法上打标**：
   ```java
   @AuditLog(operation = "CREATE_ORDER", module = "ORDER_MGMT", severity = "INFO")
   public String createOrder(OrderRequest request) {
       // 业务逻辑...
   }
   ```
3. **配置 `application.yml`**：
   ```yaml
   auditvault:
     enabled: true
     webhook-url: http://localhost:8080/api/logs/webhook
     token: auditvault-webhook-default-secret-token-2026
     service-name: ORDER_MICROSERVICE
   ```

方法执行后，AOP 切面将自动提取耗时、操作用户、客户端 IP 与 TraceId，以异步非阻塞形式自动推送到 AuditVault 进行全局检索、ClickHouse 直方图分析与告警！
