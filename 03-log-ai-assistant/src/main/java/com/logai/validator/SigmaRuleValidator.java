package com.logai.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sigma 告警规则结构校验与清洗器 (Sigma Rule Validator & Linter)。
 *
 * <p>对 LLM 生成的 SIEM Sigma 规则做<b>真实 YAML 解析后的结构化校验</b>（不是关键字 contains 匹配）：</p>
 * <ol>
 *   <li>先用 SnakeYAML SafeConstructor 解析成 Map，语法错误直接定位；</li>
 *   <li>校验必需顶级键：title、logsource、detection；</li>
 *   <li>logsource 必须含 category / product / service 之一；</li>
 *   <li>detection 必须是映射且包含 condition 表达式；</li>
 *   <li>level 若存在必须属于 Sigma 规范的严重级别枚举；</li>
 *   <li>清洗 Markdown 代码围栏，保证输出可直接导入 Elastic SIEM / Splunk。</li>
 * </ol>
 */
@Component
public class SigmaRuleValidator {

    private static final Logger log = LoggerFactory.getLogger(SigmaRuleValidator.class);

    private static final Set<String> VALID_LEVELS =
            Set.of("informational", "low", "medium", "high", "critical");

    private static final Set<String> LOGSOURCE_KEYS = Set.of("category", "product", "service");

    /** SafeConstructor 关闭任意类反序列化，只生成 Map/List/标量，杜绝 YAML 反序列化攻击 */
    private static final Yaml SAFE_YAML = new Yaml(new SafeConstructor(new LoaderOptions()));

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

        String sanitized = cleanMarkdownWrappers(yamlContent);
        List<String> errors = new ArrayList<>();

        Map<String, Object> document;
        try {
            Object loaded = SAFE_YAML.load(sanitized);
            if (!(loaded instanceof Map)) {
                return new ValidationResult(false,
                        List.of("Sigma rule must be a YAML mapping (key: value)"), sanitized);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> asMap = (Map<String, Object>) loaded;
            document = asMap;
        } catch (YAMLException ex) {
            log.warn("Sigma YAML 解析失败: {}", ex.getMessage());
            return new ValidationResult(false,
                    List.of("YAML syntax error: " + ex.getMessage()), sanitized);
        }

        // 1. 必需顶级键
        requireNonBlank(document, "title", errors);
        requireMapping(document, "logsource", errors);
        requireMapping(document, "detection", errors);

        // 2. logsource 必须声明数据源类别
        Object logsource = document.get("logsource");
        if (logsource instanceof Map<?, ?> logsourceMap) {
            boolean hasAny = logsourceMap.keySet().stream()
                    .map(String::valueOf)
                    .map(key -> key.toLowerCase(Locale.ROOT))
                    .anyMatch(LOGSOURCE_KEYS::contains);
            if (!hasAny) {
                errors.add("logsource must declare at least one of: category / product / service");
            }
        }

        // 3. detection 必须有 condition 检测逻辑（detection 整体缺失同样视为缺 condition）
        Object detection = document.get("detection");
        if (detection instanceof Map<?, ?> detectionMap) {
            Object condition = detectionMap.get("condition");
            if (condition == null || String.valueOf(condition).isBlank()) {
                errors.add("detection must contain a 'condition' expression");
            }
        } else {
            errors.add("detection must contain a 'condition' expression");
        }

        // 4. level 若存在必须是合法枚举
        Object level = document.get("level");
        if (level != null) {
            String normalized = String.valueOf(level).trim().toLowerCase(Locale.ROOT);
            if (!VALID_LEVELS.contains(normalized)) {
                errors.add("Invalid severity level (must be informational/low/medium/high/critical)");
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, sanitized);
    }

    private void requireNonBlank(Map<String, Object> document, String key, List<String> errors) {
        Object value = document.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            errors.add("Missing required top-level key: '" + key + "'");
        }
    }

    private void requireMapping(Map<String, Object> document, String key, List<String> errors) {
        Object value = document.get(key);
        if (value == null) {
            errors.add("Missing required top-level key: '" + key + "'");
        } else if (!(value instanceof Map)) {
            errors.add("'" + key + "' must be a YAML mapping, got: " + value.getClass().getSimpleName());
        }
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
