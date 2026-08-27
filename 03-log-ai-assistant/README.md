# Nexus AI — 企业级日志安全研判与响应副驾驶 (Security Copilot Studio)

> 大语言模型驱动的企业级日志安全研判、CVSS 3.1 评分、MITRE ATT&CK 攻击链路推演与自动化防御剧本生成系统。

深度对标 **Microsoft Security Copilot** 与 **CrowdStrike Charlotte AI**，提供端到端从威胁分析到应急响应剧本下发的完整闭环。

---

## 🌟 核心功能特性

| 维度 | 功能说明 |
|:---|:---|
| **全视口 Copilot Studio** | 2-Pane 工业级暗色控制台，内置 Monaco 级行号高亮编辑器与 Token 预估器 |
| **预置工业级攻击场景** | 内置 SSH/Web 暴力破解、SQL 注入探针、XSS 跨站脚本、路径穿越读取系统敏感文件及越权访问载荷 |
| **CVSS 3.1 威胁评分** | 自动输出专业级严重程度评级与矢量字符串（如 `CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H (9.8)`） |
| **MITRE ATT&CK 攻击链路推演** | 5 阶段 Kill Chain（初始访问 $\rightarrow$ 命令执行 $\rightarrow$ 权限持久化 $\rightarrow$ 防御规避 $\rightarrow$ 敏感外发）动态点亮 |
| **自动化安全防御剧本 (Playbooks)** | 动态生成多维防御配置：<br>• 🛡️ **Nginx / OpenResty WAF** 动态拦截配置<br>• 🧱 **Linux iptables / nftables** 网络层熔断指令<br>• 🚨 **Sigma SIEM** 通用告警规则 (YAML)<br>• ⚡ **Snort / Suricata IDS** 签名规则 |
| **企业安全应急研判报告** | 自动排版生成规范化《企业安全事件应急响应研判报告》，支持 Markdown 一键下载与浏览器打印/PDF 导出 |
| **双模引擎与熔断降级** | 优先调用 DeepSeek-V3 / Qwen 2.5 大模型；网络或 API 异常时秒级毫秒切换至本地专家规则引擎兜底 |

---

## 🛠️ 技术栈

- **后端核心**：Spring Boot 3.2.0 + JDK 17
- **持久化层**：MySQL 8.0 + MyBatis
- **大模型通信**：`java.net.http.HttpClient`（JDK 11+ 原生零依赖）+ SSE 流式响应
- **认证与权限**：Spring Security RBAC + JWT 跨站安全认证
- **API 文档**：SpringDoc OpenAPI 3.0 / Swagger UI
- **单元测试**：JUnit 5 (8/8 自动化测试用例通过)

---

## 🚀 快速启动

1. **配置数据库与模型密钥**：
   在 `src/main/resources/application.yml` 中设置数据库连接与大模型 API Key（未配置 API Key 时将自动启动内置规则引擎）。
2. **构建与运行**：
   ```bash
   mvn clean package -DskipTests
   java -jar target/log-ai-assistant-1.0.0.jar
   ```
3. **访问工作台**：
   - Copilot Studio：`http://localhost:8081` (账号: `admin` / 密码: `admin123`)
   - OpenAPI 文档：`http://localhost:8081/swagger-ui/index.html`
