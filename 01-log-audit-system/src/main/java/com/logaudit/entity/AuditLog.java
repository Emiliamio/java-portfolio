package com.logaudit.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    private Long id;
    private String operator;
    private String action;
    private String target;
    private String ipAddress;
    private String status;
    private LocalDateTime createdAt;
}
