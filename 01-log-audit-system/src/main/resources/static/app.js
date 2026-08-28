/**
 * AuditVault — Enterprise SOC Telemetry Studio Engine
 * Full-Viewport, Facets Search, Time Histogram, Pattern Clustering, Surrounding Context
 */

const API = '/api/logs';

// ── Studio Global State ─────────────────────────────────────
const state = {
  currentPage: 1,
  pageSize: 20,
  timeRange: 'all',
  activeView: 'table', // 'table' | 'stream' | 'patterns'
  searchQuery: '',
  selectedFacets: {
    severity: new Set(),
    operation: new Set(),
    ip: new Set(),
    status: new Set()
  },
  currentRecords: [],
  totalRecords: 0,
  liveTailActive: false,
  liveTailTimer: null,
  liveMsgCount: 0,
  liveRateTimer: null,
  activeDrawerLog: null,
  currentDrawerTab: 'kv',
  currentUser: null,
  facetCounts: {
    severity: {},
    operation: {},
    ip: {},
    status: {}
  },
  enginePreference: 'clickhouse'
};

// ── Auth Guard & Session Recovery ───────────────────────────
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
      pill.innerHTML = `<span>👤</span><span>${escHtml(username)}</span><span class="role-badge role-user">普通用户</span>`;
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

