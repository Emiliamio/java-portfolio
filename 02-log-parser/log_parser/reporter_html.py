"""
HTML 报告生成器 — 生成单文件现代化离线可视化分析报告。
特性：零外部 CDN 依赖、内联暗色系现代样式、交互式统计图表与安全态势总结。
"""

import html
import logging
import os
from datetime import datetime
from typing import Dict, Any

logger = logging.getLogger(__name__)


def export_to_html(report: Dict[str, Any], output_path: str) -> None:
    """
    将分析报告导出为美观的离线 HTML 可视化报告。

    Args:
        report: detect_anomalies() 返回的报告字典
        output_path: 输出 HTML 文件路径 (.html)
    """
    logger.info("Exporting interactive HTML report to: %s", output_path)
    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)

    total = report.get("total_records", 0)
    failed = report.get("failed_records", 0)
    unique_ips = report.get("unique_ips", 0)
    suspicious_df = report.get("suspicious_ips")
    suspicious_count = len(suspicious_df) if suspicious_df is not None else 0
    fail_rate = f"{(failed / total * 100):.1f}%" if total > 0 else "0.0%"

    # 1. 严重程度分布柱状条
    severity_dist = report.get("severity_distribution", {})
    sev_bars_html = ""
    for sev, count in severity_dist.items():
        pct = (count / total * 100) if total > 0 else 0
        cls = "danger" if sev in ("ERROR", "CRITICAL") else ("warn" if sev == "WARN" else "info")
        sev_bars_html += f"""
        <div class="bar-item">
          <div class="bar-header">
            <span class="bar-label">{html.escape(str(sev))}</span>
            <span class="bar-val">{count:,} ({pct:.1f}%)</span>
          </div>
          <div class="bar-track">
            <div class="bar-fill {cls}" style="width: {max(pct, 2)}%;"></div>
          </div>
        </div>
        """

    # 2. 失败操作分布
    fail_ops = report.get("fail_operations", {})
    fail_ops_html = ""
    for op, count in sorted(fail_ops.items(), key=lambda x: x[1], reverse=True)[:6]:
        pct = (count / failed * 100) if failed > 0 else 0
        fail_ops_html += f"""
        <div class="bar-item">
          <div class="bar-header">
            <span class="bar-label">{html.escape(str(op))}</span>
            <span class="bar-val">{count:,} ({pct:.1f}%)</span>
          </div>
          <div class="bar-track">
            <div class="bar-fill warn" style="width: {max(pct, 2)}%;"></div>
          </div>
        </div>
        """
    if not fail_ops_html:
        fail_ops_html = '<div class="empty-hint">暂无失败操作记录</div>'

    # 3. 可疑 IP 表格
    suspicious_table_html = ""
    if suspicious_df is not None and not suspicious_df.empty:
        rows = ""
        for idx, row in suspicious_df.reset_index().iterrows():
            ip = str(row.get("ip_address", "—"))
            count = row.get("fail_count", 0)
            first_seen = str(row.get("first_seen", "—"))
            last_seen = str(row.get("last_seen", "—"))
            risk_badge = '<span class="badge badge-critical">高危异常</span>' if count >= 10 else '<span class="badge badge-warn">可疑爆破</span>'

            rows += f"""
            <tr>
              <td>{idx + 1}</td>
              <td class="mono font-bold">{html.escape(ip)}</td>
              <td><span class="mono count-pill">{count}</span></td>
              <td>{risk_badge}</td>
              <td class="mono text-muted">{html.escape(first_seen)}</td>
              <td class="mono text-muted">{html.escape(last_seen)}</td>
            </tr>
            """
        suspicious_table_html = f"""
        <table class="data-table">
          <thead>
            <tr>
              <th>#</th>
              <th>源 IP 地址</th>
              <th>失败频次</th>
              <th>风险研判</th>
              <th>首次发生</th>
              <th>最近发生</th>
            </tr>
          </thead>
          <tbody>{rows}</tbody>
        </table>
        """
    else:
        suspicious_table_html = '<div class="empty-hint">✓ 未发现超过阈值的可疑 IP 攻击行为</div>'

    gen_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>LogScope 安全态势与日志分析报告</title>
  <style>
    :root {{
      --bg-dark: #090d16;
      --bg-panel: #121824;
      --bg-raised: #1a2233;
      --border: #222d42;
      --text-main: #e2e8f0;
      --text-muted: #8896ab;
      --accent-blue: #60a5fa;
      --accent-green: #34d399;
      --accent-amber: #fbbf24;
      --accent-red: #f87171;
      --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      --font-mono: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
    }}
    * {{ box-sizing: border-box; margin: 0; padding: 0; }}
    body {{
      background: var(--bg-dark);
      color: var(--text-main);
      font-family: var(--font-sans);
      line-height: 1.5;
      padding: 32px 20px 60px;
    }}
    .container {{ max-width: 1040px; margin: 0 auto; }}
    header {{ margin-bottom: 28px; border-bottom: 1px solid var(--border); padding-bottom: 16px; display: flex; justify-content: space-between; align-items: flex-end; }}
    .logo {{ font-family: var(--font-mono); font-size: 1.25rem; font-weight: 700; color: var(--text-main); display: flex; align-items: center; gap: 8px; }}
    .logo-badge {{ background: #1e3a8a; color: #93c5fd; font-size: 0.7rem; padding: 2px 8px; border-radius: 4px; font-weight: 600; }}
    .meta-time {{ font-family: var(--font-mono); font-size: 0.75rem; color: var(--text-muted); }}

    /* Stat Cards */
    .stat-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }}
    .stat-card {{ background: var(--bg-panel); border: 1px solid var(--border); border-radius: 8px; padding: 18px 20px; }}
    .stat-val {{ font-family: var(--font-mono); font-size: 1.75rem; font-weight: 700; color: var(--text-main); line-height: 1.2; }}
    .stat-label {{ font-size: 0.8rem; color: var(--text-muted); margin-top: 4px; font-weight: 500; }}
    .stat-sub {{ font-size: 0.72rem; color: var(--accent-blue); margin-top: 6px; font-family: var(--font-mono); }}

    /* Section & Charts */
    .grid-2 {{ display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px; }}
    @media (max-width: 768px) {{ .grid-2 {{ grid-template-columns: 1fr; }} }}
    .panel {{ background: var(--bg-panel); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }}
    .panel-header {{ padding: 14px 20px; background: var(--bg-raised); border-bottom: 1px solid var(--border); font-size: 0.85rem; font-weight: 600; }}
    .panel-body {{ padding: 18px 20px; }}

    .bar-item {{ margin-bottom: 14px; }}
    .bar-item:last-child {{ margin-bottom: 0; }}
    .bar-header {{ display: flex; justify-content: space-between; font-size: 0.75rem; margin-bottom: 5px; font-family: var(--font-mono); }}
    .bar-track {{ height: 8px; background: var(--bg-dark); border-radius: 4px; overflow: hidden; }}
    .bar-fill {{ height: 100%; border-radius: 4px; }}
    .bar-fill.info {{ background: var(--accent-blue); }}
    .bar-fill.warn {{ background: var(--accent-amber); }}
    .bar-fill.danger {{ background: var(--accent-red); }}

    /* Tables */
    .data-table {{ width: 100%; border-collapse: collapse; text-align: left; font-size: 0.8rem; }}
    .data-table th {{ padding: 10px 14px; background: var(--bg-raised); color: var(--text-muted); font-weight: 600; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.5px; border-bottom: 1px solid var(--border); }}
    .data-table td {{ padding: 12px 14px; border-bottom: 1px solid rgba(34,45,66,0.6); }}
    .data-table tr:hover td {{ background: rgba(30,41,59,0.3); }}
    .mono {{ font-family: var(--font-mono); }}
    .font-bold {{ font-weight: 600; }}
    .text-muted {{ color: var(--text-muted); font-size: 0.75rem; }}
    .count-pill {{ background: #3b1818; color: #fca5a5; padding: 2px 8px; border-radius: 12px; font-weight: 600; font-size: 0.75rem; }}
    .badge {{ font-size: 0.68rem; padding: 3px 8px; border-radius: 4px; font-weight: 600; }}
    .badge-critical {{ background: rgba(239,68,68,0.2); color: var(--accent-red); border: 1px solid rgba(239,68,68,0.4); }}
    .badge-warn {{ background: rgba(245,158,11,0.2); color: var(--accent-amber); border: 1px solid rgba(245,158,11,0.4); }}
    .empty-hint {{ text-align: center; padding: 24px; color: var(--text-muted); font-size: 0.85rem; }}

    footer {{ margin-top: 40px; text-align: center; font-size: 0.75rem; color: var(--text-muted); font-family: var(--font-mono); }}
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div class="logo">
        <span>LogScope</span>
        <span class="logo-badge">Security Report</span>
      </div>
      <div class="meta-time">生成时间: {gen_time}</div>
    </header>

    <!-- Stat Grid -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-val">{total:,}</div>
        <div class="stat-label">总日志条数</div>
        <div class="stat-sub">全量样本</div>
      </div>
      <div class="stat-card">
        <div class="stat-val" style="color:var(--accent-red)">{failed:,}</div>
        <div class="stat-label">失败 / 异常操作</div>
        <div class="stat-sub">异常率 {fail_rate}</div>
      </div>
      <div class="stat-card">
        <div class="stat-val">{unique_ips:,}</div>
        <div class="stat-label">独立源 IP 数</div>
        <div class="stat-sub">访问来源</div>
      </div>
      <div class="stat-card">
        <div class="stat-val" style="color:var(--accent-amber)">{suspicious_count}</div>
        <div class="stat-label">可疑恶意 IP</div>
        <div class="stat-sub">频控触发</div>
      </div>
    </div>

    <!-- Charts -->
    <div class="grid-2">
      <div class="panel">
        <div class="panel-header">严重程度分布 (Severity Distribution)</div>
        <div class="panel-body">{sev_bars_html}</div>
      </div>
      <div class="panel">
        <div class="panel-header">高频失败操作 (Top Failed Operations)</div>
        <div class="panel-body">{fail_ops_html}</div>
      </div>
    </div>

    <!-- Suspicious IPs Table -->
    <div class="panel" style="margin-bottom:24px;">
      <div class="panel-header">可疑异常行为源 IP 画像 (Suspicious Attackers)</div>
      <div>{suspicious_table_html}</div>
    </div>

    <footer>
      LogScope v1.0 · 日志解析与异常检测引擎 · Java & Python 联动闭环作品集
    </footer>
  </div>
</body>
</html>
"""

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html_content)

    logger.info("HTML report successfully written to: %s", output_path)
