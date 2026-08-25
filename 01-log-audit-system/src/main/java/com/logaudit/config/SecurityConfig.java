package com.logaudit.config;

import com.logaudit.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 安全配置 — 无状态 JWT 认证 + 角色鉴权。
 *
 * 规则：
 * - 静态资源、登录/登出接口：放行
 * - /api/logs/** 查询接口：需要登录（任意角色）
 * - 导入 / 导出：仅 ADMIN
 * - 未认证访问 API：返回 401 JSON
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 静态资源放行
                        .requestMatchers("/", "/index.html", "/dashboard.html", "/detail.html",
                                "/style.css", "/app.js", "/login.html", "/login.js",
                                "/favicon.ico", "/error").permitAll()
                        // 认证接口与 Webhook 采集接口放行（Webhook 内部做 X-Audit-Token 专用令牌鉴权）
                        .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/logs/webhook").permitAll()
                        // 写操作仅 ADMIN（批量导入、导出）
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/logs/batch-import").hasRole("ADMIN")

                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/logs/export").hasRole("ADMIN")
                        // 其余 API 需要登录
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false,\"message\":\"未登录或登录已过期\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\":false,\"message\":\"权限不足\"}");
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
