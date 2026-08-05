package com.logai.controller;

import com.logai.entity.AiAnalysis;
import com.logai.entity.AnalyzeRequest;
import com.logai.entity.AnalysisResult;
import com.logai.entity.ApiResponse;
import com.logai.service.LlmService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 分析 REST API。
 *
 * 接口列表：
 * - POST /api/ai/analyze    提交日志文本，获取 AI 分析结果
 * - GET  /api/ai/history    获取分析历史列表
 * - GET  /api/ai/history/{id}  获取单条分析详情
 * - GET  /api/ai/stats      获取统计信息（总分析次数）
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")  // 允许前端跨域
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);
    private final LlmService llmService;

    public AiController(LlmService llmService) {
        this.llmService = llmService;
    }

    /**
     * 分析日志 — 核心接口。
     */
    @PostMapping("/analyze")
    public ApiResponse<AnalysisResult> analyze(@Valid @RequestBody AnalyzeRequest request) {
        log.info("Received analyze request, log length={}", request.getLogContent().length());
        AnalysisResult result = llmService.analyze(request.getLogContent());
        return ApiResponse.success(result);
    }

    /**
     * 获取最近的分析历史（默认 20 条）。
     */
    @GetMapping("/history")
    public ApiResponse<List<AiAnalysis>> history(
            @RequestParam(defaultValue = "20") int limit) {
        List<AiAnalysis> list = llmService.getHistory(Math.min(limit, 100));
        return ApiResponse.success(list);
    }

    /**
     * 获取单条分析详情。
     */
    @GetMapping("/history/{id}")
    public ApiResponse<AiAnalysis> detail(@PathVariable Long id) {
        AiAnalysis analysis = llmService.getDetail(id);
        if (analysis == null) {
            return ApiResponse.error(404, "分析记录不存在");
        }
        return ApiResponse.success(analysis);
    }

    /**
     * 获取统计信息。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        int total = llmService.getHistoryCount();
        Map<String, Object> map = new HashMap<>();
        map.put("totalAnalyses", total);
        return ApiResponse.success(map);
    }
}
