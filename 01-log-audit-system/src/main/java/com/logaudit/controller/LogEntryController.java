package com.logaudit.controller;

import com.logaudit.entity.LogEntry;
import com.logaudit.service.AuditLogService;
import com.logaudit.service.LogEntryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "日志检索与审计", description = "多条件组合查询、详情获取、流式 Excel 导出、批量导入与统计分析")
public class LogEntryController {

    private static final DateTimeFormatter EXPORT_FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final LogEntryService logEntryService;
    private final AuditLogService auditLogService;
    private final com.logaudit.security.ClientIpResolver clientIpResolver;

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

    /**
     * 流式导出 Excel。
     *
     * 直接写入 HTTP 响应输出流，SXSSF 只保留 100 行内存窗口，导出 5 万行与导出 500 行内存占用一致。
     * 前端以 window.location.href 发起下载（Cookie 鉴权），浏览器原生接收流式响应，无需改动。
     */
    @GetMapping("/export")
    public void exportLogs(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request,
            HttpServletResponse response) {

        long total = logEntryService.countForExport(startTime, endTime, ipAddress, operation, severity, keyword);

        // 审计在前：响应流转为二进制后已无法再写业务日志
        auditLogService.recordSuccess(currentUser(), "EXPORT_LOGS",
                "ip=" + ipAddress + ", op=" + operation + ", kw=" + keyword + ", total=" + total, clientIp(request));

        String fileName = "日志导出_" + LocalDateTime.now().format(EXPORT_FILE_TIME) + ".xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName);
        response.setHeader("X-Total-Count", String.valueOf(total));

        try {
            logEntryService.exportLogsStreaming(startTime, endTime, ipAddress, operation, severity, keyword,
                    total, response.getOutputStream());
        } catch (IOException e) {
            log.warn("导出响应流不可用（客户端可能已断开）：{}", e.getMessage());
        }
    }

    /** 从 Spring Security 上下文取当前登录用户名。 */
    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : "anonymous";
    }

    /** 取客户端真实 IP（走可信代理链解析，避免 XFF 伪造污染审计轨迹）。 */
    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
