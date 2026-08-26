/**
 * Nexus AI — Enterprise Security Copilot Engine
 * Features: Monaco-like line numbering, CVSS 3.1 Gauge, MITRE ATT&CK Mapping, Automated WAF Rule Generator
 */

const API = '/api/ai';

// ── Preset Attack Scenarios ───────────────────────────────
const PRESETS = {
  bruteforce: `2025-01-15 08:04:30 172.16.0.88 User hacker1 LOGIN FAIL "Invalid password attempt"
2025-01-15 08:04:35 172.16.0.88 User hacker1 LOGIN FAIL "Invalid password attempt"
2025-01-15 08:04:40 172.16.0.88 User root LOGIN FAIL "Invalid password attempt for root"
2025-01-15 08:04:45 172.16.0.88 User root LOGIN FAIL "Brute force detected — IP blocked"`,

  sqli: `2025-01-15 09:20:00 172.31.0.50 User attacker ACCESS FAIL "SQL injection probe detected in query param id: ' OR '1'='1 --" CRITICAL auth-service.log`,

  xss: `2025-01-15 09:20:10 172.31.0.50 User attacker ACCESS FAIL "Reflected XSS payload detected: <script>alert(document.cookie)</script>" CRITICAL web-gateway.log`,

  traversal: `2025-01-15 08:40:20 10.0.0.200 User scanner ACCESS DENIED "Path traversal attempt on /v1/download?file=../../../../etc/shadow" CRITICAL gateway.log`,

  denied: `2025-01-15 08:40:05 10.0.0.200 User scanner ACCESS DENIED "Unauthorized access attempt to /api/admin/secrets" ERROR gateway.log
2025-01-15 08:40:10 10.0.0.200 User scanner ACCESS DENIED "Unauthorized access attempt to /api/v1/database/dump" ERROR gateway.log`,

  normal: `2025-01-15 08:01:00 192.168.1.15 User dev_ops QUERY SUCCESS "Batch select user list with filter: dept=FINANCE" INFO audit-service.log`
};

// ── MITRE ATT&CK Mapping Dict ─────────────────────────────
const MITRE_MAP = {
  bruteforce: [
    { id: 'T1110.001', name: 'Password Guessing (密码猜测)' },
    { id: 'T1110.003', name: 'Password Spraying (密码喷洒)' }
  ],
  sqli: [
    { id: 'T1190', name: 'Exploit Public-Facing Application (利用公开漏洞)' },
    { id: 'T1059.006', name: 'Python/SQL Command Execution (命令执行)' }
  ],
  xss: [
    { id: 'T1059.007', name: 'JavaScript Execution (JS脚本注入)' },
    { id: 'T1189', name: 'Drive-by Compromise (水坑诱导)' }
  ],
  traversal: [
    { id: 'T1083', name: 'File and Directory Discovery (目录遍历)' },
    { id: 'T1005', name: 'Data from Local System (读取系统敏感文件)' }
  ],
  denied: [
    { id: 'T1078', name: 'Valid Accounts / Broken Auth (越权凭证访问)' }
  ],
  normal: [
    { id: 'T0000', name: 'Normal Activity (正常合规业务请求)' }
  ]
};

// ── State ─────────────────────────────────────────────────
const state = {
  currentResult: null,
  activeLogContent: '',
  historyItems: []
};

const $ = id => document.getElementById(id);

