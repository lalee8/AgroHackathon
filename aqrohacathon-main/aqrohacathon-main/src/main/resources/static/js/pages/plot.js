import { api, renderLayout, $, on, esc, icon, fmt, CAT, params, countdown, demining, plotStatusBadge, catBadge, session, compareStore, renderCompareTray, setTabs, toast, leaseText, favButton } from '../core.js';
import { renderDelivery } from '../delivery.js';
import { satCover } from '../components.js';
import { equipmentCard, rentDialog, compareBlock } from '../machinery-ui.js';
import { renderTimeMachine } from '../timemachine.js';
import { renderPotential } from '../potential.js';
import { createMap } from '../basemap.js';

renderLayout('catalog');
const id = Number(params().get('id'));
const root = $('#root');

function spec(ic, k, v) {
  return `<div class="spec"><div class="k">${icon(ic, 'i-sm')}${k}</div><div class="v">${v}</div></div>`;
}

async function renderMarket(p) {
  const el = $('#market');
  const [dem, prj] = await Promise.all([api(`/api/v1/plots/${p.id}/demands`).catch(() => []), api(`/api/v1/plots/${p.id}/projects`).catch(() => [])]);
  el.innerHTML = `<h3 style="margin:0 0 4px">${icon('hand-coins')} Satış bazarı və investorlar</h3>
    <p class="small muted" style="margin:0 0 12px">Bu sahənin məhsulunu kim alır və sahədə hansı sahibkar investor axtarır?</p>
    <h4 style="margin:0 0 6px">Təsdiqlənmiş alıcı tələbləri (${dem.length})</h4>
    ${dem.length ? dem.map((m) => `<div class="int-row"><div><b>${esc(m.demand.company)}</b> · ${esc(m.demand.productLabel)} ${fmt.num(m.demand.volumeTons)} t/il<div class="small muted">${fmt.num(m.demand.priceMin)}-${fmt.num(m.demand.priceMax)} ₼/t · ${m.demand.contractYears} illik müqavilə · ${fmt.num1(m.distanceKm)} km · sahənin məhsulu tələbin ${m.coveragePercent}%-ni ödəyə bilər</div></div><a class="btn btn-sm" href="/alicilar.html?demand=${m.demand.id}">${icon('file-check', 'i-sm')}Niyyət məktubu</a></div>`).join('') : '<p class="small muted">Hələ bu məhsul üzrə radiusda təsdiqlənmiş tələb yoxdur. <a href="/alicilar.html">Bütün alıcılar</a></p>'}
    <h4 style="margin:16px 0 6px">Bu sahədə investor axtaran layihələr (${prj.length})</h4>
    ${prj.length ? prj.map((x) => `<div class="int-row"><div><b>${esc(x.title)}</b><div class="small muted">${esc(x.sectorLabel)} · ${fmt.compactMoney(x.askAmount)} · ${esc(x.dealLabel)} · reallıq yoxlaması ${x.credibility}/100</div></div><a class="btn btn-sm" href="/layihe.html?id=${x.id}">Bax</a></div>`).join('') : `<p class="small muted">Hələ layihə yoxdur. <a href="/layihe-yeni.html">Bu sahə üçün layihə təqdim edin</a></p>`}`;
}

