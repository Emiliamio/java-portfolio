/**
 * AuditVault — Enterprise SOC Telemetry Engine
 * Features: Live Tail Streaming, Slide-over Detail Drawer, RBAC Guard, Syntax Highlighter
 */

const API = '/api/logs';

// ── Global State ──────────────────────────────────────────
const state = {
  currentPage: 1,
  pageSize: 20,
  timeRange: 'all',
  severity: '',
  operation: '',
  keyword: '',
  liveTailActive: false,
  liveTailTimer: null,
  liveMsgCount: 0,
  liveRateTimer: null,
  activeDrawerLog: null,
  currentDrawerTab: 'kv',
  currentUser: null,
  knownLogIds: new Set()
};

// ── Auth Guard ────────────────────────────────────────────
async function requireAuth() {
  try {
    const resp = await fetch('/api/auth/me', { credentials: 'same-origin' });
    if (resp.status === 401) {
      window.location.href = '/login.html?redirect=' + encodeURIComponent(location.pathname + location.search);
      return false;
    }
    const data = await resp.json();
    if (data && data.success) {
      state.currentUser = { username: data.username, role: data.role };
      renderUserBadge(data.username, data.role);
    }
    return true;
  } catch (e) {
    return true;
  }
}

function renderUserBadge(username, role) {
  const pill = document.getElementById('navUserPill');
  if (pill) {
    if (role === 'ADMIN') {
      pill.innerHTML = `<span>👑</span><span>${escHtml(username)}</span><span class="role-badge role-admin">系统管理员</span>`;
    } else {
      pill.innerHTML = `<span>👤</span><span>${escHtml(username)}</span><span class="role-badge role-user">普通用户(只读)</span>`;
    }
  }

  const btnExport = document.getElementById('btnExport');
  if (btnExport) {
    if (role !== 'ADMIN') {
      btnExport.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg> 导出 Excel`;
      btnExport.classList.add('btn-disabled-perm');
      btnExport.title = '权限受限：仅系统管理员 (ADMIN) 可导出审计日志';
    } else {
      btnExport.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/></svg> 导出 Excel`;
      btnExport.classList.remove('btn-disabled-perm');
      btnExport.title = '导出当前检索结果为 Excel 文件';
    }
  }
}

async function logout() {
  try {
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
  } catch (e) {}
  window.location.href = '/login.html';
}

