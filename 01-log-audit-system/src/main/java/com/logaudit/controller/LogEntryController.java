package com.logaudit.controller;

import com.logaudit.entity.LogEntry;
import com.logaudit.service.AuditLogService;
import com.logaudit.service.LogEntryService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "日志检索与审计", description = "多条件组合查询、详情获取、流式 Excel 导出、批量导入与统计分析")
public class LogEntryController {

    private final LogEntryService logEntryService;
    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "多条件分页查询日志", description = "支持时间范围、IP、操作类型、严重程度、关键字全局模糊等多维度筛选，并自动留存查询操作审计")
    public ResponseEntity<Map<String, Object>> searchLogs(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {

        Map<String, Object> result = logEntryService.searchLogs(
                startTime, endTime, ipAddress, operation, severity, keyword, page, pageSize);

        // 审计记录：谁查了日志（真实登录用户 + 真实 IP）
        auditLogService.recordSuccess(currentUser(), "VIEW_LOGS",
                "ip=" + ipAddress + ", op=" + operation + ", kw=" + keyword, clientIp(request));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LogEntry> getDetail(@PathVariable Long id, HttpServletRequest request) {
        LogEntry logEntry = logEntryService.getDetail(id);

        auditLogService.recordSuccess(currentUser(), "VIEW_DETAIL",
                "logId=" + id, clientIp(request));

        return ResponseEntity.ok(logEntry);
    }

    @PostMapping("/batch-import")
    public ResponseEntity<String> batchImport(@RequestBody List<LogEntry> logList, HttpServletRequest request) {
        logEntryService.asyncBatchImport(logList);

        auditLogService.recordSuccess(currentUser(), "UPLOAD_LOGS",
                "count=" + logList.size(), clientIp(request));

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
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {

        byte[] excelData = logEntryService.exportLogs(startTime, endTime, ipAddress, operation, severity, keyword);

        auditLogService.recordSuccess(currentUser(), "EXPORT_LOGS",
                "ip=" + ipAddress + ", op=" + operation + ", kw=" + keyword, clientIp(request));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=logs.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelData);
    }

    /** 从 Spring Security 上下文取当前登录用户名。 */
    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : "anonymous";
    }

    /** 取客户端真实 IP（处理代理转发头）。 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
