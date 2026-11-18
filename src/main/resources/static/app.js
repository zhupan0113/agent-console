const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => Array.from(el.querySelectorAll(sel));
const view = $('#view');

const TITLES = {
    overview: ['总览', '本地 Codex / Trae / Claude 资产与成本'],
    agents: ['Agents', '会话、项目与模型归属'],
    usage: ['模型开销', '逐条用量与成本归因'],
    skills: ['Skills', '跨平台技能资产'],
    mcp: ['MCP', '已安装的 MCP Server'],
    scan: ['扫描与导入', '采集任务与 Trae 账单导入'],
    settings: ['设置', '采集参数与统计口径']
};

function today() {
    const d = new Date();
    return localIso(d);
}

function daysAgo(n) {
    const d = new Date();
    d.setDate(d.getDate() - n);
    return localIso(d);
}

function localIso(d) {
    const z = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
    return z.toISOString().slice(0, 10);
}

const state = {
    view: 'overview',
    agents: { page: 1, size: 20, platform: '', keyword: '' },
    usage: { page: 1, size: 20, from: daysAgo(29), to: today(), platform: '' },
    skills: { page: 1, size: 20, platform: '', scope: '', keyword: '' }
};

const esc = (v) => String(v == null ? '' : v).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[c]));

const num = (v) => Number(v || 0).toLocaleString('zh-CN');
const credits = (v) => Number(v || 0).toFixed(2) + ' 积分';
const dt = (v) => v ? String(v).replace('T', ' ').slice(0, 16) : '—';

function compact(v) {
    const n = Number(v || 0);
    if (n >= 1e9) return (n / 1e9).toFixed(2) + 'B';
    if (n >= 1e6) return (n / 1e6).toFixed(2) + 'M';
    if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K';
    return String(n);
}

const platBadge = (p) => `<span class="badge ${esc(p)}">${esc(p)}</span>`;
const cap = (p) => ({ codex: 'Codex', trae: 'Trae', claude: 'Claude' }[p] || p);
const statusBadge = (s) => `<span class="badge ${s === 'OK' ? 'ok' : (s === 'PARTIAL' ? 'warn' : '')}">${esc(s)}</span>`;

function toast(msg) {
    const el = $('#toast');
    el.textContent = msg;
    el.classList.remove('hidden');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => el.classList.add('hidden'), 2600);
}

async function api(path, options = {}) {
    const res = await fetch(path, options);
    let body;
    try {
        body = await res.json();
    } catch (e) {
        throw new Error('响应解析失败：' + res.status);
    }
    if (body.code !== 0) throw new Error(body.message || '请求失败');
    return body.data;
}

function openModal(html) {
    $('#modal-body').innerHTML = html;
    $('#modal').classList.remove('hidden');
}

function closeModal() {
    $('#modal').classList.add('hidden');
}

function statCard(label, value, foot) {
    return `<div class="card stat"><div class="stat-label">${label}</div><div class="stat-value">${value}</div><div class="stat-foot">${foot || ''}</div></div>`;
}

function empty(text) {
    return `<div class="empty">${esc(text)}</div>`;
}

async function render() {
    if (state.view === 'overview') return renderOverview();
    if (state.view === 'agents') return renderAgents();
    if (state.view === 'usage') return renderUsage();
    if (state.view === 'skills') return renderSkills();
    if (state.view === 'mcp') return renderMcp();
    if (state.view === 'scan') return renderScan();
    if (state.view === 'settings') return renderSettings();
}

function switchView(name) {
    state.view = name;
    $$('.nav-item').forEach((b) => b.classList.toggle('active', b.dataset.view === name));
    const t = TITLES[name] || ['', ''];
    $('#page-title').textContent = t[0];
    $('#page-sub').textContent = t[1];
    render().catch((e) => {
        view.innerHTML = `<div class="hint">加载失败：${esc(e.message)}</div>`;
    });
}

/* ---------------- overview ---------------- */

