// Fəaliyyət planı: bu torpaqda nə, nə vaxt, niyə və nə ilə etmək olar.
import { api, renderLayout, $, $$, esc, icon, fmt, params, ring } from '../core.js';

renderLayout('plan');

const MON = ['Yan', 'Fev', 'Mar', 'Apr', 'May', 'İyn', 'İyl', 'Avq', 'Sen', 'Okt', 'Noy', 'Dek'];
const KIND = { prep: ['Hazırlıq', '#6b7a8f'], plant: ['Əkin, salınma', '#17804a'], water: ['Suvarma', '#2a78d6'], care: ['Qulluq', '#c98a12'], harvest: ['Yığım, satış', '#b4471f'], other: ['Digər', '#8a8f98'] };
const PHASE = { year1: 'Salınma ili', mature: 'Bar illəri', all: 'Hər il' };
const q = params();
const state = { plot: Number(q.get('plot')) || null, use: q.get('use') || '', item: q.get('item') || '', phase: 'all' };
const charts = [];

async function load() {
  const qs = new URLSearchParams();
  if (state.use) qs.set('use', state.use);
  if (state.item) qs.set('item', state.item);
  const d = await api(`/api/v1/plots/${state.plot}/plan?${qs}`);
  state.use = d.useCase;
  state.item = d.item.id;
  history.replaceState(null, '', `/plan.html?plot=${state.plot}&use=${d.useCase}&item=${encodeURIComponent(d.item.id)}`);
  render(d);
}

function pickers(plots, d) {
  $('#pick').innerHTML = `
    <label class="plan-sel">${icon('map-pin', 'i-sm')}<select id="plotSel" aria-label="Torpaq sahəsi">${plots.map((p) => `<option value="${p.id}" ${p.id === state.plot ? 'selected' : ''}>${esc(p.code)} · ${esc(p.name)}</option>`).join('')}</select></label>
    <div class="plan-uses" role="tablist" aria-label="İstiqamət">${d.options.map((o) => `<button type="button" class="plan-use ${o.useCase === d.useCase ? 'active' : ''}" data-use="${o.useCase}" title="${esc(o.verdict)}">${icon(o.icon, 'i-sm')}<span>${esc(o.label)}</span><b class="${o.suitability >= 75 ? 'hi' : o.suitability >= 50 ? 'mid' : 'lo'}">${o.suitability}</b></button>`).join('')}</div>`;
  $('#plotSel').addEventListener('change', (e) => { state.plot = Number(e.target.value); state.use = ''; state.item = ''; load(); });
  $('#pick').querySelectorAll('[data-use]').forEach((b) => b.addEventListener('click', () => { state.use = b.dataset.use; state.item = ''; load(); }));
}

let plotsCache = null;

