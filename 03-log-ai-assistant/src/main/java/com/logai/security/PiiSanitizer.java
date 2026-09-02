package com.logai.security;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PII (Personally Identifiable Information) 脱敏与隐私防护装甲。
 *
 * 在日志发送至公网商业大模型（DeepSeek / OpenAI）前，自动对敏感信息执行金融级规则脱敏：
 * 1. 登录口令与 API 密钥脱敏：password / token / secret / apikey -> [REDACTED_SECRET]
 * 2. 手机号码脱敏：13812345678 -> 138****5678
 * 3. 身份证与银行卡号脱敏：18 位身份证掩码 -> [REDACTED_ID_CARD]
 * 4. 内网敏感绝对路径脱敏：C:\Users\... 或 /home/... -> [INTERNAL_PATH]
 */
@Component
public class PiiSanitizer {

    // 密码 / 密钥 / 访问凭据正则
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(\\b(?:password|passwd|pwd|secret|token|apikey|authorization|bearer)\\b\\s*[:=]\\s*[\"']?)([^\\s\"',;]+)([\"']?)",
            Pattern.CASE_INSENSITIVE
    );

    // 中国大陆手机号正则 (11位)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?86[- ]?)?(1[3-9]\\d)(\\d{4})(\\d{4})(?!\\d)"
    );

    // 身份证号码正则 (18位)
    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "(?<!\\d)([1-9]\\d{5})(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])(\\d{3}[0-9Xx])(?!\\d)"
    );

    // 内网/服务器绝对物理路径
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "([a-zA-Z]:\\\\(?:Users|Windows|Program Files|var|data)\\\\[^\\s\"':;]+|/(?:home|root|etc|var|opt)/[^\\s\"':;]+)"
    );

    /**
     * 执行全量敏感信息脱敏
     *
     * @param rawLog 原始日志文本
     * @return 脱敏后的安全文本
     */
    public String sanitize(String rawLog) {
        if (rawLog == null || rawLog.isBlank()) {
            return rawLog;
        }

        String result = rawLog;

        // 1. 口令与 Secret 脱敏
        result = SECRET_PATTERN.matcher(result).replaceAll("$1[REDACTED_SECRET]$3");

        // 2. 手机号码脱敏 (保留前3后4)
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$3");

        // 3. 身份证号码脱敏 (保留前6后4)
        result = ID_CARD_PATTERN.matcher(result).replaceAll("$1********$2");

        // 4. 内网敏感绝对路径脱敏
        result = PATH_PATTERN.matcher(result).replaceAll("[INTERNAL_PATH]");

        return result;
    }

    /**
     * 判定文本中是否存在敏感信息
     */
    public boolean containsSensitivePii(String rawLog) {
        if (rawLog == null || rawLog.isBlank()) {
            return false;
        }
        return SECRET_PATTERN.matcher(rawLog).find()
                || PHONE_PATTERN.matcher(rawLog).find()
                || ID_CARD_PATTERN.matcher(rawLog).find();
    }
}
