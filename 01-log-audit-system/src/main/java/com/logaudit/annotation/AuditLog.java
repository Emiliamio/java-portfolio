package com.logaudit.annotation;

import java.lang.annotation.*;

/**
 * 企业级无侵入审计埋点注解 (Enterprise Audit Annotation)。
 *
 * 标注在 Controller 或 Service 方法上，由 AuditLogAspect 统一拦截：
 * 1. 自动提取操作名称 (operation) 与 模块归属 (module)；
 * 2. 自动捕获操作者用户名与客户端真实 IP；
 * 3. 统计方法执行耗时 (ms) 与执行结果 (SUCCESS/FAIL)；
 * 4. 自动绑定分布式 TraceId 并异步落库。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作名称（如 "USER_LOGIN"、"EXPORT_LOGS"、"UPDATE_USER"）
     */
    String operation() default "";

    /**
     * 模块名称（如 "AUTH"、"AUDIT_RETRIEVAL"、"SYSTEM"）
     */
    String module() default "SYSTEM";

    /**
     * 默认严重等级（INFO / WARN / ERROR / CRITICAL）
     */
    String severity() default "INFO";

    /**
     * 是否记录方法入参（敏感接口可设为 false）
     */
    boolean recordParams() default true;
}
