import { api, renderLayout, $, $$, esc, icon, fmt, params, toast } from '../core.js';
import { USE, verdictBadge } from '../potential.js';

renderLayout('sim');

const C = {
  band90: 'rgba(158, 197, 244, .55)', band50: 'rgba(85, 152, 231, .55)', median: '#1c5cab', path: '#eb6834',
  grid: '#e1e0d9', axis: '#c3c2b7', muted: '#898781', ink: '#52514e', pos: '#2a78d6', neg: '#e34948', other: '#c3c2b7',
};
const BASE_YEAR = new Date().getFullYear();
const q = params();
const state = { plots: [], plotId: Number(q.get('plot')) || null, use: q.get('use') || null, pot: null, res: null, which: 'typical', speed: 1, playing: false, token: 0 };
// Portfel və ya məsləhətçidən gələn link: miqyas və üfüq ilk açılışda URL-dən götürülür
const initial = { scale: Number(q.get('scale')) || null, horizon: Number(q.get('horizon')) || null };
let fanChart = null;

const money = (v) => (v < 0 ? '−' : '') + fmt.compactMoney(Math.abs(v));
const signed = (v) => (v > 0 ? '+' : v < 0 ? '−' : '') + fmt.compactMoney(Math.abs(v));
const pct = (v) => `${Math.round(v * 100)}%`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ------------------------------------------------------------------ controls

async function init() {
  state.plots = await api('/api/v1/plots');
  if (!state.plotId || !state.plots.some((p) => p.id === state.plotId)) state.plotId = (state.plots.find((p) => p.code === 'FZL-001') || state.plots[0]).id;
  await loadPlot();
}

async function loadPlot() {
  $('#controls').innerHTML = '<div class="loader"></div>';
  state.pot = await api(`/api/v1/plots/${state.plotId}/potential`);
  if (!state.use || !state.pot.options.some((o) => o.useCase === state.use)) state.use = state.pot.options[0].useCase;
  renderControls();
  renderStageIdle();
  $('#after').innerHTML = '';
}

function opt() { return state.pot.options.find((o) => o.useCase === state.use); }

