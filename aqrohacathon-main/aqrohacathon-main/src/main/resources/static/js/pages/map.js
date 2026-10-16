import { api, renderLayout, $, $$, on, esc, icon, fmt, CAT, DISTRICTS, params, countdown, demining, plotStatusBadge } from '../core.js';
import { createMap, addDistrictLabels } from '../basemap.js';

renderLayout('map');
document.querySelector('.site-footer')?.remove();

const L = window.L;
const { map } = createMap('map', { layersControl: true, center: [39.65, 46.75], zoom: 9, zoomControl: true });
L.control.scale({ imperial: false }).addTo(map);

const state = { cats: new Set(), plots: [], layers: new Map(), activeId: null, risk: params().get('risk') === '1' };
const polyLayer = L.layerGroup().addTo(map);
// Yaxın sahələr uzaqdan qrup kimi (say ilə) görünür, yaxınlaşdıqca ayrılır
const plotLayer = L.markerClusterGroup({
  showCoverageOnHover: false, maxClusterRadius: 48, disableClusteringAtZoom: 12, spiderfyOnMaxZoom: true,
  iconCreateFunction: (c) => {
    // Risk rejimində qrup ən riskli sahəsinin rəngini alır
    const lv = state.risk ? Math.max(...c.getAllChildMarkers().map((m) => m.options.riskLevel ?? 0)) : -1;
    return L.divIcon({ html: `<div class="mc-bubble${lv >= 0 ? ` risk lv${lv}` : ''}"><b>${c.getChildCount()}</b><span>sahə</span></div>`, className: 'mc-wrap', iconSize: [48, 48] });
  },
}).addTo(map);

$('#catChips').innerHTML = Object.entries(CAT).map(([k, c]) =>
  `<button class="chip chip-cat" type="button" aria-pressed="false" data-cat="${k}" style="--c:${c.hex}"><span class="sw" style="background:${c.color}"></span>${c.label}</button>`).join('');
$('#district').innerHTML += Object.entries(DISTRICTS).map(([k, v]) => `<option value="${k}">${v}</option>`).join('');

/**
 * Risk radarı: sahənin məlumatından qayda əsaslı xəbərdarlıqlar (fakt deyil, yoxlanmalı məqamlar).
 * Təsdiqlənməmiş ekoloji və geoloji risk iddia edilmir: yalnız portaldakı məlumat və onun çatışmazlığı.
 */
function risks(p) {
  const r = [];
  const cropping = !['ARICILIQ', 'HEYVANDARLIQ'].includes(p.category);
  if (p.deminingStatus !== 'CLEARED') r.push({ w: 2, t: `Minatəmizləmə başa çatmayıb (${p.deminingLabel || p.deminingStatus})` });
  if (!p.deminingCertificate) r.push({ w: 1, t: 'ANAMA sertifikatı portalda yoxdur' });
  if (cropping && !p.irrigation) r.push({ w: 2, t: 'Suvarma yoxdur: quraqlıq riski yüksəkdir' });
  if (p.irrigation && !p.waterSource) r.push({ w: 1, t: 'Su mənbəyi təsdiqlənməyib' });
  if (!p.electricity) r.push({ w: 1, t: 'Elektrik xətti yoxdur' });
  if (p.roadDistanceKm == null) r.push({ w: 1, t: 'Yola məsafə məlum deyil' });
  else if (p.roadDistanceKm > 10) r.push({ w: 2, t: `Asfalt yola ${fmt.num1(p.roadDistanceKm)} km` });
  else if (p.roadDistanceKm > 4) r.push({ w: 1, t: `Asfalt yola ${fmt.num1(p.roadDistanceKm)} km` });
  if (cropping && p.soilQuality != null && p.soilQuality < 55) r.push({ w: 1, t: `Torpaq keyfiyyəti aşağıdır (bonitet ${p.soilQuality})` });
  if (p.soilQuality == null) r.push({ w: 1, t: 'Torpaq göstəriciləri natamamdır' });
  if (p.slopePercent != null && p.slopePercent > 12) r.push({ w: 1, t: `Maillik ${fmt.num1(p.slopePercent)}%: eroziya və texnika çətinliyi` });
  if (p.competition?.open) {
    const cd = countdown(p.competition.deadlineAt);
    if (!cd.over && cd.d < 7) r.push({ w: 1, t: `Müraciət müddəti yaxındır: ${cd.text}` });
  }
  const score = r.reduce((s, x) => s + x.w, 0);
  return { list: r, score, level: score >= 4 ? 2 : score >= 2 ? 1 : 0 };
}
const RISK_COLORS = ['#17804a', '#e0a000', '#d03b3b'];
const RISK_LABEL = ['Aşağı risk', 'Orta risk', 'Yüksək risk'];