function render(d) {
  pickers(plotsCache, d);
  const p = d.plot;
  $('#title').textContent = `${p.name}: ${d.useLabel.toLowerCase()}`;
  document.title = `Fəaliyyət planı: ${p.code} - Əkinçi 2.0`;
  const f = d.fit;
  const cal = d.calendar.filter((c) => state.phase === 'all' || c.yearPhase === state.phase || c.yearPhase === 'all');
  const now = new Date().getMonth() + 1;
  const clim = d.climate;
  charts.forEach((c) => c.destroy());
  charts.length = 0;

  $('#root').innerHTML = `
  <div class="plan-meta card card-pad">
    <div class="plan-chips">
      <span class="chip-s">${icon('map-pin', 'i-sm')}${esc(p.district)}${p.village ? ', ' + esc(p.village) : ''}</span>
      <span class="chip-s">${icon('ruler', 'i-sm')}${fmt.ha(p.areaHa)}</span>
      <span class="chip-s">${icon('mountain', 'i-sm')}${p.altitudeM ?? '?'} m · ${esc(d.zoneLabel)}</span>
      <span class="chip-s">${icon('droplets', 'i-sm')}${p.irrigation ? 'Suvarma var' : 'Suvarma yoxdur'}</span>
      <span class="chip-s">${icon('shield-check', 'i-sm')}${esc(p.demining || '')}</span>
    </div>
    ${d.items.length > 1 ? `<div class="plan-items"><span class="small muted">Bitki / fəaliyyət:</span>${d.items.map((i) => `<button type="button" class="chip chip-tg ${i.id === d.item.id ? 'active' : ''}" data-item="${esc(i.id)}" title="${esc(i.note)}">${i.suitable ? '' : icon('triangle-alert', 'i-sm')}${esc(i.name)}</button>`).join('')}</div>` : ''}
  </div>

  <div class="plan-grid2">
    <section class="card card-pad">
      <h2 class="plan-h">${icon('target')} Niyə bu torpaqda?</h2>
      <div class="plan-fit">${ring(f.suitability, 'lg')}<div><b class="plan-verdict">${esc(f.verdict)}</b><div class="small muted">${esc(d.use.summary || '')}</div></div></div>
      <ul class="plan-reasons">${(f.reasons || []).map((r) => `<li class="${r.positive ? 'ok' : 'no'}">${icon(r.positive ? 'circle-check' : 'circle-x', 'i-sm')}${esc(r.text)}</li>`).join('')}</ul>
      <h3 class="plan-h3">Sahənin yoxlanması</h3>
      <ul class="plan-reasons">${d.checks.map((c) => `<li class="${c.ok ? 'ok' : 'no'}">${icon(c.ok ? 'circle-check' : 'triangle-alert', 'i-sm')}${esc(c.text)}</li>`).join('')}</ul>
    </section>
    <section class="card card-pad">
      <h2 class="plan-h">${icon('package')} Nə lazımdır? <span class="small muted">${fmt.ha(p.areaHa)} üçün</span></h2>
      <div class="plan-needs">${d.needs.map((n) => `<div class="plan-need"><span class="pn-ico">${icon(n.icon)}</span><div><span class="pn-lbl">${esc(n.label)}</span>${n.value ? `<b>${esc(n.value)}</b>` : ''}${n.note ? `<small>${esc(n.note)}</small>` : ''}</div></div>`).join('')}</div>
    </section>
  </div>

  ${clim ? `<section class="card card-pad">
    <div class="row between" style="flex-wrap:wrap;gap:8px"><h2 class="plan-h" style="margin:0">${icon('thermometer')} İqlim və su balansı</h2><span class="small muted">${esc(clim.source || 'NASA POWER')}</span></div>
    <div class="plan-kpis">
      <div><b>${fmt.num1(clim.annual.T2M)}°C</b><span>orta illik temperatur</span></div>
      <div><b>${fmt.num(d.water.rainAnnualMm)} mm</b><span>illik yağıntı</span></div>
      <div><b>${fmt.num(clim.annual.FROST_DAYS)}</b><span>şaxtalı gün / il</span></div>
      <div><b>${fmt.num1(clim.annual.T2M_MIN)}°C</b><span>ekstremal minimum</span></div>
      <div><b>${fmt.num1((clim.annual.ALLSKY_SFC_SW_DWN || 0) / 3.6)} kVt.saat</b><span>günəş, m² gündə</span></div>
    </div>
    <div class="plan-charts">
      <figure><figcaption>Temperatur, °C</figcaption><div class="chart-box"><canvas id="tChart" aria-label="Aylıq temperatur qrafiki"></canvas></div></figure>
      <figure><figcaption>${d.water.needMm ? 'Yağıntı və bitkinin su tələbi, mm' : 'Aylıq yağıntı, mm'}</figcaption><div class="chart-box"><canvas id="wChart" aria-label="Su balansı qrafiki"></canvas></div></figure>
    </div>
    ${d.water.needMm ? `<p class="small" style="margin:6px 0 0">${icon('droplets', 'i-sm')} Yağıntı bitkinin ehtiyacını ${monthsNeeding(d.water)} aylarında ödəmir: ${fmt.ha(p.areaHa)} üçün ildə təxminən <b>${fmt.num(d.water.irrigationM3Total)} m³</b> suvarma suyu lazımdır. ${esc(d.water.method)}</p>` : `<p class="small muted" style="margin:6px 0 0">${esc(d.water.method || '')}</p>`}
  </section>` : ''}

  <section class="card card-pad">
    <div class="row between" style="flex-wrap:wrap;gap:8px"><h2 class="plan-h" style="margin:0">${icon('calendar-range')} Nə vaxt nə etmək lazımdır?</h2>
      <div class="seg" id="phaseSeg">${Object.entries({ all: 'Hamısı', year1: 'Salınma ili', mature: 'Bar illəri' }).map(([k, v]) => `<button type="button" data-ph="${k}" class="${state.phase === k ? 'active' : ''}">${v}</button>`).join('')}</div></div>
    <div class="gantt" role="table" aria-label="Fəaliyyət təqvimi">
      <div class="g-row g-head" role="row"><div class="g-name">Fəaliyyət</div>${MON.map((m, i) => `<div class="g-m ${i + 1 === now ? 'now' : ''}">${m}</div>`).join('')}</div>
      ${cal.map((c) => `<div class="g-row" role="row"><div class="g-name"><b>${esc(c.activity)}</b><span>${esc(PHASE[c.yearPhase] || '')}${c.why ? ' · ' + esc(c.why) : ''}</span>${c.needs ? `<em>${icon('package', 'i-sm')}${esc(c.needs)}</em>` : ''}</div>
        ${MON.map((m, i) => `<div class="g-m ${i + 1 === now ? 'now' : ''}">${c.months.includes(i + 1) ? `<i style="background:${KIND[c.kind][1]}" title="${esc(c.activity)}: ${m}"></i>` : ''}</div>`).join('')}</div>`).join('') || '<p class="muted small">Bu mərhələ üçün fəaliyyət yoxdur</p>'}
    </div>
    <div class="g-legend">${Object.entries(KIND).filter(([k]) => cal.some((c) => c.kind === k)).map(([, [l, col]]) => `<span><i style="background:${col}"></i>${l}</span>`).join('')}<span><i class="now"></i>Bu ay</span></div>
  </section>

  <div class="plan-grid2">
    <section class="card card-pad">
      <h2 class="plan-h">${icon('triangle-alert')} Risklər və qorunma</h2>
      <div class="plan-risks">${d.risks.map((r) => `<div class="plan-risk"><div class="row between" style="gap:8px;flex-wrap:wrap"><b>${esc(r.risk)}</b><span class="pr-months">${(r.months || []).map((m) => `<span>${MON[m - 1]}</span>`).join('')}</span></div><div class="small">${esc(r.mitigation || '')}</div></div>`).join('') || '<p class="small muted">Xüsusi iqlim riski qeyd olunmayıb</p>'}</div>
    </section>
    <section class="card card-pad">
      <h2 class="plan-h">${icon('scroll-text')} Müsabiqə Qaydalarının tələbləri (4.9)</h2>
      <ul class="plan-rules">${d.rules.map((r) => `<li>${icon('check', 'i-sm')}${esc(r)}</li>`).join('')}</ul>
      <a class="small" href="/qaydalar.html#minimum">Qaydalar haqqında ətraflı →</a>
    </section>
  </div>

  <section class="card card-pad">
    <h2 class="plan-h">${icon('list-checks')} Müqavilədən sonra ilk 12 ay: addım-addım</h2>
    <ol class="plan-steps">${(d.first12 || []).map((s) => `<li><span class="ps-n">${s.step}</span><div><div class="row between" style="gap:8px;flex-wrap:wrap"><b>${esc(s.task)}</b><span class="badge">${(s.monthsAfterLease || []).map((m) => m + '-ci ay').join(', ')}</span></div>
      <div class="small">${esc(s.details || '')}</div>${s.where ? `<div class="small muted">${icon('landmark', 'i-sm')}${esc(s.where)}</div>` : ''}</div></li>`).join('')}</ol>
  </section>

  <div class="plan-actions card card-pad">
    <b>Növbəti addım</b>
    <a class="btn btn-gold" href="/simulyator.html?plot=${p.id}&use=${d.useCase}">${icon('play', 'i-sm')}Gələcəyi simulyatorda yaşa</a>
    <a class="btn" href="/sahe.html?id=${p.id}">${icon('map', 'i-sm')}Sahə səhifəsi</a>
    <a class="btn" href="/texnika.html">${icon('tractor', 'i-sm')}Texnikanı icarəyə götür</a>
    <a class="btn" href="/meslehetci.html?q=${encodeURIComponent(`${p.code} sahəsində ${d.useLabel.toLowerCase()} üçün ilk il nə qədər investisiya lazımdır?`)}">${icon('bot', 'i-sm')}AI-dan soruş</a>
    <button class="btn btn-ghost" type="button" id="printPlan">${icon('printer', 'i-sm')}Çap et</button>
  </div>

  <details class="card card-pad plan-src"><summary>${icon('info', 'i-sm')} Mənbələr və qeyd</summary>
    <p class="small">${esc(d.disclaimer)}</p>
    <ul class="small">${d.sources.map((s) => `<li>${/^https?:/.test(s) ? `<a href="${esc(s.split(' ')[0])}" target="_blank" rel="noopener">${esc(s)}</a>` : esc(s)}</li>`).join('')}</ul>
  </details>`;

  $('#printPlan')?.addEventListener('click', () => window.print());
  $$('[data-item]').forEach((b) => b.addEventListener('click', () => { state.item = b.dataset.item; load(); }));
  $('#phaseSeg')?.addEventListener('click', (e) => { const b = e.target.closest('[data-ph]'); if (!b) return; state.phase = b.dataset.ph; render(d); });
  if (clim) drawCharts(d);
}

