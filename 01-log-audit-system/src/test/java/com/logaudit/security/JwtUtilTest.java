package com.logaudit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "auditvault-demo-secret-key-change-me-in-production-32bytes-min";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(secret, expirationMs, "auditvault", "auditvault-web");
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
        JwtUtil expiredUtil = new JwtUtil(secret, -1000, "auditvault", "auditvault-web");
        String token = expiredUtil.generateToken("expiredUser", "USER");

        assertNull(jwtUtil.getUsername(token));
        assertNull(jwtUtil.getRole(token));
        assertNull(jwtUtil.parse(token));
    }

    @Test
    void testTamperedTokenReturnsNull() {
        String token = jwtUtil.generateToken("admin", "ADMIN");
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertNull(jwtUtil.getUsername(tamperedToken));
        assertNull(jwtUtil.getRole(tamperedToken));
        assertNull(jwtUtil.parse(tamperedToken));
    }

    @Test
    void testTokenRejectedWhenIssuerMismatch() {
        Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        // Generate a token with a forged/external issuer
        String forgedToken = Jwts.builder()
                .setIssuer("malicious-issuer")
                .setAudience("auditvault-web")
                .setSubject("attacker")
                .claim("role", "ADMIN")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        assertNull(jwtUtil.parse(forgedToken));
        assertNull(jwtUtil.getUsername(forgedToken));
        assertNull(jwtUtil.getRole(forgedToken));
    }

    @Test
    void testTokenParsedOnceReturnsAllClaims() {
        String token = jwtUtil.generateToken("auditor", "AUDIT");
        Claims claims = jwtUtil.parse(token);

        assertNotNull(claims);
        assertEquals("auditor", claims.getSubject());
        assertEquals("AUDIT", claims.get("role", String.class));
        assertEquals("auditvault", claims.getIssuer());
        assertTrue(claims.getAudience().contains("auditvault-web"));
        assertNotNull(claims.getId()); // jti UUID exists
        assertFalse(claims.getId().isBlank());
    }
}