function markerIcon(p) {
  const c = CAT[p.category];
  const awarded = p.status === 'AWARDED';
  const rk = state.risk ? risks(p) : null;
  if (rk) {
    return L.divIcon({
      className: '',
      html: `<div class="plot-marker risk lv${rk.level}" style="background:${RISK_COLORS[rk.level]}" title="${RISK_LABEL[rk.level]}">${rk.list.length ? `<b>${rk.list.length}</b>` : icon('check', 'i-sm')}</div>`,
      iconSize: [34, 34], iconAnchor: [17, 34], popupAnchor: [0, -28],
    });
  }
  return L.divIcon({
    className: '',
    html: `<div class="plot-marker ${awarded ? 'awarded' : ''}" style="background:${awarded ? '#8a8f8b' : c.hex}">${icon(awarded ? 'lock' : c.icon)}</div>`,
    iconSize: awarded ? [26, 26] : [34, 34], iconAnchor: awarded ? [13, 26] : [17, 34], popupAnchor: [0, -28],
  });
}

function popupHtml(p) {
  const c = CAT[p.category];
  const cd = p.competition ? countdown(p.competition.deadlineAt) : null;
  return `<div>
    <div class="row" style="gap:6px;flex-wrap:wrap"><span class="badge badge-outline"><span class="cat-dot" style="background:${c.hex}"></span>${c.label}</span>${plotStatusBadge(p.status)}</div>
    <h4>${esc(p.name)}</h4>
    <div class="small muted">${esc(p.code)} · ${esc(p.districtLabel)}, ${esc(p.village || '')}</div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:4px 12px;margin:10px 0;font-size:13px">
      <span class="muted">Sahə</span><b>${fmt.ha(p.areaHa)}</b>
      <span class="muted">İcarə</span><b>${fmt.num(p.annualRentPerHa)} ₼/ha</b>
      <span class="muted">Min. investisiya</span><b>${fmt.compactMoney(p.minInvestment)}</b>
      <span class="muted">İnfrastruktur</span><b>${p.infrastructureScore}/100</b>
    </div>
    <div style="margin-bottom:10px">${demining(p.deminingStatus)}</div>
    ${state.risk ? riskHtml(p) : ''}
    ${cd && !cd.over ? `<div class="small" style="margin-bottom:10px">${icon('hourglass', 'i-sm')} ${cd.text}</div>` : ''}
    <a class="btn btn-primary btn-sm btn-block" href="/sahe.html?id=${p.id}" style="color:#fff">Ətraflı bax ${icon('arrow-right', 'i-sm')}</a>
  </div>`;
}

function riskHtml(p) {
  const rk = risks(p);
  return `<div class="risk-pop lv${rk.level}"><b>${icon('triangle-alert', 'i-sm')} ${RISK_LABEL[rk.level]}</b>${rk.list.length ? `<ul>${rk.list.map((x) => `<li>${esc(x.t)}</li>`).join('')}</ul>` : '<div class="small">Portal məlumatında xəbərdarlıq yoxdur</div>'}<div class="small muted">Qayda əsaslı xəbərdarlıqdır, yerində yoxlama tövsiyə olunur</div></div>`;
}

function draw(list) {
  plotLayer.clearLayers();
  polyLayer.clearLayers();
  state.layers.clear();
  list.forEach((p) => {
    const c = CAT[p.category];
    const latlngs = (p.polygon || []).map(([lng, lat]) => [lat, lng]);
    const poly = L.polygon(latlngs, { color: c.hex, weight: 2, fillColor: c.hex, fillOpacity: p.status === 'AWARDED' ? 0.12 : 0.28, dashArray: p.status === 'AWARDED' ? '4 4' : null });
    const marker = L.marker([p.latitude, p.longitude], { icon: markerIcon(p), title: p.name, riseOnHover: true, opacity: p.status === 'AWARDED' && !state.risk ? 0.6 : 1, zIndexOffset: p.status === 'AWARDED' ? -500 : 0, riskLevel: risks(p).level });
    marker.bindPopup(popupHtml(p), { maxWidth: 280 });
    poly.on('click', () => marker.openPopup());
    marker.on('popupopen', () => highlight(p.id));
    polyLayer.addLayer(poly);
    plotLayer.addLayer(marker);
    state.layers.set(p.id, { marker, poly });
  });
  if (list.length) {
    const b = L.latLngBounds(list.map((p) => [p.latitude, p.longitude]));
    map.fitBounds(b.pad(0.15), { maxZoom: 12 });
  }
}

function highlight(id) {
  state.activeId = id;
  $$('.map-item').forEach((el) => el.classList.toggle('active', Number(el.dataset.id) === id));
  const el = $(`.map-item[data-id="${id}"]`);
  el?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
}

