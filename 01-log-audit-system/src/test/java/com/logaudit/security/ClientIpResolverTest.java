package com.logaudit.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("可信代理链 IP 解析与 XFF 防伪造测试")
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr, String xff) {
        return request(remoteAddr, xff, null);
    }

    private static MockHttpServletRequest request(String remoteAddr, String xff, String realIp) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        if (xff != null) {
            req.addHeader("X-Forwarded-For", xff);
        }
        if (realIp != null) {
            req.addHeader("X-Real-IP", realIp);
        }
        return req;
    }

    @Test
    @DisplayName("未配置可信代理时，伪造 X-Forwarded-For 不生效，只认 remoteAddr")
    void shouldIgnoreSpoofedXffWhenNoTrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest req = request("203.0.113.7", "1.1.1.1, 8.8.8.8", "9.9.9.9");

        assertEquals("203.0.113.7", resolver.resolve(req));
    }

    @Test
    @DisplayName("直连方不在可信代理网段时，同样忽略代理头")
    void shouldIgnoreXffWhenRemoteAddrNotTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest req = request("203.0.113.7", "1.1.1.1");

        assertEquals("203.0.113.7", resolver.resolve(req));
    }

    @Test
    @DisplayName("可信代理后面取 XFF 最右侧不可信地址（防止左侧伪造）")
    void shouldTakeRightMostUntrustedHop() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        // 攻击者可在最左侧塞入任意伪造值，真实来源是最右侧第一个不可信跳
        MockHttpServletRequest req = request("10.0.0.1", "1.1.1.1, 198.51.100.23");

        assertEquals("198.51.100.23", resolver.resolve(req));
    }

    @Test
    @DisplayName("多层可信代理链逐跳跳过可信段")
    void shouldSkipAllTrustedHops() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8, 192.168.0.0/16");
        MockHttpServletRequest req = request("10.0.0.1", "198.51.100.23, 192.168.1.5, 10.0.0.2");

        assertEquals("198.51.100.23", resolver.resolve(req));
    }

    @Test
    @DisplayName("链路全部为可信代理时返回最左侧地址")
    void shouldReturnLeftMostWhenAllHopsTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest req = request("10.0.0.1", "10.0.0.5, 10.0.0.3");

        assertEquals("10.0.0.5", resolver.resolve(req));
    }

    @Test
    @DisplayName("无 XFF 时回退 X-Real-IP（仅限可信代理场景）")
    void shouldFallbackToRealIp() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest req = request("10.0.0.1", null, "198.51.100.9");

        assertEquals("198.51.100.9", resolver.resolve(req));
    }

    @Test
    @DisplayName("非法/带端口的 XFF 值被安全清洗，不抛异常")
    void shouldHandleGarbageHeadersSafely() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");

        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "unknown", null)));
        assertEquals("198.51.100.9", resolver.resolve(request("10.0.0.1", "198.51.100.9:8443", null)));
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "999.1.1.1", null)));
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "", null)));
    }

    @Test
    @DisplayName("IPv6 精确地址与 CIDR 网段均可匹配")
    void shouldSupportIpv6() {
        ClientIpResolver resolver = new ClientIpResolver("2001:db8::/32, ::1");
        // InetAddress#getHostAddress 返回非压缩形式，断言按规范形式书写
        assertEquals("2001:db8:1234:0:0:0:0:9", resolver.resolve(request("2001:db8::1", "2001:db8:1234::9")));
        assertEquals("0:0:0:0:0:0:0:1", resolver.resolve(request("0:0:0:0:0:0:0:1", null)));
    }

    @Test
    @DisplayName("CIDR 边界：/8 覆盖 10.0.0.0-10.255.255.255，不含 11.x")
    void shouldMatchCidrBoundaries() {
        ClientIpResolver.CidrMatcher matcher = ClientIpResolver.CidrMatcher.parse("10.0.0.0/8");

        assertTrue(matcher.matches(ClientIpResolver.parseIpAddress("10.0.0.1")));
        assertTrue(matcher.matches(ClientIpResolver.parseIpAddress("10.255.255.255")));
        assertFalse(matcher.matches(ClientIpResolver.parseIpAddress("11.0.0.0")));
        assertFalse(matcher.matches(ClientIpResolver.parseIpAddress("192.168.1.1")));
    }

    @Test
    @DisplayName("非法 trusted-proxies 配置段被忽略，不影响其余规则")
    void shouldIgnoreInvalidCidrConfig() {
        ClientIpResolver resolver = new ClientIpResolver("bad-value, 10.0.0.0/8");
        assertEquals("198.51.100.23", resolver.resolve(request("10.0.0.1", "198.51.100.23")));
    }
}
