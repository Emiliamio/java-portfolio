# 日志智能分析助手 — Log AI Assistant

> 项目三 · AI 集成 · LLM 驱动的日志安全分析

一个基于大语言模型的日志智能分析系统。用户粘贴一段日志文本，后台调用大模型 API 进行分析——识别操作类型、判断安全风险等级、给出处置建议。所有分析历史存入 MySQL，支持回溯查阅。

---

## 功能概览

| 功能 | 说明 |
|------|------|
| **AI 日志分析** | 调用大模型 API，分析日志的安全风险和操作类型 |
| **风险评估** | 五级风险：NORMAL / LOW / MEDIUM / HIGH / CRITICAL |
| **处置建议** | AI 自动生成中文处置方案 |
| **降级保护** | LLM 输出异常时，自动切换关键词规则引擎，保证可用性 |
| **分析历史** | 每次分析结果存入 MySQL，可按时间回顾 |
| **历史详情** | 点击历史卡片查看完整分析结果（含原始日志） |
| **快捷示例** | 内置 5 种典型日志场景，一键填充测试 |
| **多 API 兼容** | 同时支持 Anthropic Messages API 和 OpenAI Chat Completions API |

---

## 技术栈

| 技术 | 用途 |
|------|------|
| **Spring Boot 3.2** | 后端框架 |
| **MyBatis** | ORM — 分析历史 CRUD |
| **MySQL** | 分析历史持久化 |
| **java.net.http.HttpClient** | 调用 LLM API（JDK 11+ 内置，零外部 HTTP 依赖） |
| **FastJSON2** | LLM 响应 JSON 解析 |
| **JSR-303 Validation** | 输入参数校验 |
| **原生 HTML + CSS + JS** | 前端界面（无框架，fetch 调后端 API） |
| **JUnit 5** | 单元测试 |

---

## 项目结构

```
03-log-ai-assistant/
├── src/main/java/com/logai/
│   ├── LogAiApplication.java          # Spring Boot 入口
│   ├── controller/
│   │   └── AiController.java          # REST API (分析/历史/统计)
│   ├── service/
│   │   └── LlmService.java            # LLM 调用 + Prompt 工程 + 降级逻辑
│   ├── entity/
│   │   ├── AiAnalysis.java            # 分析记录实体
│   │   ├── AnalyzeRequest.java        # 请求 DTO (含校验)
│   │   ├── AnalysisResult.java        # 响应 DTO
│   │   └── ApiResponse.java           # 统一响应包装
│   ├── mapper/
│   │   └── AiAnalysisMapper.java      # MyBatis Mapper
│   └── handler/
│       └── GlobalExceptionHandler.java # 全局异常处理
├── src/main/resources/
│   ├── sql/schema.sql                 # 建表脚本
│   ├── mapper/AiAnalysisMapper.xml    # MyBatis XML
│   ├── application.properties         # 配置（含 LLM 参数）
│   └── static/
│       ├── index.html                 # 前端 SPA
│       ├── css/style.css              # 样式（暗色主题）
│       └── js/app.js                  # 前端逻辑
├── src/test/java/com/logai/
│   └── LlmServiceTest.java            # 8 个测试（解析 + 降级逻辑）
├── pom.xml
├── .gitignore
└── README.md
```

---

## 快速开始

### 1. 环境准备

- JDK 17+
- MySQL 8.0+（与项目一共享 `log_audit` 数据库）
- Maven 3.8+
- 大模型 API Key（Anthropic / OpenAI 兼容均可）

### 2. 创建数据表

```bash
mysql -u root -p < src/main/resources/sql/schema.sql
```

### 3. 配置 API Key

**关键安全要求：API Key 必须通过环境变量设置，绝不硬编码！**

```bash
# Linux / macOS
export AI_API_KEY="sk-your-api-key-here"

# Windows (PowerShell)
$env:AI_API_KEY="sk-your-api-key-here"

# Windows (CMD)
set AI_API_KEY=sk-your-api-key-here
```

### 4. 修改数据库密码

编辑 `src/main/resources/application.properties`：

```properties
spring.datasource.password=你的MySQL密码
```

### 5. 启动

```bash
mvn clean package -DskipTests
java -jar target/log-ai-assistant-1.0.0.jar
```

访问 http://localhost:8081 即可使用。

### 6. 运行测试

```bash
mvn test
```

---

## API 接口文档

