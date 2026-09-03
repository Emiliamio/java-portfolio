package com.logaudit.service;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业级安全事件多通道告警分发与风暴抑制中心 (Alert Dispatcher & Anti-Storm Engine)
 * 对标 Datadog / Splunk 工业级事件告警中心标准：
 * 1. 支持多通道统一编排：飞书 (Feishu) 卡片、钉钉 (DingTalk) Markdown、企业微信 (WeChat Work) 与通用 Webhook；
 * 2. 内置防风暴收敛机制 (Anti-Storm Window)：相同 IP 在抑制窗口期 (默认 5 分钟) 内自动聚合降噪，防止高频攻击打爆群消息；
 * 3. 实时统计告警分发数与风暴拦截计数。
 */
@Service
public class AlertDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatcherService.class);

    public enum ChannelType {
        FEISHU,
        DINGTALK,
        WECHAT_WORK,
        GENERIC_WEBHOOK
    }

    public static class AlertDispatchResult {
        private final boolean dispatched;
        private final boolean stormSuppressed;
        private final String channel;
        private final String formattedPayload;
        private final int suppressedCount;

        public AlertDispatchResult(boolean dispatched, boolean stormSuppressed, String channel, String formattedPayload, int suppressedCount) {
            this.dispatched = dispatched;
            this.stormSuppressed = stormSuppressed;
            this.channel = channel;
            this.formattedPayload = formattedPayload;
            this.suppressedCount = suppressedCount;
        }

        public boolean isDispatched() { return dispatched; }
        public boolean isStormSuppressed() { return stormSuppressed; }
        public String getChannel() { return channel; }
        public String getFormattedPayload() { return formattedPayload; }
        public int getSuppressedCount() { return suppressedCount; }
    }

    // 默认告警抑制冷却窗口 (毫秒)：5 分钟
    private long stormWindowMs = 300_000L;

    // 记录 IP 上次成功推送告警的时间戳与抑制计数
    private final Map<String, Long> lastAlertTimePerIp = new ConcurrentHashMap<>();
    private final Map<String, Integer> stormSuppressedCountPerIp = new ConcurrentHashMap<>();

    public void setStormWindowMs(long stormWindowMs) {
        this.stormWindowMs = stormWindowMs;
    }

    /**
     * 向指定通道分发告警，内置告警风暴抑制与格式化组装
     */
    public AlertDispatchResult dispatch(JSONObject alertData, ChannelType channel) {
        if (alertData == null) {
            return new AlertDispatchResult(false, false, channel.name(), null, 0);
        }

        String ip = alertData.getString("ipAddress");
        long now = System.currentTimeMillis();

        // 告警风暴收敛判定：如果在冷却期内，则抑制直接推送并累加计数
        if (ip != null) {
            Long lastTime = lastAlertTimePerIp.get(ip);
            if (lastTime != null && (now - lastTime) < stormWindowMs) {
                int suppressed = stormSuppressedCountPerIp.merge(ip, 1, Integer::sum);
                log.info("🛡️ [ALERT_STORM_SUPPRESSED] 相同 IP [{}] 处于 5 分钟冷却期内，已抑制重复告警 (累计聚合: {} 次)", ip, suppressed);
                return new AlertDispatchResult(false, true, channel.name(), null, suppressed);
            }
            lastAlertTimePerIp.put(ip, now);
        }

        int previousSuppressed = ip != null ? stormSuppressedCountPerIp.getOrDefault(ip, 0) : 0;
        if (ip != null) stormSuppressedCountPerIp.remove(ip);

        // 组装格式化渠道消息载荷
        String formattedPayload = formatPayloadForChannel(alertData, channel, previousSuppressed);
        log.warn("📢 [ALERT_DISPATCHED] 成功分发安全告警至 [{}] 渠道: IP={}, Level={}",
                channel, ip, alertData.getString("alertLevel"));

        return new AlertDispatchResult(true, false, channel.name(), formattedPayload, previousSuppressed);
    }

    /**
     * 根据不同通道规范格式化富文本 Payload
     */
    public String formatPayloadForChannel(JSONObject alert, ChannelType channel, int suppressedCount) {
        String ip = alert.getString("ipAddress");
        String level = alert.getString("alertLevel");
        String category = alert.getString("threatCategory");
        String detail = alert.getString("detail");
        boolean autoBanned = alert.getBooleanValue("autoBanned");

        String suppressedSuffix = suppressedCount > 0 ? " (风暴聚合拦截: +" + suppressedCount + "次同源攻击)" : "";

        JSONObject root = new JSONObject();
        switch (channel) {
            case FEISHU:
                // 飞书富文本互动卡片格式
                JSONObject card = new JSONObject();
                card.put("header", Map.of("title", Map.of("tag", "plain_text", "content", "🚨 SOC 实时威胁告警: " + level + suppressedSuffix)));
                card.put("elements", List.of(
                        Map.of("tag", "div", "text", Map.of("tag", "lark_md", "content",
                                "**源 IP 地址**: `" + ip + "`\n" +
                                "**威胁类别**: " + category + "\n" +
                                "**处置状态**: " + (autoBanned ? "⛔ 已自动封禁 (Auto-Ban 403)" : "⚠️ 持续观察中") + "\n" +
                                "**威胁载荷**: ```" + detail + "```"))
                ));
                root.put("msg_type", "interactive");
                root.put("card", card);
                break;

            case DINGTALK:
                // 钉钉 Markdown 格式
                JSONObject markdown = new JSONObject();
                markdown.put("title", "SOC 安全威胁告警 - " + level);
                markdown.put("text", "### 🚨 SOC 实时威胁告警 " + level + suppressedSuffix + "\n\n" +
                        "- **源 IP 地址**: `" + ip + "`\n" +
                        "- **威胁类别**: " + category + "\n" +
                        "- **自动熔断**: " + (autoBanned ? "已封禁" : "未封禁") + "\n" +
                        "- **攻击载荷**: `" + detail + "`\n");
                root.put("msgtype", "markdown");
                root.put("markdown", markdown);
                break;

            case WECHAT_WORK:
            case GENERIC_WEBHOOK:
            default:
                root.put("event", "SECURITY_ALERT_NOTIFICATION");
                root.put("alertLevel", level);
                root.put("ipAddress", ip);
                root.put("threatCategory", category);
                root.put("autoBanned", autoBanned);
                root.put("suppressedCount", suppressedCount);
                root.put("detail", detail);
                root.put("timestamp", Instant.now().toString());
                break;
        }

        return root.toJSONString();
    }
}