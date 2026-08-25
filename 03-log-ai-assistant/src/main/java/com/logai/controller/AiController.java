package com.logai.controller;

import com.logai.entity.AiAnalysis;
import com.logai.entity.AnalyzeRequest;
import com.logai.entity.AnalysisResult;
import com.logai.entity.ApiResponse;
import com.logai.service.LlmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 分析 REST API。
 *
 * 接口列表：
 * - POST /api/ai/analyze         提交日志文本，获取 AI 分析结果 (同步阻塞)
 * - POST /api/ai/analyze-stream  提交日志文本，获取 AI 分析结果 (SSE 打字机流式)
 * - GET  /api/ai/history         获取当前用户的分析历史
 * - GET  /api/ai/history/{id}    获取单条分析详情（仅本人）
 * - GET  /api/ai/stats           获取统计信息（当前用户）
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 日志分析", description = "基于大语言模型与规则引擎的日志安全分析、SSE 打字机流式输出及历史审计")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);
    private final LlmService llmService;

    public AiController(LlmService llmService) {
        this.llmService = llmService;
    }

    @PostMapping("/analyze")
    @Operation(summary = "同步分析日志", description = "提交待分析日志文本，由大模型分析安全风险等级、事件摘要及处置建议")
    public ApiResponse<AnalysisResult> analyze(@Valid @RequestBody AnalyzeRequest request) {

        log.info("Received analyze request, log length={}", request.getLogContent().length());
        AnalysisResult result = llmService.analyze(request.getLogContent(), currentUser());
        return ApiResponse.success(result);
    }

    @PostMapping(value = "/analyze-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@Valid @RequestBody AnalyzeRequest request) {
        log.info("Received SSE analyze stream request, log length={}", request.getLogContent().length());
        SseEmitter emitter = new SseEmitter(120_000L);
        llmService.analyzeStream(request.getLogContent(), currentUser(), emitter);
        return emitter;
    }

    @GetMapping("/history")
    public ApiResponse<List<AiAnalysis>> history(
            @RequestParam(defaultValue = "20") int limit) {
        List<AiAnalysis> list = llmService.getHistory(Math.min(limit, 100), currentUser());
        return ApiResponse.success(list);
    }

    @GetMapping("/history/{id}")
    public ApiResponse<AiAnalysis> detail(@PathVariable Long id) {
        AiAnalysis analysis = llmService.getDetail(id, currentUser());
        if (analysis == null) {
            return ApiResponse.error(404, "分析记录不存在");
        }
        return ApiResponse.success(analysis);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        int total = llmService.getHistoryCount(currentUser());
        Map<String, Object> map = new HashMap<>();
        map.put("totalAnalyses", total);
        return ApiResponse.success(map);
    }

    /** 从 Spring Security 上下文取当前登录用户名。 */
    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : null;
    }
}
