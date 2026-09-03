"""
Unit tests for SchemaSniffer
"""

import pytest
from log_parser.schema_sniffer import SchemaSniffer


def test_schema_sniffer_json_lines():
    sniffer = SchemaSniffer()
    sample_lines = [
        '{"timestamp": "2026-08-01T12:00:00Z", "level": "INFO", "service": "order-api", "msg": "Order created"}',
        '{"timestamp": "2026-08-01T12:00:01Z", "level": "ERROR", "service": "order-api", "msg": "Payment failed"}'
    ]
    result = sniffer.sniff_lines(sample_lines)
    assert result["detected_format"] == "JSON_LINES"
    assert result["confidence"] == 1.0
    assert "service" in result["fields"]
    assert "level" in result["fields"]


def test_schema_sniffer_nginx():
    sniffer = SchemaSniffer()
    sample_lines = [
        '183.23.100.55 - admin [01/Aug/2026:12:00:00 +0800] "GET /api/v1/users HTTP/1.1" 200 1024 "-" "Mozilla/5.0"',
        '220.181.38.148 - - [01/Aug/2026:12:00:02 +0800] "POST /api/v1/login HTTP/1.1" 401 256 "-" "Curl/7.68"'
    ]
    result = sniffer.sniff_lines(sample_lines)
    assert result["detected_format"] == "NGINX_COMBINED"
    assert result["confidence"] == 1.0
    assert "client_ip" in result["fields"]
    assert "status" in result["fields"]