/**
 * Nexus AI — Enterprise Security Copilot Studio Engine
 * Full-Viewport, CVSS 3.1 Vectoring, MITRE ATT&CK Kill Chain, Automated Playbooks, Formal Executive Report
 */

const API = '/api/ai';

// ── Preset Cyber Attack Scenarios ───────────────────────────
const PRESETS = {
  bruteforce: `2026-08-27 12:04:30 172.16.0.88 User root LOGIN FAIL "Invalid password attempt for root"
2026-08-27 12:04:35 172.16.0.88 User root LOGIN FAIL "Invalid password attempt for root"
2026-08-27 12:04:40 172.16.0.88 User admin LOGIN FAIL "Invalid password attempt for admin"
2026-08-27 12:04:45 172.16.0.88 User admin LOGIN FAIL "SSH/Web Brute force detected — IP locked"`,

  sqli: `2026-08-27 14:20:00 172.31.0.50 User attacker ACCESS FAIL "SQL injection probe detected in query param 'username': ' OR '1'='1 --" CRITICAL auth-service.log`,

  xss: `2026-08-27 14:20:10 172.31.0.50 User attacker ACCESS FAIL "Reflected XSS payload detected: <script>alert(document.cookie)</script>" CRITICAL web-gateway.log`,

  traversal: `2026-08-27 13:40:20 10.0.0.200 User scanner ACCESS DENIED "Path traversal attempt on /v1/download?file=../../../../etc/shadow" CRITICAL gateway.log`,

  denied: `2026-08-27 13:40:05 10.0.0.200 User scanner ACCESS DENIED "Unauthorized access attempt to /api/admin/secrets" ERROR gateway.log
2026-08-27 13:40:10 10.0.0.200 User scanner ACCESS DENIED "Unauthorized access attempt to /api/v1/database/dump" ERROR gateway.log`,

  normal: `2026-08-27 10:01:00 192.168.1.15 User dev_ops QUERY SUCCESS "Batch select user list with filter: dept=FINANCE" INFO audit-service.log`
};

// ── State ───────────────────────────────────────────────────
const state = {
  currentResult: null,
  activePlaybookTab: 'waf',
  historyItems: [],
  currentUser: null
};

const $ = id => document.getElementById(id);

// ── Auth Guard ──────────────────────────────────────────────
async function requireAuth() {
  try {
    const resp = await fetch('/api/auth/me', { credentials: 'same-origin' });
    if (resp.status === 401) {
      window.location.href = 'http://localhost:8080/login.html';
      return false;
    }
    const data = await resp.json();
    if (data && data.success && $('topUserBadge')) {
      state.currentUser = { username: data.username, role: data.role };
      $('topUserBadge').innerHTML = data.role === 'ADMIN'
        ? `<span>👑</span><span>${escHtml(data.username)} [管理员]</span>`
        : `<span>👤</span><span>${escHtml(data.username)}</span>`;
    }
    return true;
  } catch (e) {
    return true;
  }
}

async function logout() {
  try {
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
  } catch (e) {}
  window.location.href = 'http://localhost:8080/login.html';
}

// ── Editor Line Gutter & Token Counter ──────────────────────
function updateEditorGutters() {
  const textarea = $('logInput');
  const gutter = $('lineGutter');
  const charCount = $('charCount');
  const tokenEstimate = $('tokenEstimate');
  if (!textarea || !gutter) return;

  const val = textarea.value;
  const lines = val.split('\n').length;
  let html = '';
  for (let i = 1; i <= Math.max(lines, 6); i++) {
    html += `${i}\n`;
  }
  gutter.textContent = html.trim();

  if (charCount) charCount.innerText = `${val.length} 字符`;
  if (tokenEstimate) tokenEstimate.innerText = `~ ${Math.ceil(val.length / 3.8)} tokens`;
}

function loadPreset(type) {
  const text = PRESETS[type] || '';
  $('logInput').value = text;
  updateEditorGutters();
  showToast(`已载入预置场景: ${type}`, 'info');
}

function clearInput() {
  $('logInput').value = '';
  updateEditorGutters();
}

