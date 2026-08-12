package com.logai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 分析结果 DTO — 返回给前端。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {
    private String operationType;
    private String riskLevel;
    private boolean needIntervention;
    private String suggestion;
    private String summary;
    private String sourceIp;
    private String modelUsed;
    private int analysisTimeMs;
    private boolean fallback;  // true 表示使用了降级规则引擎（非 AI）
}