function renderControls() {
  const o = opt();
  const byDistrict = {};
  state.plots.forEach((p) => (byDistrict[p.districtLabel] ||= []).push(p));
  const step = o.unit === 'MWp' ? 0.1 : o.unit === 't' ? 50 : o.unit === 'otaq' ? 1 : (o.maxScale > 50 ? 1 : 0.1);
  const minScale = o.unit === 'MWp' ? 0.1 : o.unit === 't' ? 500 : o.unit === 'otaq' ? 4 : step;
  $('#controls').innerHTML = `
    <div class="field"><label>Hazır ssenari</label><div class="seg sim-presets" id="presets">${SCEN.map((x) => `<button type="button" data-p="${x.key}" class="${x.key === 'base' ? 'active' : ''}" title="${esc(x.hint)}">${icon(x.icon, 'i-sm')}${x.label}</button>`).join('')}</div><div class="hint" id="presetHint">${esc(SCEN[0].hint)}</div></div>
    <div class="field"><label>Torpaq sahəsi</label>
      <select class="input" id="plotSel">${Object.entries(byDistrict).map(([d, ps]) => `<optgroup label="${esc(d)}">${ps.map((p) => `<option value="${p.id}" ${p.id === state.plotId ? 'selected' : ''}>${esc(p.code)} · ${esc(p.name)} (${fmt.ha(p.areaHa)})</option>`).join('')}</optgroup>`).join('')}</select>
      <div class="hint">Təyinat: <b>${esc(state.pot.designationLabel)}</b></div>
    </div>
    <div class="field"><label>Nə etmək istəyirsiniz?</label>
      <div class="sim-uses">${state.pot.options.map((x) => `<button type="button" class="sim-use ${x.useCase === state.use ? 'active' : ''} ${x.suitability < 35 ? 'dim' : ''}" data-use="${x.useCase}" title="${esc(x.label)}: uyğunluq ${x.suitability}/100">${icon(USE[x.useCase].icon)}<span>${esc(USE[x.useCase].short)}</span><small>${x.suitability}</small></button>`).join('')}</div>
      <div class="sim-usebox"><b>${esc(o.label)}</b> ${verdictBadge(o)}${o.permitted ? '' : ` <span class="badge">${icon('lock')}Təyinat dəyişikliyi lazımdır</span>`}<div class="small muted" style="margin-top:4px">${esc(o.reasons[0]?.text || o.hint)}</div></div>
    </div>
    <div class="field"><label>Miqyas: <b id="scaleLbl" class="num"></b></label><input type="range" id="scale" min="${minScale}" max="${Math.max(o.maxScale, minScale)}" step="${step}" value="${initial.scale && initial.scale <= o.maxScale ? initial.scale : o.scale}"><div class="hint">Bu sahədə maksimum ${o.unit === 'ha' ? fmt.ha(o.maxScale) : fmt.num(o.maxScale) + ' ' + o.unit}</div></div>
    <div class="field"><label>Qiymət dəyişikliyi</label>${pills('priceAdj')}</div>
    <div class="field"><label>Məhsuldarlıq</label>${pills('yieldAdj')}</div>
    <div class="sim-2col">
      <div class="field"><label>Alıcı müqaviləsi: <b id="offLbl" class="num"></b></label><input type="range" id="offtake" min="0" max="100" step="10" value="0" title="Məhsulun bu hissəsi qabaqcadan razılaşdırılmış qiymətlə satılır: qiymət riski azalır"></div>
      <div class="field"><label for="horizon">Müddət</label><select class="input" id="horizon">${[10, 15, 20, 25].map((h) => `<option value="${h}" ${h === (initial.horizon || o.horizon) ? 'selected' : ''}>${h} il</option>`).join('')}</select></div>
    </div>
    <div class="sim-run">
      <button class="btn btn-gold btn-block btn-lg" id="runBtn">${icon('play')}Gələcəyi yaşa</button>
      <p class="small muted">2000 ssenari · nəticələr təxminidir, fərziyyələr mənbələri ilə aşağıda</p>
    </div>`;
  initial.scale = null;
  initial.horizon = null;
  const sc = $('#scale');
  const lbl = () => { $('#scaleLbl').textContent = o.unit === 'ha' ? fmt.ha(sc.value) : `${fmt.num(sc.value)} ${o.unit}`; $('#offLbl').textContent = `${$('#offtake').value}%`; };
  lbl();
  sc.addEventListener('input', lbl);
  $('#offtake').addEventListener('input', lbl);
  $('#plotSel').addEventListener('change', (e) => { state.plotId = Number(e.target.value); state.use = null; history.replaceState(null, '', `?plot=${state.plotId}`); loadPlot(); });
  $$('.sim-use').forEach((b) => b.addEventListener('click', () => { state.use = b.dataset.use; history.replaceState(null, '', `?plot=${state.plotId}&use=${state.use}`); renderControls(); renderStageIdle(); $('#after').innerHTML = ''; }));
  $('#runBtn').addEventListener('click', run);
  // Dəyər düymələri (pill): seçim dəyişəndə hazır ssenari "fərdi" olur
  $$('.sim-pills').forEach((g) => g.addEventListener('click', (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    setPill(g.id, Number(b.dataset.v));
    markPreset(null);
  }));
  $('#offtake').addEventListener('input', () => markPreset(null));
  $('#presets').addEventListener('click', (e) => {
    const b = e.target.closest('[data-p]');
    if (!b) return;
    const x = SCEN.find((k) => k.key === b.dataset.p);
    setPill('priceAdj', x.price);
    setPill('yieldAdj', x.yield);
    $('#offtake').value = String(x.offtake);
    lbl();
    markPreset(x.key);
    run();
  });
}

// Hazır ssenarilər: bir kliklə şərtləri qurur və simulyasiyanı işə salır
const SCEN = [
  { key: 'base', label: 'Baza', icon: 'gauge', price: 0, yield: 0, offtake: 0, hint: 'Regional orta göstəricilər: qiymət və məhsuldarlıq dəyişmir' },
  { key: 'bear', label: 'Bədbin', icon: 'trending-down', price: -10, yield: -30, offtake: 0, hint: 'Quraqlıq ili: məhsuldarlıq -30%, bazar qiyməti -10%' },
  { key: 'bull', label: 'Nikbin', icon: 'trending-up', price: 0, yield: 20, offtake: 50, hint: 'İxrac bumu: məhsuldarlıq +20%, məhsulun 50%-i alıcı müqaviləsi ilə' },
];
const STEPS = [-30, -20, -10, 0, 10, 20];
const pills = (id) => `<div class="sim-pills" id="${id}" role="group">${STEPS.map((v) => `<button type="button" data-v="${v}" class="${v === 0 ? 'active' : ''}" aria-pressed="${v === 0}">${v > 0 ? '+' : ''}${v}%</button>`).join('')}</div>`;
const pillValue = (id) => Number($(`#${id} .active`)?.dataset.v || 0);
function setPill(id, v) {
  $$(`#${id} button`).forEach((b) => { const on = Number(b.dataset.v) === v; b.classList.toggle('active', on); b.setAttribute('aria-pressed', String(on)); });
}
function markPreset(key) {
  $$('#presets [data-p]').forEach((b) => b.classList.toggle('active', b.dataset.p === key));
  const x = SCEN.find((k) => k.key === key);
  $('#presetHint').textContent = x ? x.hint : 'Fərdi ssenari: dəyərləri özünüz seçdiniz';
}

