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
     * 统计当前筛选条件下的可导出条数（用于导出前设置响应头，无需二次全量查询）
     */
    long countForExport(LocalDateTime startTime, LocalDateTime endTime,
                        String ipAddress, String operation, String severity, String keyword);

    /**
     * 流式导出 Excel 到指定输出流。
     *
     * SXSSFWorkbook 只保留 100 行窗口，其余溢出到临时文件后直接写入 HTTP 响应流，
     * 堆内存占用与数据量无关，彻底避免大导出 OOM。
     */
    void exportLogsStreaming(LocalDateTime startTime, LocalDateTime endTime,
                             String ipAddress, String operation, String severity, String keyword,
                             long total, java.io.OutputStream outputStream);

    /**
     * 导出 Excel（支持多条件与关键字检索）。
     *
     * @deprecated 返回 byte[] 会把整份 Excel 驻留堆内存，正是本次改造要消除的问题；
     * 生产路径请使用 {@link #exportLogsStreaming}，此方法仅为兼容历史单元测试保留。
     */
    @Deprecated
    byte[] exportLogs(LocalDateTime startTime, LocalDateTime endTime,
                      String ipAddress, String operation, String severity, String keyword);

    @Deprecated
    default byte[] exportLogs(LocalDateTime startTime, LocalDateTime endTime,
                              String ipAddress, String operation, String severity) {
        return exportLogs(startTime, endTime, ipAddress, operation, severity, null);
    }
}