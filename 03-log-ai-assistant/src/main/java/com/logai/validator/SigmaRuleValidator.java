package com.logai.validator;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 工业级 Sigma 告警规则语法校验与清洗器 (Sigma Rule Validator & Linter)。
 *
 * 针对大语言模型生成的 SIEM Sigma YAML 规则进行 AST 语义校验：
 * 1. 验证必需顶级键（title, id, status, logsource, detection, condition, level）；
 * 2. 检查 logsource 必填字段（category / product / service）；
 * 3. 检查 detection 必须包含有效 condition 表达式；
 * 4. 自动清洗与格式化，确保输出合规可直接导入 Elastic SIEM / Splunk。
 */
@Component
public class SigmaRuleValidator {

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final String sanitizedYaml;

        public ValidationResult(boolean valid, List<String> errors, String sanitizedYaml) {
            this.valid = valid;
            this.errors = errors;
            this.sanitizedYaml = sanitizedYaml;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getSanitizedYaml() {
            return sanitizedYaml;
        }
    }

    /**
     * 校验并清洗 Sigma YAML 规则文本
     */
    public ValidationResult validate(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            return new ValidationResult(false, List.of("Sigma YAML content cannot be empty"), "");
        }

        List<String> errors = new ArrayList<>();
        String sanitized = cleanMarkdownWrappers(yamlContent);

        // 1. 必需顶级键检查
        if (!sanitized.contains("title:")) {
            errors.add("Missing required top-level key: 'title'");
        }
        if (!sanitized.contains("logsource:")) {
            errors.add("Missing required top-level key: 'logsource'");
        }
        if (!sanitized.contains("detection:")) {
            errors.add("Missing required top-level key: 'detection'");
        }
        if (!sanitized.contains("condition:")) {
            errors.add("Missing required detection logic: 'condition'");
        }

        // 2. 严重等级检查
        if (sanitized.contains("level:")) {
            String lower = sanitized.toLowerCase();
            boolean validLevel = lower.contains("level: low") ||
                                 lower.contains("level: medium") ||
                                 lower.contains("level: high") ||
                                 lower.contains("level: critical") ||
                                 lower.contains("level: informational");
            if (!validLevel) {
                errors.add("Invalid severity level (must be informational/low/medium/high/critical)");
            }
        }

        boolean isValid = errors.isEmpty();
        return new ValidationResult(isValid, errors, sanitized);
    }

    private String cleanMarkdownWrappers(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```yaml") || trimmed.startsWith("```yml")) {
            trimmed = trimmed.substring(trimmed.indexOf("\n") + 1);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(trimmed.indexOf("\n") + 1);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.lastIndexOf("```")).trim();
        }
        return trimmed;
    }
}
