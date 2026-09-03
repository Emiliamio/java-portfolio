"""
多模态日志格式自动嗅探与 Schema 智能推导探针 (Log Schema Sniffer)
对标 Fluentbit / Vector 工业级零配置即席解析标准：
1. 采样未知日志文件前 N 行样本 (默认 50 行)；
2. 依据特征模式向量与正则分布在候选 Schema 之间计算置信度评分；
3. 自动识别 Logback Standard (Spring Boot)、Nginx Combined、JSON Lines 与 Syslog RFC5424；
4. 输出格式推导结果与字段拓扑，实现免配置一键解析。
"""

import json
import re
from typing import Dict, List, Any, Optional


class SchemaSniffer:

    # 预定义核心工业格式特征正则
    PATTERNS = {
        "LOGBACK_STANDARD": re.compile(
            r"^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:\.\d{3})?\s+\[?[A-Z\s]+\]?\s+(\d+)\s+---\s+\[([^\]]+)\]\s+([\w\.\$]+)\s*:\s*(.*)$"
        ),
        "NGINX_COMBINED": re.compile(
            r"^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\s+-\s+(\S+)\s+\[([^\]]+)\]\s+\"([A-Z]+)\s+([^\s\"]+)\s+([^\"]+)\"\s+(\d{3})\s+(\d+)"
        ),
        "SYSLOG_RFC5424": re.compile(
            r"^<\d{1,3}>\d?\s*(?:\d{4}-\d{2}-\d{2}T|[A-Z][a-z]{2}\s+\d+)\s+[\w\.\-]+\s+([\w\.\-]+)(?:\[\d+\])?:\s*(.*)$"
        )
    }

    def sniff_lines(self, lines: List[str]) -> Dict[str, Any]:
        """
        对传入的日志行数组进行格式嗅探与置信度打分
        """
        if not lines:
            return {
                "detected_format": "UNKNOWN",
                "confidence": 0.0,
                "sample_count": 0,
                "fields": []
            }

        valid_lines = [l.strip() for l in lines if l and l.strip()]
        total = len(valid_lines)
        if total == 0:
            return {
                "detected_format": "UNKNOWN",
                "confidence": 0.0,
                "sample_count": 0,
                "fields": []
            }

        # 1. 优先测试是否为全量 JSON Lines
        json_matches = 0
        json_fields = set()
        for line in valid_lines:
            if line.startswith("{") and line.endswith("}"):
                try:
                    data = json.loads(line)
                    if isinstance(data, dict):
                        json_matches += 1
                        json_fields.update(data.keys())
                except Exception:
                    pass

        json_confidence = json_matches / total
        if json_confidence >= 0.7:
            return {
                "detected_format": "JSON_LINES",
                "confidence": round(json_confidence, 3),
                "sample_count": total,
                "fields": sorted(list(json_fields))
            }

        # 2. 正则模式匹配度打分
        scores = {}
        for fmt_name, pattern in self.PATTERNS.items():
            matched = sum(1 for line in valid_lines if pattern.match(line))
            scores[fmt_name] = matched / total

        best_fmt, best_score = max(scores.items(), key=lambda x: x[1])

        if best_score >= 0.5:
            fields_map = {
                "LOGBACK_STANDARD": ["timestamp", "level", "pid", "thread", "logger", "message"],
                "NGINX_COMBINED": ["client_ip", "remote_user", "timestamp", "method", "path", "protocol", "status", "body_bytes"],
                "SYSLOG_RFC5424": ["timestamp", "hostname", "process", "message"]
            }
            return {
                "detected_format": best_fmt,
                "confidence": round(best_score, 3),
                "sample_count": total,
                "fields": fields_map.get(best_fmt, [])
            }

        return {
            "detected_format": "UNSTRUCTURED_TEXT",
            "confidence": 0.3,
            "sample_count": total,
            "fields": ["raw_line"]
        }