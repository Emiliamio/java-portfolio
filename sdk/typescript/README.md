# @auditvault/sdk — TypeScript / Node.js 全类型安全客户端 SDK

> 官方 TypeScript SDK，为 Node.js、NestJS、Next.js 与前端应用提供 100% 类型提示、W3C OTel 链路追踪与指数退避重试的极简接入体验。

---

## 🚀 3 行代码快速接入

```typescript
import { AuditVaultClient } from "@auditvault/sdk";

// 1. 初始化客户端
const client = new AuditVaultClient({
  endpoint: "http://localhost:8080/api/logs/webhook",
  serviceName: "ORDER_NODE_SERVICE",
  token: process.env.AUDITVAULT_TOKEN,
});

// 2. 异步上报审计日志 (自动生成 W3C traceparent 并注入链路)
await client.ship({
  username: "alice@example.com",
  operation: "PAYMENT_CHARGE",
  operationResult: "SUCCESS",
  detail: "Stripe payment succeeded, amount=$99.00",
  severity: "INFO",
});
```

---

## 🌟 核心特性

- **100% TypeScript 强类型定义**：提供完整代码补全与参数校验；
- **W3C TraceContext 双模注入**：自动生成 `traceparent` 与 `X-Trace-Id` 标头；
- **自适应指数退避重试 (Exponential Backoff)**：网络瞬断时自动重试 3 次，避免日志丢单；
- **主动感知 SOC 熔断**：捕获 403 拦截并反馈 IP 威胁封禁状态。
