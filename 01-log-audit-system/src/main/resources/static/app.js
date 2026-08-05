/**
 * AuditVault — 日志审计系统前端
 * 设计原则: 每个 UI 状态都有归宿 — 加载、空数据、错误、正常
 */

const API = '/api/logs';

// ── DOM refs ────────────────────────────────────────────
const dom = {
  get tableBody()    { return document.getElementById('tableBody'); },
  get pagination()   { return document.getElementById('pagination'); },
  get resultMeta()   { return document.getElementById('resultMeta'); },
  get statTotal()    { return document.getElementById('statTotal'); },
  get statAbnormal() { return document.getElementById('statAbnormal'); },
  get statRate()     { return document.getElementById('statRate'); },
  get statIps()      { return document.getElementById('statIps'); },
  get statStatus()   { return document.getElementById('statStatus'); },
  get statDate()     { return document.getElementById('statDate'); },
  get statPort()     { return document.getElementById('statPort'); },
  get navIndicator() { return document.getElementById('navIndicator'); },
  get navStatus()    { return document.getElementById('navStatus'); },
  get btnSearch()    { return document.getElementById('btnSearch'); },
};

// ── Skeleton screen ─────────────────────────────────────
function renderSkeleton() {
  let html = '';
  for (let i = 0; i < 8; i++) {
    const widths = ['85%','60%','78%','55%','70%','45%','65%','80%'];
    html += `<tr class="skeleton-row"><td colspan="8">
      <div class="skeleton-line" style="width:${widths[i]}"></div>
    </td></tr>`;
  }
  dom.tableBody.innerHTML = html;
  dom.pagination.innerHTML = '';
  dom.resultMeta.textContent = '';
}

function renderError(msg) {
  dom.tableBody.innerHTML = `<tr class="table-empty"><td colspan="8">
    <span class="table-empty-icon">!</span>${escHtml(msg || '加载失败，请确认后端服务已启动')}
  </td></tr>`;
  dom.pagination.innerHTML = '';
  dom.resultMeta.textContent = '';
}

function renderEmpty() {
  dom.tableBody.innerHTML = `<tr class="table-empty"><td colspan="8">
    <span class="table-empty-icon">—</span>没有匹配的日志记录
  </td></tr>`;
  dom.pagination.innerHTML = '';
  dom.resultMeta.textContent = '0 条结果';
}

// ── Severity badge ──────────────────────────────────────
function badge(level) {
  const s = (level || 'INFO').toUpperCase();
  const cls = { INFO:'info', WARN:'warning', WARNING:'warning', ERROR:'error', CRITICAL:'critical' }[s] || 'info';
  return `<span class="badge badge-${cls}">${s}</span>`;
}