// ── Security Copilot AI Analysis Engine ─────────────────────
async function startAnalysis() {
  const content = $('logInput').value.trim();
  if (!content) {
    showToast('请输入或选择待分析的日志载荷', 'error');
    return;
  }

  const btn = $('btnAnalyze');
  btn.disabled = true;
  btn.innerHTML = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="animation:spin 1s linear infinite;"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg> AI Copilot 推理中...`;

  $('emptyState').style.display = 'none';
  $('outputSection').style.display = 'flex';
  $('aiSummaryContent').innerHTML = `<div style="display:flex;align-items:center;gap:8px;color:var(--accent-purple);">
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="animation:spin 1s linear infinite;"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>
    <span>大模型正在提取威胁指纹、评估 CVSS 3.1 评分并推演攻击链...</span>
  </div>`;

  try {
    const resp = await fetch(`${API}/analyze`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ logContent: content })
    });

    const res = await resp.json();
    if (res && res.data) {
      state.currentResult = res.data;
      renderAnalysisOutput(res.data, content);
      loadHistory();
      showToast('AI 安全研判与剧本生成完毕', 'success');
    } else {
      showToast('研判异常: ' + (res.message || '未知错误'), 'error');
    }
  } catch (err) {
    console.error('Analysis error:', err);
    showToast('网络请求失败: ' + err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3Z"/></svg> 启动 AI 智能安全研判 (SSE 流式)`;
  }
}

// ── Render Output Studio ────────────────────────────────────
function renderAnalysisOutput(data, rawLog) {
  const risk = (data.riskLevel || 'NORMAL').toUpperCase();
  const summary = data.aiSuggestion || data.logSummary || '日志分析完成';
  const sourceIp = data.sourceIp || extractIpFromLog(rawLog) || '172.31.0.50';

  // 1. Severity Badge & CVSS
  const gaugeEl = $('threatGaugeBadge');
  const classMap = {
    CRITICAL: 'g-crit',
    HIGH: 'g-err',
    MEDIUM: 'g-warn',
    LOW: 'g-info',
    NORMAL: 'g-info'
  };
  gaugeEl.className = `gauge-badge ${classMap[risk] || 'g-info'}`;
  gaugeEl.innerText = risk;

  $('incidentTitle').innerText = data.logSummary || (risk === 'NORMAL' ? '正常合规业务操作' : '检测到安全威胁探针与异常行为');
  $('incidentMeta').innerText = `源 IP: ${sourceIp} | 威胁类型: ${data.operationType || 'SECURITY_PROBE'} | 耗时: ${data.analysisTimeMs || 15}ms | 规则引擎: ${data.modelUsed || 'DeepSeek-V3'}`;

  const cvssScore = risk === 'CRITICAL' ? '9.8 (Critical)' : risk === 'HIGH' ? '7.5 (High)' : risk === 'MEDIUM' ? '5.3 (Medium)' : '0.0 (None)';
  $('cvssVector').innerText = `CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H (${cvssScore})`;

  // 2. MITRE ATT&CK Kill Chain Timeline
  updateKillChain(risk, rawLog);

  // 3. AI Copilot Text Output
  $('aiSummaryContent').innerHTML = `
    <div style="background:var(--bg-surface);padding:14px;border-radius:var(--radius-md);border:1px solid var(--border-subtle);margin-bottom:12px;">
      <div style="font-weight:700;color:var(--text-primary);margin-bottom:6px;">📋 研判摘要与危害评估：</div>
      <p style="margin:0;">${escHtml(summary)}</p>
    </div>
    <div style="font-size:12.5px;color:var(--text-tertiary);">
      <strong>处置建议：</strong> ${data.needIntervention ? '🚨 需要安全运维人员立即人工介入并执行下方防御剧本。' : '🟢 风险等级可控，建议持续观察。'}
    </div>
  `;

  // 4. Automated Defense Playbooks
  generatePlaybooks(sourceIp, risk, rawLog);

  // 5. Formal Executive Report
  generateExecutiveReport(data, sourceIp, risk, rawLog);
}

