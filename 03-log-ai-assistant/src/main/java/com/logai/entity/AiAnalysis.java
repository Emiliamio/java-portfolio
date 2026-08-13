package com.logai.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 分析记录实体 — 映射 ai_analysis 表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysis {
    private Long id;
    private String username;
    private String logContent;
    private String logSummary;
    private String operationType;
    private String riskLevel;
    private Boolean needIntervention;
    private String aiSuggestion;
    private String sourceIp;
    private String modelUsed;
    private Integer analysisTimeMs;
    private LocalDateTime createdAt;
}
