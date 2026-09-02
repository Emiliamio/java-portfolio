# Nexus AI — 企业级日志安全研判与响应副驾驶 (Security Copilot Studio)

> 大语言模型驱动的企业级日志安全研判、CVSS 3.1 评分、MITRE ATT&CK 攻击链路推演与自动化防御剧本生成系统。

深度对标 **Microsoft Security Copilot** 与 **CrowdStrike Charlotte AI**，提供端到端从威胁分析到应急响应剧本下发的完整闭环。

---

## 🌟 核心功能特性

| 维度 | 功能说明 |
|:---|:---|
| **全视口 Copilot Studio** | 2-Pane 工业级暗色控制台，内置 Monaco 级行号高亮编辑器与 Token 预估器 |
| **云端/本地三级热备路由** | 优先调用 DeepSeek/OpenAI；异常时自动秒级切换至本地私有化 Ollama (Qwen/DeepSeek) 或内置规则引擎 |
| **100% 离线隐私盾 (Air-Gapped)** | 支持完全脱网物理隔离环境，敏感日志数据不出本地局域网，满足金融级安全合规 |
| **CVSS 3.1 威胁评分** | 自动输出专业级严重程度评级与矢量字符串（如 `CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H (9.8)`） |
| **MITRE ATT&CK 攻击链路推演** | 5 阶段 Kill Chain（初始访问 $\rightarrow$ 命令执行 $\rightarrow$ 权限持久化 $\rightarrow$ 防御规避 $\rightarrow$ 敏感外发）动态点亮 |
| **自动化安全防御剧本 (Playbooks)** | 动态生成多维防御配置：Nginx WAF 规则、Linux iptables 指令、Sigma SIEM 告警规则 (YAML) |
| **企业安全应急研判报告** | 自动排版生成规范化《企业安全事件应急响应研判报告》，支持 Markdown 一键下载与 PDF 导出 |

---

## 🛠️ 技术栈

- **后端核心**：Spring Boot 3.2.0 + Java 17 / 21 LTS
- **模型路由**：云端 API (DeepSeek/OpenAI) + 本地私有化 Ollama + 离线规则引擎三级容灾
- **流式通信**：JDK 原生 `HttpClient` + SSE (Server-Sent Events) 打字机流式长连接
- **认证与权限**：Spring Security RBAC + JWT 跨站安全认证
- **单元测试**：JUnit 5 (9/9 自动化测试用例通过)

---

## 🧪 单元测试

```bash
mvn clean test
```

---

## 🚀 快速启动

1. **一键 Docker Compose 启动**：
   ```bash
   cd .. && docker compose up -d
   ```
2. **访问工作台**：
   - Copilot Studio：`http://localhost:8081` (账号: `admin` / 密码: `admin123`)
   - OpenAPI 文档：`http://localhost:8081/swagger-ui/index.html`
