package com.logaudit.mapper;

import com.logaudit.entity.LogEntry;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface LogEntryMapper {

    List<LogEntry> findByConditions(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("ipAddress") String ipAddress,
            @Param("operation") String operation,
            @Param("severity") String severity,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    long countByConditions(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("ipAddress") String ipAddress,
            @Param("operation") String operation,
            @Param("severity") String severity
    );

    LogEntry findById(@Param("id") Long id);

    int batchInsert(@Param("list") List<LogEntry> list);

    Map<String, Object> todayStats();

    Map<String, Object> overallStats();
}