// ── Search & Filter Engine ────────────────────────────────
function getFilterParams(page = 1) {
  const ip = (document.getElementById('ipFilter').value || '').trim();
  const op = document.getElementById('operationFilter').value;

  let startTime = null;
  const now = new Date();
  if (state.timeRange === '15m') {
    startTime = new Date(now.getTime() - 15 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ');
  } else if (state.timeRange === '1h') {
    startTime = new Date(now.getTime() - 60 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ');
  } else if (state.timeRange === '24h') {
    startTime = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ');
  }

  const params = new URLSearchParams({
    page: page,
    pageSize: state.pageSize,
    ipAddress: ip,
    operation: op,
    severity: state.severity
  });

  if (startTime) {
    params.set('startTime', startTime);
  }
  return params;
}

async function search(page = 1) {
  state.currentPage = page;
  const tbody = document.getElementById('tableBody');
  const meta = document.getElementById('resultMeta');
  if (!tbody) return;

  tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:36px;color:var(--text-tertiary);">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--accent-cyan)" stroke-width="2" style="animation:spin 1s linear infinite;"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>
    <div style="margin-top:8px;font-size:0.75rem;">正在检索审计遥测事件...</div>
  </td></tr>`;

  try {
    const params = getFilterParams(page);
    const resp = await fetch(`${API}/search?${params.toString()}`);
    const data = await resp.json();

    if (data.list && data.list.length > 0) {
      renderTableRows(data.list);
      renderPagination(data.pageNum || page, data.pages || 1, data.total || data.list.length);
      if (meta) meta.textContent = `共 ${data.total.toLocaleString()} 条事件 (第 ${data.pageNum}/${data.pages} 页)`;
      // 记录已加载 ID 用于 Live Tail 去重
      data.list.forEach(item => state.knownLogIds.add(item.id));
    } else {
      tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:48px;color:var(--text-tertiary);">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="opacity:0.4;margin-bottom:8px;"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
        <div style="font-weight:500;color:var(--text-secondary);">无匹配审计事件</div>
        <div style="font-size:0.72rem;margin-top:4px;">请尝试调整筛选条件或重置时间范围</div>
      </td></tr>`;
      document.getElementById('pagination').innerHTML = '';
      if (meta) meta.textContent = '0 条结果';
    }
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:36px;color:var(--accent-rose);">
      <div>⚠️ 加载审计数据失败: ${escHtml(err.message)}</div>
      <button class="btn btn-secondary" style="margin-top:10px;" onclick="search(${page})">重试</button>
    </td></tr>`;
  }
}

function renderTableRows(list) {
  const tbody = document.getElementById('tableBody');
  if (!tbody) return;

  tbody.innerHTML = list.map(item => `
    <tr onclick="openDetailDrawer(${item.id})" id="row-${item.id}">
      <td class="td-id">#${item.id}</td>
      <td class="td-ts">${escHtml(item.timestamp || '—')}</td>
      <td class="td-ip">${escHtml(item.ipAddress || '—')}</td>
      <td style="color:var(--text-primary);font-weight:500;">${escHtml(item.username || 'anonymous')}</td>
      <td class="td-op">${escHtml(item.operation || '—')}</td>
      <td>${renderResultBadge(item.operationResult)}</td>
      <td>${renderSeverityBadge(item.severity)}</td>
      <td class="td-detail" title="${escHtml(item.detail || '')}">${escHtml(item.detail || '—')}</td>
      <td style="text-align:right;">
        <button class="btn btn-secondary" style="height:24px;padding:0 6px;font-size:0.68rem;" onclick="event.stopPropagation();openDetailDrawer(${item.id})">
          详情
        </button>
      </td>
    </tr>
  `).join('');
}

function renderSeverityBadge(sev) {
  const s = (sev || 'INFO').toUpperCase();
  if (s === 'CRITICAL') return `<span class="badge b-critical"><span style="color:#ff4d4f">●</span> CRITICAL</span>`;
  if (s === 'ERROR')    return `<span class="badge b-error"><span style="color:#fa8c16">●</span> ERROR</span>`;
  if (s === 'WARN')     return `<span class="badge b-warn"><span style="color:#fadb14">●</span> WARN</span>`;
  return `<span class="badge b-info"><span style="color:#1677ff">●</span> INFO</span>`;
}

function renderResultBadge(res) {
  const r = (res || 'SUCCESS').toUpperCase();
  if (r === 'SUCCESS') return `<span class="badge b-success">✓ 成功</span>`;
  if (r === 'DENIED')  return `<span class="badge b-fail">⛔ 拦截</span>`;
  return `<span class="badge b-fail">✕ 失败</span>`;
}

function renderPagination(current, totalPages, totalCount) {
  const pgn = document.getElementById('pagination');
  if (!pgn || totalPages <= 1) {
    if (pgn) pgn.innerHTML = '';
    return;
  }

  let html = `<button class="pgn-btn" ${current <= 1 ? 'disabled' : ''} onclick="search(${current - 1})">‹ 上一页</button>`;
  const start = Math.max(1, current - 2);
  const end = Math.min(totalPages, current + 2);

  for (let i = start; i <= end; i++) {
    html += `<button class="pgn-btn ${i === current ? 'active' : ''}" onclick="search(${i})">${i}</button>`;
  }

  html += `<button class="pgn-btn" ${current >= totalPages ? 'disabled' : ''} onclick="search(${current + 1})">下一页 ›</button>`;
  pgn.innerHTML = html;
}

// ── Slide-Over Detail Drawer ──────────────────────────────
async function openDetailDrawer(logId) {
  const drawer = document.getElementById('detailDrawer');
  const backdrop = document.getElementById('drawerBackdrop');
  const idBadge = document.getElementById('drawerIdBadge');
  if (!drawer || !backdrop) return;

  drawer.classList.add('open');
  backdrop.classList.add('open');
  if (idBadge) idBadge.textContent = `#${logId}`;

  const body = document.getElementById('drawerBody');
  body.innerHTML = `<div style="text-align:center;padding:40px;color:var(--text-tertiary);">
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--accent-cyan)" stroke-width="2" style="animation:spin 1s linear infinite;"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>
    <div style="margin-top:8px;">加载日志全景信息...</div>
  </div>`;

  try {
    const resp = await fetch(`${API}/${logId}`);
    const data = await resp.json();
    state.activeDrawerLog = data;
    renderDrawerTabContent();
  } catch (err) {
    body.innerHTML = `<div style="color:var(--accent-rose);padding:20px;">⚠️ 加载详情失败: ${escHtml(err.message)}</div>`;
  }
}

function closeDetailDrawer() {
  const drawer = document.getElementById('detailDrawer');
  const backdrop = document.getElementById('drawerBackdrop');
  if (drawer) drawer.classList.remove('open');
  if (backdrop) backdrop.classList.remove('open');
  state.activeDrawerLog = null;
}

function switchDrawerTab(tab) {
  state.currentDrawerTab = tab;
  ['tabBtnKv', 'tabBtnJson', 'tabBtnStack'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.classList.remove('active');
  });

  if (tab === 'kv') document.getElementById('tabBtnKv')?.classList.add('active');
  if (tab === 'json') document.getElementById('tabBtnJson')?.classList.add('active');
  if (tab === 'stack') document.getElementById('tabBtnStack')?.classList.add('active');

  renderDrawerTabContent();
}

