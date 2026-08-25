/**
 * Nexus AI — 日志智能分析助手
 * 设计：渐进披露 —— 输入 → 等待 → 结果，每个状态都明确
 */

const API = '/api/ai';

// ── Preset logs ──────────────────────────────────────────
const PRESETS = {
  bruteforce: `2025-01-15 08:04:30 172.16.0.88 User hacker1 LOGIN FAIL "Invalid password attempt"
2025-01-15 08:04:35 172.16.0.88 User hacker1 LOGIN FAIL "Invalid password attempt"
2025-01-15 08:04:40 172.16.0.88 User root LOGIN FAIL "Invalid password attempt for root"
2025-01-15 08:04:45 172.16.0.88 User root LOGIN FAIL "Brute force detected — IP blocked"`,

  sqli: `2025-01-15 09:20:00 172.31.0.50 User attacker LOGIN FAIL "SQL injection attempt: ' OR '1'='1" CRITICAL auth-service.log`,

  xss: `2025-01-15 09:20:10 172.31.0.50 User attacker LOGIN FAIL "XSS attempt in redirect param: <script>alert(1)</script>" CRITICAL auth-service.log`,

  traversal: `2025-01-15 08:40:20 10.0.0.200 User scanner ACCESS DENIED "Path traversal attempt: ../../etc/passwd" CRITICAL gateway.log`,

  normal: `2025-01-15 08:01:00 192.168.1.15 User lisi QUERY SUCCESS "Query user list with filter: dept=IT" INFO query-service.log`,

  denied: `2025-01-15 08:40:05 10.0.0.200 User scanner ACCESS DENIED "Unauthorized access attempt to /api/users" ERROR gateway.log
2025-01-15 08:40:10 10.0.0.200 User scanner ACCESS DENIED "Unauthorized access attempt to /api/config" ERROR gateway.log`,
};

// ── 认证守卫 + 退出 ──────────────────────────────────────
function logout() {
  fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' })
    .finally(() => { window.location.href = '/login.html'; });
}

function requireAuth() {
  return fetch('/api/auth/me', { credentials: 'same-origin' })
    .then(r => {
      if (r.status === 401) {
        window.location.href = '/login.html?redirect=' + encodeURIComponent(location.pathname);
        return false;
      }
      return r.json();
    })
    .then(data => {
      if (data && data.success && $('topUserBadge')) {
        window.currentUser = { username: data.username, role: data.role };
        if (data.role === 'ADMIN') {
          $('topUserBadge').innerHTML = `<span style="color:var(--accent-amber,#caa351);margin-right:8px;">👑 ${data.username} [管理员]</span>`;
        } else {
          $('topUserBadge').innerHTML = `<span style="color:var(--accent-blue,#679bc9);margin-right:8px;">👤 ${data.username} [普通用户]</span>`;
        }
      }
      return true;
    })
    .catch(() => true);
}


// ── DOM refs ─────────────────────────────────────────────
const $ = id => document.getElementById(id);

// ── Setup ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  requireAuth().then(ok => { if (!ok) return; });
  // Quick chips
  document.querySelectorAll('.chip').forEach(btn => {
    btn.addEventListener('click', () => {
      const preset = PRESETS[btn.dataset.preset];
      if (preset) {
        $('logInput').value = preset;
        updateCharCount();
        $('analyzeBtn').disabled = false;
        $('logInput').focus();
      }
    });
  });

  // Char count
  $('logInput').addEventListener('input', updateCharCount);

  // Ctrl+Enter
  $('logInput').addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      analyze();
    }
  });

  // Overlay close
  $('closeOverlay').addEventListener('click', hideDetail);
  document.querySelector('.overlay-bg').addEventListener('click', hideDetail);
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') hideDetail();
  });

  // Init
  updateClock();
  setInterval(updateClock, 60000);
  loadHistory();
  checkHealth();
});

