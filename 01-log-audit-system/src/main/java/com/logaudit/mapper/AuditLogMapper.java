package com.logaudit.mapper;

import com.logaudit.entity.AuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AuditLogMapper {

    @Insert("INSERT INTO audit_log (operator, action, target, ip_address, status) " +
            "VALUES (#{operator}, #{action}, #{target}, #{ipAddress}, #{status})")
    int insert(AuditLog auditLog);

    List<AuditLog> findByOperator(@Param("operator") String operator);
}
