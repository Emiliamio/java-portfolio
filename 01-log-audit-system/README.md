# 日志审计查询系统

Spring Boot 全栈项目，实现日志数据从采集、存储、查询到智能分析的全生命周期管理。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.2 + JDK 17 |
| 数据库 | MySQL 8 + MyBatis + HikariCP |
| 缓存 | Redis + Lettuce |
| 安全 | Spring Security + BCrypt |
| 异步处理 | 自定义线程池 + @Async |
| 文件导出 | Apache POI (Excel) |
| 工具 | Lombok + FastJSON2 |

## 功能模块

- **日志分页查询**：多条件筛选（时间范围、IP、操作类型、严重程度），联合索引优化
- **批量异步导入**：CSV 文件上传，线程池异步处理，支持万级数据
- **Excel 导出**：筛选结果一键导出
- **操作审计**：所有查询/导出/导入操作全记录，审计自闭环
- **统计面板**：今日日志总量、异常日志占比

## 数据库设计

3 张表：`user`（用户）、`log_entry`（日志记录，含联合索引）、`audit_log`（操作审计）。

索引策略：timestamp + ip_address 联合索引覆盖最高频审计查询场景。

## 快速启动

1. 创建数据库 `log_audit`，执行 `src/main/resources/sql/schema.sql`
2. 修改 `application.yml` 中的数据库密码和 Redis 连接
3. 启动：`mvn spring-boot:run` 或直接运行 `LogAuditApplication`
4. 访问：`http://localhost:8080`

## 项目结构

```
com.logaudit
├── config/        # Spring 配置（线程池）
├── controller/    # REST 接口
├── service/       # 业务逻辑
│   └── impl/
├── mapper/        # MyBatis 数据访问
├── entity/        # 实体类
├── dto/           # 数据传输对象
├── handler/       # 全局异常处理
└── utils/         # 工具类
```