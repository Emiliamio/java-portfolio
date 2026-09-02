import {
  AuditLogEntry,
  AuditVaultClientOptions,
  AuditShipResponse,
} from "./types";

export class AuditVaultClient {
  private readonly endpoint: string;
  private readonly token: string;
  private readonly serviceName: string;
  private readonly timeoutMs: number;
  private readonly maxRetries: number;

  constructor(options: AuditVaultClientOptions) {
    this.endpoint = options.endpoint.replace(/\/+$/, "");
    this.token = options.token || "auditvault-webhook-default-secret-token-2026";
    this.serviceName = options.serviceName || "NODE_MICROSERVICE";
    this.timeoutMs = options.timeoutMs || 5000;
    this.maxRetries = options.maxRetries ?? 3;
  }

  /**
   * 生成符合 W3C TraceContext 规范的 traceId 与 traceparent
   */
  public static generateTraceId(): string {
    const chars = "0123456789abcdef";
    let traceId = "";
    for (let i = 0; i < 32; i++) {
      traceId += chars[Math.floor(Math.random() * chars.length)];
    }
    return traceId;
  }

  public static generateTraceparent(traceId?: string): string {
    const tid = traceId || AuditVaultClient.generateTraceId();
    const parentId = "00f067aa0ba902b7";
    return `00-${tid}-${parentId}-01`;
  }

  /**
   * 异步上报单条审计日志 (Non-blocking Asynchronous Shipping)
   */
  public async ship(entry: AuditLogEntry): Promise<AuditShipResponse> {
    const traceId = entry.traceId || AuditVaultClient.generateTraceId();
    const traceparent = AuditVaultClient.generateTraceparent(traceId);

    const payload = {
      timestamp: entry.timestamp || new Date().toISOString(),
      ipAddress: entry.ipAddress || "127.0.0.1",
      username: entry.username,
      operation: `[${this.serviceName}] ${entry.operation}`,
      operationResult: entry.operationResult,
      detail: entry.detail || "",
      severity: entry.severity || "INFO",
      sourceFile: entry.sourceFile || `${this.serviceName}.ts`,
      traceId: traceId,
    };

    let attempt = 0;
    while (attempt <= this.maxRetries) {
      try {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), this.timeoutMs);

        const response = await fetch(this.endpoint, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Audit-Token": this.token,
            "X-Trace-Id": traceId,
            traceparent: traceparent,
          },
          body: JSON.stringify(payload),
          signal: controller.signal,
        });

        clearTimeout(timer);

        if (response.ok || response.status === 202) {
          return { success: true, code: response.status, traceId };
        }

        if (response.status === 403) {
          return {
            success: false,
            code: 403,
            message: "IP Auto-Banned by AuditVault SOC Armor",
            traceId,
          };
        }
      } catch (err: any) {
        attempt++;
        if (attempt > this.maxRetries) {
          return {
            success: false,
            message: `Failed after ${this.maxRetries} retries: ${err.message}`,
            traceId,
          };
        }
        // 指数退避等待 (Exponential Backoff)
        await new Promise((res) => setTimeout(res, Math.pow(2, attempt) * 100));
      }
    }

    return { success: false, message: "Exceeded max retries", traceId };
  }
}