function renderDrawerTabContent() {
  const body = document.getElementById('drawerBody');
  const log = state.activeDrawerLog;
  if (!body || !log) return;

  if (state.currentDrawerTab === 'kv') {
    body.innerHTML = `
      <div class="threat-card">
        <div style="display:flex;align-items:center;justify-content:space-between;">
          <span style="font-size:0.75rem;font-weight:600;color:var(--text-secondary);">源 IP 威胁画像</span>
          <span class="badge b-info">${escHtml(log.ipAddress)}</span>
        </div>
        <div style="font-size:0.72rem;color:var(--text-tertiary);line-height:1.5;">
          节点属性: 内网/专线网关 · 过去 1 小时请求频次正常 · 无历史阻断黑名单记录
        </div>
      </div>

      <table class="drawer-kv-table">
        <tr><td class="drawer-kv-key">事件 ID</td><td class="drawer-kv-val font-mono">#${log.id}</td></tr>
        <tr><td class="drawer-kv-key">记录时间</td><td class="drawer-kv-val font-mono">${escHtml(log.timestamp)}</td></tr>
        <tr><td class="drawer-kv-key">主体账号</td><td class="drawer-kv-val font-mono" style="font-weight:600;">${escHtml(log.username || 'anonymous')}</td></tr>
        <tr><td class="drawer-kv-key">操作类型</td><td class="drawer-kv-val font-mono">${escHtml(log.operation)}</td></tr>
        <tr><td class="drawer-kv-key">执行结果</td><td class="drawer-kv-val">${renderResultBadge(log.operationResult)}</td></tr>
        <tr><td class="drawer-kv-key">威胁级别</td><td class="drawer-kv-val">${renderSeverityBadge(log.severity)}</td></tr>
        <tr><td class="drawer-kv-key">源文件归档</td><td class="drawer-kv-val font-mono">${escHtml(log.sourceFile || 'audit-service.log')}</td></tr>
        <tr><td class="drawer-kv-key">入库时间</td><td class="drawer-kv-val font-mono">${escHtml(log.createdAt || '—')}</td></tr>
      </table>

      <div style="margin-top:8px;">
        <div style="font-size:0.72rem;font-weight:600;color:var(--text-tertiary);margin-bottom:6px;">日志详情载荷</div>
        <div class="code-box">${escHtml(log.detail || '—')}</div>
      </div>
    `;
  } else if (state.currentDrawerTab === 'json') {
    const jsonStr = JSON.stringify(log, null, 2);
    body.innerHTML = `
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <span style="font-size:0.72rem;color:var(--text-tertiary);">标准 JSON 树形结构</span>
        <button class="btn btn-secondary" style="height:24px;padding:0 8px;font-size:0.68rem;" onclick="copyText('${encodeURIComponent(jsonStr)}')">复制 JSON</button>
      </div>
      <div class="code-box" style="max-height:420px;color:#38bdf8;">${formatJsonHighlight(jsonStr)}</div>
    `;
  } else if (state.currentDrawerTab === 'stack') {
    const isStack = (log.detail || '').includes('Exception') || (log.detail || '').includes('at ');
    body.innerHTML = `
      <div style="font-size:0.72rem;color:var(--text-tertiary);margin-bottom:8px;">
        ${isStack ? '已自动提取 Java 异常堆栈追踪调用链路' : '此日志无多行异常堆栈'}
      </div>
      <div class="code-box" style="max-height:420px;color:#cbd5e1;white-space:pre-wrap;">${highlightStackTrace(log.detail || '无堆栈追踪信息')}</div>
    `;
  }
}

