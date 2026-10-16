// Təkrar istifadə olunan UI blokları: sahə kartı, müsabiqə kartı.
import { esc, icon, fmt, CAT, countdown, plotStatusBadge, catBadge, compareStore, favButton } from './core.js';
import { plotArt } from './art.js';

export function infraIcons(p) {
  const item = (ok, ic, label) => `<span class="${ok ? '' : 'no'}" title="${label}">${icon(ic, 'i-sm')}${label}</span>`;
  return `<div class="infra-icons">${item(p.irrigation, 'droplets', 'Suvarma')}${item(p.electricity, 'zap', 'Elektrik')}${item(p.gas, 'flame', 'Qaz')}${item(p.roadDistanceKm != null && p.roadDistanceKm <= 3, 'route', `Yol ${fmt.num1(p.roadDistanceKm)} km`)}</div>`;
}

export function deadlineText(comp) {
  if (!comp) return `<span class="countdown">${icon('clock', 'i-sm')}Müsabiqə elan edilməyib</span>`;
  const cd = countdown(comp.deadlineAt);
  return `<span class="countdown ${cd.urgent ? 'urgent' : ''}">${icon('hourglass', 'i-sm')}${cd.text}</span>`;
}

/**
 * Sahənin real peyk görüntüsü (Sentinel-2, 2025 buludsuz mozaika) və sarı sərhəd konturu. Şəkil yüklənməsə
 * (yeni əlavə olunmuş sahə, internet yoxdur) arxadakı sxematik təsvir görünür.
 */
export function satCover(p, { epoch = '2025' } = {}) {
  const W = 640, H = 480;
  const side = Math.sqrt(Number(p.areaHa) * 10000);
  const wM = Math.max(2600, side * 2.8), hM = (wM * H) / W;
  const dLat = hM / 2 / 111320, dLng = wM / 2 / (111320 * Math.cos((p.latitude * Math.PI) / 180));
  const minLng = p.longitude - dLng, maxLat = p.latitude + dLat;
  const poly = Array.isArray(p.polygon) ? p.polygon : [];
  const pts = poly.map(([lng, lat]) => `${(((lng - minLng) / (2 * dLng)) * W).toFixed(1)},${(((maxLat - lat) / (2 * dLat)) * H).toFixed(1)}`).join(' ');
  return `<span class="sat">${plotArt(p.category, p.code)}<img class="sat-img" loading="lazy" decoding="async" alt="${esc(p.code)}: peyk görüntüsü" src="/api/v1/plots/${p.id}/imagery/${epoch}">${pts ? `<svg class="sat-poly" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid slice" aria-hidden="true"><polygon points="${pts}"/></svg>` : ''}</span>`;
}

export function plotCard(p, { compare = true, extra = '' } = {}) {
  const inCmp = compareStore.has(p.id);
  return `<article class="plot-card" data-id="${p.id}">
    <a class="media" href="/sahe.html?id=${p.id}" aria-label="${esc(p.name)}">${satCover(p)}
      <div class="tl">${catBadge(p.category, true)}</div>
      <div class="tr">${plotStatusBadge(p.status, true)}</div>
      <div class="bl"><span class="badge badge-dark">${icon('map-pin')}${esc(p.districtLabel)}${p.village ? ', ' + esc(p.village) : ''}</span></div>
    </a>
    <div class="body">
      <div class="row between"><span class="code">${esc(p.code)}</span><span class="row" style="gap:8px">${p.applicationCount ? `<span class="small muted">${icon('users', 'i-sm')} ${p.applicationCount} müraciət</span>` : ''}${favButton(p.id)}</span></div>
      <h3><a href="/sahe.html?id=${p.id}">${esc(p.name)}</a></h3>
      <div class="facts">
        <div class="fact"><div class="k">Sahə</div><div class="v">${fmt.ha(p.areaHa)}</div></div>
        <div class="fact"><div class="k">İcarə/ha</div><div class="v">${fmt.money(p.annualRentPerHa)}</div></div>
        <div class="fact"><div class="k">Min. invest.</div><div class="v">${fmt.compactMoney(p.minInvestment)}</div></div>
      </div>
      ${infraIcons(p)}
      ${extra}
    </div>
    <div class="foot">
      ${p.status === 'AWARDED' ? `<span class="countdown">${icon('lock', 'i-sm')}İcarəyə verilib</span>` : deadlineText(p.competition)}
      ${compare ? `<label class="compare-toggle"><input type="checkbox" data-compare="${p.id}" data-code="${esc(p.code)}" ${inCmp ? 'checked' : ''}>Müqayisə</label>` : ''}
    </div>
  </article>`;
}

export function competitionCard(c) {
  const cd = countdown(c.deadlineAt);
  const cats = [...new Set(c.plots.map((p) => p.category))];
  return `<article class="card" id="c${c.id}">
    <div class="card-body">
      <div class="row between" style="margin-bottom:8px"><span class="code small muted" style="font-weight:700">${esc(c.code)}</span>${c.open ? `<span class="badge badge-good">${icon('circle-check')}Müraciət qəbulu açıqdır</span>` : `<span class="badge">${icon('lock')}${esc(c.statusLabel)}</span>`}</div>
      <h3 style="font-size:18px">${esc(c.title)}</h3>
      <p class="muted small" style="margin-bottom:14px">${esc((c.description || '').slice(0, 220))}${(c.description || '').length > 220 ? '…' : ''}</p>
      <div class="row-wrap" style="margin-bottom:14px">${cats.map((k) => catBadge(k)).join('')}</div>
      <div class="facts">
        <div class="fact"><div class="k">Sahələr</div><div class="v">${c.plotCount}</div></div>
        <div class="fact"><div class="k">Ümumi ərazi</div><div class="v">${fmt.ha(c.totalAreaHa)}</div></div>
        <div class="fact"><div class="k">Müraciət</div><div class="v">${c.applicationCount}</div></div>
      </div>
    </div>
    <div class="card-foot row between">
      <span class="countdown ${cd.urgent && !cd.over ? 'urgent' : ''}">${icon('calendar', 'i-sm')}${c.open ? cd.text : 'Son tarix: ' + fmt.date(c.deadlineAt)}</span>
      <a class="btn btn-sm btn-primary" href="/musabiqeler.html#c${c.id}">Ətraflı ${icon('arrow-right', 'i-sm')}</a>
    </div>
  </article>`;
}

export function bindCompare(root, onChange) {
  root.addEventListener('change', (e) => {
    const cb = e.target.closest('[data-compare]');
    if (!cb) return;
    const list = compareStore.toggle({ id: Number(cb.dataset.compare), code: cb.dataset.code });
    cb.checked = list.some((x) => x.id === Number(cb.dataset.compare));
    onChange?.();
  });
}
