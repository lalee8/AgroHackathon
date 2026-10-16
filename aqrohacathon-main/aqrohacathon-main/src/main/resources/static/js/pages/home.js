import { api, renderLayout, $, fmt, icon, esc, DISTRICTS, renderCompareTray } from '../core.js';
import { heroArt } from '../art.js';
import { plotCard, competitionCard, bindCompare, satCover } from '../components.js';
import { renderTimeMachine } from '../timemachine.js';

renderLayout('home');

$('#heroVisual').innerHTML = `${heroArt()}
  <div class="float-card fc1"><span class="ico" style="background:var(--cat-EKINCILIK)">${icon('wheat')}</span><div><b>FZL-001 · 520 ha</b>Taxılçılıq massivi · Füzuli</div></div>
  <div class="float-card fc2"><span class="ico" style="background:var(--green-600)">${icon('shield-check')}</span><div><b>Minalardan təmizlənib</b>ANAMA sertifikatı</div></div>
  <div class="float-card fc3"><span class="ico" style="background:var(--cat-HEYVANDARLIQ)">${icon('file-check')}</span><div><b>AQR-2026-000016</b>Statusu: Qəbul edilib</div></div>`;

// Ana səhifənin vizualı: illüstrasiya yox, FZL-001 sahəsinin real son peyk görüntüsü (Sentinel-2) və sərhədi
api('/api/v1/plots').then(async (all) => {
  const p = all.find((x) => x.code === 'FZL-001');
  if (!p) return;
  const view = await api(`/api/v1/plots/${p.id}/imagery`).catch(() => null);
  const live = view?.epochs.find((e) => e.live);
  const box = document.createElement('a');
  box.className = 'hero-sat';
  box.href = `/sahe.html?id=${p.id}#tm`;
  box.setAttribute('aria-label', `${p.code}: real peyk görüntüsü`);
  box.innerHTML = `${satCover(p, { epoch: live ? 'live' : '2025' })}<span class="hero-sat-tag">${icon('satellite', 'i-sm')} Real peyk görüntüsü · Sentinel-2${live?.date ? ' · ' + live.date.split('-').reverse().join('.') : ''}</span>`;
  const art = $('#heroVisual > svg, #heroVisual .art');
  if (art) art.replaceWith(box);
}).catch(() => {});

