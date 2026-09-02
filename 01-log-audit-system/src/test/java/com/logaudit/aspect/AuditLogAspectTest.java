package com.logaudit.aspect;

import com.logaudit.annotation.AuditLog;
import com.logaudit.service.AuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuditLogAspect 单元测试 — 验证 AOP 审计切面拦截与耗时记录。
 */
@ExtendWith(MockitoExtension.class)
class AuditLogAspectTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private AuditLogAspect aspect;

    // 假目标方法
    @AuditLog(operation = "TEST_OPERATION", module = "TEST_MODULE", recordParams = true)
    public String sampleMethod(String arg1) {
        return "SUCCESS_" + arg1;
    }

    @Test
    void testAuditLogAspectSuccess() throws Throwable {
        Method method = this.getClass().getMethod("sampleMethod", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"input-param"});
        when(joinPoint.proceed()).thenReturn("SUCCESS_input-param");

        Object result = aspect.around(joinPoint);

        assertEquals("SUCCESS_input-param", result);
        verify(auditLogService, times(1)).recordSuccess(eq("TEST_MODULE"), eq("TEST_OPERATION"), anyString(), anyString());
    }

    @Test
    void testAuditLogAspectException() throws Throwable {
        Method method = this.getClass().getMethod("sampleMethod", String.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Simulated Failure"));

        assertThrows(RuntimeException.class, () -> aspect.around(joinPoint));
        verify(auditLogService, times(1)).recordFail(eq("TEST_MODULE"), eq("TEST_OPERATION"), anyString(), anyString());
    }
}
