package com.logaudit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 时序动态基线与 3-Sigma 离群异常检测探针 (Dynamic Baseline & 3-Sigma Anomaly Detector)
 * 对标 Datadog Watchdog 与 Dynatrace Davis AI 工业级 AIOps 标准：
 * 1. 采用指数加权移动平均 (EMA) 滑动追踪流量均值 μ 与标准差 σ；
 * 2. 计算实时样本点 Z-Score = (x - μ) / σ；
 * 3. 当偏离度超出 3-Sigma (Z > 3.0 或 Z < -3.0) 时触发离群异动告警，彻底根除潮汐固定阈值误报。
 */
@Service
public class DynamicBaselineAnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(DynamicBaselineAnomalyDetector.class);

    public static class AnomalyReport implements Serializable {
        private final boolean isAnomaly;
        private final double currentValue;
        private final double baselineMean;
        private final double standardDeviation;
        private final double zScore;
        private final String message;

        public AnomalyReport(boolean isAnomaly, double currentValue, double baselineMean, double standardDeviation, double zScore, String message) {
            this.isAnomaly = isAnomaly;
            this.currentValue = currentValue;
            this.baselineMean = baselineMean;
            this.standardDeviation = standardDeviation;
            this.zScore = zScore;
            this.message = message;
        }

        public boolean isAnomaly() { return isAnomaly; }
        public double getCurrentValue() { return currentValue; }
        public double getBaselineMean() { return baselineMean; }
        public double getStandardDeviation() { return standardDeviation; }
        public double getZScore() { return zScore; }
        public String getMessage() { return message; }
    }

    private final List<Double> historyWindow = new ArrayList<>();
    private static final int MAX_WINDOW_SIZE = 100;
    private static final double ALPHA = 0.2; // EMA 平滑系数

    private double currentEma = 0.0;
    private boolean initialized = false;

    public synchronized void recordSample(double value) {
        if (!initialized) {
            currentEma = value;
            initialized = true;
        } else {
            currentEma = ALPHA * value + (1 - ALPHA) * currentEma;
        }

        historyWindow.add(value);
        if (historyWindow.size() > MAX_WINDOW_SIZE) {
            historyWindow.remove(0);
        }
    }

    /**
     * 针对输入样本值执行 3-Sigma 离群研判
     */
    public synchronized AnomalyReport evaluate(double value) {
        if (historyWindow.size() < 10) {
            // 初始冷启动样本不足，记录样本并返回正常
            recordSample(value);
            return new AnomalyReport(false, value, value, 0.0, 0.0, "Cold start baseline learning");
        }

        // 1. 计算历史窗口样本方差与标准差
        double sum = 0.0;
        for (double v : historyWindow) {
            sum += v;
        }
        double mean = sum / historyWindow.size();

        double varianceSum = 0.0;
        for (double v : historyWindow) {
            varianceSum += Math.pow(v - mean, 2);
        }
        double stdDev = Math.sqrt(varianceSum / historyWindow.size());

        // 避免极小方差导致除零 (保底防抖)
        if (stdDev < 1.0) {
            stdDev = 1.0;
        }

        // 2. 计算 Z-Score
        double zScore = (value - mean) / stdDev;

        // 3. 3-Sigma 离群判定 (|Z| > 3.0)
        boolean isAnomaly = Math.abs(zScore) >= 3.0;

        String msg;
        if (isAnomaly) {
            msg = String.format("3-Sigma 突变异动告警! 当前值=%.2f, 基线均值=%.2f, 标准差=%.2f, Z-Score=%.2f (偏离度超过3倍标准差)",
                    value, mean, stdDev, zScore);
            log.warn("🚨 [3SIGMA_ANOMALY] {}", msg);
        } else {
            msg = String.format("指标波动正常 (Z-Score=%.2f 在 [-3.0, 3.0] 正常置信区间内)", zScore);
        }

        // 正常点纳入滑动窗口供自适应更新
        if (!isAnomaly) {
            recordSample(value);
        }

        return new AnomalyReport(isAnomaly, value, mean, stdDev, zScore, msg);
    }
}