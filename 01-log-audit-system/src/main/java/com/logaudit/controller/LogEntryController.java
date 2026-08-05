package com.logaudit.controller;

import com.logaudit.entity.LogEntry;
import com.logaudit.service.AuditLogService;
import com.logaudit.service.LogEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogEntryController {

    private final LogEntryService logEntryService;
    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> searchLogs(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        Map<String, Object> result = logEntryService.searchLogs(
                startTime, endTime, ipAddress, operation, severity, page, pageSize);

        // 审计记录：谁查了日志
        auditLogService.recordSuccess("admin", "VIEW_LOGS",
                "ip=" + ipAddress + ", operation=" + operation, "127.0.0.1");

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LogEntry> getDetail(@PathVariable Long id) {
        LogEntry logEntry = logEntryService.getDetail(id);

        // 审计记录：谁看了某条日志的详情
        auditLogService.recordSuccess("admin", "VIEW_DETAIL",
                "logId=" + id, "127.0.0.1");

        return ResponseEntity.ok(logEntry);
    }

    @PostMapping("/batch-import")
    public ResponseEntity<String> batchImport(@RequestBody List<LogEntry> logList) {
        logEntryService.asyncBatchImport(logList);

        auditLogService.recordSuccess("admin", "UPLOAD_LOGS",
                "count=" + logList.size(), "127.0.0.1");

        return ResponseEntity.ok("导入任务已提交，正在后台处理，共 " + logList.size() + " 条");
    }

    @GetMapping("/today-stats")
    public ResponseEntity<Map<String, Object>> todayStats() {
        return ResponseEntity.ok(logEntryService.todayStats());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportLogs(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String severity) {

        byte[] excelData = logEntryService.exportLogs(startTime, endTime, ipAddress, operation, severity);

        auditLogService.recordSuccess("admin", "EXPORT_LOGS",
                "ip=" + ipAddress + ", operation=" + operation, "127.0.0.1");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=logs.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelData);
    }
}