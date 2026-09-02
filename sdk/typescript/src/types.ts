/**
 * AuditVault TypeScript SDK 核心数据类型定义
 */

export type SeverityLevel = "INFO" | "WARN" | "ERROR" | "CRITICAL";
export type OperationResult = "SUCCESS" | "FAIL";

export interface AuditLogEntry {
  /** 日志时间戳 (ISO-8601 或本地格式, 可选, 默认当前时间) */
  timestamp?: string;
  /** 客户端 IP 地址 (可选, 默认 '127.0.0.1') */
  ipAddress?: string;
  /** 操作用户名 */
  username: string;
  /** 操作类型 (如: 'CREATE_ORDER', 'LOGIN', 'SQLI_ATTACK') */
  operation: string;
  /** 操作结果 ('SUCCESS' | 'FAIL') */
  operationResult: OperationResult;
  /** 详细描述或报错信息 */
  detail?: string;
  /** 严重级别 ('INFO' | 'WARN' | 'ERROR' | 'CRITICAL') */
  severity?: SeverityLevel;
  /** 源码定位文件 */
  sourceFile?: string;
  /** W3C / MDC 分布式链路追踪 ID (可选, 自动生成) */
  traceId?: string;
}

export interface AuditVaultClientOptions {
  /** AuditVault 服务端 Webhook 地址 (如: 'http://localhost:8080/api/logs/webhook') */
  endpoint: string;
  /** Webhook 访问鉴权令牌 Token */
  token?: string;
  /** 微服务/客户端标识名称 (如: 'PAYMENT_SERVICE') */
  serviceName?: string;
  /** 请求超时时间 (毫秒, 默认 5000ms) */
  timeoutMs?: number;
  /** 失败最大重试次数 (默认 3 次) */
  maxRetries?: number;
}

export interface AuditShipResponse {
  success: boolean;
  code?: number;
  message?: string;
  traceId: string;
}