function renderList(list) {
  $('#count').textContent = `${list.length} sahə tapıldı`;
  $('#list').innerHTML = list.length ? list.map((p) => `
    <div class="map-item" data-id="${p.id}" tabindex="0">
      <span class="sym" style="background:${CAT[p.category].hex}">${icon(p.status === 'AWARDED' ? 'lock' : CAT[p.category].icon)}</span>
      <div style="min-width:0">
        <div class="t">${esc(p.name)}</div>
        <div class="s">${esc(p.code)} · ${esc(p.districtLabel)} · ${fmt.ha(p.areaHa)}</div>
        <div class="s">${p.competition && p.competition.open ? `<span style="color:var(--green-700);font-weight:600">${icon('circle-check', 'i-sm')} Müraciət açıqdır</span>` : esc(p.statusLabel)}</div>
      </div>
    </div>`).join('') : `<div class="empty">${icon('search')}<div>Filtrlərə uyğun sahə tapılmadı</div></div>`;
}

function filtered() {
  const q = $('#q').value.trim().toLowerCase();
  const d = $('#district').value;
  const [amin, amax] = ($('#area').value || '-').split('-').map((x) => (x === '' ? null : Number(x)));
  return state.plots.filter((p) =>
    (!state.cats.size || state.cats.has(p.category)) &&
    (!d || p.district === d) &&
    (amin == null || p.areaHa >= amin) && (amax == null || p.areaHa <= amax) &&
    (!$('#irr').checked || p.irrigation) && (!$('#elec').checked || p.electricity) &&
    (!$('#open').checked || (p.competition && p.competition.open)) &&
    (!q || [p.name, p.code, p.village, p.districtLabel].join(' ').toLowerCase().includes(q)));
}

function refresh() {
  const list = filtered();
  draw(list);
  renderList(list);
  syncUrl();
}

/** Filtrlər URL-də saxlanılır: axtarışın linkini paylaşmaq olur. */
function syncUrl() {
  const u = new URLSearchParams();
  if (state.cats.size) u.set('kateqoriya', [...state.cats].join(','));
  if ($('#district').value) u.set('rayon', $('#district').value);
  if ($('#area').value) u.set('olcu', $('#area').value);
  if ($('#q').value.trim()) u.set('q', $('#q').value.trim());
  if ($('#irr').checked) u.set('suvarma', '1');
  if ($('#elec').checked) u.set('elektrik', '1');
  if ($('#open').checked) u.set('aktiv', '1');
  if (state.risk) u.set('risk', '1');
  const plot = params().get('plot');
  if (plot) u.set('plot', plot);
  history.replaceState(null, '', u.toString() ? `?${u.toString().replace(/%2C/g, ',')}` : location.pathname);
}

function readUrl() {
  const p = params();
  const cats = (p.get('kateqoriya') || p.get('category') || '').split(',').filter((k) => CAT[k]);
  cats.forEach((k) => { state.cats.add(k); $(`[data-cat="${k}"]`)?.classList.add('active'); });
  if (p.get('rayon')) $('#district').value = p.get('rayon');
  if (p.get('olcu')) $('#area').value = p.get('olcu');
  if (p.get('q')) $('#q').value = p.get('q');
  $('#irr').checked = p.get('suvarma') === '1';
  $('#elec').checked = p.get('elektrik') === '1';
  $('#open').checked = p.get('aktiv') === '1';
}

on($('#catChips'), 'click', '[data-cat]', (e, b) => {
  const k = b.dataset.cat;
  state.cats.has(k) ? state.cats.delete(k) : state.cats.add(k);
  b.classList.toggle('active');
  b.setAttribute('aria-pressed', b.classList.contains('active'));
  refresh();
});
['#district', '#area', '#irr', '#elec', '#open'].forEach((s) => $(s).addEventListener('change', refresh));
let t;
$('#q').addEventListener('input', () => { clearTimeout(t); t = setTimeout(refresh, 200); });
on($('#list'), 'click', '.map-item', (e, el) => focusPlot(Number(el.dataset.id)));
on($('#list'), 'keydown', '.map-item', (e, el) => { if (e.key === 'Enter') focusPlot(Number(el.dataset.id)); });

function focusPlot(id) {
  const l = state.layers.get(id);
  if (!l) return;
  plotLayer.zoomToShowLayer(l.marker, () => {
    map.flyTo(l.marker.getLatLng(), Math.max(map.getZoom(), 13), { duration: 0.8 });
    setTimeout(() => l.marker.openPopup(), 850);
  });
}

function paintRisk() {
  $('#riskToggle').classList.toggle('active', state.risk);
  $('#riskToggle').setAttribute('aria-pressed', String(state.risk));
  $('#riskLegend').hidden = !state.risk;
}
$('#riskToggle').addEventListener('click', () => {
  state.risk = !state.risk;
  paintRisk();
  refresh();
});

async function init() {
  const [plots, ref] = await Promise.all([api('/api/v1/plots'), api('/api/v1/reference')]);
  state.plots = plots;
  addDistrictLabels(map, ref.districts);
  const p = params();
  readUrl();
  paintRisk();
  refresh();
  if (p.get('plot')) focusPlot(Number(p.get('plot')));
}
init().catch((e) => { $('#list').innerHTML = `<div class="empty">${esc(e.message)}</div>`; });
