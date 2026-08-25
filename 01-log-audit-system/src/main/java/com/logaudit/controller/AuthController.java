package com.logaudit.controller;

import com.logaudit.dto.LoginRequest;
import com.logaudit.entity.User;
import com.logaudit.mapper.UserMapper;
import com.logaudit.security.JwtAuthFilter;
import com.logaudit.security.JwtUtil;
import com.logaudit.security.RedisRateLimiter;
import com.logaudit.security.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口 — 登录 / 登出 / 当前用户信息。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证鉴权", description = "用户登录、登出及当前身份会话接口")
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisRateLimiter redisRateLimiter;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "校验账号密码、执行 Redis 防爆破频控检查并写入 httpOnly Cookie")
    public ResponseEntity<Map<String, Object>> login(

            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        String clientIp = clientIp(httpRequest);

        // Redis 防爆破频次限流检查
        if (!redisRateLimiter.isAllowed(clientIp)) {
            long remainingSec = redisRateLimiter.getRemainingLockSeconds(clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "success", false,
                    "message", "登录失败次数过多，IP已被锁定，请 " + (remainingSec / 60 + 1) + " 分钟后再试"
            ));
        }

        User user = userMapper.findByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            redisRateLimiter.recordFailure(clientIp);
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "用户名或密码错误"
            ));
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "message", "账号已被禁用"
            ));
        }

        // 登录成功，重置失败计数
        redisRateLimiter.recordSuccess(clientIp);

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

        // httpOnly Cookie：前端 JS 读不到，防 XSS 窃取
        Cookie cookie = new Cookie(JwtAuthFilter.COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(86400); // 24h
        cookie.setSecure(false); // 本地 HTTP；生产 HTTPS 时置 true
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("username", user.getUsername());
        body.put("role", user.getRole());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        // 获取当前 Token 并加入 Redis 黑名单，彻底吊销令牌
        String token = extractToken(request);
        if (token != null) {
            long remainingMs = jwtUtil.getRemainingExpirationMs(token);
            tokenBlacklistService.blacklistToken(token, remainingMs);
        }

        Cookie cookie = new Cookie(JwtAuthFilter.COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (JwtAuthFilter.COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 返回当前登录用户信息（前端刷新时恢复状态用）。 */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登录或登录已过期"));
        }
        String username = auth.getName();
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .orElse("USER");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "username", username,
                "role", role
        ));
    }
}
