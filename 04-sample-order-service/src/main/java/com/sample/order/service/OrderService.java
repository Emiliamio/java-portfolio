package com.sample.order.service;

import com.sample.order.annotation.AuditLog;
import com.sample.order.dto.OrderRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private final Map<String, OrderRequest> mockOrderDb = new ConcurrentHashMap<>();

    @AuditLog(operation = "CREATE_ORDER", module = "ORDER_MGMT", severity = "INFO")
    public String createOrder(OrderRequest request) {
        String orderId = request.getOrderId() != null ? request.getOrderId() : "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        request.setOrderId(orderId);
        mockOrderDb.put(orderId, request);
        return orderId;
    }

    @AuditLog(operation = "CANCEL_ORDER", module = "ORDER_MGMT", severity = "WARN")
    public boolean cancelOrder(String orderId) {
        if (!mockOrderDb.containsKey(orderId)) {
            throw new IllegalArgumentException("Order " + orderId + " does not exist");
        }
        mockOrderDb.remove(orderId);
        return true;
    }

    public OrderRequest getOrder(String orderId) {
        return mockOrderDb.get(orderId);
    }
}