// ── Format time ─────────────────────────────────────────
function fmt(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function escHtml(s) {
  if (!s) return '';
  const el = document.createElement('span');
  el.textContent = String(s);
  return el.innerHTML;
}

// ── Search ──────────────────────────────────────────────
let currentPage = 1;

function search(page) {
  const p = page || 1;
  currentPage = p;

  const params = new URLSearchParams({
    ipAddress: (document.getElementById('ipFilter').value || '').trim(),
    operation: document.getElementById('operationFilter').value,
    severity:  document.getElementById('severityFilter').value,
    page: p,
    pageSize: 20,
  });

  renderSkeleton();
  dom.btnSearch.disabled = true;

  fetch(API + '?' + params.toString())
    .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
    .then(data => {
      if (!data.records || data.records.length === 0) {
        renderEmpty();
        dom.resultMeta.textContent = '0 条结果';
        return;
      }
      dom.tableBody.innerHTML = data.records.map(item => `
        <tr>
          <td>${item.id}</td>
          <td>${fmt(item.timestamp)}</td>
          <td><span title="${escHtml(item.ipAddress)}">${escHtml(item.ipAddress)}</span></td>
          <td>${escHtml(item.username)}</td>
          <td>${escHtml(item.operation)}</td>
          <td>${escHtml(item.operationResult)}</td>
          <td>${badge(item.severity)}</td>
          <td class="col-view"><a href="detail.html?id=${item.id}">查看</a></td>
        </tr>
      `).join('');
      dom.resultMeta.textContent = `共 ${data.total} 条 · 第 ${data.page} 页`;
      renderPagination(data.total, data.page, data.pageSize);
    })
    .catch(err => { renderError(err.message); })
    .finally(() => { dom.btnSearch.disabled = false; });
}

// ── Pagination ──────────────────────────────────────────
function renderPagination(total, current, size) {
  const totalPages = Math.ceil(total / size);
  if (totalPages <= 1) { dom.pagination.innerHTML = ''; return; }

  let html = `<button class="pgn-btn" ${current <= 1 ? 'disabled' : ''} onclick="search(${current - 1})">‹</button>`;

  // Window: show at most 7 page buttons
  let start = Math.max(1, current - 3);
  let end = Math.min(totalPages, current + 3);
  if (end - start < 6) {
    if (start === 1) end = Math.min(totalPages, start + 6);
    else start = Math.max(1, end - 6);
  }

  if (start > 1) { html += `<button class="pgn-btn" onclick="search(1)">1</button>`; }
  if (start > 2) { html += `<span class="pgn-info">…</span>`; }

  for (let i = start; i <= end; i++) {
    html += `<button class="pgn-btn ${i === current ? 'pgn-active' : ''}" onclick="search(${i})">${i}</button>`;
  }

  if (end < totalPages - 1) { html += `<span class="pgn-info">…</span>`; }
  if (end < totalPages) { html += `<button class="pgn-btn" onclick="search(${totalPages})">${totalPages}</button>`; }

  html += `<button class="pgn-btn" ${current >= totalPages ? 'disabled' : ''} onclick="search(${current + 1})">›</button>`;
  dom.pagination.innerHTML = html;
}

// ── Export ──────────────────────────────────────────────
function exportExcel() {
  const params = new URLSearchParams({
    ipAddress: (document.getElementById('ipFilter').value || '').trim(),
    operation: document.getElementById('operationFilter').value,
    severity:  document.getElementById('severityFilter').value,
  });
  window.open(API + '/export?' + params.toString());
}

// ── Stats ───────────────────────────────────────────────
function loadStats() {
  fetch(API + '/today-stats')
    .then(r => r.json())
    .then(d => {
      const total = d.total || 0;
      const abnormal = d.abnormal || 0;
      dom.statTotal.textContent = total.toLocaleString();
      dom.statAbnormal.textContent = abnormal.toLocaleString();
      dom.statRate.textContent = total > 0 ? `异常率 ${((abnormal / total) * 100).toFixed(1)}%` : '异常率 0%';
      dom.statIps.textContent = (d.uniqueIps || '—').toString();
      dom.statDate.textContent = new Date().toLocaleDateString('zh-CN', { month:'short', day:'numeric', weekday:'short' });

      // Nav status: online
      dom.navIndicator.className = abnormal > 0 ? 'nav-indicator warn' : 'nav-indicator';
      dom.navStatus.textContent = `UTC+8 · ${new Date().toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' })}`;
      dom.statStatus.textContent = '运行中';
      dom.statStatus.style.color = '';
    })
    .catch(() => {
      dom.statTotal.textContent = '—';
      dom.statAbnormal.textContent = '—';
      dom.statStatus.textContent = '离线';
      dom.statStatus.style.color = 'var(--accent-red)';
      dom.navIndicator.className = 'nav-indicator off';
      dom.navStatus.textContent = '未连接';
    });
}

// ── Keyboard shortcut: Enter to search ──────────────────
document.addEventListener('keydown', e => {
  if (e.key === 'Enter' && document.activeElement && document.activeElement.tagName === 'INPUT') {
    search(1);
  }
});

// ── Init ────────────────────────────────────────────────
(function init() {
  if (!dom.tableBody) return;
  loadStats();
  search(1);
})();