function render(p) {
  document.title = `${p.name} - Əkinçi 2.0`;
  const c = CAT[p.category];
  const comp = p.competition;
  const canApply = comp && comp.open && p.status !== 'AWARDED';
  root.innerHTML = `
  <section class="detail-hero">
    <div class="bgart">${satCover(p)}</div>
    <div class="container">
      <div class="crumbs"><a href="/index.html">Ana səhifə</a> / <a href="/kataloq.html">Kataloq</a> / ${esc(p.code)}</div>
      <div class="row-wrap" style="margin-bottom:12px">${catBadge(p.category, true)}${plotStatusBadge(p.status, true)}${demining(p.deminingStatus)}<span class="badge badge-on-dark" title="${esc(p.designationDescription || '')}">${icon('landmark')}Təyinat: ${esc(p.designationLabel || '')}</span>${p.scenic ? `<span class="badge badge-on-dark">${icon('mountain')}Mənzərəli yer</span>` : ''}</div>
      <h1>${esc(p.name)}</h1>
      <div class="row-wrap" style="color:#dbe9df;gap:18px">
        <span>${icon('map-pin')} ${esc(p.districtLabel)} rayonu, ${esc(p.village || '')} · ${esc(p.region)} iqtisadi rayonu</span>
        <span>${icon('hexagon')} ${esc(p.code)}</span>
        <span>${icon('locate-fixed')} ${p.latitude.toFixed(4)}, ${p.longitude.toFixed(4)}</span>
      </div>
    </div>
  </section>
  <section class="section-sm" style="padding-bottom:0">
    ${p.status === 'AWARDED' ? '<div class="container" style="margin-bottom:22px"><div class="card card-pad" id="delivery" style="scroll-margin-top:90px"><div class="loader"></div></div></div>' : ''}
    <div class="container"><div class="card card-pad" id="potential" style="scroll-margin-top:90px"></div></div>
    <div class="container" style="margin-top:22px"><div class="card card-pad" id="evidence" style="scroll-margin-top:90px"><div class="loader"></div></div></div>
    <div class="container" style="margin-top:22px"><div class="card card-pad" id="machinery" style="scroll-margin-top:90px"><div class="loader"></div></div></div>
  </section>
  <section class="section-sm">
    <div class="container detail-layout">
      <div class="stack">
        <div class="card card-pad">
          <div class="spec-grid">
            ${spec('ruler', 'Sahənin ölçüsü', fmt.ha(p.areaHa))}
            ${spec('sprout', 'Torpaq (bonitet)', `${p.soilQuality ?? '-'}/100`)}
            ${spec('mountain', 'Hündürlük / meyl', `${fmt.num(p.altitudeM)} m · ${fmt.num1(p.slopePercent)}%`)}
            ${spec('banknote', 'İllik icarə haqqı', `${fmt.num(p.annualRentPerHa)} ₼/ha`)}
            ${spec('coins', 'Min. investisiya', fmt.money(p.minInvestment))}
            ${spec('users', 'Min. iş yeri', p.minJobs)}
          </div>
        </div>

        <div class="card card-pad" id="market"></div>

        <div class="card card-pad" id="tm" style="scroll-margin-top:90px"></div>

        <div class="card">
          <div class="tabs" style="padding:0 12px">
            <button class="active" data-tab="info">${icon('info', 'i-sm')}Ümumi məlumat</button>
            <button data-tab="map">${icon('map', 'i-sm')}Xəritə</button>
            <button data-tab="terms">${icon('file-text', 'i-sm')}İstifadə şərtləri</button>
            <button data-tab="roi">${icon('trending-up', 'i-sm')}Gəlirlilik kalkulyatoru</button>
          </div>
          <div class="card-body">
            <div class="tab-panel active" data-panel="info">
              <p style="font-size:15.5px">${esc(p.description || '')}</p>
              <h4 style="margin-top:18px">Tövsiyə olunan bitkilər / istiqamətlər</h4>
              <div class="crop-list">${(p.recommendedCrops || []).map((x) => `<span>${esc(x)}</span>`).join('')}</div>
              <h4 style="margin-top:22px">İnfrastruktur və torpaq</h4>
              <div class="kv">
                <div>Su mənbəyi</div><div>${esc(p.waterSource || '-')} ${p.irrigation ? '<span class="badge badge-good">' + icon('droplets') + 'Suvarma var</span>' : '<span class="badge">Suvarma yoxdur</span>'}</div>
                <div>Elektrik / qaz</div><div>${p.electricity ? '✓ Elektrik xətti' : '✗ Elektrik yoxdur'} · ${p.gas ? '✓ Qaz xətti' : '✗ Qaz yoxdur'}</div>
                <div>Asfalt yola məsafə</div><div>${fmt.num1(p.roadDistanceKm)} km</div>
                <div>Torpağın tipi</div><div>${esc(p.soilType || '-')}</div>
                <div>Minatəmizləmə</div><div>${esc(p.deminingLabel)}${p.deminingCertificate ? ` · <code>${esc(p.deminingCertificate)}</code>` : ''}</div>
                <div>İstifadə növü</div><div>${esc(p.usageLabel)}, ${leaseText(p)}</div>
              </div>
            </div>
            <div class="tab-panel" data-panel="map"><div class="mini-map" id="miniMap"></div><p class="small muted" style="margin-top:8px">Sərhədlər nümayiş məqsədli təxmini poliqondur. Rəsmi sərhədlər Dövlət Torpaq Kadastrından alınacaq.</p></div>
            <div class="tab-panel" data-panel="terms">
              <ul class="terms-list">${(p.terms || '').split('\n').filter(Boolean).map((t) => `<li>${icon('circle-check')}<span>${esc(t)}</span></li>`).join('')}</ul>
              <div class="alert alert-info" style="margin-top:16px">${icon('info')}<div>Ümumi illik icarə haqqı: <b>${fmt.money(p.annualRentTotal)}</b> (${fmt.ha(p.areaHa)} × ${fmt.num(p.annualRentPerHa)} ₼). İlk 2 il üçün 50% güzəşt tətbiq olunur.</div></div>
            </div>
            <div class="tab-panel" data-panel="roi">
              <div class="row-wrap" style="gap:16px;margin-bottom:16px;align-items:flex-end">
                <div class="field" style="margin:0;flex:1;min-width:200px"><label>İnvestisiya məbləği (₼)</label><input class="input" type="number" id="roiInv" min="0" step="10000"></div>
                <div class="field" style="margin:0;flex:1;min-width:200px"><label>Fəaliyyət istiqaməti</label><select class="input" id="roiCat">${Object.entries(CAT).map(([k, v]) => `<option value="${k}" ${k === p.category ? 'selected' : ''}>${v.label}</option>`).join('')}</select></div>
                <button class="btn btn-primary" id="roiBtn">${icon('refresh-cw', 'i-sm')}Hesabla</button>
              </div>
              <div id="roiOut"><div class="loader"></div></div>
            </div>
          </div>
        </div>
      </div>

      <aside class="sticky-side">
        <div class="card card-pad">
          ${comp ? `
            <div class="small muted" style="font-weight:700">${esc(comp.code)}</div>
            <h3 style="margin:4px 0 12px">${esc(comp.title)}</h3>
            ${comp.open ? `<div class="deadline-box"><div class="small" style="font-weight:650">Müraciətin bitməsinə qalıb</div><div class="cd" id="cd"></div><div class="small muted" style="margin-top:8px">Son tarix: ${fmt.dateTime(comp.deadlineAt)}</div></div>` : `<div class="alert alert-warn">${icon('clock')}<div>Müraciət qəbulu bu an açıq deyil</div></div>`}
          ` : p.status === 'AWARDED' ? `<div class="alert alert-good">${icon('award')}<div><b>Sahə icarəyə verilib.</b> Müsabiqə yekunlaşıb və qalib müəyyən edilib.</div></div>`
          : `<div class="alert alert-info">${icon('info')}<div>Sahə hələ müsabiqəyə çıxarılmayıb. Yeni müsabiqələr barədə bildiriş almaq üçün qeydiyyatdan keçin.</div></div>`}
          <div style="margin-top:16px;display:flex;flex-direction:column;gap:10px">
            ${canApply ? `<button class="btn btn-primary btn-lg btn-block" id="applyBtn">${icon('file-text')}Müraciət et</button>` : ''}
            <button class="btn btn-block" id="cmpBtn">${icon('git-compare')}<span>${compareStore.has(p.id) ? 'Müqayisədən çıxar' : 'Müqayisəyə əlavə et'}</span></button>
            <div class="row" style="gap:10px"><a class="btn btn-block" href="/xerite.html?plot=${p.id}">${icon('map')}Xəritədə</a><a class="btn btn-block" href="/lovhe.html?id=${p.id}" target="_blank" rel="noopener">${icon('qr-code')}QR lövhə</a></div>
            <div class="row" style="gap:10px">${favButton(p.id, true)}<button type="button" class="btn" id="shareBtn" title="Paylaşım linki: Telegram, WhatsApp, Facebook-da sahənin şəkli və şərtləri görünür">${icon('share-2')}Paylaş</button></div>
            <a class="btn btn-block" href="/plan.html?plot=${p.id}">${icon('calendar-range')}Fəaliyyət planı</a>
          </div>
        </div>
        <div class="card card-pad">
          <div class="row between"><b>İnfrastruktur indeksi</b><b class="num">${p.infrastructureScore}/100</b></div>
          <div class="meter ${p.infrastructureScore < 45 ? 'warn' : ''}" style="margin:10px 0 14px"><span style="width:${p.infrastructureScore}%"></span></div>
          <div class="row between small"><span class="muted">Rəqabət</span><b>${p.applicationCount} aktiv müraciət</b></div>
          <div class="row between small" style="margin-top:6px"><span class="muted">Ümumi illik icarə</span><b>${fmt.money(p.annualRentTotal)}</b></div>
          <div class="row between small" style="margin-top:6px"><span class="muted">Son yenilənmə</span><b>${fmt.date(p.updatedAt)}</b></div>
        </div>
      </aside>
    </div>
  </section>`;

  if (comp && comp.open) {
    const tick = () => {
      const cd = countdown(comp.deadlineAt);
      const el = $('#cd');
      if (el) el.innerHTML = `<div><b>${cd.d}</b><span>gün</span></div><div><b>${cd.h}</b><span>saat</span></div><div><b>${cd.m}</b><span>dəq</span></div><div><b>${cd.s}</b><span>san</span></div>`;
    };
    tick();
    setInterval(tick, 1000);
  }

  $('#applyBtn')?.addEventListener('click', () => {
    const target = `/muraciet.html?plot=${p.id}&competition=${comp.id}`;
    if (!session.loggedIn()) location.href = '/giris.html?next=' + encodeURIComponent(target);
    else if (session.role() !== 'INVESTOR') toast('Müraciəti yalnız investor hesabı ilə vermək olar', 'err');
    else location.href = target;
  });
  $('#shareBtn').addEventListener('click', async () => {
    const url = `${location.origin}/p/${p.id}`;
    if (navigator.share) {
      try { await navigator.share({ title: `${p.code} · ${p.name}`, text: `${p.districtLabel}, ${fmt.num(p.areaHa)} ha`, url }); return; } catch (e) { if (e.name === 'AbortError') return; }
    }
    try { await navigator.clipboard.writeText(url); toast(`Paylaşım linki kopyalandı: ${url}`); } catch { prompt('Paylaşım linki', url); }
  });
  $('#cmpBtn').addEventListener('click', (e) => {
    const list = compareStore.toggle({ id: p.id, code: p.code });
    e.currentTarget.querySelector('span').textContent = list.some((x) => x.id === p.id) ? 'Müqayisədən çıxar' : 'Müqayisəyə əlavə et';
    renderCompareTray();
  });

  let mapReady = false;
  setTabs(root, (t) => {
    if (t === 'map' && !mapReady) { mapReady = true; drawMap(p); }
    if (t === 'roi' && !roiChart) loadRoi(p);
  });
  $('#roiInv').value = Math.round(p.minInvestment);
  renderMarket(p);
  renderPotential($('#potential'), p.id).then(() => { if (location.hash === '#potential') $('#potential').scrollIntoView({ behavior: 'smooth' }); });
  renderEvidence(p);
  renderMachinery(p);
  if ($('#delivery')) renderDelivery($('#delivery'), p.id).then(() => { if (location.hash === '#delivery') $('#delivery')?.scrollIntoView({ behavior: 'smooth' }); });
  renderTimeMachine($('#tm'), p.id).then(() => { if (location.hash === '#tm') $('#tm').scrollIntoView({ behavior: 'smooth' }); });
  $('#roiBtn').addEventListener('click', () => loadRoi(p));
}

