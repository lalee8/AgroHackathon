import { api, renderLayout, requireAuth, $, $$, esc, icon, fmt, params, toast, withLoading, showFieldErrors, DISTRICTS } from '../core.js';
import { credMeter, checksList, scenariosTable } from '../market.js';

renderLayout('projects');
if (!requireAuth(['INVESTOR'])) throw new Error('auth');

const UNITS = {
  ENERJI: { scale: 'Güc (MWp)', yield: 'İstehsal (MWh/MWp/il)', price: 'Tarif (₼/MWh)' },
  TURIZM: { scale: 'Otaq sayı' },
  EMAL: { scale: 'Anbar tutumu (ton)', price: 'Xidmət gəliri (₼/t)' },
  AQROTEXNOLOGIYA: {},
  ISTIXANA: { scale: 'Örtülü sahə (ha)', yield: 'Məhsuldarlıq (t/ha)', price: 'Qiymət (₼/t)' },
};
const DEF = { scale: 'Sahə (ha)', yield: 'Məhsuldarlıq (t/ha)', price: 'Qiymət (₼/t)' };
const CAT_OF = { EKINCILIK: 'EKINCILIK', BAGCILIQ: 'BAGCILIQ', UZUMCULUK: 'UZUMCULUK', ISTIXANA: 'ISTIXANA', HEYVANDARLIQ: 'HEYVANDARLIQ', ARICILIQ: 'ARICILIQ' };
const editId = Number(params().get('id')) || null;
const state = { sector: params().get('sector') || 'BAGCILIQ', deal: 'EQUITY', plots: [], timer: null };
const form = $('#form');

function plotOptions() {
  const s = state.sector;
  let list = state.plots;
  if (CAT_OF[s]) list = list.filter((p) => p.category === CAT_OF[s]);
  else if (s === 'ENERJI') list = list.filter((p) => ['YARARSIZ', 'OTLAQ', 'SENAYE'].includes(p.designation));
  else if (s === 'TURIZM') list = list.filter((p) => p.scenic || p.designation === 'COXILLIK');
  else if (s === 'EMAL') list = list.filter((p) => p.designation === 'SENAYE' || p.category === 'ISTIXANA');
  else list = [];
  return list;
}

function applySector() {
  const u = { ...DEF, ...(UNITS[state.sector] || {}) };
  if (state.sector === 'TURIZM' || state.sector === 'AQROTEXNOLOGIYA') { delete u.yield; }
  if (state.sector === 'TURIZM' || state.sector === 'AQROTEXNOLOGIYA') { delete u.price; }
  if (state.sector === 'EMAL') delete u.yield;
  $('#scaleField').classList.toggle('hidden', state.sector === 'AQROTEXNOLOGIYA');
  $('#scaleLbl').textContent = u.scale || 'Miqyas';
  $$('.yp').forEach((el, i) => el.classList.toggle('hidden', i === 0 ? !u.yield : !u.price));
  if (u.yield) $('#yieldLbl').textContent = u.yield;
  if (u.price) $('#priceLbl').textContent = u.price;
  const opts = plotOptions();
  const cur = $('#plotId').value;
  $('#plotField').classList.toggle('hidden', state.sector === 'AQROTEXNOLOGIYA');
  $('#plotId').innerHTML = `<option value="">Sahə seçməmişəm (rayonun orta şərtləri)</option>${opts.map((p) => `<option value="${p.id}">${esc(p.code)} · ${esc(p.name)} (${fmt.ha(p.areaHa)}, ${esc(p.designationLabel)})</option>`).join('')}`;
  if (opts.some((p) => String(p.id) === cur)) $('#plotId').value = cur;
  $('#plotHint').textContent = opts.length ? `${opts.length} uyğun sahə` : 'Bu istiqamət üçün xüsusi sahə yoxdur';
  $$('#sectorPick .sector-tile').forEach((b) => b.classList.toggle('active', b.dataset.s === state.sector));
}

function body(publish) {
  const f = new FormData(form);
  const num = (k) => (f.get(k) === '' || f.get(k) === null ? null : Number(f.get(k)));
  const pct = num('pct');
  return {
    sector: state.sector, plotId: num('plotId'), district: f.get('district') || null,
    title: f.get('title') || 'Adsız layihə', tagline: f.get('tagline'), problem: f.get('problem'), solution: f.get('solution'),
    market: f.get('market'), team: f.get('team'), stage: f.get('stage'),
    scale: state.sector === 'AQROTEXNOLOGIYA' ? null : num('scale'), capex: num('capex'), ownFunds: num('ownFunds') || 0, askAmount: num('askAmount'),
    dealType: state.deal, equityPercent: state.deal === 'EQUITY' ? pct : null, interestPercent: state.deal === 'LOAN' ? pct : null,
    revenueSharePercent: state.deal === 'REVENUE_SHARE' ? pct : null,
    claimedRevenue: num('claimedRevenue'), claimedProfit: num('claimedProfit'),
    claimedYield: $('.yp').classList.contains('hidden') ? null : num('claimedYield'),
    claimedPrice: $$('.yp')[1].classList.contains('hidden') ? null : num('claimedPrice'),
    jobs: num('jobs') || 0, publish,
  };
}

function schedule() {
  clearTimeout(state.timer);
  state.timer = setTimeout(check, 650);
}