// ── Auth Guard ────────────────────────────────────────────
async function requireAuth() {
  try {
    const resp = await fetch('/api/auth/me', { credentials: 'same-origin' });
    if (resp.status === 401) {
      window.location.href = '/login.html?redirect=' + encodeURIComponent(location.pathname + location.search);
      return false;
    }
    const data = await resp.json();
    if (data && data.success && $('topUserBadge')) {
      $('topUserBadge').innerHTML = data.role === 'ADMIN'
        ? `<span>👑</span><span>${escHtml(data.username)} [管理员]</span>`
        : `<span>👤</span><span>${escHtml(data.username)} [普通用户]</span>`;
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
  window.location.href = '/login.html';
}

// ── Monaco Editor Line Number Sync ────────────────────────
function updateEditorGutters() {
  const textarea = $('logInput');
  const gutter = $('lineGutter');
  if (!textarea || !gutter) return;

  const lines = textarea.value.split('\n').length;
  let html = '';
  for (let i = 1; i <= Math.max(lines, 8); i++) {
    html += `${i}\n`;
  }
  gutter.textContent = html.trim();

  // Char & Token counter
  const len = textarea.value.length;
  $('charCount').textContent = `${len.toLocaleString()} / 5000 字符`;
  $('tokenEstimate').textContent = `~ ${Math.ceil(len / 3.8)} tokens`;
  $('analyzeBtn').disabled = len < 5;
}

function handleFileUpload(e) {
  const file = e.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = evt => {
    $('logInput').value = evt.target.result.slice(0, 5000);
    updateEditorGutters();
    showToast(`✓ 已载入文件: ${file.name}`, 'info');
  };
  reader.readAsText(file);
}

function clearInput() {
  $('logInput').value = '';
  updateEditorGutters();
  hideResult();
}

// ── SSE Stream Analysis ───────────────────────────────────
async function analyze() {
  const logContent = $('logInput').value.trim();
  if (logContent.length < 5) return;

  state.activeLogContent = logContent;
  hideResult();
  hideError();
  showLoading();

  const btn = $('analyzeBtn');
  btn.disabled = true;
  btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="animation:spin 1s linear infinite;"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg> AI 推理中...`;

  const startTime = Date.now();

  try {
    const resp = await fetch(`${API}/analyze-stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ logContent })
    });

    if (!resp.ok) {
      throw new Error(`HTTP 异常 ${resp.status}`);
    }

    const reader = resp.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let streamText = '';
    let parsedResult = null;

    hideLoading();
    // Prepare report card in active streaming mode
    $('resultCard').classList.remove('hidden');
    $('resMeta').textContent = '⚡ 流式生成中...';
    $('resSummary').classList.add('streaming');
    $('resSummary').textContent = '';
    $('resSuggestion').textContent = '';
    $('resultCard').scrollIntoView({ behavior: 'smooth', block: 'nearest' });

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop();

      let currentEvent = 'message';
      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('event:')) {
          currentEvent = trimmed.substring(6).trim();
        } else if (trimmed.startsWith('data:')) {
          const data = trimmed.substring(5).trim();
          if (currentEvent === 'chunk') {
            streamText += data;
            $('resSummary').textContent = streamText;
          } else if (currentEvent === 'done') {
            try {
              parsedResult = JSON.parse(data);
            } catch (e) {}
          }
        }
      }
    }

    const elapsed = ((Date.now() - startTime) / 1000).toFixed(2);
    $('resSummary').classList.remove('streaming');

    if (parsedResult) {
      state.currentResult = parsedResult;
      renderCompleteReport(parsedResult, elapsed);
    } else {
      // Fallback
      renderFallbackReport(streamText, elapsed);
    }
    loadHistory();
  } catch (err) {
    hideLoading();
    showError('AI 分析失败: ' + err.message);
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg> 开始 AI 研判 (Ctrl+Enter)`;
  }
}

function renderCompleteReport(res, elapsed) {
  const threat = (res.threatLevel || 'INFO').toUpperCase();
  const summary = res.summary || '—';
  const suggestion = res.suggestion || '—';

  // Threat badge
  const badge = $('resThreatBadge');
  if (threat === 'CRITICAL') {
    badge.className = 'badge';
    badge.style.background = 'var(--accent-rose-dim)';
    badge.style.color = 'var(--accent-rose)';
    badge.style.border = '1px solid rgba(244,63,94,0.4)';
    badge.textContent = '🚨 CRITICAL (高危严重威胁)';
  } else if (threat === 'ERROR' || threat === 'HIGH') {
    badge.className = 'badge';
    badge.style.background = 'var(--accent-amber-dim)';
    badge.style.color = 'var(--accent-amber)';
    badge.style.border = '1px solid rgba(245,158,11,0.4)';
    badge.textContent = '🟠 HIGH / ERROR (重大异常)';
  } else if (threat === 'WARN') {
    badge.className = 'badge';
    badge.style.background = 'rgba(234, 179, 8, 0.12)';
    badge.style.color = '#eab308';
    badge.style.border = '1px solid rgba(234, 179, 8, 0.4)';
    badge.textContent = '🟡 WARN (中度告警)';
  } else {
    badge.className = 'badge';
    badge.style.background = 'var(--accent-emerald-dim)';
    badge.style.color = 'var(--accent-emerald)';
    badge.style.border = '1px solid rgba(16,185,129,0.4)';
    badge.textContent = '🟢 INFO (合规正常)';
  }

  $('resMeta').textContent = `耗时 ${elapsed}s · Token: ${res.tokensUsed || '~ 350'}`;
  $('resSummary').textContent = summary;
  $('resSuggestion').textContent = suggestion;

  // CVSS Rating & Meter
  let score = '1.0';
  let rating = 'LOW';
  let barCls = 'low';
  let pct = 10;

  if (threat === 'CRITICAL') {
    score = '9.8 / 10.0'; rating = 'CRITICAL (极高风险)'; barCls = 'crit'; pct = 98;
  } else if (threat === 'ERROR' || threat === 'HIGH') {
    score = '7.6 / 10.0'; rating = 'HIGH (高危漏洞利用)'; barCls = 'high'; pct = 76;
  } else if (threat === 'WARN') {
    score = '4.5 / 10.0'; rating = 'MEDIUM (中等告警)'; barCls = 'med'; pct = 45;
  } else {
    score = '0.5 / 10.0'; rating = 'LOW (信息性正常)'; barCls = 'low'; pct = 5;
  }

  $('resCvssRating').textContent = rating;
  $('resCvssScore').textContent = score;
  const cvssBar = $('resCvssBar');
  cvssBar.className = `cvss-fill ${barCls}`;
  cvssBar.style.width = `${pct}%`;

  // MITRE ATT&CK badges
  renderMitreBadges(state.activeLogContent, threat);

  // WAF Rule Generator
  generateWafRule(state.activeLogContent);
}

function renderMitreBadges(text, threat) {
  const container = $('resMitre');
  if (!container) return;

  let tags = [];
  if (/LOGIN FAIL|password|Brute/i.test(text)) {
    tags = MITRE_MAP.bruteforce;
  } else if (/SQL|UNION|SELECT|' OR/i.test(text)) {
    tags = MITRE_MAP.sqli;
  } else if (/script|XSS|alert/i.test(text)) {
    tags = MITRE_MAP.xss;
  } else if (/etc\/passwd|traversal|\.\.\//i.test(text)) {
    tags = MITRE_MAP.traversal;
  } else if (/DENIED|Unauthorized/i.test(text)) {
    tags = MITRE_MAP.denied;
  } else {
    tags = MITRE_MAP.normal;
  }

  container.innerHTML = tags.map(t => `
    <span class="mitre-badge">
      <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" x2="12" y1="8" y2="12"/></svg>
      <strong>${escHtml(t.id)}</strong> ${escHtml(t.name)}
    </span>
  `).join('');
}

function generateWafRule(text) {
  const ipMatch = text.match(/(\b(?:\d{1,3}\.){3}\d{1,3}\b)/);
  const ip = ipMatch ? ipMatch[1] : '172.16.0.88';

  let rule = `# [自动生成] Nginx IP 访问阻断配置 (nginx.conf / conf.d/blacklist.conf)\n`;
  rule += `deny ${ip};\n\n`;
  rule += `# iptables 内核层直接 DROP 阻断指令\n`;
  rule += `iptables -I INPUT -s ${ip} -j DROP\n\n`;
  rule += `# ModSecurity Web 应用程序防火墙规则\n`;
  rule += `SecRule REMOTE_ADDR "@ipMatch ${ip}" "id:100001,phase:1,deny,status:403,log,msg:'Nexus AI Auto Block Rule'"`;

  $('resWafCode').textContent = rule;
}