function animate(el, to, format) {
  const start = performance.now(), dur = 1100;
  const step = (t) => {
    const k = Math.min(1, (t - start) / dur), v = to * (1 - Math.pow(1 - k, 3));
    el.textContent = format(v);
    if (k < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

async function loadStats() {
  const s = await api('/api/v1/stats/public');
  $('#heroStats').innerHTML = `<div><b data-n="${s.plots}">0</b><span>torpaq sahəsi</span></div>
    <div><b data-n="${s.totalAreaHa}" data-f="ha">0</b><span>ümumi ərazi</span></div>
    <div><b data-n="${s.districts}">0</b><span>azad edilmiş rayon</span></div>`;
  $('#kpis').innerHTML = `
    <div class="kpi hero-kpi"><span class="ico">${icon('banknote')}</span><div class="lbl">Təklif olunan investisiya</div><div class="val" data-n="${s.proposedInvestment}" data-f="money">0</div><div class="sub">${s.applications} müraciət üzrə</div></div>
    <div class="kpi"><span class="ico">${icon('trophy')}</span><div class="lbl">Aktiv müsabiqə</div><div class="val" data-n="${s.activeCompetitions}">0</div><div class="sub">müraciət qəbulu açıqdır</div></div>
    <div class="kpi"><span class="ico">${icon('users')}</span><div class="lbl">Planlaşdırılan iş yerləri</div><div class="val" data-n="${s.plannedJobs}">0</div><div class="sub">yerli məşğulluq</div></div>
    <div class="kpi"><span class="ico">${icon('briefcase')}</span><div class="lbl">Qeydiyyatlı investor</div><div class="val" data-n="${s.investors}">0</div><div class="sub">sahibkar və şirkət</div></div>`;
  document.querySelectorAll('[data-n]').forEach((el) => {
    const f = el.dataset.f;
    animate(el, Number(el.dataset.n), (v) => (f === 'money' ? fmt.compactMoney(v) : f === 'ha' ? fmt.num(v) + ' ha' : fmt.num(v)));
  });
}

async function loadCompetitions() {
  const list = (await api('/api/v1/competitions')).filter((c) => c.open);
  $('#activeComps').innerHTML = list.length ? list.slice(0, 2).map(competitionCard).join('') : '<p class="muted">Hazırda aktiv müsabiqə yoxdur.</p>';
}

async function loadPlots() {
  const plots = await api('/api/v1/plots?sort=infra&openOnly=true');
  $('#featured').innerHTML = plots.slice(0, 6).map((p) => plotCard(p)).join('');
  const all = await api('/api/v1/plots');
  const counts = {};
  all.forEach((p) => { counts[p.district] = (counts[p.district] || 0) + 1; });
  $('#districts').innerHTML = Object.entries(DISTRICTS).map(([k, v]) =>
    `<a class="chip" href="/kataloq.html?district=${k}">${icon('map-pin', 'i-sm')}${esc(v)} <b>${counts[k] || 0}</b></a>`).join('');
}

async function loadTimeMachine() {
  const list = await api('/api/v1/plots?q=AGD-005');
  const p = list[0];
  if (!p) return;
  $('#tmLink').href = `/sahe.html?id=${p.id}#tm`;
  await renderTimeMachine($('#tmHome'), p.id, { compact: true, chart: false });
  $('#tmHome').insertAdjacentHTML('beforeend', `<p class="small" style="margin:8px 0 0"><b>${esc(p.code)}</b> · ${esc(p.name)} · ${esc(p.districtLabel)}</p>`);
}

bindCompare(document.body, renderCompareTray);
loadTimeMachine().catch(console.error);
loadStats().catch(console.error);
loadCompetitions().catch(console.error);
loadPlots().catch(console.error);

// Bazar göstəriciləri (layihələr, alıcı tələbləri)
(async () => {
  try {
    const [sectors, demands] = await Promise.all([api('/api/v1/projects/sectors'), api('/api/v1/demands')]);
    const projects = sectors.reduce((a, s) => a + s.projects, 0);
    const ask = sectors.reduce((a, s) => a + Number(s.totalAsk), 0);
    const jobs = sectors.reduce((a, s) => a + s.jobs, 0);
    const tons = demands.reduce((a, d) => a + Number(d.volumeTons), 0);
    document.getElementById('marketKpis').innerHTML = `
      <div class="kpi"><span class="ico">${icon('handshake')}</span><div class="lbl">İnvestor axtaran layihə</div><div class="val">${projects}</div><div class="sub">${sectors.filter((s) => s.projects).length} istiqamətdə</div></div>
      <div class="kpi"><span class="ico">${icon('coins')}</span><div class="lbl">İnvestorlardan tələb</div><div class="val">${fmt.compactMoney(ask)}</div></div>
      <div class="kpi"><span class="ico">${icon('truck')}</span><div class="lbl">Təsdiqlənmiş alıcı tələbi</div><div class="val">${fmt.num(tons)} t/il</div><div class="sub">${demands.length} tələb</div></div>
      <div class="kpi"><span class="ico">${icon('users')}</span><div class="lbl">Layihələrdə iş yeri</div><div class="val">${fmt.num(jobs)}</div></div>`;
  } catch { /* boş */ }
})();

// Keçirilmiş müsabiqələrin rəsmi göstəriciləri (Kənd Təsərrüfatı Nazirliyi)
api('/api/v1/impact/official').then((o) => {
  const n = (v) => Number(v).toLocaleString('az-AZ', { maximumFractionDigits: 0 }).replace(/,/g, ' ');
  $('#officialStrip').innerHTML = `<span class="off-strip-lbl">${icon('landmark', 'i-sm')} Rəsmi nəticələr</span>
    <span><b>${o.competitions}</b> müsabiqə</span><span><b>${n(o.offeredHa)} ha</b> müsabiqəyə çıxarılıb</span><span><b>${n(o.participants)}</b> iştirakçı</span>
    <span><b>${n(o.winners)}</b> qalib</span><span><b>${n(o.leasedHa)} ha</b> icarəyə verilib</span>
    <a href="/regionlar.html#official">Ətraflı ${icon('arrow-right', 'i-sm')}</a>`;
}).catch(() => {});