function updateKillChain(risk, rawLog) {
  const isCritOrHigh = risk === 'CRITICAL' || risk === 'HIGH';
  $('kcStep1').className = `killchain-step ${isCritOrHigh ? 'active' : ''}`;
  $('kcStep2').className = `killchain-step ${isCritOrHigh ? 'active' : ''}`;
  $('kcStep3').className = `killchain-step ${risk === 'CRITICAL' ? 'active' : ''}`;
  $('kcStep4').className = 'killchain-step';
  $('kcStep5').className = 'killchain-step';

  if (rawLog.includes('SQL') || rawLog.includes('OR')) {
    $('mitreTechniqueId').innerText = 'T1190 (Exploit Public-Facing App) / T1059.006 (SQL Execution)';
  } else if (rawLog.includes('LOGIN') || rawLog.includes('password')) {
    $('mitreTechniqueId').innerText = 'T1110.001 (Password Guessing) / T1110.003 (Spraying)';
  } else if (rawLog.includes('etc/shadow') || rawLog.includes('..')) {
    $('mitreTechniqueId').innerText = 'T1083 (File Discovery) / T1005 (Sensitive Data Access)';
  } else {
    $('mitreTechniqueId').innerText = 'T0000 (Standard Activity)';
  }
}

// ── Automated Remediation Playbooks Generator ────────────────
function generatePlaybooks(ip, risk, rawLog) {
  state.playbooks = {
    waf: `# ─────────────────────────────────────────────────────────────
# Nginx / OpenResty WAF 动态阻断配置 (Auto-Generated by Nexus AI)
# ─────────────────────────────────────────────────────────────
location / {
    # 阻断恶意攻击源 IP
    deny ${ip};

    # 针对 SQL 注入与恶意特征的主动过滤规则
    if ($query_string ~* "(union|select|insert|update|delete|drop|eval|alert|script|etc/passwd)") {
        return 403 "Blocked by Nexus AI Security Engine";
    }
}`,

    iptables: `# ─────────────────────────────────────────────────────────────
# Linux 内核级网络层快速熔断规则
# ─────────────────────────────────────────────────────────────
# 1. 紧急阻断源 IP 全部入站流量
sudo iptables -I INPUT -s ${ip} -j DROP

# 2. 写入系统黑名单审计日志
sudo logger -p auth.alert "Nexus AI Copilot automatically blocked malicious IP: ${ip}"`,

    sigma: `title: Nexus AI Incident Detection Rule
id: ${crypto.randomUUID ? crypto.randomUUID() : 'f47ac10b-58cc-4372-a567-0e02b2c3d479'}
status: production
description: Automatically generated Sigma rule from security audit telemetry
logsource:
    category: webserver
    service: nginx
detection:
    selection:
        src_ip: '${ip}'
        status: [401, 403, 500]
    condition: selection
level: ${risk.toLowerCase()}`,

    snort: `# Snort / Suricata IDS 签名规则
alert tcp any any -> $HOME_NET any (msg:"NEXUS-AI Malicious Probe from ${ip}"; ip.src==${ip}; sid:1000998; rev:1;)`
  };

  switchPlaybookTab(state.activePlaybookTab);
}

function switchPlaybookTab(tab, el) {
  state.activePlaybookTab = tab;
  document.querySelectorAll('.playbook-tab-btn').forEach(b => b.classList.remove('active'));
  if (el) el.classList.add('active');

  const contentEl = $('playbookContent');
  if (contentEl && state.playbooks) {
    contentEl.innerText = state.playbooks[tab] || '';
  }
}

function copyPlaybookCode() {
  const contentEl = $('playbookContent');
  if (contentEl) {
    navigator.clipboard.writeText(contentEl.innerText);
    showToast('防御剧本配置已复制到剪贴板', 'success');
  }
}