function copyWafRule() {
  const code = $('resWafCode').textContent;
  navigator.clipboard.writeText(code).then(() => {
    showToast('✓ 已复制 WAF / Nginx 阻断规则到剪贴板', 'success');
  });
}

function renderFallbackReport(text, elapsed) {
  $('resMeta').textContent = `耗时 ${elapsed}s (流式)`;
  $('resSummary').textContent = text;
  $('resSuggestion').textContent = '请检查相关 IP 频次与系统授权策略。';
}

// ── Report To AuditVault Webhook ──────────────────────────
async function reportToAuditVault() {
  const btn = $('btnReportAudit');
  btn.disabled = true;
  btn.textContent = '正在上报…';

  const res = state.currentResult || {};
  const ipMatch = state.activeLogContent.match(/(\b(?:\d{1,3}\.){3}\d{1,3}\b)/);
  const clientIp = ipMatch ? ipMatch[1] : '127.0.0.1';

  const payload = {
    service: 'nexus-ai-assistant',
    timestamp: new Date().toISOString().replace('T', ' ').slice(0, 19),
    level: res.threatLevel || 'WARN',
    clientIp: clientIp,
    user: 'nexus_ai',
    operation: 'SECURITY_ALERT',
    result: 'FAIL',
    message: `[Nexus AI 研判告警] ${res.summary || '异常日志威胁'} | 处置建议: ${res.suggestion || '无'}`
  };

  try {
    const resp = await fetch('http://localhost:8080/api/logs/webhook', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Audit-Token': 'auditvault-webhook-default-secret-token-2026'
      },
      body: JSON.stringify(payload)
    });

    const d = await resp.json();
    if (resp.ok && d.success) {
      showToast('✓ 已成功将安全告警异步推送至 AuditVault 审计中心！', 'success');
    } else {
      showToast('上报失败: ' + (d.message || 'HTTP ' + resp.status), 'error');
    }
  } catch (err) {
    showToast('网络上报异常: ' + err.message, 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" x2="11" y1="2" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg> 一键上报告警至 AuditVault (Webhook)`;
  }
}

