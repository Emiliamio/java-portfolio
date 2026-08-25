package com.logaudit.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "auditvault-demo-secret-key-change-me-in-production-32bytes-min";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(secret, expirationMs);
    }

    @Test
    void testGenerateAndParseToken() {
        String token = jwtUtil.generateToken("admin", "ADMIN");
        assertNotNull(token);
        assertFalse(token.isBlank());

        assertEquals("admin", jwtUtil.getUsername(token));
        assertEquals("ADMIN", jwtUtil.getRole(token));
    }

    @Test
    void testUserRoleToken() {
        String token = jwtUtil.generateToken("testuser", "USER");
        assertEquals("testuser", jwtUtil.getUsername(token));
        assertEquals("USER", jwtUtil.getRole(token));
    }

    @Test
    void testInvalidTokenReturnsNull() {
        assertNull(jwtUtil.getUsername("invalid.token.string"));
        assertNull(jwtUtil.getRole("invalid.token.string"));
    }

    @Test
    void testExpiredTokenReturnsNull() {
        JwtUtil expiredUtil = new JwtUtil(secret, -1000);
        String token = expiredUtil.generateToken("expiredUser", "USER");

        assertNull(jwtUtil.getUsername(token));
        assertNull(jwtUtil.getRole(token));
    }

    @Test
    void testTamperedTokenReturnsNull() {
        String token = jwtUtil.generateToken("admin", "ADMIN");
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertNull(jwtUtil.getUsername(tamperedToken));
        assertNull(jwtUtil.getRole(tamperedToken));
    }
}