function drawMap(p) {
  const L = window.L;
  const { map: m } = createMap('miniMap', { base: 'Peyk görüntüsü', layersControl: true, center: [p.latitude, p.longitude], zoom: 14 });
  const poly = L.polygon((p.polygon || []).map(([lng, lat]) => [lat, lng]), { color: '#ffd84d', weight: 3, fillColor: CAT[p.category].hex, fillOpacity: 0.3 }).addTo(m);
  m.fitBounds(poly.getBounds().pad(0.4));
}

let roiChart;
async function loadRoi(p) {
  const out = $('#roiOut');
  try {
    const inv = $('#roiInv').value;
    const r = await api(`/api/v1/roi/${p.id}?category=${$('#roiCat').value}${inv ? '&investment=' + inv : ''}`);
    out.innerHTML = `
      <div class="roi-grid">
        <div class="spec"><div class="k">Yetkin ildə gəlir</div><div class="v">${fmt.compactMoney(r.annualRevenueAtMaturity)}</div></div>
        <div class="spec"><div class="k">İllik xalis mənfəət</div><div class="v">${fmt.compactMoney(r.annualNetAtMaturity)}</div></div>
        <div class="spec"><div class="k">Geri ödəmə müddəti</div><div class="v">${r.paybackYear ? r.paybackYear + ' il' : '10 ildən çox'}</div></div>
        <div class="spec"><div class="k">10 illik ROI</div><div class="v">${fmt.num1(r.roiPercent)}%</div></div>
      </div>
      <div class="chart-legend" style="margin-top:18px"><span><i style="background:#2a78d6"></i>İllik xalis pul axını (müsbət)</span><span><i style="background:#e34948"></i>İllik xalis pul axını (mənfi)</span><span><i class="line" style="background:#eb6834"></i>Yığılmış nəticə (investisiya daxil)</span></div>
      <div class="chart-box"><canvas id="roiCanvas" aria-label="10 illik pul axını qrafiki"></canvas></div>
      <details style="margin-top:10px"><summary class="small" style="cursor:pointer;font-weight:600">Cədvəl görünüşü</summary>
        <div class="table-wrap"><table class="table" style="margin-top:8px"><thead><tr><th>İl</th><th class="r">Gəlir</th><th class="r">Xərclər</th><th class="r">Xalis</th><th class="r">Yığılmış</th></tr></thead>
        <tbody>${r.years.map((y) => `<tr><td>${y.year}</td><td class="r num">${fmt.num(y.revenue)}</td><td class="r num">${fmt.num(y.costs)}</td><td class="r num">${fmt.num(y.net)}</td><td class="r num">${fmt.num(y.cumulative)}</td></tr>`).join('')}</tbody></table></div></details>
      <p class="small muted" style="margin-top:10px">${icon('info', 'i-sm')} ${esc(r.product)}: məhsuldarlıq ${fmt.num1(r.yieldPerHa)} t/ha, qiymət ${fmt.num(r.pricePerTon)} ₼/t, təxmini ${r.jobsEstimate} iş yeri. ${esc(r.disclaimer)}</p>`;
    const Chart = window.Chart;
    roiChart?.destroy();
    roiChart = new Chart($('#roiCanvas'), {
      data: {
        labels: r.years.map((y) => `${y.year}-ci il`),
        datasets: [
          { type: 'bar', label: 'İllik xalis', data: r.years.map((y) => y.net), backgroundColor: r.years.map((y) => (y.net >= 0 ? '#2a78d6' : '#e34948')), borderRadius: 4, borderSkipped: 'start', maxBarThickness: 24, order: 2 },
          { type: 'line', label: 'Yığılmış', data: r.years.map((y) => y.cumulative), borderColor: '#eb6834', backgroundColor: '#eb6834', borderWidth: 2, pointRadius: 4, pointBorderColor: '#fff', pointBorderWidth: 2, tension: 0.25, order: 1 },
        ],
      },
      options: {
        responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: false }, tooltip: { callbacks: { label: (ctx) => `${ctx.dataset.label}: ${fmt.money(ctx.parsed.y)}` } } },
        scales: {
          y: { grid: { color: '#eef0ec' }, border: { display: false }, ticks: { color: '#5a6860', callback: (v) => fmt.compactMoney(v) } },
          x: { grid: { display: false }, ticks: { color: '#5a6860' } },
        },
      },
    });
  } catch (e) {
    out.innerHTML = `<div class="alert alert-crit">${icon('circle-x')}${esc(e.message)}</div>`;
  }
}

