/**
 * AuditVault — 日志审计系统前端
 * 设计原则: 每个 UI 状态都有归宿 — 加载、空数据、错误、正常
 */

const API = '/api/logs';

// ── 认证守卫 ────────────────────────────────────────────
async function requireAuth() {
  try {
    const resp = await fetch('/api/auth/me', { credentials: 'same-origin' });
    if (resp.status === 401) {
      window.location.href = '/login.html?redirect=' + encodeURIComponent(location.pathname + location.search);
      return false;
    }
    const data = await resp.json();
    if (data && data.success) {
      window.currentUser = { username: data.username, role: data.role };
      renderUserRole(data.username, data.role);
    }
    return true;
  } catch (e) {
    // 网络异常时先放行，让后续请求自己报错
    return true;
  }
}

function renderUserRole(username, role) {
  const pill = document.getElementById('navUserPill');
  if (pill) {
    if (role === 'ADMIN') {
      pill.innerHTML = `<span>👑</span><span>${escHtml(username)}</span><span class="role-badge role-admin">系统管理员</span>`;
    } else {
      pill.innerHTML = `<span>👤</span><span>${escHtml(username)}</span><span class="role-badge role-user">普通用户(只读)</span>`;
    }
  }

  // 权限受限控制：普通用户只读，置灰导出与测试功能
  const btnExport = document.getElementById('btnExport');
  if (btnExport) {
    if (role !== 'ADMIN') {
      btnExport.innerHTML = '🔒 导出 Excel';
      btnExport.classList.add('btn-disabled-perm');
      btnExport.title = '权限受限：仅系统管理员 (ADMIN) 可导出审计日志';
    } else {
      btnExport.innerHTML = '导出 Excel';
      btnExport.classList.remove('btn-disabled-perm');
      btnExport.title = '导出当前检索结果为 Excel 文件';
    }
  }
}


// ── 退出登录 ────────────────────────────────────────────
async function logout() {
  try {
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
  } catch (e) { /* ignore */ }
  window.location.href = '/login.html';
}

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
  if (window.currentUser && window.currentUser.role !== 'ADMIN') {
    showToast('⛔ 权限不足：当前账号为普通用户（只读），仅系统管理员 (ADMIN) 可导出审计日志', 'error');
    return;
  }
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

// ── Toast Notifications ─────────────────────────────────
function showToast(msg, type = 'info') {
  const container = document.getElementById('toastContainer');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `<span style="font-size:1.1rem;">${type === 'success' ? '✓' : type === 'error' ? '!' : 'ℹ'}</span><span>${escHtml(msg)}</span>`;
  container.appendChild(toast);
  setTimeout(() => {
    if (toast.parentNode) toast.parentNode.removeChild(toast);
  }, 4500);
}

// ── Webhook Simulator ───────────────────────────────────
const WEBHOOK_PRESETS = {
  payment_error: {
    time: new Date().toISOString(),
    level: "ERROR",
    logger: "com.payment.gateway.AlipayAdapter",
    message: "支付网关 HTTP 504 Gateway Timeout，订单号: ORD-202608250089",
    clientIp: "192.168.1.108",
    user: "pay-worker-01",
    thread: "http-nio-8080-exec-12",
    stack_trace: "java.net.SocketTimeoutException: Read timed out\n\tat java.net.http.HttpClient.send(HttpClient.java:550)\n\tat com.payment.gateway.AlipayAdapter.execute(AlipayAdapter.java:88)"
  },
  rate_limit: {
    time: new Date().toISOString(),
    level: "WARN",
    logger: "com.logaudit.security.RedisRateLimiter",
    message: "检测到同一 IP 连续 5 次登录失败，触发 Redis 频控锁定 15 分钟",
    clientIp: "172.16.88.99",
    user: "anonymous",
    thread: "http-nio-8080-exec-4"
  },
  sqli_probe: {
    time: new Date().toISOString(),
    level: "CRITICAL",
    logger: "com.logaudit.security.WafFilter",
    message: "WAF 拦截 SQL 注入探针攻击: ' UNION SELECT null, username, password FROM user --",
    clientIp: "203.0.113.42",
    user: "attacker",
    thread: "http-nio-8080-exec-9"
  },
  sso_login: {
    time: new Date().toISOString(),
    level: "INFO",
    logger: "com.logaudit.controller.AuthController",
    message: "用户 admin 通过 SSO 单点登录成功，分配角色: ADMIN",
    clientIp: "10.0.0.15",
    user: "admin",
    thread: "http-nio-8080-exec-1"
  },
  batch_logs: [
    {
      time: new Date().toISOString(),
      level: "INFO",
      logger: "com.service.OrderService",
      message: "订单 ORD-202608250090 创建成功",
      clientIp: "192.168.1.120",
      user: "zhangsan"
    },
    {
      time: new Date().toISOString(),
      level: "WARN",
      logger: "com.service.StockService",
      message: "商品 SKU-8899 库存不足 10 件预警",
      clientIp: "192.168.1.121",
      user: "inventory-job"
    }
  ]
};

function openWebhookModal() {
  const modal = document.getElementById('webhookModal');
  if (modal) {
    modal.classList.remove('hidden');
    setPreset('payment_error');
  }
}

function closeWebhookModal() {
  const modal = document.getElementById('webhookModal');
  if (modal) modal.classList.add('hidden');
}

function setPreset(key) {
  const payloadEl = document.getElementById('webhookPayload');
  if (!payloadEl) return;
  const data = WEBHOOK_PRESETS[key];
  if (data) {
    payloadEl.value = JSON.stringify(data, null, 2);
  }
}

async function sendWebhookPayload() {
  if (window.currentUser && window.currentUser.role !== 'ADMIN') {
    showToast('⛔ 权限不足：当前账号为普通用户（只读），仅系统管理员 (ADMIN) 可推送模拟日志', 'error');
    return;
  }
  const token = (document.getElementById('webhookToken').value || '').trim();
  const rawPayload = (document.getElementById('webhookPayload').value || '').trim();
  const btn = document.getElementById('btnSendWebhook');


  if (!rawPayload) {
    showToast('Payload 不能为空', 'error');
    return;
  }

  let parsed;
  try {
    parsed = JSON.parse(rawPayload);
  } catch (e) {
    showToast('JSON 格式错误: ' + e.message, 'error');
    return;
  }

  btn.disabled = true;
  btn.textContent = '发送中…';

  try {
    const resp = await fetch('/api/logs/webhook', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Audit-Token': token
      },
      body: JSON.stringify(parsed)
    });

    const resData = await resp.json();
    if (resp.ok && resData.success) {
      showToast(`✓ Webhook 采集成功：已接收 ${resData.accepted} 条日志并在后台异步入库！`, 'success');
      closeWebhookModal();
      // 触发界面实时刷新
      setTimeout(() => {
        loadStats();
        search(1);
      }, 300);
    } else {
      showToast('上报失败: ' + (resData.message || ('HTTP ' + resp.status)), 'error');
    }
  } catch (e) {
    showToast('网络错误: ' + e.message, 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = '🚀 发送实时 Webhook';
  }
}

// ── Init ────────────────────────────────────────────────
(function init() {
  if (!dom.tableBody) return;
  requireAuth().then(ok => {
    if (!ok) return;
    loadStats();
    search(1);
  });
})();