// ── Char count ───────────────────────────────────────────
function updateCharCount() {
  const len = $('logInput').value.trim().length;
  const el = $('charCount');
  el.textContent = `${len} / 5000`;
  el.className = len > 4500 ? 'char-count warn' : 'char-count';
  $('analyzeBtn').disabled = len < 5;
}

// ── Clock ────────────────────────────────────────────────
function updateClock() {
  $('topTime').textContent = new Date().toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' });
}

// ── Health Check ─────────────────────────────────────────
function checkHealth() {
  fetch(API + '/stats')
    .then(r => r.json())
    .then(d => {
      if (d.code === 200) {
        $('apiDot').classList.remove('off');
        $('topCount').textContent = `${d.data.totalAnalyses} 次分析`;
      }
    })
    .catch(() => {
      $('apiDot').classList.add('off');
      $('topCount').textContent = '离线';
    });
}

// ── Analyze (SSE Stream) ─────────────────────────────────
async function analyze() {
  const logContent = $('logInput').value.trim();
  if (logContent.length < 5) return;

  hideResult();
  hideError();
  showLoading();
  $('analyzeBtn').classList.add('loading');
  $('analyzeBtn').textContent = 'AI 推理中…';

  try {
    const resp = await fetch(API + '/analyze-stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ logContent }),
    });

    if (!resp.ok) {
      throw new Error(`HTTP ${resp.status}`);
    }

    const reader = resp.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let streamText = '';
    let parsedResult = null;

    hideLoading();
    // Prepare result card in streaming state
    $('resMeta').innerHTML = '<span class="tag tag-op">流式生成中...</span>';
    $('resTags').innerHTML = '';
    $('resSummary').textContent = '';
    $('resSuggestion').textContent = '';
    $('resultCard').classList.remove('hidden');
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
            } catch (e) {
              console.warn('Failed to parse final done data', e);
            }
          }
        }
      }
    }

    if (parsedResult) {
      renderResult(parsedResult);
    }
  } catch (err) {
    console.warn('SSE stream failed, falling back to sync endpoint', err);
    try {
      const syncResp = await fetch(API + '/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ logContent }),
      });
      const data = await syncResp.json();
      if (data.code === 200) {
        renderResult(data.data);
      } else {
        showError(data.message || '分析失败');
      }
    } catch (syncErr) {
      showError(`无法连接到后端服务 (${syncErr.message})`);
    }
  } finally {
    hideLoading();
    $('analyzeBtn').classList.remove('loading');
    $('analyzeBtn').textContent = '分析';
    loadHistory();
    checkHealth();
  }
}

