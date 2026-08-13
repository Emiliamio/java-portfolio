package com.logai.controller;

import com.logai.entity.AiAnalysis;
import com.logai.entity.AnalyzeRequest;
import com.logai.entity.AnalysisResult;
import com.logai.entity.ApiResponse;
import com.logai.service.LlmService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 分析 REST API。
 *
 * 接口列表：
 * - POST /api/ai/analyze    提交日志文本，获取 AI 分析结果
 * - GET  /api/ai/history    获取当前用户的分析历史
 * - GET  /api/ai/history/{id}  获取单条分析详情（仅本人）
 * - GET  /api/ai/stats      获取统计信息（当前用户）
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);
    private final LlmService llmService;

    public AiController(LlmService llmService) {
        this.llmService = llmService;
    }

    @PostMapping("/analyze")
    public ApiResponse<AnalysisResult> analyze(@Valid @RequestBody AnalyzeRequest request) {
        log.info("Received analyze request, log length={}", request.getLogContent().length());
        AnalysisResult result = llmService.analyze(request.getLogContent(), currentUser());
        return ApiResponse.success(result);
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