async function check() {
  const b = body(false);
  if (!b.capex || b.capex < 1000 || !b.askAmount || b.askAmount < 1000) { $('#live').innerHTML = '<div class="card card-pad"><p class="muted">İnvestisiya və investordan istənən məbləği yazın: yoxlama burada görünəcək.</p></div>'; return; }
  $('#live').style.opacity = '.6';
  try {
    const a = await api('/api/v1/projects/reality-check', { method: 'POST', body: b });
    $('#live').innerHTML = `
      <div class="card card-pad">${credMeter(a.credibility, a.credibilityLabel, { big: true })}
        <p class="small muted" style="margin:10px 0 0">Simulyasiya (orta ssenari): gəlir <b>${fmt.compactMoney(a.benchmarkRevenue)}</b>/il, mənfəət <b>${fmt.compactMoney(a.benchmarkNet)}</b>/il${a.virtualPlot ? ' · sahə seçilmədiyi üçün rayonun orta şərtləri götürülüb' : ''}.</p></div>
      <div class="card card-pad" style="background:#fffaf0;border-color:#f0d78a"><span class="overline" style="color:#9a6b00">İnvestor bunu görəcək</span><p style="margin:0 0 10px;font-weight:650">${esc(a.terms.headline)}</p>${scenariosTable(a.terms, { compact: true })}</div>
      <div class="card card-pad"><h4 style="margin:0 0 8px">Yoxlamanın nəticəsi</h4>${checksList(a.checks)}</div>`;
  } catch (e) {
    $('#live').innerHTML = `<div class="card card-pad"><p class="muted">${esc(e.message)}</p></div>`;
  }
  $('#live').style.opacity = '1';
}

async function init() {
  const [sectors, plots] = await Promise.all([api('/api/v1/projects/sectors'), api('/api/v1/plots')]);
  state.plots = plots;
  if (!sectors.some((x) => x.code === state.sector)) state.sector = 'BAGCILIQ';
  $('#sectorPick').innerHTML = sectors.map((s) => `<button type="button" class="sector-tile" data-s="${s.code}"><span class="st-ico">${icon(s.icon)}</span><b>${esc(s.label)}</b></button>`).join('');
  $('#district').innerHTML = `<option value="">Seçin</option>${Object.entries(DISTRICTS).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('')}`;
  $$('#sectorPick .sector-tile').forEach((b) => b.addEventListener('click', () => { state.sector = b.dataset.s; applySector(); schedule(); }));
  $$('#deal button').forEach((b) => b.addEventListener('click', () => {
    state.deal = b.dataset.v;
    $$('#deal button').forEach((x) => x.classList.toggle('active', x === b));
    $('#pctLbl').textContent = state.deal === 'EQUITY' ? 'Təklif olunan pay (%)' : state.deal === 'LOAN' ? 'İllik faiz (%)' : 'Gəlirdən pay (%)';
    schedule();
  }));
  $('#plotId').addEventListener('change', () => {
    const p = state.plots.find((x) => String(x.id) === $('#plotId').value);
    if (p) { $('#district').value = p.district; if (!form.scale.value) form.scale.value = state.sector === 'ENERJI' ? Math.min(25, Math.floor(p.areaHa / 1.5)) : state.sector === 'ISTIXANA' ? Math.round(p.areaHa * 0.15 * 10) / 10 : CAT_OF[state.sector] ? p.areaHa : ''; }
  });
  form.addEventListener('input', schedule);
  form.addEventListener('change', schedule);
  if (editId) {
    const d = await api(`/api/v1/projects/${editId}`);
    const c = d.card;
    $('#pageTitle').textContent = `Layihəni redaktə et: ${c.code}`;
    state.sector = c.sector;
    state.deal = c.dealType;
    applySector();
    $$('#deal button').forEach((x) => x.classList.toggle('active', x.dataset.v === state.deal));
    const set = (k, v) => { if (form[k] && v !== null && v !== undefined) form[k].value = v; };
    set('plotId', c.plot?.id); set('district', c.district); set('title', c.title); set('tagline', c.tagline); set('problem', d.problem); set('solution', d.solution);
    set('market', d.market); set('team', d.team); set('stage', c.stage); set('scale', d.scale); set('capex', c.capex); set('ownFunds', c.ownFunds);
    set('askAmount', c.askAmount); set('claimedRevenue', d.claimedRevenue); set('claimedProfit', d.claimedProfit); set('claimedYield', d.claimedYield);
    set('claimedPrice', d.claimedPrice); set('jobs', c.jobs); set('pct', d.equityPercent ?? d.interestPercent ?? d.revenueSharePercent);
    $('#pctLbl').textContent = state.deal === 'EQUITY' ? 'Təklif olunan pay (%)' : state.deal === 'LOAN' ? 'İllik faiz (%)' : 'Gəlirdən pay (%)';
    check();
  } else applySector();
}

form.addEventListener('submit', (e) => {
  e.preventDefault();
  const publish = e.submitter?.dataset.publish === '1';
  $('#formErr').innerHTML = '';
  withLoading(e.submitter, async () => {
    try {
      const d = await api(editId ? `/api/v1/projects/${editId}` : '/api/v1/projects', { method: editId ? 'PUT' : 'POST', body: body(publish) });
      toast(publish ? 'Layihə dərc edildi' : 'Qaralama saxlanıldı');
      setTimeout(() => { location.href = `/layihe.html?id=${d.card.id}`; }, 500);
    } catch (err) {
      if (!showFieldErrors(form, err)) $('#formErr').innerHTML = `<div class="alert alert-crit">${icon('circle-x')}<div>${esc(err.message)}</div></div>`;
    }
  });
});

init().catch((e) => toast(e.message, 'err'));
