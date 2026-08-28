package com.logaudit.service;

import com.alibaba.fastjson2.JSON;
import com.logaudit.dto.WebhookLogDto;
import com.logaudit.entity.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka 日志消费者组服务
 *
 * 批量并行拉取消息并批量落库 MySQL、Redis HLL 与 ClickHouse，
 * 支持异常/毒丸消息自动路由至 Dead Letter Queue (DLQ) 死信队列。
 */
@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogConsumer.class);

    private final LogEntryService logEntryService;

    @Autowired(required = false)
    private ClickHouseAnalyticsService clickHouseAnalyticsService;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.dlq-topic:audit.logs.dlq}")
    private String dlqTopic;

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
                log.error("Failed to parse Kafka log payload, forwarding to DLQ: {}", msg, e);
                forwardToDlq(msg, e.getMessage());
            }
        }

        if (!batchEntries.isEmpty()) {
            try {
                // 批量持久化到 MySQL 与 Redis HLL
                logEntryService.asyncBatchImport(batchEntries);

                // 若 ClickHouse 开启，同步落入 ClickHouse 列式分析引擎
                if (clickHouseAnalyticsService != null && clickHouseAnalyticsService.isAvailable()) {
                    clickHouseAnalyticsService.batchInsert(batchDtos);
                }
            } catch (Exception e) {
                log.error("Batch processing failed in Kafka consumer, attempting DLQ fallback: {}", e.getMessage());
                for (String m : messages) {
                    forwardToDlq(m, "BATCH_PROCESSING_FAILURE: " + e.getMessage());
                }
            }
        }
    }

    private void forwardToDlq(String payload, String reason) {
        if (kafkaTemplate != null && dlqTopic != null && !dlqTopic.isBlank()) {
            try {
                kafkaTemplate.send(dlqTopic, payload);
                log.warn("Poison pill forwarded to DLQ [{}], reason: {}", dlqTopic, reason);
            } catch (Exception ex) {
                log.error("Failed to send message to DLQ [{}]: {}", dlqTopic, ex.getMessage());
            }
        }
    }
}
