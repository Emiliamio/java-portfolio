package com.logaudit.service.impl;

import com.logaudit.entity.AuditLog;
import com.logaudit.mapper.AuditLogMapper;
import com.logaudit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void record(String operator, String action, String target, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setOperator(operator);
        log.setAction(action);
        log.setTarget(target);
        log.setIpAddress(ipAddress);
        log.setStatus("SUCCESS");
        auditLogMapper.insert(log);
    }

    @Override
    public void recordSuccess(String operator, String action, String target, String ipAddress) {
        record(operator, action, target, ipAddress);
    }

    @Override
    public void recordFail(String operator, String action, String target, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setOperator(operator);
        log.setAction(action);
        log.setTarget(target);
        log.setIpAddress(ipAddress);
        log.setStatus("FAILED");
        auditLogMapper.insert(log);
    }
}
