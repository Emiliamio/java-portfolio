package com.logai;

import com.logai.security.PiiSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PiiSanitizer 单元测试 — 验证敏感口令、手机号、身份证和内网路径脱敏。
 */
class PiiSanitizerTest {

    private final PiiSanitizer sanitizer = new PiiSanitizer();

    @Test
    void testPasswordRedaction() {
        String log = "2026-09-02 10:00:00 INFO User login attempt: username=admin, password=SuperSecret123! from 1.2.3.4";
        String sanitized = sanitizer.sanitize(log);

        assertFalse(sanitized.contains("SuperSecret123!"));
        assertTrue(sanitized.contains("[REDACTED_SECRET]"));
        assertTrue(sanitized.contains("username=admin"));
    }

    @Test
    void testPhoneNumberMasking() {
        String log = "User verification SMS sent to 13812345678, status=SUCCESS";
        String sanitized = sanitizer.sanitize(log);

        assertFalse(sanitized.contains("13812345678"));
        assertTrue(sanitized.contains("138****5678"));
    }

    @Test
    void testIdCardMasking() {
        String log = "Identity check for citizen_id=440106199001011234 passed";
        String sanitized = sanitizer.sanitize(log);

        assertFalse(sanitized.contains("440106199001011234"));
        assertTrue(sanitized.contains("440106********1234"));
    }

    @Test
    void testInternalPathMasking() {
        String log = "File uploaded to /home/app/data/secure_report.pdf successfully";
        String sanitized = sanitizer.sanitize(log);

        assertFalse(sanitized.contains("/home/app/data/secure_report.pdf"));
        assertTrue(sanitized.contains("[INTERNAL_PATH]"));
    }

    @Test
    void testCleanLogUntouched() {
        String cleanLog = "2026-09-02 12:00:00 INFO System health check OK, latency=2ms";
        String sanitized = sanitizer.sanitize(cleanLog);

        assertEquals(cleanLog, sanitized);
        assertFalse(sanitizer.containsSensitivePii(cleanLog));
    }
}