api(`/api/v1/plots/${id}`).then(render).catch((e) => {
  root.innerHTML = `<div class="container section">${'<div class="empty">' + icon('circle-x') + '<div>' + esc(e.message) + '</div><a class="btn" href="/kataloq.html" style="margin-top:12px">Kataloqa qayıt</a></div>'}</div>`;
});

// Bu sahə üçün texnika: lazım olan növlər, "al və ya icarə" müqayisəsi və yaxındakı elanlar
async function renderMachinery(p) {
  const box = $('#machinery');
  let m;
  try { m = await api(`/api/v1/plots/${p.id}/machinery`); } catch { box.remove(); return; }
  box.innerHTML = `
    <div class="row between" style="flex-wrap:wrap;gap:10px"><div><span class="overline">Texnika icarəsi · ${esc(m.useLabel)}</span><h3 style="margin:2px 0 4px">${icon('tractor')} Bu sahə üçün texnika: al, yoxsa icarəyə götür?</h3>
      <p class="small muted" style="margin:0">Lazım olan texnika: ${m.needed.map((n) => `<span class="badge">${icon(n.icon, 'i-sm')}${esc(n.label)}</span>`).join(' ') || 'xüsusi texnika tələb olunmur'}</p></div>
      <a class="btn btn-sm" href="/texnika.html">${icon('list', 'i-sm')}Bütün texnika</a></div>
    <div style="margin-top:14px">${compareBlock(m.compare)}</div>
    ${m.listings.length ? `<h4 style="margin:18px 0 10px">Yaxınlıqdakı elanlar</h4><div class="eq-grid">${m.listings.slice(0, 4).map((e) => equipmentCard(e, { compact: true })).join('')}</div>` : ''}`;
  box.addEventListener('click', (e) => {
    const b = e.target.closest('[data-rent]');
    if (!b) return;
    const eq = m.listings.find((x) => x.id === Number(b.dataset.rent));
    if (eq) rentDialog(eq, { plotId: p.id });
  });
  if (location.hash === '#machinery') box.scrollIntoView({ behavior: 'smooth' });
}

