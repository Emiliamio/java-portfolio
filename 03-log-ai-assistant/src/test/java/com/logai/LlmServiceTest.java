package com.logai;

import com.logai.service.LlmService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlmService 单元测试 — 测试 JSON 解析和降级分析。
 * 不依赖真实 LLM API，在不设置 AI_API_KEY 的情况下测试解析逻辑。
 */
class LlmServiceTest {

    // 用 null mapper 创建 service（测试解析逻辑不需要数据库）
    private final LlmService service = new LlmService(
            null, "https://api.deepseek.com/v1/chat/completions", "deepseek-chat", 60,
            "http://localhost:11434", "deepseek-r1:7b", 60
    );

    @Test
    void testProviderStatusReporting() {
        var status = service.getProviderStatus();
        assertNotNull(status);
        assertTrue(status.containsKey("cloud"));
        assertTrue(status.containsKey("ollama"));
        assertTrue(status.containsKey("rule"));
        assertTrue(status.containsKey("activeDefault"));
    }

    @Test
    void testParseValidJsonResponse() {
        String rawJson = """
            {
              "operationType": "LOGIN",
              "riskLevel": "HIGH",
              "needIntervention": true,
              "suggestion": "建议封禁源IP并排查相关账户",
              "summary": "检测到SQL注入攻击特征",
              "sourceIp": "172.31.0.50"
            }""";

        var result = service.parseResponse(rawJson);

        assertEquals("LOGIN", result.getOperationType());
        assertEquals("HIGH", result.getRiskLevel());
        assertTrue(result.isNeedIntervention());
        assertEquals("172.31.0.50", result.getSourceIp());
        assertEquals("建议封禁源IP并排查相关账户", result.getSuggestion());
        assertEquals("检测到SQL注入攻击特征", result.getSummary());
    }

    @Test
    void testParseJsonInMarkdownCodeBlock() {
        String raw = """
            Here is my analysis:
            ```json
            {
              "operationType": "QUERY",
              "riskLevel": "NORMAL",
              "needIntervention": false,
              "suggestion": "正常查询操作，无需处理",
              "summary": "用户执行了常规数据查询",
              "sourceIp": null
            }
            ```
            Hope this helps!""";

        var result = service.parseResponse(raw);

        assertEquals("QUERY", result.getOperationType());
        assertEquals("NORMAL", result.getRiskLevel());
        assertFalse(result.isNeedIntervention());
        assertNull(result.getSourceIp());
    }

    @Test
    void testFallbackSqlInjection() {
        String raw = "SQL injection: ' OR '1'='1 in username field";

        var result = service.parseResponse(raw);

        assertEquals("CRITICAL", result.getRiskLevel());
        assertEquals("ATTACK", result.getOperationType());
        assertTrue(result.isNeedIntervention());
    }

    @Test
    void testFallbackXss() {
        String raw = "XSS detected: <script>alert(1)</script>";

        var result = service.parseResponse(raw);

        assertEquals("CRITICAL", result.getRiskLevel());
        assertTrue(result.isNeedIntervention());
    }

    @Test
    void testFallbackPathTraversal() {
        String raw = "Unauthorized path traversal attempt: ../../etc/passwd";

        var result = service.parseResponse(raw);

        assertTrue(
                result.getRiskLevel().equals("HIGH")
                        || result.getRiskLevel().equals("CRITICAL")
        );
        assertTrue(result.isNeedIntervention());
    }

    @Test
    void testFallbackNormalLoginFail() {
        String raw = "2025-01-15 08:00:05 User zhangsan LOGIN FAIL wrong password";

        var result = service.parseResponse(raw);

        assertEquals("LOW", result.getRiskLevel());
        assertEquals("LOGIN", result.getOperationType());
    }

    @Test
    void testFallbackDeniedAccess() {
        String raw = "Unauthorized access attempt to /admin by scanner";

        var result = service.parseResponse(raw);

        assertEquals("MEDIUM", result.getRiskLevel());
        assertEquals("ACCESS", result.getOperationType());
    }

    @Test
    void testMissingFieldsInJsonGetDefaults() {
        String rawJson = """
            {
              "operationType": "UNKNOWN"
            }""";

        var result = service.parseResponse(rawJson);

        assertEquals("UNKNOWN", result.getOperationType());
        assertEquals("NORMAL", result.getRiskLevel());        // default
        assertFalse(result.isNeedIntervention());              // default
        assertEquals("无需特殊处理", result.getSuggestion());   // default
    }
}
