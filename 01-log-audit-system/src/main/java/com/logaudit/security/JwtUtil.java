package com.logaudit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类 — 生成 / 解析令牌。
 *
 * 令牌安全加固：
 * 1. 强制校验 issuer 与 audience，防跨系统伪造复用；
 * 2. 携带唯一 jti (UUID) 用于令牌审计与黑名单精细化吊销；
 * 3. parse(token) 单次解析提取全部 Claims，消除单请求 3 次重复解密开销；
 * 4. httpOnly Cookie 下发，结合可配置 secureCookie 防窃取。
 */
@Slf4j
@Component
public class JwtUtil {

    private final Key key;
    private final long expirationMs;
    private final String issuer;
    private final String audience;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${app.jwt.issuer:auditvault}") String issuer,
            @Value("${app.jwt.audience:auditvault-web}") String audience
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.issuer = (issuer != null && !issuer.isBlank()) ? issuer : "auditvault";
        this.audience = (audience != null && !audience.isBlank()) ? audience : "auditvault-web";
    }

    public JwtUtil(String secret, long expirationMs) {
        this(secret, expirationMs, "auditvault", "auditvault-web");
    }

    /**
     * 生成包含 jti、issuer、audience 的标准化 JWT 令牌。
     */
    public String generateToken(String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setIssuer(issuer)
                .setAudience(audience)
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 统一单次解析校验令牌。校验签名、过期时间、issuer 及 audience。
     * 异常统一捕获转 null，避免向调用方泄露解密细节。
     */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.debug("JWT verification failed: {}", e.getMessage());
            return null;
        }
    }

    /** 解析令牌，返回用户名。非法或过期返回 null。 */
    public String getUsername(String token) {
        Claims claims = parse(token);
        return claims != null ? claims.getSubject() : null;
    }

    /** 解析令牌中的角色。 */
    public String getRole(String token) {
        Claims claims = parse(token);
        return claims != null ? claims.get("role", String.class) : null;
    }

    /** 获取令牌剩余有效时间（毫秒）。非法或已过期返回 0。 */
    public long getRemainingExpirationMs(String token) {
        Claims claims = parse(token);
        if (claims == null || claims.getExpiration() == null) {
            return 0;
        }
        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}
