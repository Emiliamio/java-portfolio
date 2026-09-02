package com.logai;

import com.logai.validator.SigmaRuleValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SigmaRuleValidator 单元测试 — 验证工业级 SIEM Sigma 规则语法校验与清洗。
 */
class SigmaRuleValidatorTest {

    private SigmaRuleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SigmaRuleValidator();
    }

    @Test
    void testValidSigmaRule() {
        String validYaml = """
                title: Detect SQL Injection in Audit Logs
                id: 4bf92f35-77b3-4da6-a3ce-929d0e0e4736
                status: production
                description: Detects SQL union select and tautology attacks
                logsource:
                    category: webserver
                    product: spring_boot
                detection:
                    selection:
                        detail|contains:
                            - "union select"
                            - "' or '1'='1"
                    condition: selection
                level: high
                """;

        SigmaRuleValidator.ValidationResult result = validator.validate(validYaml);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getSanitizedYaml().contains("title: Detect SQL Injection"));
    }

    @Test
    void testValidSigmaRuleWithMarkdownWrap() {
        String markdownYaml = """
                ```yaml
                title: Detect Path Traversal
                logsource:
                    category: application
                detection:
                    selection:
                        detail|contains: "../"
                    condition: selection
                level: critical
                ```
                """;

        SigmaRuleValidator.ValidationResult result = validator.validate(markdownYaml);

        assertTrue(result.isValid());
        assertFalse(result.getSanitizedYaml().startsWith("```yaml"));
        assertFalse(result.getSanitizedYaml().endsWith("```"));
    }

    @Test
    void testMissingRequiredKeys() {
        String invalidYaml = """
                title: Incomplete Rule
                description: Missing logsource, detection and condition
                """;

        SigmaRuleValidator.ValidationResult result = validator.validate(invalidYaml);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() >= 3);
    }

    @Test
    void testEmptyContent() {
        SigmaRuleValidator.ValidationResult result = validator.validate("");
        assertFalse(result.isValid());
    }
}
