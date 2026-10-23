package com.logaudit.security;

import com.logaudit.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebSocket 实时威胁告警通道的鉴权校验。
 *
 * 该端点推送内网 IP、操作类型与攻击载荷原文，不允许匿名订阅。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("WebSocket 威胁告警通道必须登录后才可握手")
class WebSocketSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private RedisRateLimiter redisRateLimiter;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    @DisplayName("匿名握手 /ws/threat-alerts 必须返回 401")
    void anonymousWebSocketHandshakeIsRejected() throws Exception {
        mockMvc.perform(get("/ws/threat-alerts")
                        .header("Upgrade", "websocket")
                        .header("Connection", "Upgrade")
                        .header("Sec-WebSocket-Version", "13")
                        .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ=="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("伪造 JWT Cookie 的握手同样被拒绝")
    void forgedTokenHandshakeIsRejected() throws Exception {
        mockMvc.perform(get("/ws/threat-alerts")
                        .header("Upgrade", "websocket")
                        .header("Connection", "Upgrade")
                        .cookie(new jakarta.servlet.http.Cookie("auditvault_token", "forged.jwt.value")))
                .andExpect(status().isUnauthorized());
    }
}
