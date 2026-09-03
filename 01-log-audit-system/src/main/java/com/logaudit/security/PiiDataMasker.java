package com.logaudit.security;

import com.logaudit.entity.LogEntry;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AuditVault 金融合规入库级 PII (Personally Identifiable Information) 敏感脱敏装甲
 * 符合等保三级、GDPR 与《个人信息保护法 (PIPL)》落地合规要求：
 * 在外部日志推入 Kafka、写入 MySQL 或 ClickHouse 物理存储前，执行全自动切面规则脱敏：
 * 1. 登录口令与 API 凭据：password / token / secret / apikey -> [REDACTED_SECRET]
 * 2. 手机号码：13812345678 -> 138****5678
 * 3. 身份证件号：440106199001011234 -> 440106********1234
 * 4. 银行卡号：6222021234567890123 -> 622202********0123
 * 5. 服务器内网敏感路径：/home/app/... -> [INTERNAL_PATH]
 */
@Component
public class PiiDataMasker {

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(\\b(?:password|passwd|pwd|secret|token|apikey|authorization|bearer)\\b\\s*[:=]\\s*[\"']?)([^\\s\"',;]+)([\"']?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?86[- ]?)?(1[3-9]\\d)(\\d{4})(\\d{4})(?!\\d)"
    );

    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
            "(?<!\\d)([1-9]\\d{5})(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])(\\d{3}[0-9Xx])(?!\\d)"
    );

    private static final Pattern BANK_CARD_PATTERN = Pattern.compile(
            "(?<!\\d)(62[0-9]{4})(\\d{8,11})(\\d{4})(?!\\d)"
    );

    private static final Pattern PATH_PATTERN = Pattern.compile(
            "([a-zA-Z]:\\\\(?:Users|Windows|Program Files|var|data)\\\\[^\\s\"':;]+|/(?:home|root|etc|var|opt)/[^\\s\"':;]+)"
    );

    /**
     * 对文本执行入库级金融脱敏
     */
    public String mask(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }

        // 1. 口令凭据脱敏
        Matcher secretMatcher = SECRET_PATTERN.matcher(raw);
        raw = secretMatcher.replaceAll("$1[REDACTED_SECRET]$3");

        // 2. 手机号脱敏
        Matcher phoneMatcher = PHONE_PATTERN.matcher(raw);
        raw = phoneMatcher.replaceAll("$1****$3");

        // 3. 身份证号脱敏
        Matcher idCardMatcher = ID_CARD_PATTERN.matcher(raw);
        raw = idCardMatcher.replaceAll("$1********$2");

        // 4. 银行卡号脱敏
        Matcher bankCardMatcher = BANK_CARD_PATTERN.matcher(raw);
        raw = bankCardMatcher.replaceAll("$1********$3");

        // 5. 绝对敏感路径脱敏
        Matcher pathMatcher = PATH_PATTERN.matcher(raw);
        raw = pathMatcher.replaceAll("[INTERNAL_PATH]");

        return raw;
    }

    /**
     * 对 LogEntry 实体执行全量入库清洗
     */
    public void maskLogEntry(LogEntry entry) {
        if (entry != null && entry.getDetail() != null) {
            entry.setDetail(mask(entry.getDetail()));
        }
    }
}