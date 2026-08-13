package com.logaudit.controller;

import com.logaudit.dto.LoginRequest;
import com.logaudit.entity.User;
import com.logaudit.mapper.UserMapper;
import com.logaudit.security.JwtAuthFilter;
import com.logaudit.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口 — 登录 / 登出 / 当前用户信息。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        User user = userMapper.findByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
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
    public ResponseEntity<Map<String, Object>> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(JwtAuthFilter.COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 返回当前登录用户信息（前端刷新时恢复状态用）。 */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestAttribute(value = "username", required = false) String username,
            @RequestAttribute(value = "role", required = false) String role) {
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("success", false));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "username", username,
                "role", role
        ));
    }
}