async function renderOverview() {
    view.innerHTML = empty('加载中…');
    const d = await api('/api/overview');
    const t = d.today || {};
    const m = d.month || {};
    const all = d.allTime || {};
    view.innerHTML = `
        <div class="grid c4">
            ${statCard('Agent 会话', num(d.agents), 'Codex / Trae / Claude')}
            ${statCard('Skills', num(d.skills), '全局 + 插件 + 项目')}
            ${statCard('MCP Server', num(d.mcpServers), '已安装配置')}
            ${statCard('项目', num(d.projects), '按工作目录归并')}
        </div>
        <div class="grid c3" style="margin-top:14px" id="platform-cards"></div>
        <div class="card" style="margin-top:14px">
            <div class="card-head">
                <div><div class="card-title">近 30 天消耗</div><div class="card-sub" id="trend-total"></div></div>
                <div>
                    <button class="btn sm ghost trend-mode" data-mode="credits">积分</button>
                    <button class="btn sm ghost trend-mode active" data-mode="tokens">Token</button>
                </div>
            </div>
            <div id="chart-trend"></div>
        </div>
        <div class="grid c2" style="margin-top:14px">
            <div class="card">
                <div class="card-head"><div class="card-title">模型消耗 Top</div><div class="card-sub">本月</div></div>
                <div class="table-wrap" id="top-models"></div>
            </div>
            <div class="card">
                <div class="card-head"><div class="card-title">最近扫描</div></div>
                <div class="table-wrap" id="recent-scans"></div>
            </div>
        </div>`;

    renderPlatformCards(d.platforms || []);

    const trend = d.trend || [];
    const draw = (mode) => drawTrend($('#chart-trend'), trend, mode);
    draw('tokens');
    $$('.trend-mode').forEach((b) => b.addEventListener('click', () => {
        $$('.trend-mode').forEach((x) => x.classList.remove('active'));
        b.classList.add('active');
        const mode = b.dataset.mode;
        const total = trend.reduce((s, x) => s + Number(x[mode] || 0), 0);
        $('#trend-total').textContent = mode === 'tokens' ? ('合计 ' + compact(total) + ' tokens') : ('合计 ' + total.toFixed(2) + ' 积分');
        draw(mode);
    }));
    const totalTokens = trend.reduce((s, x) => s + Number(x.tokens || 0), 0);
    $('#trend-total').textContent = '合计 ' + compact(totalTokens) + ' tokens';

    const models = d.topModels || [];
    $('#top-models').innerHTML = models.length ? `<table><thead><tr><th>模型</th><th>平台</th><th>调用</th><th>Token</th><th>积分</th></tr></thead><tbody>
        ${models.map((x) => `<tr><td>${esc(x.model || 'unknown')}</td><td>${platBadge(x.platform)}</td><td>${num(x.calls)}</td>
        <td>${compact(x.tokens)}</td><td>${Number(x.credits || 0) > 0 ? Number(x.credits).toFixed(2) : '—'}</td></tr>`).join('')}
    </tbody></table>` : empty('暂无用量');

    const scans = d.recentScans || [];
    $('#recent-scans').innerHTML = scans.length ? `<table><thead><tr><th>开始</th><th>状态</th><th>条目</th></tr></thead><tbody>
        ${scans.map((x) => `<tr><td class="mono">${dt(x.startedAt)}</td><td>${statusBadge(x.status)}</td><td>${num(x.itemCount)}</td></tr>`).join('')}
    </tbody></table>` : empty('尚未扫描');
}

function renderPlatformCards(platforms) {
    const el = $('#platform-cards');
    if (!el) return;
    el.innerHTML = platforms.map((p) => {
        const m = p.month || {};
        const isTrae = p.platform === 'trae';
        const calls = Number(m.calls || 0);
        const creditValue = Number(m.credits || 0);
        const tokens = Number(m.tokens || 0);
        const main = isTrae
            ? credits(creditValue)
            : `${compact(tokens)} <span class="card-sub">tokens</span>`;
        const middle = isTrae
            ? `<div><span>日均积分</span><b>${(creditValue / new Date().getDate()).toFixed(1)}</b></div>`
            : `<div><span>缓存命中</span><b>${compact(m.cachedTokens)}</b></div>`;
        const top = p.topModel
            ? `<span class="mono">${esc(p.topModel.model)}</span> · ${num(p.topModel.calls)} 次`
            : '暂无用量';
        const io = isTrae ? '' : `输入 ${compact(m.inputTokens)} · 输出 ${compact(m.outputTokens)} · `;
        return `<div class="card p-card">
            <div class="p-head">
                <div><span class="badge ${esc(p.platform)}">${esc(p.platform)}</span> <b>${cap(p.platform)}</b></div>
                <span class="card-sub">${p.lastActive ? dt(p.lastActive) : '未检测到'}</span>
            </div>
            <div class="p-main">${calls ? main : '<span class="muted" style="font-size:15px">未检测到用量</span>'}</div>
            <div class="p-metrics">
                <div><span>本月调用</span><b>${num(calls)}</b></div>
                ${middle}
                <div><span>资产</span><b>${p.agents} Agent · ${p.mcp} MCP</b></div>
            </div>
            <div class="spark" id="spark-${esc(p.platform)}"></div>
            <div class="card-sub" style="margin-top:6px">${io}Top ${top}</div>
        </div>`;
    }).join('');
    platforms.forEach((p) => drawSpark($('#spark-' + p.platform), p.trend || [], p.platform === 'trae' ? 'credits' : 'tokens'));
}

function drawSpark(el, data, mode) {
    if (!el) return;
    if (!data || data.length < 2) {
        el.innerHTML = '';
        return;
    }
    const w = Math.max(el.clientWidth || 280, 200);
    const h = 54;
    const pad = 4;
    const values = data.map((d) => Number(d[mode] || 0));
    const max = Math.max(1, ...values);
    const x = (i) => pad + (w - pad * 2) * i / (values.length - 1);
    const y = (v) => h - pad - (v / max) * (h - pad * 2);
    const line = values.map((v, i) => (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1)).join(' ');
    const area = line + ` L ${x(values.length - 1).toFixed(1)} ${h - pad} L ${x(0).toFixed(1)} ${h - pad} Z`;
    const color = mode === 'credits' ? '#7c3aed' : '#2563eb';
    el.innerHTML = `<svg width="${w}" height="${h}"><path d="${area}" fill="${color}22"/><path d="${line}" fill="none" stroke="${color}" stroke-width="1.8"/></svg>`;
}