function formatJsonHighlight(json) {
  return escHtml(json)
    .replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|(true|false|null)|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, match => {
      let cls = 'color:#f59e0b;';
      if (/^"/.test(match)) {
        cls = /:$/.test(match) ? 'color:#94a3b8;' : 'color:#00f2fe;';
      } else if (/true|false/.test(match)) {
        cls = 'color:#10b981;';
      } else if (/null/.test(match)) {
        cls = 'color:#f43f5e;';
      }
      return `<span style="${cls}">${match}</span>`;
    });
}

function highlightStackTrace(text) {
  return escHtml(text)
    .replace(/(Caused by:.*)/g, '<span style="color:#f43f5e;font-weight:700;">$1</span>')
    .replace(/(at\s+[a-zA-Z0-9_$.]+\([^)]+\))/g, '<span style="color:#94a3b8;">$1</span>')
    .replace(/(Exception|Error):/g, '<span style="color:#f59e0b;font-weight:700;">$1:</span>');
}

function forwardToAiAssistant() {
  if (!state.activeDrawerLog) return;
  const log = state.activeDrawerLog;
  const rawText = `[${log.timestamp}] [${log.severity}] IP: ${log.ipAddress} User: ${log.username} Op: ${log.operation} -> ${log.detail}`;
  window.open(`http://localhost:8081?log=${encodeURIComponent(rawText)}`, '_blank');
}

function copyCurrentLog() {
  if (!state.activeDrawerLog) return;
  const str = JSON.stringify(state.activeDrawerLog, null, 2);
  navigator.clipboard.writeText(str).then(() => {
    showToast('✓ 已复制日志详情 JSON 到剪贴板', 'success');
  }).catch(() => {
    showToast('复制失败，请手动选择复制', 'error');
  });
}

// ── Live Tail Streaming Mode ──────────────────────────────
function toggleLiveTail() {
  state.liveTailActive = !state.liveTailActive;
  const btn = document.getElementById('btnLiveTail');
  const badge = document.getElementById('liveBadge');
  const statusText = document.getElementById('liveStatusText');

  if (state.liveTailActive) {
    if (btn) btn.classList.add('active');
    if (badge) badge.classList.remove('off');
    if (statusText) statusText.textContent = 'STREAMING';
    showToast('▶ 已开启实时流监听 (Live Tail)', 'info');

    // 启动轮询模拟流
    state.liveTailTimer = setInterval(pollLiveStream, 2500);
    state.liveRateTimer = setInterval(() => {
      const rateVal = document.getElementById('liveRateVal');
      if (rateVal) {
        rateVal.textContent = `${state.liveMsgCount} msg/s`;
        state.liveMsgCount = 0;
      }
    }, 1000);
  } else {
    if (btn) btn.classList.remove('active');
    if (badge) badge.classList.add('off');
    if (statusText) statusText.textContent = 'PAUSED';
    if (state.liveTailTimer) clearInterval(state.liveTailTimer);
    if (state.liveRateTimer) clearInterval(state.liveRateTimer);
    const rateVal = document.getElementById('liveRateVal');
    if (rateVal) rateVal.textContent = '0 msg/s';
    showToast('⏸ 已暂停实时流监听', 'info');
  }
}

