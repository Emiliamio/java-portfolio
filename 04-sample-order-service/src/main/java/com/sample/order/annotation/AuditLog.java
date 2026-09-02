package com.sample.order.annotation;

import java.lang.annotation.*;

/**
 * 示例微服务无侵入埋点注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    String operation() default "";
    String module() default "ORDER_SERVICE";
    String severity() default "INFO";
}
