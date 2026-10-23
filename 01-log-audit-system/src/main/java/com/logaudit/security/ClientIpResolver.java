package com.logaudit.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可信反向代理链 IP 解析器 (纯 JDK 实现，无第三方依赖)。
 *
 * <p>安全前提：{@code X-Forwarded-For} / {@code X-Real-IP} 是**客户端可任意伪造**的请求头。
 * 只有在"直连方 (remoteAddr) 本身是可信反向代理"时，才允许采信这些头；否则一律使用 remoteAddr。
 * 否则攻击者只需伪造 XFF 即可绕过登录防爆破限流、嫁祸他人 IP 触发自动封禁。</p>
 *
 * <p>解析顺序：remoteAddr 不在可信代理内 → 直接返回 remoteAddr；
 * 在可信代理内 → XFF 从右向左跳过可信代理，取第一个不可信地址。</p>
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private static final String HEADER_XFF = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final int MAX_IP_STR_LENGTH = 128;

    private final List<CidrMatcher> trustedMatchers;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxies) {
        List<CidrMatcher> matchers = new ArrayList<>();
        if (trustedProxies != null && !trustedProxies.isBlank()) {
            for (String part : trustedProxies.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    matchers.add(CidrMatcher.parse(trimmed));
                } catch (IllegalArgumentException e) {
                    log.warn("忽略非法的 trusted-proxies 配置段 [{}]: {}", trimmed, e.getMessage());
                }
            }
        }
        this.trustedMatchers = Collections.unmodifiableList(matchers);
        if (this.trustedMatchers.isEmpty()) {
            log.info("[CLIENT_IP_RESOLVER] 未配置可信代理，安全默认：不采信任何代理转发头，直接使用 remoteAddr");
        } else {
            log.info("[CLIENT_IP_RESOLVER] 已载入可信代理网段规则 {} 条", this.trustedMatchers.size());
        }
    }

    /**
     * 解析最终可信客户端 IP。任何异常输入都会安全降级为 remoteAddr，绝不抛异常。
     */
    public String resolve(HttpServletRequest request) {
        String rawRemoteAddr = request.getRemoteAddr();
        InetAddress remoteInet = parseIpAddress(rawRemoteAddr);
        if (remoteInet == null) {
            return rawRemoteAddr != null ? rawRemoteAddr : "";
        }

        // 直连方不是可信代理（或压根没配）→ 代理头一律不可信
        if (!isTrusted(remoteInet)) {
            return remoteInet.getHostAddress();
        }

        String xff = request.getHeader(HEADER_XFF);
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            String leftMostValid = null;

            // 从右向左：紧邻我们的那一跳最可信，遇到第一个不可信地址即为客户端
            for (int i = hops.length - 1; i >= 0; i--) {
                InetAddress hop = parseIpAddress(hops[i]);
                if (hop == null) {
                    continue;
                }
                leftMostValid = hop.getHostAddress();
                if (!isTrusted(hop)) {
                    return leftMostValid;
                }
            }
            if (leftMostValid != null) {
                return leftMostValid;
            }
        }

        String realIp = request.getHeader(HEADER_X_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            InetAddress realInet = parseIpAddress(realIp);
            if (realInet != null) {
                return realInet.getHostAddress();
            }
        }

        return remoteInet.getHostAddress();
    }

    private boolean isTrusted(InetAddress target) {
        for (CidrMatcher matcher : trustedMatchers) {
            if (matcher.matches(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清洗 IP 字面量：剥离端口、IPv6 方括号与多余空白；非法则返回空串。
     */
    static String sanitizeIpLiteral(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty() || s.length() > MAX_IP_STR_LENGTH) {
            return "";
        }

        // [2001:db8::1] 或 [2001:db8::1]:8080
        if (s.startsWith("[")) {
            int closing = s.indexOf(']');
            return closing > 1 ? s.substring(1, closing) : "";
        }

        // 192.168.1.1:8080（仅一个冒号才视为 IPv4:端口；多个冒号是 IPv6）
        int firstColon = s.indexOf(':');
        if (firstColon != -1 && firstColon == s.lastIndexOf(':')) {
            return s.substring(0, firstColon);
        }
        return s;
    }

    /**
     * 严格解析 IP 字面量，杜绝 InetAddress.getByName 触发 DNS 解析。
     * IPv4 逐段校验 0-255；IPv6 只允许十六进制与冒号（字面量不会走 DNS）。
     */
    static InetAddress parseIpAddress(String raw) {
        String s = sanitizeIpLiteral(raw);
        if (s.isEmpty()) {
            return null;
        }

        if (s.indexOf(':') >= 0) {
            if (!s.matches("[0-9a-fA-F:.]+")) {
                return null;
            }
            try {
                return InetAddress.getByName(s);
            } catch (UnknownHostException e) {
                return null;
            }
        }

        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }
            if (value < 0 || value > 255) {
                return null;
            }
            bytes[i] = (byte) value;
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * CIDR / 精确 IP 匹配器，按字节逐位比对掩码。
     */
    static final class CidrMatcher {

        private final byte[] networkBytes;
        private final int prefixLength;

        private CidrMatcher(byte[] networkBytes, int prefixLength) {
            this.networkBytes = networkBytes;
            this.prefixLength = prefixLength;
        }

        static CidrMatcher parse(String notation) {
            String clean = notation.trim();
            String ipPart = clean;
            int prefix = -1;

            int slash = clean.indexOf('/');
            if (slash >= 0) {
                ipPart = clean.substring(0, slash);
                try {
                    prefix = Integer.parseInt(clean.substring(slash + 1).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("非法的 CIDR 前缀长度");
                }
            }

            InetAddress base = parseIpAddress(ipPart);
            if (base == null) {
                throw new IllegalArgumentException("非法的 IP 地址: " + ipPart);
            }

            byte[] bytes = base.getAddress();
            int maxBits = bytes.length * 8;
            if (prefix < 0) {
                prefix = maxBits;
            }
            if (prefix > maxBits) {
                throw new IllegalArgumentException("前缀掩码超出 " + maxBits + " 位: " + prefix);
            }

            applyMask(bytes, prefix);
            return new CidrMatcher(bytes, prefix);
        }

        boolean matches(InetAddress target) {
            if (target == null) {
                return false;
            }
            byte[] targetBytes = target.getAddress();
            if (targetBytes.length != networkBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (targetBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            int remainingBits = prefixLength % 8;
            if (remainingBits > 0) {
                int mask = (0xFF00 >> remainingBits) & 0xFF;
                return (targetBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
            }
            return true;
        }

        private static void applyMask(byte[] bytes, int prefix) {
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;

            if (remainingBits > 0 && fullBytes < bytes.length) {
                int mask = (0xFF00 >> remainingBits) & 0xFF;
                bytes[fullBytes] = (byte) (bytes[fullBytes] & mask);
                fullBytes++;
            }
            for (int i = fullBytes; i < bytes.length; i++) {
                bytes[i] = 0;
            }
        }
    }
}
