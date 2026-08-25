package com.logaudit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logaudit.dto.LoginRequest;
import com.logaudit.entity.User;
import com.logaudit.mapper.UserMapper;
import com.logaudit.security.JwtAuthFilter;
import com.logaudit.security.RedisRateLimiter;
import com.logaudit.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private RedisRateLimiter redisRateLimiter;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    private User sampleAdmin;
    private User disabledUser;

    @BeforeEach
    void setUp() {
        when(redisRateLimiter.isAllowed(anyString())).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);

        sampleAdmin = new User();
        sampleAdmin.setId(1L);
        sampleAdmin.setUsername("admin");
        sampleAdmin.setPassword(passwordEncoder.encode("admin123"));
        sampleAdmin.setRole("ADMIN");
        sampleAdmin.setEnabled(true);

        disabledUser = new User();
        disabledUser.setId(2L);
        disabledUser.setUsername("disabled");
        disabledUser.setPassword(passwordEncoder.encode("user123"));
        disabledUser.setRole("USER");
        disabledUser.setEnabled(false);
    }

    @Test
    void testLoginSuccessSetsCookie() throws Exception {
        when(userMapper.findByUsername("admin")).thenReturn(sampleAdmin);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(cookie().exists(JwtAuthFilter.COOKIE_NAME))
                .andExpect(cookie().httpOnly(JwtAuthFilter.COOKIE_NAME, true));
    }

    @Test
    void testLoginBlockedByRateLimiterReturns429() throws Exception {
        when(redisRateLimiter.isAllowed(anyString())).thenReturn(false);
        when(redisRateLimiter.getRemainingLockSeconds(anyString())).thenReturn(600L);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("锁定")));
    }

    @Test
    void testLoginWrongPasswordReturns401() throws Exception {
        when(userMapper.findByUsername("admin")).thenReturn(sampleAdmin);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testLoginDisabledUserReturns403() throws Exception {
        when(userMapper.findByUsername("disabled")).thenReturn(disabledUser);

        LoginRequest req = new LoginRequest();
        req.setUsername("disabled");
        req.setPassword("user123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testLogoutClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().maxAge(JwtAuthFilter.COOKIE_NAME, 0));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testMeWithAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void testMeUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}