function drawTrend(el, data, mode) {
    if (!el) return;
    if (!data.length) {
        el.innerHTML = empty('暂无数据');
        return;
    }
    const w = Math.max(el.clientWidth || 560, 320);
    const h = 220;
    const pad = { l: 48, r: 16, t: 16, b: 28 };
    const values = data.map((d) => Number(d[mode] || 0));
    const max = Math.max(1, ...values);
    const x = (i) => pad.l + (data.length === 1 ? 0 : (w - pad.l - pad.r) * i / (data.length - 1));
    const y = (v) => h - pad.b - (v / max) * (h - pad.t - pad.b);
    const line = values.map((v, i) => (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1)).join(' ');
    const area = line + ` L ${x(values.length - 1).toFixed(1)} ${h - pad.b} L ${x(0).toFixed(1)} ${h - pad.b} Z`;
    let grid = '';
    for (let i = 0; i <= 4; i++) {
        const gy = pad.t + (h - pad.t - pad.b) * i / 4;
        const gv = max * (1 - i / 4);
        grid += `<line x1="${pad.l}" y1="${gy}" x2="${w - pad.r}" y2="${gy}" stroke="#eef2f8"/>`;
        grid += `<text x="${pad.l - 8}" y="${gy + 3}" text-anchor="end">${compact(gv)}</text>`;
    }
    const dots = data.map((d, i) => `<circle cx="${x(i)}" cy="${y(values[i])}" r="2.5" fill="#2563eb"><title>${esc(d.date)} · ${mode === 'tokens' ? num(values[i]) + ' tokens' : values[i].toFixed(2) + ' 积分'}</title></circle>`).join('');
    el.innerHTML = `<svg class="chart" width="${w}" height="${h}">
        <defs><linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#3b82f6" stop-opacity="0.22"/>
            <stop offset="100%" stop-color="#3b82f6" stop-opacity="0"/>
        </linearGradient></defs>
        ${grid}
        <path d="${area}" fill="url(#areaGrad)"/>
        <path d="${line}" fill="none" stroke="#2563eb" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
        ${dots}
        <text x="${pad.l}" y="${h - 8}">${esc(data[0].date)}</text>
        <text x="${w - pad.r}" y="${h - 8}" text-anchor="end">${esc(data[data.length - 1].date)}</text>
    </svg>`;
}

function drawBars(el, items, emptyText) {
    if (!el) return;
    if (!items.length) {
        el.innerHTML = empty(emptyText || '暂无数据');
        return;
    }
    const max = Math.max(1, ...items.map((i) => i.value));
    el.innerHTML = items.map((i) => `<div class="bar-row">
        <div class="bar-name" title="${esc(i.name)}">${esc(i.name)}</div>
        <div class="bar-track"><div class="bar-fill" style="width:${Math.max(2, (i.value / max) * 100)}%"></div></div>
        <div class="bar-val">${esc(i.label)}</div>
    </div>`).join('');
}

/* ---------------- agents ---------------- */

