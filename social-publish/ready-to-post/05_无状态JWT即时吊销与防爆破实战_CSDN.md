# Spring Security 6 + Redis 7：无状态 JWT 优雅吊销与防爆破实战方案

**版权声明**：本文为博主「郑锦城 (Emiliamio)」的原创文章，遵循 CC 4.0 BY-SA 版权协议。  
**源码地址**：[https://github.com/Emiliamio/agent-forge](https://github.com/Emiliamio/agent-forge) 与 [https://github.com/Emiliamio/java-portfolio](https://github.com/Emiliamio/java-portfolio)  
**分类专栏**：企业级架构设计 / Java 21 / AI Agent 与大模型 / 高并发性能调优  

---

> JWT（JSON Web Token）凭借自包含与无状态的特性，成为了分布式系统鉴权的事实标准。  
> 但“无状态”本身是一把双刃剑：**Token 一旦签发并在有效期内，服务端无法直接废止它**。若用户点击登出、修改密码或凭证泄露，如何实现即时安全注销？  
> 本文深度拆解 AuditVault 的鉴权体系：**HttpOnly Cookie + Redis 动态黑名单 + Fail-Open 降级** 的生产级安全架构。

---

## 🚫 一、常见 JWT 登出方案的缺陷

1. **纯前端丢弃 Token**：仅在浏览器清除 `localStorage`，若 Token 曾被拦截或被 XSS 窃取，攻击者在过期前仍可肆意访问 API；
2. **数据库全量白名单**：每次鉴权均查询数据库检查 Token 是否有效，彻底打破了无状态设计，导致数据库成为高并发瓶颈；
3. **全局版本号（User Version）**：用户登出时递增用户的 Token 版本号，会导致该用户在所有终端（手机、Pad、PC）全部被强制下线，无法支持单设备登出。

---

## ⚡ 二、AuditVault 架构：Redis 精准 TTL 黑名单

AuditVault 采用“**仅记录已吊销 Token**”的轻量黑名单策略，兼顾无状态性能与即时注销安全：

```
[ 用户点击注销 / 登出 ]
       │
       ▼
1. 后端解析 JWT，计算剩余生命周期：
   TTL = ExpireTime - CurrentTime (如剩余 1800 秒)
       │
       ▼
2. 计算 Token 哈希：key = "token:blacklist:" + SHA256(rawToken)
       │
       ▼
3. 写入 Redis 并设置动态过期时间：
   redis.set(key, "revoked", TTL, TimeUnit.SECONDS)
       │
       ▼
[ Token 到期后，Redis 自动驱逐淘汰，容量永不膨胀！ ]
```

### 1. 核心代码实现
```java
@Service
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    public void revokeToken(String token, long expirationTimeMs) {
        long remainingTtl = expirationTimeMs - System.currentTimeMillis();
        if (remainingTtl > 0) {
            String hash = sha256(token);
            redisTemplate.opsForValue().set("audit:blacklist:" + hash, "revoked", remainingTtl, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isRevoked(String token) {
        try {
            String hash = sha256(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey("audit:blacklist:" + hash));
        } catch (Exception e) {
            // Fail-Open 容灾：Redis 故障时记录告警并放行合法签名的 Token，避免全站瘫痪
            log.warn("Redis blacklist check failed, fallback to signature valid: {}", e.getMessage());
            return false;
        }
    }
}
```

---

## 🛡️ 三、传输层安全：HttpOnly + SameSite=Strict Cookie

为了彻底消灭 XSS 窃取 Token 的可能性，系统不使用 `Authorization: Bearer <token>` 头部传递，改用由服务端 Set-Cookie 写入的凭证：

```java
ResponseCookie cookie = ResponseCookie.from("access_token", token)
        .httpOnly(true)            // 禁止 JavaScript 读取 (document.cookie 无法获取)
        .secure(false)             // 本地开发 false，生产环境启用 HTTPS 时为 true
        .path("/")
        .maxAge(86400)
        .sameSite("Strict")        // 严格同源，杜绝 CSRF 跨站伪造请求
        .build();
response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
```

---

## 🚦 四、Spring Security 6 过滤器链无缝集成

在 `JwtAuthFilter` 中，请求到达 Controller 前完成双重校验：
1. **密码学验签**：验证 HMAC-SHA256 签名与未过期；
2. **黑名单比对**：查询 Redis 确认未被吊销。

若任一校验不通过，立即返回 `401 Unauthorized`，阻断非法访问。

---

## 📊 五、架构性能与内存开销评估

| 指标 | 传统 Session / 数据库方案 | AuditVault (Redis 黑名单) |
|---|---|---|
| **正常请求鉴权开销** | 数据库 I/O (5~15ms) | Redis $O(1)$ 内存查询 (< 0.5ms) |
| **黑名单内存占用** | 随全量用户线性增长 (数十 GB) | **仅暂存已登出且未过期的 Token，自动过期归零** |
| **单设备即时登出** | 不支持或逻辑复杂 | **原生完美支持** |
| **容灾特性** | 数据库宕机全站瘫痪 | **内置 Fail-Open 降级，保障业务可用性** |

---

## 🎯 六、总结

通过将 **HttpOnly Cookie** 的防 XSS 屏障与 **Redis 动态 TTL 黑名单** 结合，AuditVault 在保持微服务无状态高性能的同时，完美解决了 JWT 的即时注销难题。

---

## 🎯 总结与项目源码获取

全套系统工程已实现 **211 项自动化测试 100% 真实绿灯通过**，拒绝任何虚假假功能：
* **AgentForge 核心仓库**：[https://github.com/Emiliamio/agent-forge](https://github.com/Emiliamio/agent-forge)
* **AuditVault 审计仓库**：[https://github.com/Emiliamio/java-portfolio](https://github.com/Emiliamio/java-portfolio)
* **在线博客展厅**：[https://emiliamio.github.io](https://emiliamio.github.io)
* **联系作者**：`mio2110767128@163.com`

**欢迎大家在 GitHub 点亮 Star ⭐️ 关注！**