// ------------------------------------------------------------------ run

async function run() {
  const o = opt();
  const btn = $('#runBtn');
  btn.disabled = true;
  btn.innerHTML = `${icon('dice')}2000 gələcək hesablanır...`;
  const priceAdj = pillValue('priceAdj') / 100, yieldAdj = pillValue('yieldAdj') / 100;
  try {
    const base = await api('/api/v1/simulate', { method: 'POST', body: { plotId: state.plotId, useCase: state.use, runs: 200 } });
    state.res = await api('/api/v1/simulate', {
      method: 'POST',
      body: {
        plotId: state.plotId, useCase: state.use, scale: Number($('#scale').value), runs: 2000,
        price: priceAdj ? base.spec.price * (1 + priceAdj) : null,
        yieldPerUnit: yieldAdj ? base.spec.yieldPerUnit * (1 + yieldAdj) : null,
        offtakeShare: Number($('#offtake').value) / 100, horizon: Number($('#horizon').value),
      },
    });
  } catch (e) {
    toast(e.message, 'err');
    btn.disabled = false;
    btn.innerHTML = `${icon('play')}Gələcəyi yaşa`;
    return;
  }
  btn.disabled = false;
  btn.innerHTML = `${icon('rotate-ccw')}Yenidən hesabla`;
  state.which = 'typical';
  renderAfterSkeleton();
  $('#stage').scrollIntoView({ behavior: 'smooth', block: 'start' });
  play();
}

// ------------------------------------------------------------------ stage

function renderStageIdle() {
  const o = opt();
  if (fanChart) { fanChart.destroy(); fanChart = null; }
  $('#stage').innerHTML = `
    <div class="sim-idle">
      <span class="sim-idle-ico">${icon(USE[o.useCase].icon)}</span>
      <h2>${esc(o.label)} · ${esc(plotName())}</h2>
      <p>${o.unit === 'ha' ? fmt.ha(o.scale) : fmt.num(o.scale) + ' ' + o.unit} · investisiya ${fmt.compactMoney(o.capex)} · ${o.horizon} il</p>
      <button class="btn btn-gold btn-lg" id="idleRun">${icon('play')}Gələcəyi yaşa</button>
      <div class="sim-ghost" aria-hidden="true">
        <svg viewBox="0 0 600 160" preserveAspectRatio="none"><path d="M0 120 C120 118 200 70 300 60 S480 20 600 10 L600 70 C480 80 400 100 300 110 S120 130 0 130 Z" fill="currentColor" opacity=".13"/><path d="M0 125 C140 122 220 90 300 85 S480 50 600 40" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="6 6" opacity=".45"/></svg>
        <span>Burada 2000 ssenarinin qrafiki görünəcək: pis (P10), orta (P50) və yaxşı (P90) nəticə</span>
      </div>
    </div>`;
  $('#idleRun').addEventListener('click', run);
}

function plotName() {
  const p = state.plots.find((x) => x.id === state.plotId);
  return p ? `${p.code} ${p.districtLabel}` : '';
}

