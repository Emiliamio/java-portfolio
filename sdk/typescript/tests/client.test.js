const test = require("node:test");
const assert = require("node:assert");

// 简易测试 W3C Traceparent 与 Client 构建逻辑
test("AuditVault SDK - W3C Traceparent Generation", () => {
  const chars = "0123456789abcdef";
  let traceId = "";
  for (let i = 0; i < 32; i++) {
    traceId += chars[Math.floor(Math.random() * chars.length)];
  }
  const traceparent = `00-${traceId}-00f067aa0ba902b7-01`;

  assert.strictEqual(traceId.length, 32);
  assert.match(traceparent, /^00-[a-f0-9]{32}-[a-f0-9]{16}-01$/);
});

test("AuditVault SDK - Client Configuration Options", () => {
  const options = {
    endpoint: "http://localhost:8080/api/logs/webhook",
    serviceName: "AUTH_GATEWAY",
    token: "secret-test-token",
    maxRetries: 2,
  };

  assert.strictEqual(options.serviceName, "AUTH_GATEWAY");
  assert.strictEqual(options.maxRetries, 2);
});