// AgroEvidence: hər faktın mənbəyi; demo məlumat real fakt kimi göstərilmir
async function renderEvidence(p) {
  const box = $('#evidence');
  let e;
  try { e = await api(`/api/v1/plots/${p.id}/evidence`); } catch { box.remove(); return; }
  const kind = { REAL: ['badge-good', 'circle-check', 'Real mənbə'], PORTAL: ['badge-info', 'landmark', 'Portal'], DEMO: ['badge-warn', 'triangle-alert', 'Demo, təsdiqlənməyib'] };
  const meter = (v, l, c) => `<div class="ev-meter"><div class="row between"><span>${l}</span><b class="num">${v}%</b></div><div class="cred-bar"><span style="width:${v}%;background:${c}"></span></div></div>`;
  box.innerHTML = `
    <div class="row between" style="flex-wrap:wrap;gap:12px;align-items:flex-start">
      <div style="max-width:640px"><span class="overline">AgroEvidence · izah edilə bilən seçim</span><h3 style="margin:2px 0 4px">${icon('file-search')} Bu məlumat haradan gəlir?</h3>
        <p class="small muted" style="margin:0">${esc(e.note)}</p></div>
      <div class="ev-meters">${meter(e.completeness, 'Məlumatın tamlığı', '#2a78d6')}${meter(e.realShare, 'Real mənbəli faktlar', '#17804a')}</div>
    </div>
    <div class="table-wrap" style="margin-top:12px"><table class="table ev-table"><thead><tr><th>Fakt</th><th>Dəyər</th><th>Mənbə</th><th>Vəziyyət</th><th class="r">Yenilənib</th></tr></thead><tbody>
      ${e.facts.map((f) => { const k = kind[f.kind]; return `<tr><td><b>${esc(f.label)}</b></td><td>${esc(f.value)}</td><td class="small muted">${esc(f.source)}</td><td><span class="badge ${k[0]} nowrap">${icon(k[1], 'i-sm')}${k[2]}</span></td><td class="r small nowrap">${f.updated ? esc(f.updated.length > 4 ? fmt.date(f.updated) : f.updated) : '-'}</td></tr>`; }).join('')}
    </tbody></table></div>
    ${e.gaps.length ? `<div class="ev-gaps"><b>${icon('circle-help', 'i-sm')} Çatışmayan məlumatlar (${e.gaps.length})</b><ul>${e.gaps.map((g) => `<li><b>${esc(g.label)}:</b> ${esc(g.why)}</li>`).join('')}</ul></div>` : ''}`;
}