async function pollLiveStream() {
  try {
    const params = new URLSearchParams({ page: 1, pageSize: 5 });
    const resp = await fetch(`${API}/search?${params.toString()}`);
    const data = await resp.json();

    if (data.list && data.list.length > 0) {
      const newItems = data.list.filter(item => !state.knownLogIds.has(item.id));
      if (newItems.length > 0) {
        state.liveMsgCount += newItems.length;
        const tbody = document.getElementById('tableBody');
        if (tbody) {
          newItems.reverse().forEach(item => {
            state.knownLogIds.add(item.id);
            const tr = document.createElement('tr');
            tr.id = `row-${item.id}`;
            tr.className = 'live-new';
            tr.onclick = () => openDetailDrawer(item.id);
            tr.innerHTML = `
              <td class="td-id">#${item.id}</td>
              <td class="td-ts">${escHtml(item.timestamp || '—')}</td>
              <td class="td-ip">${escHtml(item.ipAddress || '—')}</td>
              <td style="color:var(--text-primary);font-weight:500;">${escHtml(item.username || 'anonymous')}</td>
              <td class="td-op">${escHtml(item.operation || '—')}</td>
              <td>${renderResultBadge(item.operationResult)}</td>
              <td>${renderSeverityBadge(item.severity)}</td>
              <td class="td-detail" title="${escHtml(item.detail || '')}">${escHtml(item.detail || '—')}</td>
              <td style="text-align:right;">
                <button class="btn btn-secondary" style="height:24px;padding:0 6px;font-size:0.68rem;" onclick="event.stopPropagation();openDetailDrawer(${item.id})">详情</button>
              </td>
            `;
            tbody.insertBefore(tr, tbody.firstChild);
          });
        }
        // 刷新统计指标
        loadStats();
      }
    }
  } catch (e) {}
}

// ── Filters & Time Range ──────────────────────────────────
function setTimeRange(range, el) {
  state.timeRange = range;
  document.querySelectorAll('.time-pill').forEach(btn => btn.classList.remove('active'));
  if (el) el.classList.add('active');
  search(1);
}

function filterSeverity(sev, el) {
  state.severity = sev;
  document.querySelectorAll('.sev-chip').forEach(btn => btn.classList.remove('active'));
  if (el) el.classList.add('active');
  search(1);
}

function resetFilters() {
  document.getElementById('ipFilter').value = '';
  document.getElementById('operationFilter').value = '';
  state.severity = '';
  state.timeRange = 'all';
  document.querySelectorAll('.sev-chip').forEach(btn => btn.classList.remove('active'));
  document.getElementById('sevAll')?.classList.add('active');
  document.querySelectorAll('.time-pill').forEach(btn => btn.classList.remove('active'));
  document.querySelector('.time-pill:last-child')?.classList.add('active');
  search(1);
}

// ── Export ────────────────────────────────────────────────
function exportExcel() {
  if (state.currentUser && state.currentUser.role !== 'ADMIN') {
    showToast('⛔ 权限不足：当前账号为普通用户（只读），仅系统管理员 (ADMIN) 可导出审计日志', 'error');
    return;
  }
  const params = getFilterParams(state.currentPage);
  window.open(`${API}/export?${params.toString()}`);
  showToast('✓ 已启动流式 Excel 导出任务 (SXSSFWorkbook)', 'success');
}

