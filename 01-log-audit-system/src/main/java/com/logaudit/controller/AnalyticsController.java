package com.logaudit.controller;

import com.logaudit.service.ClickHouseAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 高性能 OLAP 聚合分析控制器
 *
 * 提供时序直方图与多维 Facet 占比分析，支持在 MySQL (OLTP) 与 ClickHouse (OLAP) 引擎间切换并实时观测时延对比。
 */
@Tag(name = "OLAP 分析引擎", description = "ClickHouse 列式分析与时序直方图聚合接口")
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final ClickHouseAnalyticsService clickHouseAnalyticsService;

    public AnalyticsController(ClickHouseAnalyticsService clickHouseAnalyticsService) {
        this.clickHouseAnalyticsService = clickHouseAnalyticsService;
    }

    @Operation(summary = "获取双引擎时序直方图分布", description = "支持按 engine=clickhouse 或 engine=mysql 获取聚合桶及耗时基准")
    @GetMapping("/histogram")
    public ResponseEntity<Map<String, Object>> getHistogram(
            @RequestParam(name = "engine", defaultValue = "clickhouse") String engine
    ) {
        Map<String, Object> data = clickHouseAnalyticsService.getTimeSeriesHistogram(engine);
        return ResponseEntity.ok(data);
    }

    @Operation(summary = "获取多维 Facet 分布与压缩指标", description = "返回严重级别分布、服务分布及 ClickHouse 列式存储压缩比")
    @GetMapping("/facets")
    public ResponseEntity<Map<String, Object>> getFacets(
            @RequestParam(name = "engine", defaultValue = "clickhouse") String engine
    ) {
        Map<String, Object> data = clickHouseAnalyticsService.getFacetDistribution(engine);
        return ResponseEntity.ok(data);
    }

    @Operation(summary = "获取分析引擎就绪状态", description = "检查 ClickHouse 与 Kafka 当前可用性")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("clickhouseAvailable", clickHouseAnalyticsService.isAvailable());
        status.put("supportedEngines", new String[]{"MySQL (OLTP InnoDB)", "ClickHouse (OLAP MergeTree)"});
        status.put("defaultEngine", clickHouseAnalyticsService.isAvailable() ? "clickhouse" : "mysql");
        return ResponseEntity.ok(status);
    }
}
