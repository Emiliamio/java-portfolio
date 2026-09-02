package com.sample.order;

import com.sample.order.dto.OrderRequest;
import com.sample.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 示例微服务集成测试 — 验证业务方法调用与 @AuditLog 拦截。
 */
@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Test
    void testCreateOrderSuccessfully() {
        OrderRequest req = new OrderRequest(null, "user_1001", new BigDecimal("299.00"), "PROD-AI-CHIP");
        String orderId = orderService.createOrder(req);

        assertNotNull(orderId);
        assertTrue(orderId.startsWith("ORD-"));
        assertEquals(new BigDecimal("299.00"), orderService.getOrder(orderId).getAmount());
    }

    @Test
    void testCancelOrderSuccessfully() {
        OrderRequest req = new OrderRequest("ORD-TEST-99", "user_1002", new BigDecimal("99.00"), "PROD-LOG-PROBE");
        orderService.createOrder(req);

        boolean cancelled = orderService.cancelOrder("ORD-TEST-99");
        assertTrue(cancelled);
        assertNull(orderService.getOrder("ORD-TEST-99"));
    }

    @Test
    void testCancelNonExistentOrderThrows() {
        assertThrows(IllegalArgumentException.class, () -> orderService.cancelOrder("NON-EXISTENT"));
    }
}