function renderStage() {
  const r = state.res.result, H = r.horizon;
  $('#stage').innerHTML = `
    <div class="sim-top">
      <div><div class="sim-k">İl</div><div class="sim-year num" id="simYear">${BASE_YEAR}</div><div class="sim-dots">${Array.from({ length: H }, (_, i) => `<span data-y="${i + 1}"></span>`).join('')}</div></div>
      <div class="sim-cashbox"><div class="sim-k">Yığılmış xalis nəticə (investisiya daxil)</div><div class="sim-cash num" id="simCash">${money(-state.res.spec.capex)}</div><div class="sim-k" id="simStatus">İnvestisiya qoyuldu</div></div>
    </div>
    <div class="sim-switch">
      <div class="seg" role="tablist">${[['bad', 'Pis ssenari'], ['typical', 'Orta ssenari'], ['good', 'Yaxşı ssenari']].map(([k, l]) => `<button data-w="${k}" class="${k === state.which ? 'active' : ''}">${l}</button>`).join('')}</div>
      <div class="row" style="gap:6px"><button class="btn btn-sm" id="speedBtn">${icon('skip-forward', 'i-sm')}${state.speed === 1 ? 'Sürətləndir' : 'Normal sürət'}</button><button class="btn btn-sm" id="replayBtn">${icon('rotate-ccw', 'i-sm')}Yenidən oynat</button></div>
    </div>
    <div class="sim-chart-wrap">
      <div class="chart-legend sim-legend">
        <span><i style="background:${C.band90}"></i>80% ssenari aralığı (P10-P90)</span>
        <span><i style="background:${C.band50}"></i>50% ssenari (P25-P75)</span>
        <span><i class="line" style="background:${C.median}"></i>Mediana</span>
        <span><i class="line" style="background:${C.path}"></i>Yaşanan ssenari</span>
      </div>
      <div class="sim-chart"><canvas id="fan" aria-label="Yığılmış xalis nəticənin illər üzrə paylanması"></canvas></div>
    </div>
    <div class="sim-feed" id="feed" aria-live="polite"></div>`;
  $$('.seg button', $('#stage')).forEach((b) => b.addEventListener('click', () => { state.which = b.dataset.w; play(); }));
  $('#speedBtn').addEventListener('click', () => { state.speed = state.speed === 1 ? 4 : 1; $('#speedBtn').innerHTML = `${icon('skip-forward', 'i-sm')}${state.speed === 1 ? 'Sürətləndir' : 'Normal sürət'}`; });
  $('#replayBtn').addEventListener('click', () => play());
  buildFan();
}

