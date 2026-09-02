package com.sample.order.controller;

import com.sample.order.dto.OrderRequest;
import com.sample.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody OrderRequest request) {
        String orderId = orderService.createOrder(request);
        return ResponseEntity.ok(Map.of("success", true, "orderId", orderId));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable String orderId) {
        boolean cancelled = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(Map.of("success", cancelled, "message", "Order cancelled"));
    }
}