function monthsNeeding(w) {
  const m = w.irrigationMm.map((v, i) => (v > 5 ? MON[i] : null)).filter(Boolean);
  return m.length ? `${m[0]}-${m[m.length - 1]}` : 'heç bir';
}

function drawCharts(d) {
  if (!window.Chart) return;
  const c = d.climate.months;
  const base = { responsive: true, maintainAspectRatio: false, animation: false, interaction: { mode: 'index', intersect: false },
    plugins: { legend: { position: 'bottom', labels: { boxWidth: 12, font: { size: 11 } } } }, scales: { x: { grid: { display: false } } } };
  charts.push(new Chart($('#tChart'), { type: 'line', data: { labels: MON, datasets: [
    { label: 'Orta', data: c.T2M, borderColor: '#c98a12', backgroundColor: '#c98a12', borderWidth: 2, pointRadius: 3, tension: 0.35 },
    { label: 'Ekstremal maksimum', data: c.T2M_MAX, borderColor: '#d03b3b', borderDash: [4, 4], borderWidth: 1.5, pointRadius: 0, tension: 0.35 },
    { label: 'Ekstremal minimum', data: c.T2M_MIN, borderColor: '#2a78d6', borderDash: [4, 4], borderWidth: 1.5, pointRadius: 0, tension: 0.35 },
  ] }, options: { ...base, scales: { ...base.scales, y: { ticks: { callback: (v) => `${v}°` }, grid: { color: (ctx) => (ctx.tick.value === 0 ? '#9aa39d' : '#eef1ee') } } } } }));
  const ds = [{ label: 'Yağıntı', data: d.water.rainMm, backgroundColor: '#7fb2ea', borderRadius: 4, order: 2 }];
  if (d.water.needMm) ds.push({ type: 'line', label: 'Bitkinin su tələbi', data: d.water.needMm, borderColor: '#17804a', backgroundColor: '#17804a', borderWidth: 2, pointRadius: 3, tension: 0.35, order: 1 });
  charts.push(new Chart($('#wChart'), { type: 'bar', data: { labels: MON, datasets: ds }, options: { ...base, scales: { ...base.scales, y: { beginAtZero: true, ticks: { callback: (v) => `${v}` } } } } }));
}

(async () => {
  try {
    plotsCache = await api('/api/v1/plots');
    if (!state.plot || !plotsCache.some((p) => p.id === state.plot)) state.plot = plotsCache[0]?.id;
    await load();
  } catch (e) {
    $('#root').innerHTML = `<div class="alert alert-crit">${icon('circle-x')}<div>${esc(e.message)}</div></div>`;
  }
})();