function buildFan() {
  const r = state.res.result, H = r.horizon;
  if (fanChart) fanChart.destroy();
  const zero = { id: 'zero', afterDatasetsDraw(c) { const y = c.scales.y.getPixelForValue(0); const { left, right } = c.chartArea; const ctx = c.ctx; ctx.save(); ctx.strokeStyle = C.axis; ctx.setLineDash([4, 4]); ctx.beginPath(); ctx.moveTo(left, y); ctx.lineTo(right, y); ctx.stroke(); ctx.restore(); } };
  // Xəttin ucunda "canlı" nöqtə: parlayan halqa və cari məbləğ
  const head = {
    id: 'head',
    afterDatasetsDraw(c) {
      const d = c.data.datasets[5].data;
      if (!d.length) return;
      const pt = d[d.length - 1];
      const x = c.scales.x.getPixelForValue(pt.x), y = c.scales.y.getPixelForValue(pt.y);
      const ctx = c.ctx, pulse = 7 + 3 * Math.sin(performance.now() / 160);
      ctx.save();
      ctx.fillStyle = 'rgba(235, 104, 52, .18)';
      ctx.beginPath(); ctx.arc(x, y, pulse + 6, 0, Math.PI * 2); ctx.fill();
      ctx.fillStyle = C.path; ctx.strokeStyle = '#fff'; ctx.lineWidth = 2.5;
      ctx.beginPath(); ctx.arc(x, y, 6, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      const txt = money(pt.y);
      ctx.font = '700 12.5px Inter, system-ui, sans-serif';
      const w = ctx.measureText(txt).width + 14, bx = Math.min(c.chartArea.right - w, x + 12), by = y - 30 < c.chartArea.top ? y + 12 : y - 30;
      ctx.fillStyle = pt.y >= 0 ? '#17804a' : '#b8452f';
      ctx.beginPath(); ctx.roundRect(bx, by, w, 22, 6); ctx.fill();
      ctx.fillStyle = '#fff'; ctx.fillText(txt, bx + 7, by + 15);
      ctx.restore();
    },
  };
  // Oxlar əvvəlcədən sabitlənir: oynatma zamanı miqyas dəyişmir, xətt "sıçramır"
  const all = r.fan.flatMap((p) => [p.p10, p.p90]).concat([r.typical, r.bad, r.good].flatMap((x) => [-x.capex, ...x.years.map((y) => y.cumulative)]));
  const lo = Math.min(...all, 0), hi = Math.max(...all, 0), pad = (hi - lo) * 0.08;
  const band = (key, extra) => ({ key, data: [], borderWidth: 0, pointRadius: 0, tension: 0.3, cubicInterpolationMode: 'monotone', ...extra });
  fanChart = new Chart($('#fan'), {
    type: 'line',
    data: {
      datasets: [
        band('p10', { fill: false, order: 5 }),
        band('p90', { fill: '-1', backgroundColor: C.band90, order: 4 }),
        band('p25', { fill: false, order: 3 }),
        band('p75', { fill: '-1', backgroundColor: C.band50, order: 2 }),
        band('p50', { borderColor: C.median, borderWidth: 2, order: 1 }),
        { key: 'path', data: [], borderColor: C.path, borderWidth: 2.8, tension: 0.3, cubicInterpolationMode: 'monotone', order: 0,
          pointRadius: (ctx) => (Number.isInteger(ctx.raw?.x) && ctx.dataIndex < ctx.dataset.data.length - 1 ? 3 : 0),
          pointBackgroundColor: C.path, pointBorderColor: '#fff', pointBorderWidth: 1.5 },
      ],
    },
    options: {
      animation: false, maintainAspectRatio: false, parsing: false, normalized: true,
      interaction: { mode: 'nearest', axis: 'x', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          filter: (i) => ['p50', 'path'].includes(i.dataset.key) && Number.isInteger(i.raw.x),
          callbacks: {
            title: (it) => `${BASE_YEAR + Math.round(it[0].raw.x)}`,
            label: (i) => {
              const f = state.res.result.fan[Math.round(i.raw.x)];
              return i.dataset.key === 'p50' ? ` Mediana: ${money(i.raw.y)} (80% aralıq ${money(f.p10)} ... ${money(f.p90)})` : ` Yaşanan ssenari: ${money(i.raw.y)}`;
            },
          },
        },
      },
      scales: {
        x: { type: 'linear', min: 0, max: H, grid: { display: false }, border: { color: C.axis }, ticks: { color: C.muted, stepSize: H > 15 ? 2 : 1, callback: (v) => String(BASE_YEAR + v) } },
        y: { min: lo - pad, max: hi + pad, grid: { color: C.grid }, border: { display: false }, ticks: { color: C.muted, callback: (v) => money(v), maxTicksLimit: 6 } },
      },
    },
    plugins: [zero, head],
  });
}

/** τ (0..H, kəsr ola bilər) anına qədər seriyalar: tam illər + illər arasında interpolyasiya olunmuş "baş" nöqtə. */
function series(vals, tau) {
  const k = Math.floor(tau), f = tau - k, pts = [];
  for (let i = 0; i <= Math.min(k, vals.length - 1); i++) pts.push({ x: i, y: vals[i] });
  if (k < vals.length - 1 && f > 0.001) {
    const e = f * f * (3 - 2 * f);
    pts.push({ x: tau, y: vals[k] + (vals[k + 1] - vals[k]) * e });
  }
  return pts;
}

function pathValues(path) {
  return [-path.capex, ...path.years.map((y) => y.cumulative)];
}

function setFan(tau, path) {
  const r = state.res.result, ds = fanChart.data.datasets;
  ['p10', 'p90', 'p25', 'p75', 'p50'].forEach((k, i) => { ds[i].data = series(r.fan.map((p) => p[k]), tau); });
  ds[5].data = series(pathValues(path), tau);
  fanChart.update('none');
  return ds[5].data[ds[5].data.length - 1].y;
}

