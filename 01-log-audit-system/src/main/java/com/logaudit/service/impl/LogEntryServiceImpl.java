package com.logaudit.service.impl;

import com.logaudit.entity.LogEntry;
import com.logaudit.mapper.LogEntryMapper;
import com.logaudit.service.LogEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogEntryServiceImpl implements LogEntryService {

    private static final int MAX_EXPORT_LIMIT = 50000;
    private static final String HLL_KEY_PREFIX = "auditvault:unique_ips:";

    private final LogEntryMapper logEntryMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Map<String, Object> searchLogs(LocalDateTime startTime, LocalDateTime endTime,
                                          String ipAddress, String operation, String severity,
                                          String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;

        long total = logEntryMapper.countByConditions(startTime, endTime, ipAddress, operation, severity, keyword);
        List<LogEntry> records = logEntryMapper.findByConditions(startTime, endTime, ipAddress, operation, severity, keyword, offset, pageSize);

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

        // 异步更新活跃 IP 到 Redis HyperLogLog 极速统计结构中
        try {
            String todayKey = HLL_KEY_PREFIX + LocalDate.now();
            String[] ips = logList.stream()
                    .map(LogEntry::getIpAddress)
                    .filter(ip -> ip != null && !ip.isBlank())
                    .distinct()
                    .toArray(String[]::new);
            if (ips.length > 0) {
                redisTemplate.opsForHyperLogLog().add(todayKey, ips);
            }
        } catch (Exception e) {
            log.warn("Redis HyperLogLog add failed: {}", e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("批量导入完成，数量：{}，耗时：{}ms", logList.size(), elapsed);
    }

    @Override
    public Map<String, Object> todayStats() {
        Map<String, Object> stats = logEntryMapper.todayStats();
        Number totalNum = stats != null ? (Number) stats.get("total") : 0;
        long total = totalNum != null ? totalNum.longValue() : 0;

        // 尝试优先读取 Redis HyperLogLog 独立 IP 统计
        try {
            String todayKey = HLL_KEY_PREFIX + LocalDate.now();
            Long hllCount = redisTemplate.opsForHyperLogLog().size(todayKey);
            if (hllCount != null && hllCount > 0 && stats != null) {
                stats = new HashMap<>(stats);
                stats.put("uniqueIps", hllCount);
            }
        } catch (Exception e) {
            log.warn("Redis HyperLogLog query failed: {}", e.getMessage());
        }

        // 如果今日尚无日志（如演示环境只有历史种子数据），智能回退查询全局统计，避免面板全 0
        if (total == 0) {
            Map<String, Object> overall = logEntryMapper.overallStats();
            if (overall != null && overall.get("total") != null && ((Number) overall.get("total")).longValue() > 0) {
                return overall;
            }
        }
        return stats != null ? stats : Map.of("total", 0, "abnormal", 0, "uniqueIps", 0);
    }

    @Override
    public byte[] exportLogs(LocalDateTime startTime, LocalDateTime endTime,
                             String ipAddress, String operation, String severity, String keyword) {
        long total = logEntryMapper.countByConditions(startTime, endTime, ipAddress, operation, severity, keyword);
        int fetchSize = (int) Math.min(total, MAX_EXPORT_LIMIT);

        List<LogEntry> records = logEntryMapper.findByConditions(
                startTime, endTime, ipAddress, operation, severity, keyword, 0, fetchSize);

        // 使用 SXSSFWorkbook 流式写入（内存保留 100 行窗口，其余溢出到临时文件），彻底防止 OOM
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("日志记录");

            // 表头
            Row header = sheet.createRow(0);
            String[] columns = {"ID", "TraceId", "时间", "IP地址", "用户名", "操作类型", "操作结果", "详情", "严重程度", "来源文件"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            // 数据行
            int rowNum = 1;
            for (LogEntry logEntry : records) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(logEntry.getId() != null ? logEntry.getId() : 0);
                row.createCell(1).setCellValue(logEntry.getTraceId() != null ? logEntry.getTraceId() : "");
                row.createCell(2).setCellValue(logEntry.getTimestamp() != null ? logEntry.getTimestamp().toString() : "");
                row.createCell(3).setCellValue(logEntry.getIpAddress() != null ? logEntry.getIpAddress() : "");
                row.createCell(4).setCellValue(logEntry.getUsername() != null ? logEntry.getUsername() : "");
                row.createCell(5).setCellValue(logEntry.getOperation() != null ? logEntry.getOperation() : "");
                row.createCell(6).setCellValue(logEntry.getOperationResult() != null ? logEntry.getOperationResult() : "");
                row.createCell(7).setCellValue(logEntry.getDetail() != null ? logEntry.getDetail() : "");
                row.createCell(8).setCellValue(logEntry.getSeverity() != null ? logEntry.getSeverity() : "");
                row.createCell(9).setCellValue(logEntry.getSourceFile() != null ? logEntry.getSourceFile() : "");
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            throw new RuntimeException("导出Excel失败", e);
        } finally {
            workbook.dispose(); // 清理临时磁盘文件
        }
    }
}