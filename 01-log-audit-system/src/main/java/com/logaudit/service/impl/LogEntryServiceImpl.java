package com.logaudit.service.impl;

import com.logaudit.entity.LogEntry;
import com.logaudit.mapper.LogEntryMapper;
import com.logaudit.service.LogEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogEntryServiceImpl implements LogEntryService {

    private final LogEntryMapper logEntryMapper;

    @Override
    public Map<String, Object> searchLogs(LocalDateTime startTime, LocalDateTime endTime,
                                          String ipAddress, String operation, String severity,
                                          int page, int pageSize) {
        int offset = (page - 1) * pageSize;

        long total = logEntryMapper.countByConditions(startTime, endTime, ipAddress, operation, severity);
        List<LogEntry> records = logEntryMapper.findByConditions(startTime, endTime, ipAddress, operation, severity, offset, pageSize);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("records", records);
        return result;
    }

    @Override
    public LogEntry getDetail(Long id) {
        return logEntryMapper.findById(id);
    }

    @Override
    @Async("logImportExecutor")
    public void asyncBatchImport(List<LogEntry> logList) {
        log.info("开始异步批量导入，数量：{}", logList.size());
        long start = System.currentTimeMillis();

        logEntryMapper.batchInsert(logList);

        long elapsed = System.currentTimeMillis() - start;
        log.info("批量导入完成，数量：{}，耗时：{}ms", logList.size(), elapsed);
    }

    @Override
    public Map<String, Object> todayStats() {
        return logEntryMapper.todayStats();
    }

    @Override
    public byte[] exportLogs(LocalDateTime startTime, LocalDateTime endTime,
                             String ipAddress, String operation, String severity) {
        // 查全量数据（不分页）
        long total = logEntryMapper.countByConditions(startTime, endTime, ipAddress, operation, severity);
        List<LogEntry> records = logEntryMapper.findByConditions(
                startTime, endTime, ipAddress, operation, severity, 0, (int) total);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("日志记录");

            // 表头
            Row header = sheet.createRow(0);
            String[] columns = {"ID", "时间", "IP地址", "用户名", "操作类型", "操作结果", "详情", "严重程度", "来源文件"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            // 数据行
            int rowNum = 1;
            for (LogEntry log : records) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(log.getId() != null ? log.getId() : 0);
                row.createCell(1).setCellValue(log.getTimestamp() != null ? log.getTimestamp().toString() : "");
                row.createCell(2).setCellValue(log.getIpAddress() != null ? log.getIpAddress() : "");
                row.createCell(3).setCellValue(log.getUsername() != null ? log.getUsername() : "");
                row.createCell(4).setCellValue(log.getOperation() != null ? log.getOperation() : "");
                row.createCell(5).setCellValue(log.getOperationResult() != null ? log.getOperationResult() : "");
                row.createCell(6).setCellValue(log.getDetail() != null ? log.getDetail() : "");
                row.createCell(7).setCellValue(log.getSeverity() != null ? log.getSeverity() : "");
                row.createCell(8).setCellValue(log.getSourceFile() != null ? log.getSourceFile() : "");
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            throw new RuntimeException("导出Excel失败", e);
        }
    }
}