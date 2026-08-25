# AuditVault 日志采集 Webhook 接入指南

> 本文档说明如何将外部微服务（Spring Boot、Node.js、Python、Go 等）及标准 Logback Appender 对接到 AuditVault 实时日志审计中心。

---

## 1. 接口规范

- **请求路径**：`POST /api/logs/webhook`
- **默认端口**：`8080` (Docker 部署为 `audit-backend:8080`)
- **Content-Type**：`application/json;charset=UTF-8`
- **安全认证头**（任选一种）：
  - `X-Audit-Token: <SECRET_KEY>`（推荐）
  - `Authorization: Bearer <SECRET_KEY>`
- **密钥配置**：在 AuditVault `application.yml` 或环境变量 `WEBHOOK_SECRET_KEY` 中配置，默认为 `auditvault-webhook-default-secret-token-2026`。

---

## 2. 请求 Payload 格式

### 模式 A：单条日志推送（JSON Object）

```json
{
  "timestamp": "2026-08-25 19:30:00",
  "level": "ERROR",
  "logger": "com.example.payment.PaymentService",
  "message": "支付网关超时，订单号: ORD-20260825001",
  "clientIp": "192.168.1.100",
  "user": "zhangsan",
  "thread": "http-nio-8080-exec-5",
  "stackTrace": "java.net.SocketTimeoutException: Connect timed out\n\tat com.example.payment..."
}
```

### 模式 B：批量日志推送（JSON Array）

```json
[
  {
    "time": "2026-08-25T19:30:01.123Z",
    "level": "INFO",
    "logger": "com.example.auth.AuthService",
    "message": "用户 admin 登录成功",
    "ip": "10.0.1.20",
    "user": "admin"
  },
  {
    "time": "2026-08-25T19:30:02.456Z",
    "level": "WARN",
    "logger": "com.example.security.RateLimiter",
    "message": "触发接口频控限制: /api/pay",
    "ip": "10.0.1.55",
    "user": "anonymous"
  }
]
```

### 字段兼容映射表

| 字段名称 | 别名支持 | 类型 | 说明与推导规则 |
|---|---|---|---|
| `timestamp` | `time`, `logTime`, `@timestamp` | String / Long | 支持 ISO-8601、`yyyy-MM-dd HH:mm:ss` 或毫秒时间戳，缺省为当前时间 |
| `level` | `severity`, `logLevel` | String | `INFO`, `WARN`, `ERROR`, `DEBUG` 等，自动转大写 |
| `logger` | `loggerName`, `service`, `source`, `app` | String | 来源类名或服务名，映射至 `source_file` |
| `message` | `detail`, `content`, `msg` | String | 日志消息正文 |
| `stackTrace` | `exception`, `stack_trace`, `throwable` | String | 异常堆栈，自动拼接至 `detail` 尾部 |
| `clientIp` | `ipAddress`, `ip`, `host` | String | 客户端 IP，缺省自动取 HTTP 真实请求 IP |
| `user` | `username`, `operator`, `principal` | String | 用户名，缺省为 `SYSTEM` |
| `operation` | `action`, `event` | String | 操作类型，缺省为 logger 名或 `LOG_APPENDER` |
| `operationResult` | `result`, `status` | String | 缺省根据 level 自动推导：`ERROR`/`WARN` 为 `FAIL`，其余为 `SUCCESS` |

---

## 3. 响应结果

### 成功响应（HTTP 202 Accepted，异步处理）
```json
{
  "success": true,
  "message": "日志已接收并在后台异步处理",
  "accepted": 2
}
```

### 鉴权失败响应（HTTP 401 Unauthorized）
```json
{
  "success": false,
  "message": "未授权：无效或缺失 X-Audit-Token 接入令牌"
}
```

---

## 4. 快速调用测试（cURL 示例）

```bash
# 发送单条告警日志
curl -X POST http://localhost:8080/api/logs/webhook \
  -H "Content-Type: application/json" \
  -H "X-Audit-Token: auditvault-webhook-default-secret-token-2026" \
  -d '{
    "level": "ERROR",
    "logger": "com.logai.service.LlmService",
    "message": "Anthropic API rate limit exceeded",
    "clientIp": "192.168.1.50",
    "user": "system"
  }'
```

---

## 5. 架构优势

1. **零性能损耗**：控制器仅做快速 JSON 转换，< 5ms 立即返回 `202 Accepted`，数据交由 `logImportExecutor` 线程池异步批量落库。
2. **活跃度实时感知**：摄入的日志 IP 实时汇聚至 Redis HyperLogLog，AuditVault 统计面板实时更新今日独立访问 IP 数。
3. **审计追溯完备**：每次 Webhook 采集均在 `audit_log` 中留存 `INGEST_LOGS` 轨迹，做到采集动作自身可审计。