// ── Render Result ────────────────────────────────────────
function renderResult(result) {
  const card = $('resultCard');

  // Meta
  let metaText = `${result.modelUsed || 'AI'} · ${result.analysisTimeMs || '—'}ms`;
  if (result.fallback) {
    metaText += ' · <span class="fallback-badge" title="AI API Key 未配置或调用失败，使用本地关键词规则引擎">降级模式</span>';
  }
  $('resMeta').innerHTML = metaText;

  // Tags
  let tags = '';
  if (result.operationType) {
    tags += `<span class="tag tag-op">${esc(result.operationType)}</span>`;
  }
  if (result.riskLevel) {
    tags += `<span class="tag tag-risk-${result.riskLevel}">${result.riskLevel}</span>`;
  }
  if (result.needIntervention) {
    tags += `<span class="tag tag-intervene">需人工介入</span>`;
  } else {
    tags += `<span class="tag tag-safe">无需介入</span>`;
  }
  if (result.sourceIp) {
    tags += `<span class="tag tag-op">IP ${esc(result.sourceIp)}</span>`;
  }
  $('resTags').innerHTML = tags;

  $('resSummary').textContent = result.summary || '—';
  $('resSuggestion').textContent = result.suggestion || '—';

  // 保存当前分析结果供上报与导出
  window.currentAnalysisResult = result;

  card.classList.remove('hidden');
  card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

// ── Toast & Actions ──────────────────────────────────────
function showToast(msg, type = 'info') {
  const container = document.getElementById('toastContainer');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `<span style="font-size:1.1rem;">${type === 'success' ? '✓' : type === 'error' ? '!' : 'ℹ'}</span><span>${esc(msg)}</span>`;
  container.appendChild(toast);
  setTimeout(() => {
    if (toast.parentNode) toast.parentNode.removeChild(toast);
  }, 4500);
}

async function reportToAuditVault() {
  const res = window.currentAnalysisResult;
  if (!res) {
    showToast('暂无分析结果可上报', 'error');
    return;
  }
  const btn = $('btnReportAudit');
  btn.disabled = true;
  btn.textContent = '正在推送至 AuditVault…';

  const payload = {
    time: new Date().toISOString(),
    level: res.riskLevel === 'CRITICAL' ? 'CRITICAL' : (res.riskLevel === 'HIGH' ? 'ERROR' : (res.riskLevel === 'MEDIUM' ? 'WARN' : 'INFO')),
    logger: 'com.logai.NexusAiSecurityAnalyzer',
    message: `[Nexus AI告警] ${res.summary || '检测到异常安全日志'}`,
    detail: `[AI摘要] ${res.summary || '—'}\n[处置建议] ${res.suggestion || '—'}\n[风险等级] ${res.riskLevel || 'NORMAL'}\n[操作类型] ${res.operationType || 'UNKNOWN'}\n[需人工介入] ${res.needIntervention ? '是' : '否'}\n[分析模型] ${res.modelUsed || 'AI'}\n[原始日志] ${$('logInput').value.trim()}`,
    user: 'nexus-ai',
    clientIp: res.sourceIp || '127.0.0.1',
    operation: res.operationType || 'SECURITY_ANALYSIS'
  };

  try {
    const auditHost = window.location.hostname;
    const resp = await fetch(`http://${auditHost}:8080/api/logs/webhook`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Audit-Token': 'auditvault-webhook-default-secret-token-2026'
      },
      body: JSON.stringify(payload)
    });
    const data = await resp.json();
    if (resp.ok && data.success) {
      showToast('✓ 已成功将本次安全分析告警推送到 AuditVault 实时归档！', 'success');
    } else {
      showToast('上报失败: ' + (data.message || ('HTTP ' + resp.status)), 'error');
    }
  } catch (e) {
    showToast('上报失败 (请确认 AuditVault 8080 服务已运行): ' + e.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = '📡 一键上报告警至 AuditVault (Webhook)';
  }
}

function copyMarkdownReport() {
  const res = window.currentAnalysisResult;
  if (!res) {
    showToast('暂无分析结果可复制', 'error');
    return;
  }
  const originalLog = $('logInput').value.trim();
  const md = `# 🛡️ Nexus AI 安全分析事件报告

- **报告时间**: ${new Date().toLocaleString('zh-CN')}
- **风险等级**: \`${res.riskLevel || 'NORMAL'}\`
- **操作类型**: \`${res.operationType || 'UNKNOWN'}\`
- **来源 IP**: \`${res.sourceIp || '未知'}\`
- **需人工介入**: ${res.needIntervention ? '🚨 **是**' : '🟢 **否**'}
- **分析模型**: ${res.modelUsed || 'AI'} (${res.analysisTimeMs || 0}ms)

---

### 📌 1. 事件摘要
${res.summary || '无摘要'}

### 💡 2. 处置与防御建议
${res.suggestion || '无建议'}

### 📋 3. 原始日志片段
\`\`\`log
${originalLog}
\`\`\`
`;

  navigator.clipboard.writeText(md)
    .then(() => showToast('✓ Markdown 安全报告已成功复制到剪贴板！', 'success'))
    .catch(() => showToast('复制到剪贴板失败，请手动复制', 'error'));
}