async function play() {
  const token = ++state.token;
  renderStage();
  const r = state.res.result, H = r.horizon, path = r[state.which], spec = state.res.spec;
  const feed = $('#feed'), cash = $('#simCash');
  const yearMs = () => (H > 15 ? 1300 : 2200) / state.speed;
  feed.innerHTML = `<div class="ev ev-start">${icon('banknote', 'i-sm')}<b>${BASE_YEAR}</b> İnvestisiya: ${fmt.compactMoney(path.capex)}${path.capex > spec.capex * 1.02 ? ` (tikinti xərci planlaşdırılandan ${Math.round((path.capex / spec.capex - 1) * 100)}% çox çıxdı)` : ''}</div>`;
  setFan(0, path);
  await sleep(500 / state.speed);
  let paid = false, done = 0, tau = 0, last = performance.now();
  const onYear = (t) => {
    const y = path.years[t - 1];
    $('#simYear').textContent = BASE_YEAR + t;
    $$('.sim-dots span').forEach((d) => d.classList.toggle('on', Number(d.dataset.y) <= t));
    const facts = [];
    if (y.revenue === 0) facts.push('məhsul hələ yoxdur (bar verməyə hazırlıq)');
    else {
      const yi = Math.round((y.yieldIndex - 1) * 100), pi = Math.round((y.priceIndex - 1) * 100);
      facts.push(`${spec.useCase === 'GUNES' ? 'istehsal' : spec.useCase === 'AQROTURIZM' ? 'doluluq' : 'məhsul'} ${yi >= 0 ? '+' : ''}${yi}%`);
      if (spec.offtakeShare < 1) facts.push(`qiymət ${pi >= 0 ? '+' : ''}${pi}%`);
    }
    const bad = y.events.length > 0;
    const line = document.createElement('div');
    line.className = `ev ${bad ? 'ev-bad' : y.net >= 0 ? 'ev-ok' : ''}`;
    line.innerHTML = `${icon(bad ? 'triangle-alert' : y.net >= 0 ? 'trending-up' : 'trending-down', 'i-sm')}<b>${BASE_YEAR + t}</b> ${y.events.map((e) => `<span class="ev-tag">${esc(e)}</span>`).join('')} ${facts.join(', ')} · xalis ${signed(y.net)}`;
    feed.prepend(line);
    if (!paid && y.cumulative >= 0) {
      paid = true;
      $('#simStatus').innerHTML = `${icon('circle-check', 'i-sm')} ${t}-ci ildə investisiya qayıtdı`;
      $('#stage').classList.add('paid');
      const p = document.createElement('div');
      p.className = 'ev ev-pay';
      p.innerHTML = `${icon('award', 'i-sm')}<b>${BASE_YEAR + t}</b> İnvestisiya tam qayıtdı!`;
      feed.prepend(p);
    } else if (!paid) $('#simStatus').textContent = `${t} il keçdi, investisiya hələ qayıtmayıb`;
  };
  // Xətt fasiləsiz uzanır (hər kadrda), məbləğ sayğacı xəttin ucu ilə eyni anda dəyişir
  while (tau < H) {
    await new Promise((res) => requestAnimationFrame(res));
    if (token !== state.token) return;
    const now = performance.now();
    tau = Math.min(H, tau + (now - last) / yearMs());
    last = now;
    const v = setFan(tau, path);
    cash.textContent = money(v);
    cash.classList.toggle('neg', v < 0);
    while (done < Math.floor(tau)) onYear(++done);
  }
  if (token !== state.token) return;
  setFan(H, path);
  cash.textContent = money(pathValues(path)[H]);
  if (!paid) $('#simStatus').innerHTML = `${icon('triangle-alert', 'i-sm')} ${H} ildə investisiya qayıtmadı`;
  renderAfter();
}

// ------------------------------------------------------------------ results

function renderAfterSkeleton() {
  $('#after').innerHTML = '<div class="card card-pad sim-wait">Gələcək yaşanır... nəticələr sonda burada görünəcək. <button class="btn btn-sm" id="skipBtn">Nəticəyə keç</button></div>';
  $('#skipBtn').addEventListener('click', () => { state.speed = 50; });
}