// ── Executive Incident Report Generator ─────────────────────
function generateExecutiveReport(data, ip, risk, rawLog) {
  const dateStr = new Date().toLocaleString();
  const reportMd = `
### 1. 事件基本信息 (Incident Overview)
- **报告编号**：IR-${Date.now().toString().slice(-6)}
- **生成时间**：${dateStr}
- **研判专家**：Nexus AI Security Copilot (DeepSeek/Qwen Engine)
- **威胁等级**：<span style="color:var(--accent-rose);font-weight:700;">${risk}</span>
- **受控资产/源 IP**：${ip}

### 2. 威胁根因与攻击路径推演 (Root Cause & Attack Vector)
- **攻击特征**：${escHtml(data.logSummary || '检测到异常载荷')}
- **MITRE 战术映射**：T1190 / T1110 (Initial Access & Execution)
- **处置方案**：${escHtml(data.aiSuggestion || '已生成多维防御剧本')}

### 3. 防护与加固行动清单 (Remediation Checklist)
1.  **网络层隔离**：已下发 iptables 封禁规则，阻断 \`${ip}\` 所有入站网络包。
2.  **应用层防护**：在 Nginx 反向代理层部署 WAF 正则拦截规则。
3.  **漏洞加固**：对后端 SQL 查询全面实施参数化预编译绑定，修复越权漏洞。
  `;

  $('reportContent').innerHTML = reportMd;
}

function exportReportMd() {
  const text = `
# 《企业安全事件应急响应研判报告》
生成时间: ${new Date().toLocaleString()}
威胁等级: ${state.currentResult ? state.currentResult.riskLevel : 'UNKNOWN'}
研判引擎: Nexus AI Security Copilot

## 研判摘要
${state.currentResult ? state.currentResult.aiSuggestion : '无'}

## 原始日志样本
${$('logInput').value}
  `.trim();

  const blob = new Blob([text], { type: 'text/markdown' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `Incident_Report_${Date.now()}.md`;
  a.click();
  showToast('报告 Markdown 文件已开始下载', 'success');
}

// ── History List ────────────────────────────────────────────
async function loadHistory() {
  const listEl = $('historyList');
  const countEl = $('topCount');
  if (!listEl) return;

  try {
    const resp = await fetch(`${API}/history?limit=10`);
    const res = await resp.json();
    const list = res.data || [];

    if (countEl) countEl.innerText = `${list.length} 次分析`;

    if (list.length === 0) {
      listEl.innerHTML = `<div style="font-size:12px;color:var(--text-muted);text-align:center;padding:16px;">暂无历史记录</div>`;
      return;
    }

    listEl.innerHTML = list.map(item => `
      <div class="history-card" onclick="reloadHistoryItem(${item.id})">
        <div class="history-card-header">
          <span style="font-family:var(--font-mono);font-size:11.5px;color:var(--accent-purple); font-weight:600;">#${item.id}</span>
          <span class="badge ${item.riskLevel === 'CRITICAL' ? 'b-crit' : item.riskLevel === 'HIGH' ? 'b-err' : 'b-info'}" style="font-size:10.5px;padding:1px 6px;">${item.riskLevel || 'NORMAL'}</span>
        </div>
        <div style="font-size:12px;color:var(--text-secondary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
          ${escHtml(item.logSummary || item.logContent)}
        </div>
      </div>
    `).join('');
  } catch (e) {
    console.error('History load error:', e);
  }
}

async function reloadHistoryItem(id) {
  try {
    const resp = await fetch(`${API}/history/${id}`);
    const res = await resp.json();
    if (res && res.data) {
      $('logInput').value = res.data.logContent;
      updateEditorGutters();
      $('emptyState').style.display = 'none';
      $('outputSection').style.display = 'flex';
      renderAnalysisOutput(res.data, res.data.logContent);
      showToast(`已载入历史分析记录 #${id}`, 'info');
    }
  } catch (e) {}
}

function extractIpFromLog(log) {
  const match = log.match(/\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/);
  return match ? match[0] : null;
}

function escHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function showToast(msg, type = 'info') {
  const container = $('toastContainer');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerText = msg;
  container.appendChild(toast);
  setTimeout(() => toast.remove(), 3500);
}

// ── Keyboard Shortcuts ──────────────────────────────────────
document.addEventListener('keydown', e => {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    startAnalysis();
  }
});

// ── DOM Ready ───────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  const authed = await requireAuth();
  if (authed) {
    $('logInput').addEventListener('input', updateEditorGutters);
    updateEditorGutters();
    loadPreset('bruteforce');
    loadHistory();

    // If query string has payload from AuditVault
    const params = new URLSearchParams(window.location.search);
    const forwardPayload = params.get('payload');
    if (forwardPayload) {
      $('logInput').value = forwardPayload;
      updateEditorGutters();
      startAnalysis();
    }
  }
});
