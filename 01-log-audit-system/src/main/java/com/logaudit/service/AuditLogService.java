package com.logaudit.service;

public interface AuditLogService {
    void record(String operator, String action, String target, String ipAddress);
    void recordSuccess(String operator, String action, String target, String ipAddress);
    void recordFail(String operator, String action, String target, String ipAddress);
}