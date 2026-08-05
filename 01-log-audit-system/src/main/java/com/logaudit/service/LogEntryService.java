package com.logaudit.service;

import com.logaudit.entity.LogEntry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface LogEntryService {

    /**
     * 分页查询日志
     */
    Map<String, Object> searchLogs(LocalDateTime startTime, LocalDateTime endTime,
                                   String ipAddress, String operation, String severity,
                                   int page, int pageSize);

    /**
     * 查单条日志详情
     */
    LogEntry getDetail(Long id);

    /**
     * 批量导入日志（异步）
     */
    void asyncBatchImport(List<LogEntry> logList);

    /**
     * 今日统计
     */
    Map<String, Object> todayStats();

    /**
     * 导出 Excel
     */
    byte[] exportLogs(LocalDateTime startTime, LocalDateTime endTime,
                      String ipAddress, String operation, String severity);
}