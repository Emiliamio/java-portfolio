package com.logaudit.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Builder
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
    private String traceId;
    private LocalDateTime createdAt;

    public LogEntry(Long id, LocalDateTime timestamp, String ipAddress, String username,
                    String operation, String operationResult, String detail,
                    String severity, String sourceFile, LocalDateTime createdAt) {
        this.id = id;
        this.timestamp = timestamp;
        this.ipAddress = ipAddress;
        this.username = username;
        this.operation = operation;
        this.operationResult = operationResult;
        this.detail = detail;
        this.severity = severity;
        this.sourceFile = sourceFile;
        this.traceId = null;
        this.createdAt = createdAt;
    }
}