// ── UI State ─────────────────────────────────────────────
function showLoading() { $('loadingBlock').classList.remove('hidden'); }
function hideLoading() { $('loadingBlock').classList.add('hidden'); }
function showError(msg) { $('errorBlock').textContent = msg; $('errorBlock').classList.remove('hidden'); }
function hideError() { $('errorBlock').classList.add('hidden'); }
function hideResult() { $('resultCard').classList.add('hidden'); }

// ── History ──────────────────────────────────────────────
function loadHistory() {
  fetch(API + '/history?limit=20')
    .then(r => r.json())
    .then(data => {
      const list = data.data || [];
      $('historyCount').textContent = `${list.length} 条记录`;

      if (list.length === 0) {
        $('historyList').innerHTML =
          '<div class="history-none">暂无分析记录<br><span style="font-size:0.8rem;">提交一条日志后，这里会出现历史</span></div>';
        return;
      }

      $('historyList').innerHTML = list.map(item => `
        <div class="history-item" onclick="showDetail(${item.id})">
          <div class="history-item-header">
            <span class="tag tag-risk-${item.riskLevel || 'NORMAL'}" style="font-size:0.62rem;padding:2px 8px;">
              ${item.riskLevel || 'NORMAL'}
            </span>
            <span style="font-family:var(--font-mono);font-size:0.65rem;color:var(--text-tertiary);">
              ${fmt(item.createdAt)}
            </span>
          </div>
          <div class="history-item-summary">${esc(item.logSummary || item.logContent || '')}</div>
          <div class="history-item-meta">
            <span>${item.operationType || '—'}</span>
            <span>${item.modelUsed || '—'}</span>
            ${item.needIntervention ? '<span style="color:var(--accent-red);">需介入</span>' : ''}
          </div>
        </div>
      `).join('');
    })
    .catch(() => {
      $('historyList').innerHTML =
        '<div class="history-none">加载失败，请确认后端服务已启动</div>';
    });
}

// ── Detail Overlay ───────────────────────────────────────
function showDetail(id) {
  fetch(API + '/history/' + id)
    .then(r => r.json())
    .then(data => {
      if (data.code !== 200) { alert('记录不存在'); return; }
      const item = data.data;

      $('overlayBody').innerHTML = `
        <div class="overlay-field">
          <div class="overlay-field-label">原始日志</div>
          <pre>${esc(item.logContent || '')}</pre>
        </div>
        <div class="overlay-field">
          <div class="overlay-field-label">AI 摘要</div>
          <div class="overlay-field-value">${esc(item.logSummary || '—')}</div>
        </div>
        <div class="overlay-field">
          <div class="overlay-field-label">操作类型 / 风险等级</div>
          <div class="overlay-field-value">
            <span class="tag tag-op">${esc(item.operationType || '—')}</span>
            <span class="tag tag-risk-${item.riskLevel || 'NORMAL'}">${item.riskLevel || 'NORMAL'}</span>
          </div>
        </div>
        <div class="overlay-field">
          <div class="overlay-field-label">处置建议</div>
          <div class="overlay-field-value">${esc(item.aiSuggestion || '—')}</div>
        </div>
        <div class="overlay-field">
          <div class="overlay-field-label">模型 / 耗时</div>
          <div class="overlay-field-value">${item.modelUsed || '—'} / ${item.analysisTimeMs ? item.analysisTimeMs + 'ms' : '—'}</div>
        </div>
        <div class="overlay-field">
          <div class="overlay-field-label">分析时间</div>
          <div class="overlay-field-value">${fmt(item.createdAt)}</div>
        </div>
      `;

      $('detailOverlay').classList.remove('hidden');
    })
    .catch(() => { alert('加载详情失败'); });
}

function hideDetail() {
  $('detailOverlay').classList.add('hidden');
}

// ── Helpers ──────────────────────────────────────────────
function esc(s) {
  if (!s) return '';
  const el = document.createElement('span');
  el.textContent = String(s);
  return el.innerHTML;
}

function fmt(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
