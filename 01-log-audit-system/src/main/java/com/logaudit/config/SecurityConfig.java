package com.logaudit.config;

import com.logaudit.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * 安全配置 — 无状态 JWT 认证 + 角色鉴权。
 *
 * 规则：
 * - 静态资源、登录/登出接口：放行
 * - /actuator/health 探活接口允许匿名，其余运维监控端点收口至 ADMIN 角色
 * - /swagger-ui/**、/v3/api-docs/** 根据 app.api-docs.public 判定（默认仅 ADMIN）
 * - /api/logs/** 查询接口：需要登录（任意角色）
 * - 导入 / 导出：仅 ADMIN
 * - 未认证访问 API：返回 401 JSON；越权访问返回 403 JSON
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.api-docs.public:false}")
    private boolean apiDocsPublic;

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
                .authorizeHttpRequests(auth -> {
                    // 静态资源（WebSocket 通道 /ws/** 不在此列：威胁告警含内网 IP 与攻击载荷，必须登录后才可订阅）
                    auth.requestMatchers("/", "/index.html", "/dashboard.html", "/detail.html",
                            "/style.css", "/app.js", "/login.html", "/login.js",
                            "/favicon.ico", "/error").permitAll();

                    // 认证接口与 Webhook 采集接口（Webhook 内部做专用令牌与防重放校验）
                    auth.requestMatchers("/api/auth/login", "/api/auth/logout").permitAll();
                    auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/logs/webhook").permitAll();

                    // Actuator 探活接口允许匿名，其余运维度量端点收口至 ADMIN 角色
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    auth.requestMatchers("/actuator/**").hasRole("ADMIN");

                    // Swagger API 文档接口：根据配置动态决定是否公开暴露（生产环境默认 false，需 ADMIN 角色）
                    if (apiDocsPublic) {
                        auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();
                    } else {
                        auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").hasRole("ADMIN");
                    }

                    // 写操作仅 ADMIN（批量导入、导出）
                    auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/logs/batch-import").hasRole("ADMIN");
                    auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/logs/export").hasRole("ADMIN");

                    // 其余 API 需要登录认证
                    auth.anyRequest().authenticated();
                })
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