async function renderAgents() {
    const s = state.agents;
    view.innerHTML = `
        <div class="filters">
            <select id="ag-platform">
                <option value="">全部平台</option><option value="codex">Codex</option>
                <option value="trae">Trae</option><option value="claude">Claude</option>
            </select>
            <input id="ag-keyword" placeholder="搜索名称 / 工作目录" value="${esc(s.keyword)}" style="width:260px">
            <button class="btn" id="ag-search">查询</button>
            <span class="muted" id="ag-count"></span>
        </div>
        <div class="card"><div class="table-wrap" id="ag-table"></div><div class="pager" id="ag-pager"></div></div>
        <div class="card" id="ag-prompts" style="margin-top:14px"></div>`;
    $('#ag-platform').value = s.platform;
    const submit = () => {
        s.platform = $('#ag-platform').value;
        s.keyword = $('#ag-keyword').value.trim();
        s.page = 1;
        loadAgents();
    };
    $('#ag-search').addEventListener('click', submit);
    $('#ag-keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });
    $('#ag-platform').addEventListener('change', submit);
    await Promise.all([loadAgents(), loadPromptTemplates()]);
}

async function loadPromptTemplates() {
    const el = $('#ag-prompts');
    if (!el) return;
    try {
        const d = await api('/api/skills?size=300');
        const rows = (d.records || []).filter((r) => r.sourceType === 'prompt');
        el.innerHTML = `<div class="card-head"><div><div class="card-title">提示词模板</div><div class="card-sub">来自各平台 skills 目录，点击查看全文</div></div><span class="muted">${rows.length} 份</span></div>
            ${rows.length ? `<div class="grid c3">${rows.map((r, i) => `<div class="prompt-card" data-idx="${i}">
                <div class="p-head"><span class="mono" style="font-size:13px">${esc(r.name)}</span><span class="badge warn">${esc(cap(r.platform))} · 提示词</span></div>
                <div class="muted" style="font-size:12px;line-height:1.6">${esc((r.description || '').slice(0, 120))}</div>
                <div class="muted" style="font-size:11px">更新 ${dt(r.updatedAt)}</div>
            </div>`).join('')}</div>` : empty('未发现提示词模板')}`;
        $$('#ag-prompts .prompt-card').forEach((c) => c.addEventListener('click', () => showSkill(rows[Number(c.dataset.idx)])));
    } catch (e) {
        el.innerHTML = `<div class="hint">提示词加载失败：${esc(e.message)}</div>`;
    }
}

async function loadAgents() {
    const s = state.agents;
    const q = new URLSearchParams({ page: s.page, size: s.size, platform: s.platform, keyword: s.keyword });
    const d = await api('/api/agents?' + q);
    $('#ag-count').textContent = `共 ${d.total} 个`;
    const rows = d.records || [];
    $('#ag-table').innerHTML = rows.length ? `<table><thead><tr>
        <th>名称</th><th>平台</th><th>项目</th><th>模型</th><th>Tokens</th><th>更新时间</th></tr></thead><tbody>
        ${rows.map((r) => `<tr class="clickable" data-id="${r.id}">
            <td class="ellipsis" title="${esc(r.name)}">${esc(r.name)}</td>
            <td>${platBadge(r.platform)}</td>
            <td>${esc(r.project || '—')}</td>
            <td class="mono">${esc(r.model || '—')}</td>
            <td>${compact(r.tokensUsed)}</td>
            <td class="mono">${dt(r.updatedAt)}</td></tr>`).join('')}
        </tbody></table>` : empty('没有匹配的 Agent');
    $$('#ag-table tr[data-id]').forEach((tr) => tr.addEventListener('click', () => showAgent(tr.dataset.id)));
    renderPager($('#ag-pager'), d, (p) => { s.page = p; loadAgents(); });
}

async function showAgent(id) {
    const a = await api('/api/agents/' + id);
    let raw = a.rawJson || '';
    try { raw = JSON.stringify(JSON.parse(raw), null, 2); } catch (e) { /* keep */ }
    openModal(`<h3>${esc(a.name)}</h3>
        <div class="grid c2">
            <div class="kv"><span class="k">平台</span><span class="v">${platBadge(a.platform)}</span></div>
            <div class="kv"><span class="k">项目</span><span class="v">${esc(a.project || '—')}</span></div>
            <div class="kv"><span class="k">模型</span><span class="v mono">${esc(a.model || '—')}</span></div>
            <div class="kv"><span class="k">Provider</span><span class="v mono">${esc(a.provider || '—')}</span></div>
            <div class="kv"><span class="k">Tokens</span><span class="v">${num(a.tokensUsed)}</span></div>
            <div class="kv"><span class="k">创建</span><span class="v mono">${dt(a.createdAt)}</span></div>
            <div class="kv"><span class="k">更新</span><span class="v mono">${dt(a.updatedAt)}</span></div>
        </div>
        <div style="margin-top:14px"><div class="card-sub">工作目录</div><div class="mono" style="word-break:break-all">${esc(a.cwd || '—')}</div></div>
        <div style="margin-top:14px"><div class="card-sub">Session ID</div><div class="mono">${esc(a.externalId)}</div></div>
        <div style="margin-top:14px"><div class="card-sub">原始信息</div><pre class="mono" style="background:#f8fafc;padding:12px;border-radius:10px;overflow:auto">${esc(raw)}</pre></div>`);
}

/* ---------------- usage ---------------- */

async function renderUsage() {
    const s = state.usage;
    view.innerHTML = `
        <div class="filters">
            <select id="u-platform"><option value="">全部平台</option><option value="codex">Codex</option><option value="trae">Trae</option></select>
            <input type="date" id="u-from" value="${s.from}">
            <span class="muted">至</span>
            <input type="date" id="u-to" value="${s.to}">
            <button class="btn" id="u-search">查询</button>
        </div>
        <div class="grid c4" id="u-summary"></div>
        <div class="card" style="margin-top:14px">
            <div class="card-head"><div class="card-title">每日趋势</div><div class="card-sub" id="u-trend-label"></div></div>
            <div id="u-chart"></div>
        </div>
        <div class="grid c2" style="margin-top:14px">
            <div class="card"><div class="card-head"><div class="card-title">按模型</div></div><div class="table-wrap" id="u-models"></div></div>
            <div class="card"><div class="card-head"><div class="card-title">按项目</div></div><div class="table-wrap" id="u-projects"></div></div>
        </div>
        <div class="card" style="margin-top:14px">
            <div class="card-head"><div class="card-title">用量明细</div><div class="card-sub">逐条记录</div></div>
            <div class="table-wrap" id="u-records"></div>
            <div class="pager" id="u-pager"></div>
        </div>`;
    $('#u-platform').value = s.platform;
    $('#u-search').addEventListener('click', () => {
        s.from = $('#u-from').value;
        s.to = $('#u-to').value;
        s.platform = $('#u-platform').value;
        s.page = 1;
        loadUsage();
    });
    await loadUsage();
}

async function loadUsage() {
    const s = state.usage;
    const base = new URLSearchParams({ from: s.from, to: s.to, platform: s.platform });
    const [summary, daily, models, projects] = await Promise.all([
        api('/api/usage/summary?' + base),
        api('/api/usage/daily?' + base),
        api('/api/usage/by-model?' + base),
        api('/api/usage/by-project?' + new URLSearchParams({ from: s.from, to: s.to }))
    ]);
    const cacheRate = Number(summary.tokens || 0) > 0
        ? (Number(summary.cachedTokens || 0) / Number(summary.tokens) * 100).toFixed(1)
        : '0.0';
    $('#u-summary').innerHTML = [
        statCard('调用次数', num(summary.calls), '记录条数'),
        statCard('Token', compact(summary.tokens), `输入 ${compact(summary.inputTokens)} / 输出 ${compact(summary.outputTokens)}`),
        statCard('缓存命中', compact(summary.cachedTokens), cacheRate + '% 占比'),
        statCard('积分', Number(summary.credits || 0).toFixed(2), 'Trae 计费单位')
    ].join('');
    const totalTokens = daily.reduce((a, b) => a + Number(b.tokens || 0), 0);
    const totalCredits = daily.reduce((a, b) => a + Number(b.credits || 0), 0);
    const mode = totalTokens > 0 ? 'tokens' : 'credits';
    drawTrend($('#u-chart'), daily, mode);
    $('#u-trend-label').textContent = mode === 'tokens'
        ? `合计 ${compact(totalTokens)} tokens`
        : `合计 ${totalCredits.toFixed(2)} 积分`;

    $('#u-models').innerHTML = models.length ? `<table><thead><tr><th>模型</th><th>平台</th><th>调用</th><th>Token</th><th>积分</th></tr></thead><tbody>
        ${models.map((x) => `<tr><td class="mono">${esc(x.model || 'unknown')}</td><td>${platBadge(x.platform)}</td><td>${num(x.calls)}</td><td>${compact(x.tokens)}</td>
        <td>${Number(x.credits || 0) > 0 ? Number(x.credits).toFixed(2) : '—'}</td></tr>`).join('')}
        </tbody></table>` : empty('暂无数据');

    $('#u-projects').innerHTML = projects.length ? `<table><thead><tr><th>项目</th><th>调用</th><th>Token</th><th>积分</th></tr></thead><tbody>
        ${projects.map((x) => `<tr><td>${esc(x.project || '未归属')}</td><td>${num(x.calls)}</td><td>${compact(x.tokens)}</td><td>${Number(x.credits || 0) > 0 ? Number(x.credits).toFixed(2) : '—'}</td></tr>`).join('')}
        </tbody></table>` : empty('暂无数据');

    const q = new URLSearchParams({ page: s.page, size: s.size, from: s.from, to: s.to, platform: s.platform });
    const page = await api('/api/usage/records?' + q);
    const rows = page.records || [];
    $('#u-records').innerHTML = rows.length ? `<table><thead><tr>
        <th>时间</th><th>平台</th><th>模型</th><th>项目</th><th>输入</th><th>缓存</th><th>输出</th><th>积分</th><th>来源</th></tr></thead><tbody>
        ${rows.map((r) => `<tr>
            <td class="mono">${dt(r.occurredAt)}</td>
            <td>${platBadge(r.platform)}</td>
            <td class="mono">${esc(r.model || '—')}</td>
            <td>${esc(r.project || '—')}</td>
            <td>${compact(r.inputTokens)}</td>
            <td>${compact(r.cachedTokens)}</td>
            <td>${compact(r.outputTokens)}</td>
            <td>${r.credits == null ? '—' : Number(r.credits).toFixed(2)}</td>
            <td class="mono">${esc(r.source || '—')}</td>
        </tr>`).join('')}
        </tbody></table>` : empty('暂无记录');
    renderPager($('#u-pager'), page, (p) => { s.page = p; loadUsage(); });
}

/* ---------------- asset file viewer ---------------- */

const fmtSize = (n) => {
    const v = Number(n || 0);
    if (v >= 1048576) return (v / 1048576).toFixed(2) + ' MB';
    if (v >= 1024) return (v / 1024).toFixed(1) + ' KB';
    return v + ' B';
};

const typeLabel = (r) => (r.sourceType === 'prompt' ? '提示词' : (r.sourceType || '—'));

function renderFile(file, label) {
    const lines = String(file.content || '').split('\n').length;
    return `<div class="file-bar">
            <span class="card-sub" style="margin:0">${esc(label || file.name)}</span>
            <span class="file-meta"><span>${esc(fmtSize(file.size))}</span><span>${num(lines)} 行</span><span>修改 ${dt(file.modifiedAt)}</span>${file.truncated ? '<span class="badge warn">已截断</span>' : ''}</span>
        </div>
        <pre class="file-view mono">${esc(file.content)}</pre>
        ${file.truncated ? '<div class="hint" style="margin:8px 0 0">文件较大，仅显示前 200,000 个字符</div>' : ''}`;
}

async function loadFileInto(el, path, label) {
    if (!el || !path) return;
    el.innerHTML = '<div class="muted" style="padding:8px 0">加载中…</div>';
    try {
        const f = await api('/api/assets/content?path=' + encodeURIComponent(path));
        el.innerHTML = renderFile(f, label);
    } catch (e) {
        el.innerHTML = `<div class="hint">无法读取文件：${esc(e.message)}</div>`;
    }
}

async function showSkill(r) {
    const parts = String(r.path || '').split(/[\\/]/);
    const fileName = parts.pop() || '';
    const dir = parts.join('\\');
    openModal(`<h3>${esc(r.name)}</h3>
        <div class="filters" style="margin:0 0 12px">${platBadge(r.platform)}<span class="badge">${esc(r.scope)}</span><span class="badge ${r.sourceType === 'prompt' ? 'warn' : ''}">${esc(typeLabel(r))}</span><span class="badge ${r.enabled ? 'ok' : 'off'}">${r.enabled ? '启用' : '停用'}</span></div>
        <div class="card-sub">描述</div><p style="font-size:13px;line-height:1.7">${esc(r.description || '—')}</p>
        <div class="card-sub">路径</div><div class="mono" style="word-break:break-all;font-size:11.5px">${esc(r.path)}</div>
        <div id="sk-content" style="margin-top:6px"></div>
        <div id="sk-files"></div>`);
    await loadFileInto($('#sk-content'), r.path, fileName);
    loadSkillFiles($('#sk-files'), dir, r.path);
}

async function loadSkillFiles(el, dir, mainPath) {
    if (!el || !dir) return;
    try {
        const files = await api('/api/assets/list?dir=' + encodeURIComponent(dir));
        const others = files.filter((f) => f.path !== mainPath);
        if (!others.length) return;
        el.innerHTML = `<div class="card-sub" style="margin-top:16px">目录内其他文件（${others.length}）</div>
            <div class="sk-file-list">${others.map((f, i) => `<span class="sk-file" data-idx="${i}" title="${esc(f.relativePath)}">${esc(f.relativePath)}</span>`).join('')}</div>`;
        $$('#sk-files .sk-file').forEach((b) => b.addEventListener('click', async () => {
            $$('#sk-files .sk-file').forEach((x) => x.classList.remove('active'));
            b.classList.add('active');
            await loadFileInto($('#sk-content'), others[Number(b.dataset.idx)].path, others[Number(b.dataset.idx)].relativePath);
        }));
    } catch (e) {
        /* 目录不可读时忽略 */
    }
}

async function showMcp(r) {
    openModal(`<h3>${esc(r.name)}</h3>
        <div class="filters" style="margin:0 0 12px">${platBadge(r.platform)}<span class="badge">${esc(r.transport || '—')}</span><span class="badge ${r.enabled ? 'ok' : 'off'}">${r.enabled ? '启用' : '停用'}</span></div>
        ${r.command ? `<div class="kv"><span class="k">命令</span><span class="v mono">${esc(r.command)}</span></div>` : ''}
        ${r.url ? `<div class="kv"><span class="k">地址</span><span class="v mono">${esc(r.url)}</span></div>` : ''}
        ${r.argsJson ? `<div class="kv"><span class="k">参数</span><span class="v mono">${esc(r.argsJson)}</span></div>` : ''}
        ${r.envJson ? `<div class="kv"><span class="k">环境变量</span><span class="v mono">${esc(r.envJson)}</span></div>` : ''}
        <div class="card-sub" style="margin-top:12px">配置文件</div>
        <div class="mono" style="word-break:break-all;font-size:11.5px">${esc(r.configPath || '—')}</div>
        <div id="mcp-content"></div>`);
    if (r.configPath) {
        const name = String(r.configPath).split(/[\\/]/).pop();
        await loadFileInto($('#mcp-content'), r.configPath, name);
    }
}

/* ---------------- skills ---------------- */

async function renderSkills() {
    const s = state.skills;
    view.innerHTML = `
        <div class="filters">
            <select id="sk-platform"><option value="">全部平台</option><option value="codex">Codex</option><option value="trae">Trae</option><option value="claude">Claude</option></select>
            <select id="sk-scope"><option value="">全部范围</option><option value="global">global</option><option value="plugin">plugin</option></select>
            <input id="sk-keyword" placeholder="搜索名称或描述" value="${esc(s.keyword)}" style="width:260px">
            <button class="btn" id="sk-search">查询</button>
            <span class="muted" id="sk-count"></span>
        </div>
        <div class="card"><div class="table-wrap" id="sk-table"></div><div class="pager" id="sk-pager"></div></div>`;
    $('#sk-platform').value = s.platform;
    $('#sk-scope').value = s.scope;
    const submit = () => {
        s.platform = $('#sk-platform').value;
        s.scope = $('#sk-scope').value;
        s.keyword = $('#sk-keyword').value.trim();
        s.page = 1;
        loadSkills();
    };
    $('#sk-search').addEventListener('click', submit);
    $('#sk-keyword').addEventListener('keydown', (e) => { if (e.key === 'Enter') submit(); });
    await loadSkills();
}

async function loadSkills() {
    const s = state.skills;
    const q = new URLSearchParams({ page: s.page, size: s.size, platform: s.platform, scope: s.scope, keyword: s.keyword });
    const d = await api('/api/skills?' + q);
    $('#sk-count').textContent = `共 ${d.total} 个 · 点击行查看全文`;
    const rows = d.records || [];
    $('#sk-table').innerHTML = rows.length ? `<table><thead><tr><th>名称</th><th>平台</th><th>范围</th><th>类型</th><th>描述</th><th>更新时间</th></tr></thead><tbody>
        ${rows.map((r, i) => `<tr class="clickable" data-idx="${i}">
            <td class="mono">${esc(r.name)}</td>
            <td>${platBadge(r.platform)}</td>
            <td>${esc(r.scope)}</td>
            <td><span class="badge ${r.sourceType === 'prompt' ? 'warn' : ''}">${esc(typeLabel(r))}</span></td>
            <td class="ellipsis" title="${esc(r.description)}">${esc(r.description || '—')}</td>
            <td class="mono">${dt(r.updatedAt)}</td></tr>`).join('')}
        </tbody></table>` : empty('没有找到 Skill');
    $$('#sk-table tr[data-idx]').forEach((tr) => tr.addEventListener('click', () => {
        showSkill(rows[Number(tr.dataset.idx)]);
    }));
    renderPager($('#sk-pager'), d, (p) => { s.page = p; loadSkills(); });
}

/* ---------------- mcp ---------------- */

async function renderMcp() {
    view.innerHTML = `<div class="filters"><select id="m-platform"><option value="">全部平台</option><option value="codex">Codex</option><option value="trae">Trae</option><option value="claude">Claude</option></select><span class="muted" id="m-count"></span></div><div class="grid c3" id="m-list"></div>`;
    const load = async () => {
        const platform = $('#m-platform').value;
        const d = await api('/api/mcp?size=100&platform=' + encodeURIComponent(platform));
        $('#m-count').textContent = `共 ${d.total} 个`;
        const rows = d.records || [];
        $('#m-list').innerHTML = rows.length ? rows.map((r, i) => `<div class="card mcp-card" data-idx="${i}" title="点击查看详情与配置文件">
            <div class="mcp-head"><div><div class="mcp-name">${esc(r.name)}</div><div style="margin-top:6px">${platBadge(r.platform)} <span class="badge">${esc(r.transport || '—')}</span></div></div>
            <span class="badge ${r.enabled ? 'ok' : 'off'}">${r.enabled ? '启用' : '停用'}</span></div>
            ${r.command ? `<div class="kv"><span class="k">命令</span><span class="v mono">${esc(r.command)}</span></div>` : ''}
            ${r.url ? `<div class="kv"><span class="k">地址</span><span class="v mono">${esc(r.url)}</span></div>` : ''}
            <div class="kv"><span class="k">配置</span><span class="v mono" title="${esc(r.configPath)}">${esc((r.configPath || '').split('\\').pop())}</span></div>
            <div class="muted" style="font-size:11px">点击查看配置详情</div>
        </div>`).join('') : empty('没有 MCP Server');
        $$('#m-list .mcp-card').forEach((c) => c.addEventListener('click', () => showMcp(rows[Number(c.dataset.idx)])));
    };
    $('#m-platform').addEventListener('change', load);
    await load();
}

/* ---------------- scan ---------------- */

async function renderScan() {
    view.innerHTML = `
        <div class="grid c2">
            <div class="card">
                <div class="card-head"><div><div class="card-title">资产扫描</div><div class="card-sub">Codex / Trae / Claude 只读采集</div></div><button class="btn primary" id="btn-scan2">立即扫描</button></div>
                <div class="muted" id="scan-result">尚未执行</div>
            </div>
            <div class="card">
                <div class="card-head"><div><div class="card-title">Trae 用量导入</div><div class="card-sub">积分明细 xlsx</div></div><button class="btn primary" id="btn-import">导入</button></div>
                <div class="muted" id="import-result">同一文件不会重复写入</div>
            </div>
        </div>
        <div class="card" style="margin-top:14px"><div class="card-head"><div class="card-title">扫描记录</div></div><div class="table-wrap" id="scan-runs"></div></div>
        <div class="card" style="margin-top:14px"><div class="card-head"><div class="card-title">导入记录</div></div><div class="table-wrap" id="import-history"></div></div>`;
    $('#btn-scan2').addEventListener('click', async () => {
        $('#scan-result').textContent = '扫描中…';
        try {
            const r = await api('/api/scan', { method: 'POST' });
            $('#scan-result').textContent = `完成：${r.status}，条目 ${r.items}，耗时 ${r.elapsedMs}ms` + (r.errors && r.errors.length ? '，警告：' + r.errors.join('; ') : '');
            await loadScanRuns();
        } catch (e) {
            $('#scan-result').textContent = '失败：' + e.message;
        }
    });
    $('#btn-import').addEventListener('click', async () => {
        $('#import-result').textContent = '导入中…';
        try {
            const r = await api('/api/import/trae', { method: 'POST' });
            $('#import-result').textContent = r.skipped
                ? r.message
                : `完成：${r.rows} 行，新增 ${r.inserted}，积分 ${Number(r.credits).toFixed(2)}`;
            await loadImportHistory();
        } catch (e) {
            $('#import-result').textContent = '失败：' + e.message;
        }
    });
    await Promise.all([loadScanRuns(), loadImportHistory()]);
}

async function loadScanRuns() {
    const rows = await api('/api/scan/runs');
    $('#scan-runs').innerHTML = rows.length ? `<table><thead><tr><th>开始</th><th>结束</th><th>触发</th><th>状态</th><th>条目</th><th>说明</th></tr></thead><tbody>
        ${rows.map((r) => `<tr><td class="mono">${dt(r.startedAt)}</td><td class="mono">${dt(r.finishedAt)}</td><td>${esc(r.triggerType)}</td><td>${statusBadge(r.status)}</td><td>${num(r.itemCount)}</td><td class="ellipsis">${esc(r.message || '')}</td></tr>`).join('')}
        </tbody></table>` : empty('尚未扫描');
}

async function loadImportHistory() {
    const rows = await api('/api/import/history');
    $('#import-history').innerHTML = rows.length ? `<table><thead><tr><th>时间</th><th>文件</th><th>行数</th><th>积分</th><th>状态</th></tr></thead><tbody>
        ${rows.map((r) => `<tr><td class="mono">${dt(r.importedAt)}</td><td>${esc(r.fileName)}</td><td>${num(r.rowsCount)}</td><td>${Number(r.credits || 0).toFixed(2)}</td><td>${statusBadge(r.status)}</td></tr>`).join('')}
        </tbody></table>` : empty('尚未导入');
}

/* ---------------- settings ---------------- */

async function renderSettings() {
    const settings = await api('/api/settings');
    view.innerHTML = `
        <div class="grid c2">
            <div class="card">
                <div class="card-head"><div class="card-title">采集设置</div></div>
                <div class="filters" style="flex-direction:column;align-items:stretch">
                    <label class="card-sub">Trae 用量表路径<input id="set-xlsx" value="${esc(settings['trae.usage.xlsx'] || '')}" style="width:100%;margin-top:6px"></label>
                    <label class="card-sub">自动扫描<select id="set-auto" style="width:100%;margin-top:6px"><option value="true">开启</option><option value="false">关闭</option></select></label>
                    <div><button class="btn primary" id="set-save">保存</button></div>
                </div>
            </div>
            <div class="card">
                <div class="card-head"><div class="card-title">统计口径</div></div>
                <div class="card-sub" style="line-height:1.9">
                    Codex：来自 rollout 的 token_usage_record，按响应逐条记录，total = 输入 + 缓存 + 输出。<br>
                    Trae：来自积分用量表，按次记录积分，平台不提供 token。<br>
                    Claude：探测「~/.claude」，当前无数据。<br>
                    采集全程只读，不修改任何平台配置。
                </div>
            </div>
        </div>`;
    $('#set-auto').value = settings['scan.auto'] || 'true';
    $('#set-save').addEventListener('click', async () => {
        await api('/api/settings', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                'trae.usage.xlsx': $('#set-xlsx').value,
                'scan.auto': $('#set-auto').value
            })
        });
        toast('设置已保存');
    });
}

