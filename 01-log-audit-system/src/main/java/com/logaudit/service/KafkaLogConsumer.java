package com.logaudit.service;

import com.alibaba.fastjson2.JSON;
import com.logaudit.dto.WebhookLogDto;
import com.logaudit.entity.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka 日志消费者组服务
 *
 * 批量并行拉取消息并批量落库 MySQL、Redis HLL 与 ClickHouse。
 */
@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogConsumer.class);

    private final LogEntryService logEntryService;

    @Autowired(required = false)
    private ClickHouseAnalyticsService clickHouseAnalyticsService;

    public KafkaLogConsumer(LogEntryService logEntryService) {
        this.logEntryService = logEntryService;
    }

    /**
     * 批量监听消费 Kafka Topic
     */
    @KafkaListener(
            topics = "${app.kafka.topic:audit.logs.raw}",
            groupId = "${app.kafka.group-id:auditvault-consumer-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeBatch(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        log.info("Kafka Consumer Group received batch of {} log records.", messages.size());
        List<WebhookLogDto> batchDtos = new ArrayList<>();
        List<LogEntry> batchEntries = new ArrayList<>();

        for (String msg : messages) {
            try {
                WebhookLogDto dto = JSON.parseObject(msg, WebhookLogDto.class);
                if (dto != null) {
                    batchDtos.add(dto);
                    batchEntries.add(dto.toLogEntry("kafka-stream"));
                }
            } catch (Exception e) {
                log.error("Failed to parse Kafka log payload: {}", msg, e);
            }
        }

        if (!batchEntries.isEmpty()) {
            // 批量持久化到 MySQL 与 Redis HLL
            logEntryService.asyncBatchImport(batchEntries);

            // 若 ClickHouse 开启，同步落入 ClickHouse 列式分析引擎
            if (clickHouseAnalyticsService != null && clickHouseAnalyticsService.isAvailable()) {
                clickHouseAnalyticsService.batchInsert(batchDtos);
            }
        }
    }
}
