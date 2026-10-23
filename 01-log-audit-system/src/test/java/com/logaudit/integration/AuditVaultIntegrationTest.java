package com.logaudit.integration;

import com.logaudit.dto.LoginRequest;
import com.logaudit.entity.LogEntry;
import com.logaudit.mapper.LogEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuditVault 真实中间件全链路集成冒烟测试。
 *
 * 验证链路：真实 MySQL 8.0 容器 (Flyway 增量执行) + Redis 7 容器协同 ->
 *   1. POST /api/auth/login 校验管理员登录与 httpOnly Cookie 下发；
 *   2. GET /api/logs?page=1&pageSize=5 带 Cookie 查询审计日志列表 (total >= 1)；
 *   3. GET /api/logs/export 带 Cookie 触发流式 Excel 导出，验证 Content-Type 与 Zip 魔数 PK (0x50 0x4B)。
 *
 * 容灾保证：无 Docker 环境自动跳过 (disabledWithoutDocker = true)，保证离线环境构建不红。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditVaultIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("log_audit")
            .withUsername("root")
            .withPassword("root123456");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LogEntryMapper logEntryMapper;

    @BeforeEach
    void seedBaselineLogIfEmpty() {
        LogEntry entry = new LogEntry();
        entry.setTimestamp(LocalDateTime.now());
        entry.setIpAddress("127.0.0.1");
        entry.setUsername("admin");
        entry.setOperation("USER_LOGIN");
        entry.setOperationResult("SUCCESS");
        entry.setDetail("Integration test baseline entry");
        entry.setSeverity("INFO");
        entry.setSourceFile("AuditVaultIntegrationTest.java");
        entry.setTraceId("it-trace-001");
        logEntryMapper.batchInsert(List.of(entry));
    }

    @Test
    @DisplayName("端到端全链路冒烟：登录拿到 Cookie -> 查询日志列表 -> 导出 XLSX 校验 PK 幻数")
    void testFullAuditLifecycleSmoke() {
        // 1. 登录拿 Cookie
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("admin123");

        ResponseEntity<Map<String, Object>> loginResp = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginRequest),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertEquals(HttpStatus.OK, loginResp.getStatusCode(), "Login should return HTTP 200");
        assertNotNull(loginResp.getBody());
        assertEquals(true, loginResp.getBody().get("success"));

        String cookieHeader = loginResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookieHeader, "Login response must contain Set-Cookie header");
        assertTrue(cookieHeader.contains("auditvault_token="), "Cookie must contain auditvault_token");

        // 提取纯 Cookie 键值传递给后续请求
        String cookieVal = cookieHeader.split(";")[0];
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.add(HttpHeaders.COOKIE, cookieVal);

        // 2. 带 Cookie 查询日志列表
        ResponseEntity<Map<String, Object>> queryResp = restTemplate.exchange(
                "/api/logs?page=1&pageSize=5",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertEquals(HttpStatus.OK, queryResp.getStatusCode(), "Query logs should return HTTP 200");
        assertNotNull(queryResp.getBody());
        Object totalObj = queryResp.getBody().get("total");
        assertNotNull(totalObj, "Query response should contain total count");
        long total = ((Number) totalObj).longValue();
        assertTrue(total >= 1, "Total logs should be >= 1 after seeding");

        // 3. 带 Cookie 导出 Excel 并验证 XLSX/ZIP 幻数 PK (0x50, 0x4B)
        ResponseEntity<byte[]> exportResp = restTemplate.exchange(
                "/api/logs/export",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                byte[].class
        );

        assertEquals(HttpStatus.OK, exportResp.getStatusCode(), "Export should return HTTP 200");
        MediaType contentType = exportResp.getHeaders().getContentType();
        assertNotNull(contentType);
        assertTrue(
                contentType.toString().contains("spreadsheetml") || contentType.toString().contains("octet-stream") || contentType.toString().contains("xlsx"),
                "Content-Type should be openxmlformats spreadsheetml"
        );

        byte[] body = exportResp.getBody();
        assertNotNull(body, "Export response body must not be null");
        assertTrue(body.length > 100, "Exported excel file should be non-empty");
        // ZIP/XLSX file magic bytes: 'P' (0x50), 'K' (0x4B)
        assertEquals((byte) 0x50, body[0], "First magic byte must be 'P' (0x50)");
        assertEquals((byte) 0x4B, body[1], "Second magic byte must be 'K' (0x4B)");
    }
}
