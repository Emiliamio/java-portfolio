package com.logaudit.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {
    private Long id;
    private LocalDateTime timestamp;
    private String ipAddress;
    private String username;
    private String operation;
    private String operationResult;
    private String detail;
    private String severity;
    private String sourceFile;
    private LocalDateTime createdAt;
}