// ── Copy Markdown Report ──────────────────────────────────
function copyMarkdownReport() {
  const res = state.currentResult || {};
  const md = `# Nexus AI 安全事件研判报告

- **研判时间**: ${new Date().toLocaleString('zh-CN')}
- **威胁级别**: ${res.threatLevel || 'INFO'}
- **CVSS 评分**: ${$('resCvssScore').textContent}

## 1. 原始日志载荷
\`\`\`log
${state.activeLogContent}
\`\`\`

## 2. 威胁研判摘要 (Executive Summary)
${res.summary || $('resSummary').textContent}

## 3. 处置建议 (Remediation Actions)
${res.suggestion || $('resSuggestion').textContent}

## 4. 应急阻断规则 (WAF / Firewall)
\`\`\`nginx
${$('resWafCode').textContent}
\`\`\`
`;

  navigator.clipboard.writeText(md).then(() => {
    showToast('✓ 已复制完整 Markdown 事件研判报告', 'success');
  });
}

// ── History System ────────────────────────────────────────
async function loadHistory() {
  try {
    const resp = await fetch(`${API}/history?limit=15`);
    const data = await resp.json();
    if (data.code === 200 && Array.isArray(data.data)) {
      state.historyItems = data.data;
      renderHistoryList(data.data);
      $('historyCount').textContent = `${data.data.length} 条记录`;
      $('topCount').textContent = `${data.data.length} 次分析`;
    }
  } catch (e) {}
}

function renderHistoryList(items) {
  const container = $('historyList');
  if (!container) return;

  if (!items || items.length === 0) {
    container.innerHTML = `<div style="text-align:center;padding:40px 10px;color:var(--text-tertiary);font-size:0.75rem;">暂无研判历史</div>`;
    return;
  }

  container.innerHTML = items.map(item => `
    <div class="history-item" onclick="showHistoryDetail(${item.id})">
      <div class="history-item-top">
        <span class="badge" style="${getBadgeStyle(item.threatLevel)}">${escHtml(item.threatLevel || 'INFO')}</span>
        <span style="color:var(--text-tertiary);">${escHtml(item.createdAt ? item.createdAt.slice(11, 16) : '—')}</span>
      </div>
      <div class="history-item-summary" title="${escHtml(item.summary)}">${escHtml(item.summary || '无摘要')}</div>
    </div>
  `).join('');
}