function renderAfter() {
  state.speed = 1;
  const { result: r, spec } = state.res;
  const s = r.summary, H = r.horizon;
  const paidRuns = Math.round(s.probPayback * r.runs);
  const o = state.res.option;
  $('#after').innerHTML = `
    <div class="card card-pad">
      <div class="sim-verdict"><span class="sv-ico">${icon(s.probPayback >= 0.7 ? 'circle-check' : s.probPayback >= 0.4 ? 'triangle-alert' : 'circle-x')}</span>
        <p><b>Ssenarilərin ${pct(s.probPayback)}-ində (${fmt.num(r.runs)} ssenaridən ${fmt.num(paidRuns)}) investisiya ${H} il ərzində qayıdır.</b> Orta ssenaridə geri qayıtma ${s.paybackP50 ? `<b>${s.paybackP50}-ci il</b>` : `<b>${H} ildən sonra</b>`}. ${H} ilin sonunda yığılmış nəticə: pis ssenaridə <b>${money(s.finalCashP10)}</b>, orta ssenaridə <b>${money(s.finalCashP50)}</b>, yaxşı ssenaridə <b>${money(s.finalCashP90)}</b>.</p>
      </div>
      <div class="kpis sim-kpis">
        <div class="kpi"><div class="lbl">Geri qayıtma ehtimalı</div><div class="val">${pct(s.probPayback)}</div><div class="sub">${H} il ərzində</div></div>
        <div class="kpi"><div class="lbl">Yetkin ildə xalis gəlir</div><div class="val">${money(s.annualNetMature)}</div><div class="sub">P10 ${money(s.annualNetMatureP10)} · P90 ${money(s.annualNetMatureP90)}</div></div>
        <div class="kpi"><div class="lbl">Xalis bugünkü dəyər (P50)</div><div class="val">${money(s.npvP50)}</div><div class="sub">${Math.round(spec.discountRate * 100)}% diskontla · zərər ehtimalı ${pct(1 - s.probPositiveNpv)}</div></div>
        <div class="kpi"><div class="lbl">Daxili gəlirlilik (IRR)</div><div class="val">${s.irrP50 != null ? s.irrP50 + '%' : '-'}</div><div class="sub">${spec.jobs} iş yeri · ${fmt.compactMoney(spec.capex)} investisiya</div></div>
      </div>
      ${r.withoutOfftake ? `<div class="alert alert-info" style="margin-top:14px">${icon('handshake')}<div><b>Alıcı müqaviləsinin effekti (${Math.round(spec.offtakeShare * 100)}%):</b> pis ssenaridə ${H} illik nəticə ${money(r.withoutOfftake.finalCashP10)} → <b>${money(s.finalCashP10)}</b>, geri qayıtma ehtimalı ${pct(r.withoutOfftake.probPayback)} → <b>${pct(s.probPayback)}</b>. Təsdiqlənmiş alıcı tələbləri: <a href="/alicilar.html">Alıcılar</a>.</div></div>` : ''}
    </div>
    <div class="grid sim-charts">
      <div class="card card-pad"><h4>Neçənci ildə qayıdır?</h4><p class="small muted" style="margin:0 0 8px">${fmt.num(r.runs)} ssenarinin geri qayıtma ilinə görə bölgüsü</p><div class="chart-box" style="height:220px"><canvas id="pbChart" aria-label="Geri qayıtma ili üzrə ssenari sayı"></canvas></div></div>
      <div class="card card-pad"><h4>Nəticəyə ən çox nə təsir edir?</h4><p class="small muted" style="margin:0 0 8px">Hər amilin nəticə ilə əlaqəsi (korrelyasiya)</p>
        <div class="chart-legend" style="margin:0 0 6px"><span><i style="background:${C.pos}"></i>yüksək olanda nəticə yaxşılaşır</span><span><i style="background:${C.neg}"></i>yüksək olanda nəticə pisləşir</span></div>
        <div class="chart-box" style="height:200px"><canvas id="drvChart" aria-label="Risk amilləri"></canvas></div></div>
    </div>
    <div class="card card-pad" id="goalCard">
      <h4>${icon('goal')} Hədəf kalkulyatoru: "ayda X ₼ qazanmaq istəyirəm"</h4>
      <p class="small muted">Sahibkar ideyasını rəqəmlə ifadə etsin: hədəfə çatmaq üçün nə qədər miqyas, investisiya və torpaq lazımdır?</p>
      <div class="row-wrap" style="gap:10px;align-items:flex-end"><div class="field" style="margin:0;min-width:220px;flex:1"><label>Aylıq xalis gəlir hədəfi (₼)</label><input class="input num" type="number" id="goalIn" value="${spec.useCase === 'GUNES' ? 1000000 : 50000}" min="1000" step="1000"></div><button class="btn btn-primary" id="goalBtn">${icon('target', 'i-sm')}Hesabla</button></div>
      <div id="goalOut"></div>
    </div>
    <div class="card card-pad">
      <h4>Fərziyyələr və mənbələr</h4>
      <p class="small muted">Hər rəqəmin haradan gəldiyi açıq göstərilir. Pilotda Nazirliyin rəsmi məlumatları ilə əvəz olunacaq.</p>
      <div class="table-wrap"><table class="table"><thead><tr><th>Parametr</th><th>Dəyər</th><th>Mənbə</th></tr></thead><tbody>
        ${spec.assumptions.map((a) => `<tr><td><b>${esc(a.label)}</b></td><td>${esc(a.value)}</td><td class="small muted">${esc(a.source)}</td></tr>`).join('')}
        <tr><td><b>Simulyasiya</b></td><td>${fmt.num(r.runs)} ssenari, ${H} il, diskont ${Math.round(spec.discountRate * 100)}%, xərc inflyasiyası 4% ± 1.5%</td><td class="small muted">Monte-Carlo metodu</td></tr>
      </tbody></table></div>
    </div>`;
  drawPayback(r);
  drawDrivers(r);
  $('#goalBtn').addEventListener('click', goal);
}

