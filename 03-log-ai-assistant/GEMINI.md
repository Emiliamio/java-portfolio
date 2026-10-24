# GEMINI.md - log-ai-assistant

> 通用规范：~/.dsh/AGENTS.md（§0 为冲突仲裁序）
> 环境事实：读 D:\Antigravity\env-snapshot.json，不在本文件写死

## 技术栈
- Java 17, Spring Boot 3.5.7, Spring Web, Spring Security + JJWT 0.11.5
- MyBatis 3.0.3, MySQL Connector/J, Fastjson2 2.0.43
- SpringDoc OpenAPI 2.3.0, Lombok
- Test: Spring Boot Starter Test

## 目录职责
- `src/main/java/com/logai/`：核心应用源码（含 LogAiApplication 启动入口）
  - `cache`：语义诊断特征缓存（SemanticDiagnosisCache）
  - `controller`：日志智能分析与威胁研判 RESTful 接口
  - `engine`：高维特征嵌入与匹配引擎（FastFeatureEmbeddingEngine）
  - `entity`/`mapper`：数据模型与 MyBatis 数据库访问
  - `handler`/`security`：统一全局异常处理与 JWT 安全拦截
  - `service`：LLM 交互、PII 实时脱敏与安全应急调查流水线
  - `validator`：Sigma 威胁检测规则解析与语法校验
- `src/main/resources/`：应用配置与 MyBatis Mapper XML
- `src/test/java/`：自动化单元测试套件

## 命令
- 测试：`mvn -B test`
- 启动：`mvn spring-boot:run` 或运行 `com.logai.LogAiApplication`

## 禁区
- 凭据只读 ~/.dsh/private.env.md，绝不写进本仓库
- 唯一署名 Emiliamio <mio2110767128@163.com>
- 改完必须 `mvn -B test` 全绿
