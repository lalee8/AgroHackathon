// "Torpağın zaman maşını": real peyk arxivi (Landsat 1990-2010, Sentinel-2 2018-2025 və son çəkiliş) və NDVI tarixçəsi.
import { api, esc, icon } from './core.js';

function polyPoints(view) {
  const w = view.window;
  return view.polygon.map(([lng, lat]) => {
    const x = ((lng - w.minLng) / (w.maxLng - w.minLng)) * w.width;
    const y = ((w.maxLat - lat) / (w.maxLat - w.minLat)) * w.height;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
}

const yearOf = (e) => (e.live && e.date ? Number(e.date.slice(0, 4)) : Number(e.key));
const eraOf = (y) => (y < 1993 ? 'pre' : y <= 2020 ? 'occ' : 'free');
const ERA = { pre: 'İşğaldan əvvəl', occ: 'İşğal dövrü', free: 'Azadlıqdan sonra' };
const fmtDate = (iso) => (iso ? iso.split('-').reverse().join('.') : '');
const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
// Tək çəkilişin NDVI-si mövsümdən asılıdır: payız-qışda (məhsul yığımından sonra) aşağı olması normaldır
const seasonNote = (iso) => {
  const m = iso ? Number(iso.slice(5, 7)) : 0;
  return m >= 4 && m <= 8 ? 'vegetasiya mövsümü' : 'mövsüm sonu, məhsul yığılıb';
};

/**
 * Zaman maşını: "Oynat" rejimində 1990-dan bu günə qədər kadrlar yumşaq keçidlə avtomatik dəyişir, il sayğacı
 * işləyir, vaxt xətti dolur; sonuncu kadr ən son Sentinel-2 görüntüsüdür. "Müqayisə" rejimində iki ili sürgü ilə tutuşdurmaq olur.
 */
export async function renderTimeMachine(root, plotId, { compact = false, chart = true } = {}) {
  root.innerHTML = '<div class="loader"></div>';
  let view;
  try {
    view = await api(`/api/v1/plots/${plotId}/imagery`);
  } catch (e) {
    root.innerHTML = `<div class="alert alert-warn">${icon('satellite')}<div>Peyk arxivi hazırda əlçatan deyil.</div></div>`;
    return;
  }
  const E = view.epochs;
  const Y = E.map(yearOf);
  const y0 = Y[0], y1 = Y[Y.length - 1];
  // Epoxalar vaxt xəttində bərabər addımla (2025 və bu gün üst-üstə düşməsin); aradakı illər hissə-hissə xətti
  const pos = (y) => {
    if (y <= Y[0]) return 0;
    for (let k = 0; k < Y.length - 1; k++) {
      if (y <= Y[k + 1]) return ((k + (y - Y[k]) / Math.max(1e-6, Y[k + 1] - Y[k])) / (Y.length - 1)) * 100;
    }
    return 100;
  };
  const liveEp = E.find((e) => e.live);
  let ndviByYear = {};
  let left = E[0];
  let right = E[E.length - 1];

  root.innerHTML = `
    <div class="row between" style="flex-wrap:wrap;gap:10px;margin-bottom:12px">
      <div><span class="overline" style="margin-bottom:2px">Peyk arxivi · ${y0}-${y1}${liveEp ? ' · son çəkiliş ' + fmtDate(liveEp.date) : ''}</span>
        <h3 style="margin:0;font-size:${compact ? 18 : 20}px">${icon('satellite')} Torpağın zaman maşını</h3></div>
      <div class="seg" role="group" aria-label="Görünüş rejimi"><button class="active" data-mode="play">${icon('play', 'i-sm')}Oynat</button><button data-mode="swipe">${icon('git-compare', 'i-sm')}Müqayisə</button></div>
    </div>
    <div class="tm-stage play" data-stage>
      <div class="tm-frames">${E.map((e, i) => `<img class="tm-f${i === 0 ? ' on' : ''}" alt="${esc(e.key)} peyk görüntüsü" src="${esc(e.url)}" data-i="${i}">`).join('')}</div>
      <img class="tm-a" alt="" src="${esc(left.url)}">
      <img class="tm-b" alt="" src="${esc(right.url)}">
      <svg class="tm-poly" viewBox="0 0 ${view.window.width} ${view.window.height}" preserveAspectRatio="none" aria-hidden="true"><polygon points="${polyPoints(view)}"/></svg>
      <div class="tm-label left" data-ll></div>
      <div class="tm-label right" data-rl></div>
      <div class="tm-src" data-src></div>
      <div class="tm-hud">
        <div class="tm-year" data-year>${y0}</div>
        <div class="tm-era" data-era></div>
        <div class="tm-ndvi" data-ndvi-badge></div>
      </div>
      <button class="tm-pp" type="button" data-pp aria-label="Dayandır">${icon('pause')}</button>
      <div class="tm-bar" aria-hidden="true"><div class="tm-occ" style="left:${pos(1993)}%;width:${pos(2020.85) - pos(1993)}%"></div><i data-fill></i>${Y.map((y) => `<b style="left:${pos(y)}%"></b>`).join('')}</div>
      <div class="tm-handle" data-handle style="left:50%"><span>${icon('git-compare')}</span></div>
      <input class="tm-range" type="range" min="0" max="100" value="50" aria-label="Müqayisə sürgüsü: soldakı və sağdakı il">
    </div>
    <div class="tm-epochs" data-epochs>${E.map((e, i) => `<button class="tm-ep${e.live ? ' live' : ''}" data-i="${i}" style="left:${pos(Y[i])}%"><i></i><b>${e.live ? Y[i] : esc(e.key)}</b><span>${e.live ? 'Bu gün' : esc(ERA[eraOf(Y[i])])}</span></button>`).join('')}</div>
    <p class="tm-note">${icon('info', 'i-sm')} Sarı kontur sahənin sərhədidir. "Oynat" 35 ili bir dəqiqədən az müddətdə göstərir, "Müqayisə" iki ili sürgü ilə tutuşdurur. İli seçmək üçün vaxt xəttindəki nöqtəyə klikləyin. ${esc(view.occupationNote)}${liveEp ? ` Son görüntü ${fmtDate(liveEp.date)} tarixində çəkilib, server yeni buludsuz çəkilişi avtomatik yoxlayır.` : ''}<br>Mənbə: ${esc(view.attribution)}</p>
    ${chart ? '<div data-ndvi style="margin-top:16px"><div class="loader"></div></div>' : ''}`;

  const stage = root.querySelector('[data-stage]');
  const frames = [...stage.querySelectorAll('.tm-f')];
  const a = stage.querySelector('.tm-a');
  const b = stage.querySelector('.tm-b');
  const range = stage.querySelector('.tm-range');
  const handle = stage.querySelector('[data-handle]');
  const yearEl = stage.querySelector('[data-year]');
  const fill = stage.querySelector('[data-fill]');
  const pp = stage.querySelector('[data-pp]');
  const eps = [...root.querySelectorAll('.tm-ep')];

  // ---- oynatma
  let mode = 'play';
  let idx = 0;
  let playing = false;
  let timer = null;
  let raf = 0;
  let shownYear = y0;
  const HOLD = 1700, FADE = 1100;

  const ndviFor = (i) => {
    const e = E[i];
    if (e.live && e.ndvi != null) return { v: e.ndvi, src: 'Sentinel-2', note: seasonNote(e.date) };
    const v = ndviByYear[Y[i]];
    return v != null ? { v, src: 'Landsat' } : null;
  };
  const hud = (i) => {
    const e = E[i];
    const era = e.live ? 'free' : eraOf(Y[i]);
    const eraEl = stage.querySelector('[data-era]');
    eraEl.className = `tm-era ${era}`;
    eraEl.textContent = e.live ? `Bu gün · ${fmtDate(e.date)}` : e.period;
    stage.querySelector('[data-src]').textContent = e.live ? `${e.source}${e.cloud != null ? ` · bulud ${e.cloud}%` : ''}` : e.source;
    const n = ndviFor(i);
    stage.querySelector('[data-ndvi-badge]').innerHTML = n ? `${icon('sprout', 'i-sm')} Bitki örtüyü (NDVI) <b>${n.v.toFixed(2)}</b>${n.note ? `<small>${n.note}</small>` : ''}` : '';
    eps.forEach((x, k) => { x.classList.toggle('active', k === i); x.classList.toggle('done', k < i); });
  };
  const countTo = (target, ms) => {
    cancelAnimationFrame(raf);
    const from = shownYear, t0 = performance.now();
    const step = (t) => {
      const k = Math.min(1, (t - t0) / ms);
      const ease = k < 0.5 ? 2 * k * k : 1 - ((-2 * k + 2) ** 2) / 2;
      const y = from + (target - from) * ease;
      yearEl.textContent = Math.round(y);
      fill.style.width = `${pos(y)}%`;
      if (k < 1) raf = requestAnimationFrame(step);
      else shownYear = target;
    };
    raf = requestAnimationFrame(step);
  };
  const show = (i, { animate = true } = {}) => {
    idx = i;
    frames.forEach((f, k) => f.classList.toggle('on', k === i));
    countTo(Y[i], animate && !reduced() ? FADE : 1);
    hud(i);
  };
  const setPP = () => {
    pp.innerHTML = icon(playing ? 'pause' : (idx === E.length - 1 ? 'rotate-ccw' : 'play'));
    pp.setAttribute('aria-label', playing ? 'Dayandır' : idx === E.length - 1 ? 'Yenidən oynat' : 'Oynat');
  };
  const stop = () => { playing = false; clearTimeout(timer); setPP(); };
  const tick = () => {
    if (!playing) return;
    if (idx >= E.length - 1) { stop(); return; }
    show(idx + 1);
    timer = setTimeout(tick, FADE + HOLD + (idx === E.length - 2 ? 600 : 0));
  };
  const start = () => {
    if (mode !== 'play') setMode('play');
    if (idx >= E.length - 1) { shownYear = y0; show(0, { animate: false }); }
    playing = true;
    setPP();
    clearTimeout(timer);
    timer = setTimeout(tick, HOLD);
  };
  pp.addEventListener('click', () => (playing ? stop() : start()));

  // ---- müqayisə
  const labels = () => {
    stage.querySelector('[data-ll]').innerHTML = `${yearOf(left)}<small>${esc(left.live ? 'Bu gün' : left.period)}</small>`;
    stage.querySelector('[data-rl]').innerHTML = `${yearOf(right)}<small>${esc(right.live ? 'Bu gün · ' + fmtDate(right.date) : right.period)}</small>`;
    eps.forEach((x) => {
      const e = E[Number(x.dataset.i)];
      x.classList.toggle('left', mode === 'swipe' && e === left);
      x.classList.toggle('right', mode === 'swipe' && e === right);
    });
  };
  const setSplit = (v) => { b.style.clipPath = `inset(0 0 0 ${v}%)`; handle.style.left = `${v}%`; };
  range.addEventListener('input', () => setSplit(range.value));
  const setMode = (m) => {
    mode = m;
    root.querySelectorAll('[data-mode]').forEach((x) => x.classList.toggle('active', x.dataset.mode === m));
    stage.classList.toggle('play', m === 'play');
    stage.classList.toggle('swipe', m === 'swipe');
    if (m === 'swipe') {
      stop();
      a.src = left.url; b.src = right.url; setSplit(range.value);
      eps.forEach((x) => x.classList.remove('active', 'done'));
    } else {
      show(idx, { animate: false });
    }
    labels();
  };
  root.querySelectorAll('[data-mode]').forEach((btn) => btn.addEventListener('click', () => {
    if (btn.dataset.mode === 'play') start(); else setMode('swipe');
  }));
  root.querySelector('[data-epochs]').addEventListener('click', (e) => {
    const btn = e.target.closest('.tm-ep');
    if (!btn) return;
    const i = Number(btn.dataset.i), ep = E[i];
    if (mode === 'play') { stop(); show(i); setPP(); return; }
    if (ep === right || ep === left) return;
    if (yearOf(ep) < yearOf(right)) { left = ep; a.src = ep.url; } else { right = ep; b.src = ep.url; }
    labels();
  });

  show(0, { animate: false });
  setPP();
  labels();

  // Görünən kimi özü oynamağa başlayır (bir dəfə); hərəkət azaldılıbsa istifadəçi özü başladır
  if (!reduced() && 'IntersectionObserver' in window) {
    const io = new IntersectionObserver((ents) => {
      if (ents.some((x) => x.isIntersecting)) {
        io.disconnect();
        Promise.all(frames.map((f) => (f.complete ? 1 : new Promise((r) => { f.addEventListener('load', r, { once: true }); f.addEventListener('error', r, { once: true }); }))))
          .then(() => { if (mode === 'play' && !playing && idx === 0) start(); });
      }
    }, { threshold: 0.5 });
    io.observe(stage);
  }

  if (chart) {
    renderNdvi(root.querySelector('[data-ndvi]'), plotId, liveEp).then((s) => {
      if (s?.points) { s.points.forEach((p) => { ndviByYear[p.year] = p.ndvi; }); hud(idx); }
    });
  } else {
    api(`/api/v1/plots/${plotId}/ndvi`).then((s) => { s.points.forEach((p) => { ndviByYear[p.year] = p.ndvi; }); hud(idx); }).catch(() => {});
  }
}

const occupationBand = {
  id: 'occupationBand',
  beforeDatasetsDraw(c) {
    const x = c.scales.x;
    const y = c.scales.y;
    const ctx = c.ctx;
    const x0 = x.getPixelForValue(1993);
    const x1 = x.getPixelForValue(2020.85);
    ctx.save();
    ctx.fillStyle = 'rgba(90, 104, 96, 0.09)';
    ctx.fillRect(x0, y.top, x1 - x0, y.bottom - y.top);
    ctx.fillStyle = '#5a6860';
    ctx.font = '600 11px Inter, system-ui, sans-serif';
    ctx.fillText('İşğal dövrü (1993-2020)', x0 + 6, y.top + 14);
    ctx.restore();
  },
};

export async function renderNdvi(el, plotId, live = null) {
  let s;
  try {
    s = await api(`/api/v1/plots/${plotId}/ndvi`);
  } catch {
    el.innerHTML = '';
    return null;
  }
  if (!s.points.length) { el.innerHTML = `<p class="muted small">${esc(s.narrative)}</p>`; return s; }
  const gls = s.points.filter((p) => p.year <= 2012).map((p) => ({ x: p.year, y: p.ndvi }));
  const l8 = s.points.filter((p) => p.year > 2012).map((p) => ({ x: p.year, y: p.ndvi }));
  // Son Sentinel-2 çəkilişi (10 m) ayrıca nöqtə kimi: sensor fərqli olduğu üçün xəttə birləşdirilmir
  const liveYear = live?.date ? Number(live.date.slice(0, 4)) + (Number(live.date.slice(5, 7)) - 0.5) / 12 : null;
  const s2 = live?.ndvi != null ? [{ x: liveYear, y: live.ndvi }] : [];
  el.innerHTML = `
    <div class="row between" style="margin-bottom:6px;flex-wrap:wrap;gap:8px"><h4 style="margin:0">${icon('sprout', 'i-sm')} Bitki örtüyü indeksi (NDVI), 1990-${s.latestYear}</h4>
      <div class="chart-legend" style="margin:0"><span><i class="line" style="background:#2a78d6"></i>Landsat 5/7 (GLS)</span><span><i class="line" style="background:#eb6834"></i>Landsat 8/9</span>${s2.length ? `<span><i style="background:#1baf7a;border-radius:50%"></i>Sentinel-2, ${fmtDate(live.date)} (${seasonNote(live.date)})</span>` : ''}</div></div>
    <div class="chart-box sm"><canvas aria-label="NDVI tarixçəsi"></canvas></div>
    <div class="ndvi-narr" style="margin-top:10px">${esc(s.narrative)}</div>
    <details style="margin-top:8px"><summary class="small" style="cursor:pointer;font-weight:600">Metodologiya və cədvəl</summary>
      <p class="small muted" style="margin:8px 0">${esc(s.method)}</p>
      <div class="table-wrap"><table class="table"><thead><tr><th>İl</th><th class="r">NDVI</th><th>Mənbə</th></tr></thead><tbody>
      ${s.points.map((p) => `<tr><td>${p.year}</td><td class="r num">${p.ndvi.toFixed(2)}</td><td class="small muted">${esc(p.source)}</td></tr>`).join('')}</tbody></table></div></details>`;
  const ds = (label, data, color) => ({ label, data, borderColor: color, backgroundColor: color, borderWidth: 2, pointRadius: 4, pointHoverRadius: 6, pointBorderColor: '#fff', pointBorderWidth: 2, tension: 0.25 });
  // eslint-disable-next-line no-new
  new window.Chart(el.querySelector('canvas'), {
    type: 'line',
    data: { datasets: [ds('Landsat 5/7 (GLS)', gls, '#2a78d6'), ds('Landsat 8/9', l8, '#eb6834'), { ...ds(`Sentinel-2 (${fmtDate(live?.date)})`, s2, '#1baf7a'), showLine: false, pointRadius: 6, pointStyle: 'rectRot' }] },
    options: {
      responsive: true, maintainAspectRatio: false, interaction: { mode: 'nearest', intersect: false },
      plugins: { legend: { display: false }, tooltip: { callbacks: { title: (it) => String(Math.floor(it[0].parsed.x)), label: (c) => ` ${c.dataset.label}: NDVI ${c.parsed.y.toFixed(2)}` } } },
      scales: {
        x: { type: 'linear', min: 1988, max: Math.max(s.latestYear, Math.ceil(liveYear || 0)) + 1, grid: { display: false }, ticks: { color: '#5a6860', stepSize: 5, callback: (v) => String(v) } },
        y: { min: 0, suggestedMax: 0.9, grid: { color: '#eef0ec' }, border: { display: false }, ticks: { color: '#5a6860', maxTicksLimit: 5 } },
      },
    },
    plugins: [occupationBand],
  });
  return s;
}

export function sparkline(points, { w = 140, h = 34 } = {}) {
  if (!points?.length) return '<span class="muted small">-</span>';
  const xs = points.map((p) => p.year);
  const x0 = Math.min(...xs), x1 = Math.max(...xs);
  const X = (yr) => ((yr - x0) / Math.max(1, x1 - x0)) * (w - 6) + 3;
  const Y = (v) => h - 3 - Math.max(0, Math.min(1, v)) * (h - 6);
  const pts = points.map((p) => `${X(p.year).toFixed(1)},${Y(p.ndvi).toFixed(1)}`).join(' ');
  const last = points[points.length - 1];
  return `<svg class="spark" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" aria-label="NDVI trend">
    <rect x="${X(1993)}" y="0" width="${Math.max(0, X(2020.8) - X(1993))}" height="${h}" fill="rgba(90,104,96,.08)"/>
    <polyline points="${pts}" fill="none" stroke="#2a78d6" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
    <circle cx="${X(last.year)}" cy="${Y(last.ndvi)}" r="3.5" fill="#2a78d6" stroke="#fff" stroke-width="2"/></svg>`;
}