| Method | Path | 说明 |
|--------|------|------|
| `POST` | `/api/ai/analyze` | 提交日志文本，获取 AI 分析结果 |
| `GET` | `/api/ai/history?limit=20` | 获取最近的分析历史 |
| `GET` | `/api/ai/history/{id}` | 获取单条分析详情 |
| `GET` | `/api/ai/stats` | 获取统计信息 |

### POST /api/ai/analyze

**请求体：**
```json
{
  "logContent": "2025-01-15 08:04:30 172.16.0.88 User hacker1 LOGIN FAIL \"Invalid password\""
}
```

**成功响应：**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "operationType": "LOGIN",
    "riskLevel": "LOW",
    "needIntervention": false,
    "suggestion": "单次登录失败，可能是用户输错密码，暂无需处置。如该IP短时间内大量失败，需升级为MEDIUM",
    "summary": "用户 hacker1 从 172.16.0.88 尝试登录失败",
    "sourceIp": "172.16.0.88",
    "modelUsed": "claude-sonnet-5-20251001",
    "analysisTimeMs": 1234
  }
}
```

---

## 核心知识点（面试用）

### 1. System Prompt 工程

`LlmService.buildSystemPrompt()` 中构造了一个结构化的 System Prompt：

- **角色设定**：明确告诉模型"你是日志安全分析专家"
- **输出格式约束**：要求只返回 JSON，不包含其他文字——避免解析失败
- **风险等级标准**：在 Prompt 中直接定义了五级判定规则，确保输出一致性
- **攻击检测规则**：SQL 注入/XSS/路径遍历 → 至少 HIGH

面试可能问：**为什么用 System Prompt 而不是 User Prompt？**
→ System Prompt 设定的是持久角色和规则，User Prompt 是具体输入。把判定标准放 System Prompt 中，模型会在整个对话中始终遵循这些规则。

### 2. 降级（Fallback）策略

当 LLM 返回的 JSON 格式无法解析时，`fallbackAnalysis()` 用关键词规则引擎兜底：

- 检测到 `SQL INJECTION` → CRITICAL
- 检测到 `<script>` → CRITICAL（XSS）
- 检测到 `../` → HIGH（路径遍历）
- 检测到 `DENIED` → MEDIUM

面试可能问：**降级策略的意义是什么？**
→ **高可用性设计**。大模型 API 有时返回格式不稳定（多了 markdown 包裹、多了自然语言），降级方案保证系统在 AI 不可靠时依然能给出有意义的分析结果，不会直接崩溃。

### 3. API 格式自动适配

`buildRequestBody()` 通过判断 `apiUrl` 中是否含 `"anthropic"` 自动切换请求体格式：

- Anthropic：`system` 顶层字段 + `messages[].content[]` 数组
- OpenAI：`messages[]` 中包含 system role + string content

面试可能问：**为什么要兼容两种 API？**
→ **不锁定单一供应商**。企业实际使用中可能切换模型提供商。自动适配让配置变一下 `AI_API_URL` 和 `AI_MODEL` 就能切换，代码零改动。

### 4. HttpClient (JDK 11+) 替代 RestTemplate

没有使用 Spring 的 RestTemplate 或 WebClient，而是用 JDK 内置的 `java.net.http.HttpClient`：

- 零外部依赖
- 支持异步（`sendAsync`）、超时、HTTP/2
- 代码更轻量

### 5. 安全性要点

- **API Key 用 `System.getenv("AI_API_KEY")` 读取**，绝对不写在代码或配置文件里
- **输入校验**：`@Valid` + `@Size(min=5, max=5000)` 防止过长的恶意输入
- **响应不泄露内部信息**：`GlobalExceptionHandler` 对客户端返回友好信息，详细错误只写服务端日志
- **`.gitignore` 排除了 `.env` 文件**，防止意外提交密钥

### 6. 前端设计要点

- **暗色主题**：使用 CSS 变量实现全局配色，视觉专业
- **快捷示例按钮**：方便面试官现场体验，一键填充典型场景
- **Ctrl+Enter 提交**：效率细节
- **分析过程中禁用按钮**：防止重复提交
- **`escapeHtml()`**：防止 XSS（历史日志中可能包含 `<script>` 标签）

---

## 与其他项目的联动

| 项目 | 关联方式 |
|------|----------|
| **项目一** | 共享 `log_audit` 数据库，AI 分析的日志可以直接来自项目一的查询结果 |
| **项目二** | 项目二解析出的可疑日志，可以粘贴到本项目做深度 AI 分析 |
| **项目四** | 本项目 + 项目一 可以通过 Docker Compose 一起部署 |

---

## License

MIT