function drawPayback(r) {
  const H = r.horizon;
  const labels = Array.from({ length: H }, (_, i) => `${i + 1}`).concat(['Qayıtmır']);
  new Chart($('#pbChart'), {
    type: 'bar',
    data: { labels, datasets: [{ data: Array.from(r.paybackHistogram), backgroundColor: labels.map((_, i) => (i === H ? C.other : C.pos)), borderRadius: 4, maxBarThickness: 28 }] },
    options: {
      maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { title: (i) => (i[0].label === 'Qayıtmır' ? `${H} ildə qayıtmır` : `${i[0].label}-ci ildə qayıdır`), label: (i) => ` ${fmt.num(i.raw)} ssenari (${Math.round(i.raw / r.runs * 100)}%)` } } },
      scales: { x: { grid: { display: false }, ticks: { color: C.muted }, title: { display: true, text: 'il', color: C.muted } }, y: { grid: { color: C.grid }, border: { display: false }, ticks: { color: C.muted, maxTicksLimit: 5 } } },
    },
  });
}

function drawDrivers(r) {
  const d = r.drivers;
  new Chart($('#drvChart'), {
    type: 'bar',
    data: { labels: d.map((x) => x.label), datasets: [{ data: d.map((x) => Math.round(x.correlation * 100)), backgroundColor: d.map((x) => (x.correlation >= 0 ? C.pos : C.neg)), borderRadius: 4, maxBarThickness: 22 }] },
    options: {
      indexAxis: 'y', maintainAspectRatio: false,
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: (i) => ` korrelyasiya ${i.raw > 0 ? '+' : ''}${i.raw}%` } } },
      scales: { x: { min: -100, max: 100, grid: { color: C.grid }, border: { display: false }, ticks: { color: C.muted, callback: (v) => `${v}%` } }, y: { grid: { display: false }, ticks: { color: C.ink } } },
    },
  });
}

async function goal() {
  const v = Number($('#goalIn').value);
  const out = $('#goalOut');
  out.innerHTML = '<div class="loader"></div>';
  try {
    const g = await api(`/api/v1/simulate/goal?plotId=${state.plotId}&useCase=${state.use}&monthlyNet=${Math.round(v)}`);
    if (g.scaleP50 < 0) { out.innerHTML = `<div class="alert alert-serious" style="margin-top:12px">${icon('triangle-alert')}<div>${esc(g.explanation)}</div></div>`; return; }
    const u = g.unit === 'ha' ? 'ha' : g.unit;
    out.innerHTML = `<div class="goal-grid">
        <div><span>Lazım olan miqyas</span><b>${fmt.num1(g.scaleP50)} ${esc(u)}</b><small>90% əminliklə ${fmt.num1(g.scaleP90)} ${esc(u)}</small></div>
        <div><span>İnvestisiya</span><b>${fmt.compactMoney(g.capexP50)}</b><small>90% əminliklə ${fmt.compactMoney(g.capexP90)}</small></div>
        <div><span>Torpaq</span><b>${fmt.ha(g.landHaP50)}</b><small>90% əminliklə ${fmt.ha(g.landHaP90)}</small></div>
        <div><span>Bu sahədə</span><b>${g.feasibleOnPlot ? 'Mümkündür' : 'Yer çatmır'}</b><small>maksimum ${fmt.num1(g.maxScale)} ${esc(u)}</small></div>
      </div><p style="margin:12px 0 0">${esc(g.explanation)}</p>`;
  } catch (e) { out.innerHTML = `<p class="muted">${esc(e.message)}</p>`; }
}

init().catch((e) => { $('#controls').innerHTML = `<p class="muted">${esc(e.message)}</p>`; });
