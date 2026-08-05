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

// ── DOM refs ─────────────────────────────────────────────
const $ = id => document.getElementById(id);

// ── Setup ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
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

// ── Analyze ──────────────────────────────────────────────
async function analyze() {
  const logContent = $('logInput').value.trim();
  if (logContent.length < 5) return;

  hideResult();
  hideError();
  showLoading();
  $('analyzeBtn').classList.add('loading');
  $('analyzeBtn').textContent = '分析中…';

  try {
    const resp = await fetch(API + '/analyze', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ logContent }),
    });
    const data = await resp.json();

    if (data.code === 200) {
      renderResult(data.data);
    } else {
      showError(data.message || '分析失败');
    }
  } catch (err) {
    showError(`无法连接到后端服务。请确认服务已启动 (${err.message})`);
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
  $('resMeta').textContent = `${result.modelUsed || 'AI'} · ${result.analysisTimeMs || '—'}ms`;

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

  card.classList.remove('hidden');
  card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
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
