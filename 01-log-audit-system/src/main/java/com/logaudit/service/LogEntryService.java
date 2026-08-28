package com.logaudit.service;

import com.logaudit.entity.LogEntry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface LogEntryService {

    /**
     * 分页查询日志（多条件组合与全局关键字搜索）
     */
    Map<String, Object> searchLogs(LocalDateTime startTime, LocalDateTime endTime,
                                   String ipAddress, String operation, String severity,
                                   String keyword, int page, int pageSize);

    default Map<String, Object> searchLogs(LocalDateTime startTime, LocalDateTime endTime,
                                           String ipAddress, String operation, String severity,
                                           int page, int pageSize) {
        return searchLogs(startTime, endTime, ipAddress, operation, severity, null, page, pageSize);
    }

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
     * 导出 Excel（支持多条件与关键字检索）
     */
    byte[] exportLogs(LocalDateTime startTime, LocalDateTime endTime,
                      String ipAddress, String operation, String severity, String keyword);

    default byte[] exportLogs(LocalDateTime startTime, LocalDateTime endTime,
                              String ipAddress, String operation, String severity) {
        return exportLogs(startTime, endTime, ipAddress, operation, severity, null);
    }
}