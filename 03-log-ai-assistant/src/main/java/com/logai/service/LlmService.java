package com.logai.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.logai.entity.AiAnalysis;
import com.logai.entity.AnalysisResult;
import com.logai.mapper.AiAnalysisMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * LLM 服务 — 调用大模型 API 分析日志内容。
 *
 * 使用 java.net.http.HttpClient（JDK 11+ 内置），无第三方 HTTP 依赖。
 * API Key 从环境变量 AI_API_KEY 读取，绝不硬编码。
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final AiAnalysisMapper analysisMapper;
    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiModel;
    private final int timeoutSeconds;

    public LlmService(
            AiAnalysisMapper analysisMapper,
            @Value("${ai.api.url}") String apiUrl,
            @Value("${ai.api.model}") String apiModel,
            @Value("${ai.api.timeout-seconds:60}") int timeoutSeconds
    ) {
        this.analysisMapper = analysisMapper;
        this.apiUrl = apiUrl;
        this.apiModel = apiModel;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    /**
     * 分析一段日志文本，返回结构化分析结果。
     *
     * 核心流程：
     * 1. 构造 System Prompt（角色 + 输出格式）
     * 2. 将用户日志作为 User Message
     * 3. 调用 LLM API
     * 4. 解析 JSON 响应 → AnalysisResult
     * 5. 存入 MySQL 历史记录
     *
     * @param logContent 用户提交的原始日志文本
     * @return 结构化的分析结果
     */
    public AnalysisResult analyze(String logContent) {
        long startTime = System.currentTimeMillis();

        String apiKey = System.getenv("AI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI_API_KEY not set — falling back to keyword-based rule engine.");
            // 优雅降级：使用关键词规则引擎，不抛异常
            int elapsedMs = (int) (System.currentTimeMillis() - startTime);
            AnalysisResult result = fallbackAnalysis(logContent);
            result.setAnalysisTimeMs(elapsedMs);
            result.setModelUsed("rule-engine (fallback)");
            result.setFallback(true);
            saveToHistory(logContent, result);
            log.info("Fallback analysis complete in {}ms — risk={}, intervention={}",
                    elapsedMs, result.getRiskLevel(), result.isNeedIntervention());
            return result;
        }

        // ── 1. 构造 Prompt + 调用 LLM ──
        String systemPrompt = buildSystemPrompt();
        String aiResponseJson;
        try {
            aiResponseJson = callLlmApi(apiKey, systemPrompt, logContent);
        } catch (RuntimeException e) {
            log.error("LLM API call failed, falling back to rule engine. Error: {}", e.getMessage());
            int elapsedMs = (int) (System.currentTimeMillis() - startTime);
            AnalysisResult result = fallbackAnalysis(logContent);
            result.setAnalysisTimeMs(elapsedMs);
            result.setModelUsed("rule-engine (api-failure fallback)");
            result.setFallback(true);
            saveToHistory(logContent, result);
            log.info("Fallback analysis (API failure) complete in {}ms — risk={}, intervention={}",
                    elapsedMs, result.getRiskLevel(), result.isNeedIntervention());
            return result;
        }

        // ── 2. 解析 LLM 响应 ──
        AnalysisResult result = parseResponse(aiResponseJson);
        int elapsedMs = (int) (System.currentTimeMillis() - startTime);
        result.setAnalysisTimeMs(elapsedMs);
        result.setModelUsed(apiModel);
        result.setFallback(false);

        // ── 3. 存入数据库 ──
        saveToHistory(logContent, result);
        log.info("Analysis complete in {}ms — risk={}, intervention={}",
                elapsedMs, result.getRiskLevel(), result.isNeedIntervention());

        return result;
    }

    /**
     * 查询最近 N 条分析历史。
     */
    public List<AiAnalysis> getHistory(int limit) {
        return analysisMapper.findRecent(limit);
    }

    /**
     * 查看单条分析详情。
     */
    public AiAnalysis getDetail(Long id) {
        return analysisMapper.findById(id);
    }

    /**
     * 获取历史总数。
     */
    public int getHistoryCount() {
        return analysisMapper.countTotal();
    }

    // ── 私有方法 ──────────────────────────────────────────

    /**
     * 构造 System Prompt — 让 LLM 扮演日志安全分析师。
     */
    private String buildSystemPrompt() {
        return """
            你是一个专业的日志安全分析专家。用户会给你一段系统日志，请你分析后
            严格按照以下 JSON 格式返回结果（只返回 JSON，不要包含任何其他文字）：

            {
              "operationType": "操作类型，如 LOGIN/QUERY/DELETE/UNKNOWN",
              "riskLevel": "风险等级：NORMAL / LOW / MEDIUM / HIGH / CRITICAL",
              "needIntervention": true或false,
              "suggestion": "处置建议，中文，50字以内",
              "summary": "一句话摘要描述这条日志在做什么，中文，30字以内",
              "sourceIp": "日志中的源IP地址，没有则为null"
            }

            风险等级判断标准：
            - NORMAL: 正常操作，无异常
            - LOW: 轻微异常（如偶尔的密码错误）
            - MEDIUM: 需要关注（如连续失败、权限不足）
            - HIGH: 严重威胁（如暴力破解、未授权访问）
            - CRITICAL: 紧急事件（如SQL注入、路径遍历、数据泄露）

            注意：
            - 如果日志中有疑似攻击行为（SQL注入、XSS、路径遍历等），riskLevel 至少为 HIGH
            - 如果同一IP短时间内多次失败，考虑暴力破解，riskLevel 至少为 MEDIUM
            - 如果操作为 DENIED 或包含 "unauthorized"，riskLevel 至少为 MEDIUM
            """;
    }

    /**
     * 调用大模型 HTTP API。
     *
     * 支持的 API 格式：Anthropic Messages API / OpenAI Chat Completions API
     * 根据 apiUrl 的 host 自动适配请求体格式。
     */
    private String callLlmApi(String apiKey, String systemPrompt, String userContent) {
        try {
            JSONObject requestBody = buildRequestBody(systemPrompt, userContent);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString()))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                String errorBody = response.body();
                log.error("LLM API returned {}: {}", response.statusCode(), errorBody);
                throw new RuntimeException(
                    "大模型 API 调用失败 (HTTP " + response.statusCode() + ")，请检查 API Key 和网络连接。"
                );
            }

            return extractTextFromResponse(response.body());

        } catch (java.net.ConnectException e) {
            throw new RuntimeException(
                "无法连接到大模型 API (" + apiUrl + ")，请检查网络和 API 地址配置。", e
            );
        } catch (java.net.http.HttpTimeoutException e) {
            throw new RuntimeException(
                "大模型 API 响应超时 (" + timeoutSeconds + "s)，请稍后重试。", e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("API 调用被中断。", e);
        } catch (Exception e) {
            log.error("LLM API call failed", e);
            throw new RuntimeException("大模型分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据 API URL 自动选择请求体格式。
     * 通过判断 URL 中是否含 "anthropic" 来选择 Anthropic / OpenAI 格式。
     */
    private JSONObject buildRequestBody(String systemPrompt, String userContent) {
        if (apiUrl.contains("anthropic")) {
            // Anthropic Messages API 格式
            JSONObject body = new JSONObject();
            body.put("model", apiModel);
            body.put("max_tokens", 1024);
            body.put("system", systemPrompt);

            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");

            JSONArray content = new JSONArray();
            JSONObject textBlock = new JSONObject();
            textBlock.put("type", "text");
            textBlock.put("text", userContent);
            content.add(textBlock);

            userMsg.put("content", content);
            messages.add(userMsg);
            body.put("messages", messages);

            return body;
        } else {
            // OpenAI / 兼容格式 (Chat Completions API)
            JSONObject body = new JSONObject();
            body.put("model", apiModel);
            body.put("max_tokens", 1024);
            body.put("temperature", 0.3);

            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userContent);
            messages.add(userMsg);

            body.put("messages", messages);
            return body;
        }
    }

    /**
     * 从 API 响应中提取文本内容。
     * 兼容 Anthropic 和 OpenAI 两种响应格式。
     */
    private String extractTextFromResponse(String responseBody) {
        JSONObject json = JSON.parseObject(responseBody);

        // Anthropic 格式: content[0].text
        if (json.containsKey("content")) {
            Object contentObj = json.get("content");
            if (contentObj instanceof JSONArray arr && !arr.isEmpty()) {
                JSONObject firstBlock = arr.getJSONObject(0);
                if (firstBlock.containsKey("text")) {
                    return firstBlock.getString("text");
                }
            }
        }

        // OpenAI 格式: choices[0].message.content
        if (json.containsKey("choices")) {
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject message = firstChoice.getJSONObject("message");
                if (message != null && message.containsKey("content")) {
                    return message.getString("content");
                }
            }
        }

        // 兜底：返回整个 body，让 parseResponse 尝试提取
        return responseBody;
    }

    /**
     * 解析 LLM 返回的 JSON 字符串 → AnalysisResult。
     *
     * 容错策略：如果 LLM 返回的不是纯 JSON（可能包含自然语言包装），
     * 尝试从文本中提取 JSON 块（{ 到 }）。
     */
    public AnalysisResult parseResponse(String rawText) {
        String jsonStr = rawText.trim();

        // 尝试提取 JSON 块（处理 LLM 在 JSON 外面包了 markdown 代码块的情况）
        if (!jsonStr.startsWith("{")) {
            int braceStart = jsonStr.indexOf('{');
            int braceEnd = jsonStr.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                jsonStr = jsonStr.substring(braceStart, braceEnd + 1);
            }
        }

        try {
            JSONObject obj = JSON.parseObject(jsonStr);

            AnalysisResult result = new AnalysisResult();
            result.setOperationType(
                    obj.getString("operationType") != null
                            ? obj.getString("operationType") : "UNKNOWN");
            result.setRiskLevel(
                    obj.getString("riskLevel") != null
                            ? obj.getString("riskLevel") : "NORMAL");
            result.setNeedIntervention(
                    obj.getBoolean("needIntervention") != null
                            && obj.getBoolean("needIntervention"));
            result.setSuggestion(
                    obj.getString("suggestion") != null
                            ? obj.getString("suggestion") : "无需特殊处理");
            result.setSummary(
                    obj.getString("summary") != null
                            ? obj.getString("summary") : "日志分析完成");
            result.setSourceIp(obj.getString("sourceIp"));

            return result;

        } catch (Exception e) {
            log.warn("Failed to parse LLM response as JSON, using fallback. Raw: {}",
                    rawText.substring(0, Math.min(200, rawText.length())));
            // 降级：返回一个基于关键词的粗略分析
            return fallbackAnalysis(rawText);
        }
    }

    /**
     * 降级分析 — 当 LLM 返回格式无法解析时，用关键词做基本判断。
     * 保证系统即使在 LLM 输出不稳定时也能给出有意义的分析结果。
     */
    private AnalysisResult fallbackAnalysis(String rawText) {
        String upper = rawText.toUpperCase();
        AnalysisResult result = new AnalysisResult();
        result.setOperationType("UNKNOWN");
        result.setRiskLevel("NORMAL");
        result.setNeedIntervention(false);
        result.setSuggestion("AI 返回格式异常，已使用降级规则分析，建议人工复核");
        result.setSummary("日志分析完成（降级模式）");

        // 关键词判定
        if (upper.contains("SQL INJECTION") || upper.contains("UNION SELECT")
                || upper.contains("' OR '1'='1") || upper.contains("DROP TABLE")) {
            result.setRiskLevel("CRITICAL");
            result.setNeedIntervention(true);
            result.setOperationType("ATTACK");
            result.setSuggestion("检测到 SQL 注入攻击特征，立即封禁源 IP 并排查数据库日志");
        } else if (upper.contains("XSS") || upper.contains("<SCRIPT>")
                || upper.contains("JAVASCRIPT:")) {
            result.setRiskLevel("CRITICAL");
            result.setNeedIntervention(true);
            result.setOperationType("ATTACK");
            result.setSuggestion("检测到 XSS 攻击特征，检查 WAF 规则并审计相关页面");
        } else if (upper.contains("PATH TRAVERSAL") || upper.contains("../")
                || upper.contains("..\\")) {
            result.setRiskLevel("HIGH");
            result.setNeedIntervention(true);
            result.setOperationType("ATTACK");
            result.setSuggestion("检测到路径遍历攻击，检查文件访问权限配置");
        } else if (upper.contains("DENIED") || upper.contains("UNAUTHORIZED")) {
            result.setRiskLevel("MEDIUM");
            result.setNeedIntervention(false);
            result.setOperationType("ACCESS");
        } else if (upper.contains("FAIL") && (upper.contains("LOGIN"))) {
            result.setRiskLevel("LOW");
            result.setOperationType("LOGIN");
        }

        return result;
    }

    /**
     * 将分析结果存入 MySQL 历史记录。
     */
    private void saveToHistory(String logContent, AnalysisResult result) {
        AiAnalysis record = new AiAnalysis();
        record.setLogContent(logContent);
        record.setLogSummary(result.getSummary());
        record.setOperationType(result.getOperationType());
        record.setRiskLevel(result.getRiskLevel());
        record.setNeedIntervention(result.isNeedIntervention());
        record.setAiSuggestion(result.getSuggestion());
        record.setSourceIp(result.getSourceIp());
        record.setModelUsed(result.getModelUsed());
        record.setAnalysisTimeMs(result.getAnalysisTimeMs());
        record.setCreatedAt(LocalDateTime.now());

        analysisMapper.insert(record);
    }
}