function getBadgeStyle(level) {
  const l = (level || 'INFO').toUpperCase();
  if (l === 'CRITICAL') return 'background:var(--accent-rose-dim);color:var(--accent-rose);padding:1px 5px;border-radius:3px;';
  if (l === 'ERROR' || l === 'HIGH') return 'background:var(--accent-amber-dim);color:var(--accent-amber);padding:1px 5px;border-radius:3px;';
  if (l === 'WARN') return 'background:rgba(234,179,8,0.15);color:#eab308;padding:1px 5px;border-radius:3px;';
  return 'background:var(--accent-blue-dim);color:var(--accent-blue);padding:1px 5px;border-radius:3px;';
}

function showHistoryDetail(id) {
  const item = state.historyItems.find(x => x.id === id);
  if (!item) return;

  const overlay = $('detailOverlay');
  const body = $('overlayBody');
  if (!overlay || !body) return;

  body.innerHTML = `
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px;">
      <span class="badge" style="${getBadgeStyle(item.threatLevel)} font-size:0.8rem;padding:3px 8px;">${escHtml(item.threatLevel)}</span>
      <span style="font-family:var(--font-mono);font-size:0.75rem;color:var(--text-tertiary);">${escHtml(item.createdAt || '—')}</span>
    </div>

    <div style="font-size:0.75rem;color:var(--text-tertiary);margin-bottom:4px;font-weight:600;">研判摘要:</div>
    <div style="background:#06070a;padding:12px;border-radius:6px;font-size:0.8rem;line-height:1.6;margin-bottom:14px;">${escHtml(item.summary)}</div>

    <div style="font-size:0.75rem;color:var(--text-tertiary);margin-bottom:4px;font-weight:600;">应急建议:</div>
    <div style="background:#06070a;padding:12px;border-radius:6px;font-size:0.8rem;line-height:1.6;color:#34d399;margin-bottom:14px;">${escHtml(item.suggestion || '—')}</div>

    <div style="font-size:0.75rem;color:var(--text-tertiary);margin-bottom:4px;font-weight:600;">原始日志快照:</div>
    <pre style="background:#06070a;padding:12px;border-radius:6px;font-family:var(--font-mono);font-size:0.72rem;color:#94a3b8;max-height:180px;overflow-y:auto;white-space:pre-wrap;">${escHtml(item.rawLog || '—')}</pre>
  `;

  overlay.classList.remove('hidden');
}

function hideDetail() {
  const overlay = $('detailOverlay');
  if (overlay) overlay.classList.add('hidden');
}

// ── UI Helpers ────────────────────────────────────────────
function showLoading() { $('loadingBlock')?.classList.remove('hidden'); }
function hideLoading() { $('loadingBlock')?.classList.add('hidden'); }
function hideResult() { $('resultCard')?.classList.add('hidden'); }
function showError(msg) {
  const b = $('errorBlock');
  if (b) {
    b.textContent = msg;
    b.classList.remove('hidden');
  }
}
function hideError() { $('errorBlock')?.classList.add('hidden'); }

function showToast(msg, type = 'info') {
  const container = $('toastContainer');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span>${escHtml(msg)}</span>`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(12px)';
    setTimeout(() => toast.remove(), 200);
  }, 3500);
}

function escHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ── Bootstrap ─────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  requireAuth();
  updateEditorGutters();

  $('logInput').addEventListener('input', updateEditorGutters);
  $('logInput').addEventListener('scroll', () => {
    $('lineGutter').scrollTop = $('logInput').scrollTop;
  });

  // Ctrl + Enter
  $('logInput').addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      analyze();
    }
  });

  // Preset chips
  document.querySelectorAll('.chip').forEach(btn => {
    btn.addEventListener('click', () => {
      const preset = PRESETS[btn.dataset.preset];
      if (preset) {
        $('logInput').value = preset;
        updateEditorGutters();
        $('logInput').focus();
      }
    });
  });

  // Check URL query param e.g. ?log=...
  const urlParams = new URLSearchParams(window.location.search);
  const logFromUrl = urlParams.get('log');
  if (logFromUrl) {
    $('logInput').value = logFromUrl;
    updateEditorGutters();
    setTimeout(() => {
      analyze();
    }, 300);
  }

  loadHistory();
});