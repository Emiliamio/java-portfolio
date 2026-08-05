package com.logai.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 前端提交的分析请求 DTO。
 */
@Data
public class AnalyzeRequest {

    @NotBlank(message = "日志内容不能为空")
    @Size(min = 5, max = 5000, message = "日志内容长度需在 5-5000 字符之间")
    private String logContent;
}