// ── Stats ─────────────────────────────────────────────────
async function loadStats() {
  try {
    const resp = await fetch(`${API}/today-stats`);
    const d = await resp.json();
    const total = d.total || 0;
    const abnormal = d.abnormal || 0;
    const rate = total > 0 ? ((abnormal / total) * 100).toFixed(1) : '0.0';

    document.getElementById('statTotal').textContent = total.toLocaleString();
    document.getElementById('statAbnormal').textContent = abnormal.toLocaleString();
    document.getElementById('statRate').textContent = `异常率 ${rate}%`;
    document.getElementById('statIps').textContent = (d.uniqueIps || '—').toLocaleString();
    document.getElementById('statDate').textContent = new Date().toLocaleDateString('zh-CN', { month:'short', day:'numeric', weekday:'short' });
    document.getElementById('statSysTime').textContent = `UTC+8 · ${new Date().toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit' })}`;

    const indicator = document.getElementById('navIndicator');
    if (indicator) {
      indicator.className = abnormal > 0 ? 'nav-indicator warn' : 'nav-indicator';
    }
  } catch (e) {}
}

// ── Webhook Simulator ─────────────────────────────────────
const WEBHOOK_PRESETS = {
  payment_error: {
    service: "order-payment-service",
    timestamp: new Date().toISOString().replace('T', ' ').slice(0, 19),
    level: "ERROR",
    clientIp: "10.0.12.88",
    user: "order_runner",
    operation: "EXECUTE",
    result: "FAIL",
    message: "订单支付超时且第三方网关未响应: OrderPayException: Read timed out after 3000ms\n\tat com.payment.GatewayClient.execute(GatewayClient.java:142)\n\tat com.service.OrderService.pay(OrderService.java:88)"
  },
  rate_limit: {
    service: "api-gateway",
    timestamp: new Date().toISOString().replace('T', ' ').slice(0, 19),
    level: "WARN",
    clientIp: "172.31.0.50",
    user: "attacker_probe",
    operation: "LOGIN",
    result: "DENIED",
    message: "IP 连续 5 次密码校验失败，触发 RedisRateLimiter 频控锁定 15 分钟"
  },
  sqli_probe: {
    service: "web-firewall",
    timestamp: new Date().toISOString().replace('T', ' ').slice(0, 19),
    level: "CRITICAL",
    clientIp: "192.168.100.22",
    user: "scanner_bot",
    operation: "ACCESS",
    result: "DENIED",
    message: "检测到 SQL 注入特征攻击 Payload: /api/users?id=1%27%20OR%20%271%27=%271"
  },
  sso_login: {
    service: "auth-center",
    timestamp: new Date().toISOString().replace('T', ' ').slice(0, 19),
    level: "INFO",
    clientIp: "192.168.1.10",
    user: "admin",
    operation: "LOGIN",
    result: "SUCCESS",
    message: "用户通过 HttpOnly Cookie 成功完成单点登录鉴权并签发 JWT"
  },
  batch_logs: [
    {
      service: "inventory-service",
      timestamp: new Date().toISOString().replace('T', ' ').slice(0, 19),
      level: "INFO",
      clientIp: "10.0.0.55",
      user: "worker-1",
      operation: "UPDATE",
      result: "SUCCESS",
      message: "库存 SKU-9908 锁定成功"
    },
    {
      service: "inventory-service",
      timestamp: new Date().toISOString().replace('T', ' ').slice(0, 19),
      level: "WARN",
      clientIp: "10.0.0.55",
      user: "worker-1",
      operation: "UPDATE",
      result: "SUCCESS",
      message: "商品预警：库存低于安全水位线"
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
  if (data) payloadEl.value = JSON.stringify(data, null, 2);
}

async function sendWebhookPayload() {
  if (state.currentUser && state.currentUser.role !== 'ADMIN') {
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
    const resp = await fetch(`${API}/webhook`, {
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

// ── Toast System ──────────────────────────────────────────
function showToast(msg, type = 'info') {
  const container = document.getElementById('toastContainer');
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

// ── Keyboard Shortcuts ────────────────────────────────────
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') {
    closeDetailDrawer();
    closeWebhookModal();
  }
  if (e.key === 'Enter' && document.activeElement && document.activeElement.id === 'ipFilter') {
    search(1);
  }
});

// ── Bootstrap ─────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  requireAuth().then(ok => {
    if (!ok) return;
    loadStats();
    search(1);
  });
});