/* ---------------- common ---------------- */

function renderPager(el, page, onGo) {
    if (!el) return;
    const pages = Math.max(1, Math.ceil((page.total || 0) / (page.size || 20)));
    el.innerHTML = `<span>第 ${page.current || 1} / ${pages} 页 · 共 ${page.total || 0} 条</span>`;
    const prev = document.createElement('button');
    prev.className = 'btn sm';
    prev.textContent = '上一页';
    prev.disabled = (page.current || 1) <= 1;
    prev.addEventListener('click', () => onGo((page.current || 1) - 1));
    const next = document.createElement('button');
    next.className = 'btn sm';
    next.textContent = '下一页';
    next.disabled = (page.current || 1) >= pages;
    next.addEventListener('click', () => onGo((page.current || 1) + 1));
    el.appendChild(prev);
    el.appendChild(next);
}

/* ---------------- boot ---------------- */

$$('.nav-item').forEach((b) => b.addEventListener('click', () => switchView(b.dataset.view)));
$('#modal-close').addEventListener('click', closeModal);
$('#modal').addEventListener('click', (e) => { if (e.target.id === 'modal') closeModal(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeModal(); });

$('#btn-scan').addEventListener('click', async () => {
    const el = $('#scan-state');
    el.textContent = '扫描中…';
    try {
        const r = await api('/api/scan', { method: 'POST' });
        el.textContent = `完成 · 条目 ${r.items}`;
        if (state.view === 'overview' || state.view === 'scan') render();
    } catch (e) {
        el.textContent = '扫描失败';
        toast(e.message);
    }
});

switchView('overview');