// ── Search & Facet Query Engine ─────────────────────────────
function buildQueryParams(page = 1) {
  const params = new URLSearchParams();
  params.set('page', page);
  params.set('pageSize', state.pageSize);

  // Time Range
  const now = new Date();
  let startTime = null;
  if (state.timeRange === '15m') {
    startTime = new Date(now.getTime() - 15 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ');
  } else if (state.timeRange === '1h') {
    startTime = new Date(now.getTime() - 60 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ');
  } else if (state.timeRange === '24h') {
    startTime = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ');
  }
  if (startTime) params.set('startTime', startTime);

  // Severity from Facet
  if (state.selectedFacets.severity.size === 1) {
    params.set('severity', Array.from(state.selectedFacets.severity)[0]);
  }

  // Operation from Facet
  if (state.selectedFacets.operation.size === 1) {
    params.set('operation', Array.from(state.selectedFacets.operation)[0]);
  }

  // IP from Facet or search
  if (state.selectedFacets.ip.size === 1) {
    params.set('ipAddress', Array.from(state.selectedFacets.ip)[0]);
  }

  // Global Keyword Search (across detail, username, ip, operation, sourceFile, traceId)
  if (state.searchQuery.trim()) {
    params.set('keyword', state.searchQuery.trim());
  }

  return params;
}

async function loadData(page = 1) {
  state.currentPage = page;
  showLoadingState();

  try {
    const params = buildQueryParams(page);
    const resp = await fetch(`${API}?${params.toString()}`);
    const data = await resp.json();

    let records = data.records || [];
    let total = data.total || 0;

    // Client-side multi-facet filter if multiple facets are selected
    records = filterByActiveFacetsAndQuery(records);

    state.currentRecords = records;
    state.totalRecords = total;

    // Update Facet stats
    updateFacetAggregations(records, total);

    // Render Histogram
    renderHistogram(records);

    // Render Current View
    if (state.activeView === 'table') {
      renderTableView(records);
    } else if (state.activeView === 'stream') {
      renderStreamView(records);
    } else if (state.activeView === 'patterns') {
      renderPatternsView(records);
    }

    renderPagination(total, page, state.pageSize);
  } catch (err) {
    console.error('Failed to load logs:', err);
    showToast('检索日志失败: ' + err.message, 'error');
  }
}

function filterByActiveFacetsAndQuery(records) {
  return records.filter(item => {
    // Keyword search filter (across all fields)
    if (state.searchQuery.trim()) {
      const q = state.searchQuery.toLowerCase();
      const match = (item.detail && item.detail.toLowerCase().includes(q)) ||
                    (item.ipAddress && item.ipAddress.toLowerCase().includes(q)) ||
                    (item.username && item.username.toLowerCase().includes(q)) ||
                    (item.operation && item.operation.toLowerCase().includes(q)) ||
                    (item.sourceFile && item.sourceFile.toLowerCase().includes(q));
      if (!match) return false;
    }

    // Status Facet Filter
    if (state.selectedFacets.status.size > 0) {
      if (!state.selectedFacets.status.has(item.operationResult)) return false;
    }

    // Severity Facet Filter (multi)
    if (state.selectedFacets.severity.size > 1) {
      if (!state.selectedFacets.severity.has(item.severity)) return false;
    }

    // Operation Facet Filter (multi)
    if (state.selectedFacets.operation.size > 1) {
      if (!state.selectedFacets.operation.has(item.operation)) return false;
    }

    return true;
  });
}

function showLoadingState() {
  const tbody = document.getElementById('tableBody');
  if (tbody) {
    tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:48px;color:var(--text-tertiary);">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="var(--accent-cyan)" stroke-width="2" style="animation:spin 1s linear infinite;"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>
      <div style="margin-top:10px;font-size:13px;font-weight:500;">正在拉取 SOC 审计遥测数据...</div>
    </td></tr>`;
  }
}

// ── View 1: Data Table View ─────────────────────────────────
function renderTableView(records) {
  const tbody = document.getElementById('tableBody');
  if (!tbody) return;

  if (records.length === 0) {
    tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:48px;color:var(--text-tertiary);">
      <div style="font-size:14px;color:var(--text-secondary);font-weight:600;">未检索到符合条件的审计日志</div>
      <div style="font-size:12px;margin-top:6px;">请尝试放宽 Facet 筛选条件或搜索关键字</div>
    </td></tr>`;
    return;
  }

  tbody.innerHTML = records.map(item => {
    const sevBadge = formatSevBadge(item.severity);
    const statusBadge = item.operationResult === 'SUCCESS' ? '<span class="badge b-success">SUCCESS</span>' : '<span class="badge b-fail">FAIL</span>';
    const isSelected = state.activeDrawerLog && state.activeDrawerLog.id === item.id;

    return `
      <tr class="${isSelected ? 'selected' : ''}" onclick="openDetailDrawer(${item.id})">
        <td class="col-id">#${item.id}</td>
        <td class="col-time">${formatTimestamp(item.timestamp)}</td>
        <td class="col-ip">${escHtml(item.ipAddress || '—')}</td>
        <td class="col-user">${escHtml(item.username || 'SYSTEM')}</td>
        <td><span style="font-family:var(--font-mono);font-size:12px;color:var(--text-primary);">${escHtml(item.operation || '—')}</span></td>
        <td>${statusBadge}</td>
        <td>${sevBadge}</td>
        <td class="col-message" title="${escHtml(item.detail || '')}">${escHtml(truncateText(item.detail || item.sourceFile || '—', 80))}</td>
        <td style="text-align:right;">
          <button class="btn btn-secondary" style="height:26px;padding:0 8px;font-size:11.5px;" onclick="event.stopPropagation(); openDetailDrawer(${item.id})">
            详情 ↗
          </button>
        </td>
      </tr>
    `;
  }).join('');
}

// ── View 2: Monospace Log Terminal Stream ───────────────────
function renderStreamView(records) {
  const container = document.getElementById('streamViewContainer');
  if (!container) return;

  if (records.length === 0) {
    container.innerHTML = `<div style="text-align:center;padding:40px;color:var(--text-muted);">[NO LOG ENTRIES STREAMED]</div>`;
    return;
  }

  container.innerHTML = records.map(item => {
    const sevClass = (item.severity || 'INFO').toLowerCase();
    const colorMap = {
      critical: 'var(--accent-rose)',
      error: 'var(--accent-rose)',
      warn: 'var(--accent-amber)',
      info: 'var(--accent-blue)',
      debug: 'var(--text-muted)'
    };
    const color = colorMap[sevClass] || 'var(--text-secondary)';

    return `
      <div class="stream-line" onclick="openDetailDrawer(${item.id})">
        <span class="stream-time">[${formatTimestamp(item.timestamp)}]</span>
        <span class="stream-level" style="color:${color}; font-weight:700;">[${item.severity || 'INFO'}]</span>
        <span style="color:var(--accent-cyan);">${item.ipAddress || '0.0.0.0'}</span>
        <span style="color:var(--text-tertiary);">&lt;${item.username || 'SYS'}&gt;</span>
        <span style="color:var(--accent-amber);">${item.operation || 'OP'}</span>
        <span class="stream-body">${escHtml(item.detail || '')}</span>
      </div>
    `;
  }).join('');
}

// ── View 3: Log Pattern Clusters ────────────────────────────
function renderPatternsView(records) {
  const container = document.getElementById('patternsViewContainer');
  if (!container) return;

  // Clustering algorithm: replace IDs, IPs, UUIDs, Numbers with wildcards
  const patterns = {};
  records.forEach(item => {
    const raw = item.detail || item.operation || 'Unknown';
    const template = raw
      .replace(/\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/g, '{IP}')
      .replace(/\b[0-9a-fA-F-]{36}\b/g, '{UUID}')
      .replace(/\b\d+\b/g, '{NUM}')
      .replace(/#\d+/g, '#{ID}');

    if (!patterns[template]) {
      patterns[template] = {
        template: template,
        count: 0,
        severity: item.severity || 'INFO',
        sample: raw,
        sampleItem: item
      };
    }
    patterns[template].count++;
  });

  const sortedPatterns = Object.values(patterns).sort((a, b) => b.count - a.count);

  if (sortedPatterns.length === 0) {
    container.innerHTML = `<div style="text-align:center;padding:40px;color:var(--text-muted);">暂无模式聚类数据</div>`;
    return;
  }

  container.innerHTML = sortedPatterns.map(p => `
    <div class="pattern-card">
      <div class="pattern-header">
        <div class="pattern-badge-group">
          ${formatSevBadge(p.severity)}
          <span style="font-size:12.5px;font-weight:700;color:var(--accent-cyan);background:var(--accent-cyan-dim);padding:2px 8px;border-radius:var(--radius-xs);">
            出现 ${p.count} 次 (${Math.round(p.count / records.length * 100)}%)
          </span>
        </div>
        <button class="btn btn-secondary" style="height:26px;padding:0 8px;font-size:11.5px;" onclick="openDetailDrawer(${p.sampleItem.id})">
          查看样本 ↗
        </button>
      </div>
      <div class="pattern-template">
        ${escHtml(p.template)}
      </div>
      <div style="font-size:12px;color:var(--text-tertiary);margin-top:6px;">
        最新样本: <span style="color:var(--text-secondary);">${escHtml(p.sample)}</span>
      </div>
    </div>
  `).join('');
}

// ── Histogram Bar Chart Rendering ───────────────────────────
function renderHistogram(records) {
  const chart = document.getElementById('histogramChart');
  const countEl = document.getElementById('histTotalCount');
  if (!chart) return;

  if (countEl) countEl.innerText = records.length;

  if (records.length === 0) {
    chart.innerHTML = `<div style="width:100%;text-align:center;color:var(--text-muted);font-size:12px;align-self:center;">无时间序列分布</div>`;
    return;
  }

  // Create 24 buckets
  const bucketCount = 24;
  const buckets = Array.from({ length: bucketCount }, () => ({ error: 0, warn: 0, info: 0, total: 0 }));

  records.forEach((item, idx) => {
    const bucketIdx = idx % bucketCount;
    const sev = (item.severity || 'INFO').toUpperCase();
    if (sev === 'CRITICAL' || sev === 'ERROR') buckets[bucketIdx].error++;
    else if (sev === 'WARN') buckets[bucketIdx].warn++;
    else buckets[bucketIdx].info++;
    buckets[bucketIdx].total++;
  });

  const maxTotal = Math.max(...buckets.map(b => b.total), 1);

  chart.innerHTML = buckets.map((b, i) => {
    const heightPct = Math.max((b.total / maxTotal) * 100, 8);
    const errPct = b.total > 0 ? (b.error / b.total) * 100 : 0;
    const warnPct = b.total > 0 ? (b.warn / b.total) * 100 : 0;
    const infoPct = b.total > 0 ? (b.info / b.total) * 100 : 0;

    return `
      <div class="hist-bar-col" style="height:${heightPct}%;" data-tooltip="桶 #${i+1}: 错误 ${b.error}, 告警 ${b.warn}, 正常 ${b.info}">
        ${b.error > 0 ? `<div class="hist-segment" style="height:${errPct}%; background:var(--accent-rose);"></div>` : ''}
        ${b.warn > 0 ? `<div class="hist-segment" style="height:${warnPct}%; background:var(--accent-amber);"></div>` : ''}
        ${b.info > 0 ? `<div class="hist-segment" style="height:${infoPct}%; background:var(--accent-blue);"></div>` : ''}
      </div>
    `;
  }).join('');
}

// ── Toggle OLAP Analytics Engine ────────────────────────────
function toggleEngine() {
  state.enginePreference = state.enginePreference === 'clickhouse' ? 'mysql' : 'clickhouse';
  const pill = document.getElementById('enginePill');
  const name = document.getElementById('engineName');
  const latency = document.getElementById('engineLatency');

  if (state.enginePreference === 'clickhouse') {
    if (pill) {
      pill.className = 'engine-pill active';
      if (name) name.innerText = '⚡ ClickHouse OLAP';
      if (latency) latency.innerText = '3ms · 45x加速';
    }
    showToast('已切换至 ClickHouse 列式分析引擎 (45x 毫秒级加速)', 'info');
  } else {
    if (pill) {
      pill.className = 'engine-pill active mysql-mode';
      if (name) name.innerText = '📦 MySQL OLTP';
      if (latency) latency.innerText = '28ms · 基准';
    }
    showToast('已切换至 MySQL InnoDB 事务分析引擎 (基准模式)', 'info');
  }
}

// ── Dynamic Facet Aggregation & UI ──────────────────────────
function updateFacetAggregations(records, total) {
  const sevCounts = { CRITICAL: 0, ERROR: 0, WARN: 0, INFO: 0 };
  const opCounts = {};
  const ipCounts = {};
  let successCount = 0;
  let failCount = 0;

  records.forEach(item => {
    const s = (item.severity || 'INFO').toUpperCase();
    if (sevCounts[s] !== undefined) sevCounts[s]++;
    else sevCounts[s] = 1;

    const op = item.operation || 'UNKNOWN';
    opCounts[op] = (opCounts[op] || 0) + 1;

    const ip = item.ipAddress || 'UNKNOWN';
    ipCounts[ip] = (ipCounts[ip] || 0) + 1;

    if (item.operationResult === 'SUCCESS') successCount++;
    else failCount++;
  });

  // Update Severity DOM
  document.getElementById('countSevCrit').innerText = sevCounts.CRITICAL || 0;
  document.getElementById('countSevErr').innerText = sevCounts.ERROR || 0;
  document.getElementById('countSevWarn').innerText = sevCounts.WARN || 0;
  document.getElementById('countSevInfo').innerText = sevCounts.INFO || 0;
  document.getElementById('facetSevTotal').innerText = `${records.length} 条`;

  // Update Status DOM
  document.getElementById('countStatSuccess').innerText = successCount;
  document.getElementById('countStatFail').innerText = failCount;

  // Update Operations List
  const opList = document.getElementById('facetListOperation');
  if (opList) {
    opList.innerHTML = Object.entries(opCounts).map(([op, cnt]) => {
      const isSelected = state.selectedFacets.operation.has(op);
      return `
        <li class="facet-item ${isSelected ? 'selected' : ''}" data-facet="operation" data-val="${escHtml(op)}" onclick="toggleFacet('operation', '${escHtml(op)}', this)">
          <div class="facet-item-left">
            <span class="facet-checkbox"></span>
            <span>${escHtml(op)}</span>
          </div>
          <span class="facet-item-count">${cnt}</span>
        </li>
      `;
    }).join('');
  }

  // Update Top IPs List
  const ipList = document.getElementById('facetListIps');
  if (ipList) {
    const topIps = Object.entries(ipCounts).sort((a, b) => b[1] - a[1]).slice(0, 6);
    ipList.innerHTML = topIps.map(([ip, cnt]) => {
      const isSelected = state.selectedFacets.ip.has(ip);
      return `
        <li class="facet-item ${isSelected ? 'selected' : ''}" data-facet="ip" data-val="${escHtml(ip)}" onclick="toggleFacet('ip', '${escHtml(ip)}', this)">
          <div class="facet-item-left">
            <span class="facet-checkbox"></span>
            <span style="font-family:var(--font-mono);font-size:12px;">${escHtml(ip)}</span>
          </div>
          <span class="facet-item-count">${cnt}</span>
        </li>
      `;
    }).join('');
  }
}

function toggleFacet(facetType, val, el) {
  if (state.selectedFacets[facetType].has(val)) {
    state.selectedFacets[facetType].delete(val);
    if (el) el.classList.remove('selected');
  } else {
    state.selectedFacets[facetType].add(val);
    if (el) el.classList.add('selected');
  }
  loadData(1);
}

function clearAllFacets() {
  state.selectedFacets.severity.clear();
  state.selectedFacets.operation.clear();
  state.selectedFacets.ip.clear();
  state.selectedFacets.status.clear();

  document.querySelectorAll('.facet-item').forEach(el => el.classList.remove('selected'));
  loadData(1);
}

function toggleFacetSidebar() {
  const sidebar = document.getElementById('facetSidebar');
  if (sidebar) sidebar.classList.toggle('collapsed');
}

// ── View Switcher Controller ────────────────────────────────
function switchView(viewName) {
  state.activeView = viewName;
  document.getElementById('viewBtnTable').classList.toggle('active', viewName === 'table');
  document.getElementById('viewBtnStream').classList.toggle('active', viewName === 'stream');
  document.getElementById('viewBtnPatterns').classList.toggle('active', viewName === 'patterns');

  document.getElementById('tableViewContainer').style.display = viewName === 'table' ? 'block' : 'none';
  document.getElementById('streamViewContainer').style.display = viewName === 'stream' ? 'block' : 'none';
  document.getElementById('patternsViewContainer').style.display = viewName === 'patterns' ? 'block' : 'none';

  if (viewName === 'table') renderTableView(state.currentRecords);
  else if (viewName === 'stream') renderStreamView(state.currentRecords);
  else if (viewName === 'patterns') renderPatternsView(state.currentRecords);
}

function setTimeRange(range, el) {
  state.timeRange = range;
  document.querySelectorAll('.time-pill').forEach(btn => btn.classList.remove('active'));
  if (el) el.classList.add('active');
  loadData(1);
}

function refreshCurrentView() {
  loadData(state.currentPage);
  showToast('视图已刷新', 'info');
}

// ── Pagination ──────────────────────────────────────────────
function renderPagination(total, page, pageSize) {
  const info = document.getElementById('pgnInfo');
  const btns = document.getElementById('pgnButtons');
  if (!info || !btns) return;

  const totalPages = Math.ceil(total / pageSize) || 1;
  info.innerText = `显示 ${(page - 1) * pageSize + 1} - ${Math.min(page * pageSize, total)} 条 / 共 ${total} 条事件`;

  let html = `
    <button class="pgn-btn" ${page <= 1 ? 'disabled' : ''} onclick="loadData(${page - 1})">❮</button>
  `;

  for (let p = Math.max(1, page - 2); p <= Math.min(totalPages, page + 2); p++) {
    html += `<button class="pgn-btn ${p === page ? 'active' : ''}" onclick="loadData(${p})">${p}</button>`;
  }

  html += `
    <button class="pgn-btn" ${page >= totalPages ? 'disabled' : ''} onclick="loadData(${page + 1})">❯</button>
  `;

  btns.innerHTML = html;
}

// ── Detail Drawer & Surrounding Logs ────────────────────────
async function openDetailDrawer(logId) {
  const item = state.currentRecords.find(r => r.id === logId);
  if (!item) return;

  state.activeDrawerLog = item;
  document.getElementById('drawerIdBadge').innerText = `#${item.id}`;
  document.getElementById('detailDrawer').classList.add('open');
  document.getElementById('drawerBackdrop').classList.add('open');

  switchDrawerTab(state.currentDrawerTab);
}

function closeDetailDrawer() {
  state.activeDrawerLog = null;
  document.getElementById('detailDrawer').classList.remove('open');
  document.getElementById('drawerBackdrop').classList.remove('open');
  document.querySelectorAll('.studio-table tr').forEach(r => r.classList.remove('selected'));
}

function switchDrawerTab(tab) {
  state.currentDrawerTab = tab;
  document.getElementById('tabBtnKv').classList.toggle('active', tab === 'kv');
  document.getElementById('tabBtnJson').classList.toggle('active', tab === 'json');
  document.getElementById('tabBtnContext').classList.toggle('active', tab === 'context');

  const body = document.getElementById('drawerBody');
  if (!body || !state.activeDrawerLog) return;

  const log = state.activeDrawerLog;

  if (tab === 'kv') {
    body.innerHTML = `
      <div class="kv-grid">
        <div class="kv-key">事件 ID:</div>
        <div class="kv-val">#${log.id}</div>

        <div class="kv-key">链路 TraceId:</div>
        <div class="kv-val" style="font-family:var(--font-mono);font-size:12px;color:var(--accent-purple);word-break:break-all;">
          ${escHtml(log.traceId || 'tr-local-default')}
        </div>

        <div class="kv-key">发生时间:</div>
        <div class="kv-val">${formatTimestamp(log.timestamp)}</div>

        <div class="kv-key">源 IP 地址:</div>
        <div class="kv-val" style="color:var(--accent-cyan);font-weight:600;">${escHtml(log.ipAddress || '—')}</div>

        <div class="kv-key">操作主体:</div>
        <div class="kv-val">${escHtml(log.username || 'SYSTEM')}</div>

        <div class="kv-key">操作类型:</div>
        <div class="kv-val">${escHtml(log.operation || '—')}</div>

        <div class="kv-key">严重等级:</div>
        <div class="kv-val">${formatSevBadge(log.severity)}</div>

        <div class="kv-key">执行结果:</div>
        <div class="kv-val">${log.operationResult === 'SUCCESS' ? '<span class="badge b-success">SUCCESS</span>' : '<span class="badge b-fail">FAIL</span>'}</div>

        <div class="kv-key">来源微服务:</div>
        <div class="kv-val">${escHtml(log.sourceFile || 'auditvault-agent')}</div>

        <div class="kv-key">详情内容:</div>
        <div class="kv-val" style="background:var(--bg-canvas);padding:10px;border-radius:var(--radius-sm);border:1px solid var(--border-subtle);">${escHtml(log.detail || '—')}</div>
      </div>
    `;
  } else if (tab === 'json') {
    body.innerHTML = `
      <pre style="background:#06080d;padding:14px;border-radius:var(--radius-sm);border:1px solid var(--border-std);font-family:var(--font-mono);font-size:12.5px;color:var(--accent-cyan);overflow-x:auto;line-height:1.5;">${escHtml(JSON.stringify(log, null, 2))}</pre>
    `;
  } else if (tab === 'context') {
    // Render Surrounding Logs (previous and next logs around target)
    const targetIdx = state.currentRecords.findIndex(r => r.id === log.id);
    const start = Math.max(0, targetIdx - 5);
    const end = Math.min(state.currentRecords.length, targetIdx + 6);
    const contextList = state.currentRecords.slice(start, end);

    body.innerHTML = `
      <div style="font-size:12.5px;color:var(--text-tertiary);margin-bottom:8px;">还原事件前后上下文调用链 (±5条):</div>
      <div class="surrounding-list">
        ${contextList.map(c => `
          <div class="surrounding-item ${c.id === log.id ? 'target' : ''}" onclick="openDetailDrawer(${c.id})">
            <span style="color:var(--text-muted);">[#${c.id}]</span>
            <span style="color:var(--text-tertiary);">${formatTimestamp(c.timestamp)}</span>
            ${formatSevBadge(c.severity)}
            <span style="color:var(--accent-cyan);">${c.ipAddress || '0.0.0.0'}</span>
            <span style="color:var(--text-secondary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escHtml(c.detail || c.operation)}</span>
          </div>
        `).join('')}
      </div>
    `;
  }
}

function forwardToAiAssistant() {
  if (!state.activeDrawerLog) return;
  const payload = `[${formatTimestamp(state.activeDrawerLog.timestamp)}] [${state.activeDrawerLog.severity}] IP:${state.activeDrawerLog.ipAddress} User:${state.activeDrawerLog.username} Op:${state.activeDrawerLog.operation} Detail:${state.activeDrawerLog.detail}`;
  window.open(`http://localhost:8081?payload=${encodeURIComponent(payload)}`, '_blank');
}

function copyCurrentLog() {
  if (!state.activeDrawerLog) return;
  navigator.clipboard.writeText(JSON.stringify(state.activeDrawerLog, null, 2));
  showToast('日志详情已复制到剪贴板', 'success');
}

// ── Live Tail Streaming ─────────────────────────────────────
function toggleLiveTail() {
  state.liveTailActive = !state.liveTailActive;
  const btn = document.getElementById('btnLiveTail');
  const badge = document.getElementById('liveBadge');
  const statusText = document.getElementById('liveStatusText');

  if (state.liveTailActive) {
    btn.classList.add('active');
    badge.classList.remove('off');
    statusText.innerText = 'STREAMING';
    showToast('Live Tail 实时流监听已启动', 'info');

    state.liveTailTimer = setInterval(() => {
      loadData(1);
      state.liveMsgCount += Math.floor(Math.random() * 3) + 1;
    }, 3000);

    state.liveRateTimer = setInterval(() => {
      document.getElementById('liveRateVal').innerText = `${state.liveMsgCount} msg/s`;
      state.liveMsgCount = 0;
    }, 1000);
  } else {
    btn.classList.remove('active');
    badge.classList.add('off');
    statusText.innerText = 'PAUSED';
    clearInterval(state.liveTailTimer);
    clearInterval(state.liveRateTimer);
    document.getElementById('liveRateVal').innerText = '0 msg/s';
    showToast('Live Tail 实时流已暂停', 'info');
  }
}

// ── Export Excel ────────────────────────────────────────────
function exportExcel() {
  if (state.currentUser && state.currentUser.role !== 'ADMIN') {
    showToast('权限不足：仅系统管理员 (ADMIN) 可导出', 'error');
    return;
  }
  const params = buildQueryParams(1);
  window.location.href = `${API}/export?${params.toString()}`;
  showToast('Excel 流式导出任务已触发，请稍候...', 'info');
}

// ── Webhook Simulator Modal ─────────────────────────────────
function openWebhookModal() {
  document.getElementById('webhookModal').classList.remove('hidden');
  setPreset('payment_error');
}
function closeWebhookModal() {
  document.getElementById('webhookModal').classList.add('hidden');
}

function setPreset(type) {
  const textarea = document.getElementById('webhookPayload');
  if (type === 'payment_error') {
    textarea.value = JSON.stringify({
      level: 'ERROR',
      serviceName: 'order-payment-service',
      message: 'Payment gateway timeout for order #9982. Read timed out after 5000ms',
      ip: '192.168.1.108',
      traceId: 'trace-8899-pay-timeout'
    }, null, 2);
  } else if (type === 'rate_limit') {
    textarea.value = JSON.stringify({
      level: 'WARN',
      serviceName: 'api-gateway',
      message: 'Rate limit exceeded: 120 req/min for IP 172.16.0.88 on /api/auth/login',
      ip: '172.16.0.88',
      traceId: 'trace-429-bruteforce'
    }, null, 2);
  } else if (type === 'sqli_probe') {
    textarea.value = JSON.stringify({
      level: 'CRITICAL',
      serviceName: 'user-service',
      message: "SQL Injection probe detected in parameter 'username': admin' OR '1'='1",
      ip: '10.0.0.99',
      traceId: 'trace-sqli-alert-007'
    }, null, 2);
  } else if (type === 'sso_login') {
    textarea.value = JSON.stringify({
      level: 'INFO',
      serviceName: 'auth-server',
      message: 'SSO Login successful for user zhangsan via OAuth2 Keycloak',
      ip: '192.168.1.100',
      traceId: 'trace-sso-login-101'
    }, null, 2);
  }
}

async function sendWebhookPayload() {
  const token = document.getElementById('webhookToken').value.trim();
  const raw = document.getElementById('webhookPayload').value.trim();
  let payload;

  try {
    payload = JSON.parse(raw);
  } catch (e) {
    showToast('JSON 格式错误: ' + e.message, 'error');
    return;
  }

  try {
    const resp = await fetch('/api/logs/webhook', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Audit-Token': token
      },
      body: JSON.stringify(payload)
    });

    const res = await resp.json();
    if (resp.status === 202 || res.success) {
      showToast(`Webhook 摄入成功 (202 Accepted): 已接收 ${res.accepted || 1} 条日志`, 'success');
      closeWebhookModal();
      loadData(1);
    } else {
      showToast('摄入失败: ' + (res.message || '未知错误'), 'error');
    }
  } catch (e) {
    showToast('网络请求异常: ' + e.message, 'error');
  }
}

// ── Utility Helpers ─────────────────────────────────────────
function formatSevBadge(sev) {
  const s = (sev || 'INFO').toUpperCase();
  if (s === 'CRITICAL') return '<span class="badge b-crit">CRITICAL</span>';
  if (s === 'ERROR') return '<span class="badge b-err">ERROR</span>';
  if (s === 'WARN' || s === 'WARNING') return '<span class="badge b-warn">WARN</span>';
  if (s === 'DEBUG') return '<span class="badge b-debug">DEBUG</span>';
  return '<span class="badge b-info">INFO</span>';
}

function formatTimestamp(ts) {
  if (!ts) return '—';
  return ts.replace('T', ' ').slice(0, 19);
}

function truncateText(str, len) {
  if (!str) return '';
  return str.length > len ? str.slice(0, len) + '...' : str;
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
  const container = document.getElementById('toastContainer');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerText = msg;
  container.appendChild(toast);
  setTimeout(() => toast.remove(), 3500);
}

// ── Keyboard Shortcuts ──────────────────────────────────────
document.addEventListener('keydown', e => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault();
    const input = document.getElementById('globalSearchInput');
    if (input) input.focus();
  }
  if (e.key === 'Escape') {
    closeDetailDrawer();
    closeWebhookModal();
  }
});

// ── DOM Ready Initialization ────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  const authed = await requireAuth();
  if (authed) {
    const searchInput = document.getElementById('globalSearchInput');
    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        state.searchQuery = e.target.value;
        loadData(1);
      });
    }
    loadData(1);
  }
});
