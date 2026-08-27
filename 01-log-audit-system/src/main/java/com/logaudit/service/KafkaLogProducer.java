package com.logaudit.service;

import com.alibaba.fastjson2.JSON;
import com.logaudit.dto.WebhookLogDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kafka 日志生产者服务
 *
 * 将接收到的单条或批量日志推入 Kafka Topic。
 * 内置 Fail-Safe 容灾机制：若 Kafka 故障或未启用，直接返回 false，通知上游平滑回退至本地线程池。
 */
@Service
public class KafkaLogProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogProducer.class);

    @Value("${app.kafka.enabled:false}")
    private boolean kafkaEnabled;

    @Value("${app.kafka.topic:audit.logs.raw}")
    private String topic;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 判断 Kafka 削峰摄取是否可用
     */
    public boolean isAvailable() {
        return kafkaEnabled && kafkaTemplate != null;
    }

    /**
     * 发送单条日志到 Kafka
     * @return true 发送成功；false 触发降级
     */
    public boolean sendLog(WebhookLogDto dto) {
        if (!isAvailable()) {
            return false;
        }
        try {
            String jsonPayload = JSON.toJSONString(dto);
            String partitionKey = dto.getIpAddress() != null ? dto.getIpAddress() : "default";
            kafkaTemplate.send(topic, partitionKey, jsonPayload);
            log.debug("Published log event to Kafka topic [{}]: ip={}", topic, partitionKey);
            return true;
        } catch (Exception e) {
            log.warn("Failed to publish log to Kafka topic [{}], triggering failover fallback: {}", topic, e.getMessage());
            return false;
        }
    }

    /**
     * 批量发送日志到 Kafka
     * @return true 批量发送成功；false 触发降级
     */
    public boolean sendLogs(List<WebhookLogDto> dtoList) {
        if (!isAvailable() || dtoList == null || dtoList.isEmpty()) {
            return false;
        }
        try {
            for (WebhookLogDto dto : dtoList) {
                String jsonPayload = JSON.toJSONString(dto);
                String partitionKey = dto.getIpAddress() != null ? dto.getIpAddress() : "default";
                kafkaTemplate.send(topic, partitionKey, jsonPayload);
            }
            log.debug("Batch published {} log events to Kafka topic [{}]", dtoList.size(), topic);
            return true;
        } catch (Exception e) {
            log.warn("Failed to batch publish logs to Kafka topic [{}], triggering failover fallback: {}", topic, e.getMessage());
            return false;
        }
    }
}
