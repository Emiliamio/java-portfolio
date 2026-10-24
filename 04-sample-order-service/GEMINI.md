# GEMINI.md - sample-order-service

> 通用规范：~/.dsh/AGENTS.md（§0 为冲突仲裁序）
> 环境事实：读 D:\Antigravity\env-snapshot.json，不在本文件写死

## 技术栈
- Java 17, Spring Boot 3.5.7, Spring Web, Spring AOP
- Fastjson2 2.0.43, Lombok
- Test: Spring Boot Starter Test

## 目录职责
- `src/main/java/com/sample/order/`：外部接入样例应用（含 OrderApplication 启动入口）
  - `annotation`：`@AuditLog` 无侵入审计注解定义
  - `aspect`：`AuditLogAspect` 统一切面与异步上报埋点
  - `controller`：模拟业务订单 RESTful 接口
  - `dto`：订单请求与响应数据传输对象
  - `service`：订单业务处理逻辑
- `src/main/resources/`：应用配置文件
- `src/test/java/`：订单服务与 AOP 埋点单元测试用例

## 命令
- 测试：`mvn -B test`
- 启动：`mvn spring-boot:run` 或运行 `com.sample.order.OrderApplication`

## 禁区
- 凭据只读 ~/.dsh/private.env.md，绝不写进本仓库
- 唯一署名 Emiliamio <mio2110767128@163.com>
- 改完必须 `mvn -B test` 全绿
