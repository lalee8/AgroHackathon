import { api, renderLayout, $, $$, esc, icon, fmt, CAT, DISTRICTS, session, params, toast, modal, withLoading, setTabs, emptyState, onLive } from '../core.js';
import { createMap, addDistrictLabels } from '../basemap.js';

renderLayout('buyers');
const role = session.loggedIn() ? session.role() : null;
const PRODUCTS = {
  BUGDA: 'Buğda', ARPA: 'Arpa', QARGIDALI: 'Qarğıdalı', NAR: 'Nar', ALMA: 'Alma', FINDIQ: 'Fındıq', UZUM_TEXNIKI: 'Texniki üzüm',
  UZUM_SUFRE: 'Süfrə üzümü', POMIDOR: 'Pomidor', XIYAR: 'Xiyar', SUD: 'Çiy süd', ET: 'Ət', BAL: 'Bal',
};
const TYPES = { EMAL: 'Emal müəssisəsi', IXRAC: 'İxracatçı', PERAKENDE: 'Pərakəndə şəbəkə', HORECA: 'Restoran və otel' };
const state = { demands: [], plots: [], cat: null, map: null, circles: {} };

// ------------------------------------------------------------------ ümumi siyahı

function demandCard(d, { mine = false } = {}) {
  const c = CAT[d.category];
  const pct = Math.min(100, Math.round(Number(d.contractedTons) / Number(d.volumeTons) * 100));
  const st = d.status === 'VERIFIED' ? `<span class="badge badge-good">${icon('badge-check')}Təsdiqlənmiş tələb</span>` : d.status === 'PENDING' ? `<span class="badge badge-warn">${icon('hourglass')}${esc(d.statusLabel)}</span>` : `<span class="badge">${esc(d.statusLabel)}</span>`;
  return `<div class="demand-card" data-d="${d.id}" id="d${d.id}">
    <div class="row between" style="gap:10px;align-items:flex-start">
      <div style="min-width:0"><div class="small muted">${icon(c.icon, 'i-sm')} ${esc(c.label)} · ${esc(d.buyerTypeLabel)} · ${esc(d.code)}</div><h3>${esc(d.productLabel)}: ${fmt.num(d.volumeTons)} ton/il</h3><div style="font-weight:600">${esc(d.company)}</div></div>
      ${st}
    </div>
    <div class="dc-facts">
      <div><span>Qiymət</span><b>${d.priceMin ? `${fmt.num(d.priceMin)}-${fmt.num(d.priceMax)} ₼/t` : 'razılaşma ilə'}</b></div>
      <div><span>Müqavilə</span><b>${d.contractYears} il</b></div>
      <div><span>Qəbul</span><b>${esc(d.districtLabel)}, ${d.radiusKm} km</b></div>
    </div>
    ${d.quality ? `<p class="small" style="margin:0">${icon('clipboard-check', 'i-sm')} ${esc(d.quality)}</p>` : ''}
    <div class="dc-fill"><div class="row between small" style="margin-bottom:5px"><span>${icon('file-check', 'i-sm')} Niyyət məktubları ilə bağlanıb: <b>${fmt.num(d.contractedTons)} t</b></span><b>${pct}%</b></div><div class="dc-bar"><span style="width:${Math.max(pct, 2)}%"></span></div><div class="small muted" style="margin-top:4px">Hələ açıqdır: <b>${fmt.num(Math.max(0, Number(d.volumeTons) - Number(d.contractedTons)))} t/il</b>${pct >= 60 ? ' · tələbin çoxu artıq bağlanıb' : ''}</div></div>
    <div class="row-wrap" style="gap:8px">
      ${!mine && d.status === 'VERIFIED' && (role === 'INVESTOR' || !role) ? `<button class="btn btn-primary" data-loi="${d.id}">${icon('file-check', 'i-sm')}Niyyət məktubu istə</button>` : ''}
      <button class="btn btn-ghost btn-sm" data-show="${d.id}">${icon('map-pin', 'i-sm')}Xəritədə göstər</button>
      ${mine ? `<span class="small muted">${d.season ? esc(d.season) : ''}</span>` : ''}
    </div>
  </div>`;
}

function renderList() {
  const list = state.demands.filter((d) => !state.cat || d.category === state.cat);
  $('#demandList').innerHTML = list.length ? list.map((d) => demandCard(d)).join('') : emptyState('Bu kateqoriyada təsdiqlənmiş tələb yoxdur', 'hand-coins');
  drawMap(list);
}

function drawMap(list) {
  if (!window.L) return;
  if (!state.map) {
    state.map = createMap('dmap', { center: [39.75, 46.85], zoom: 8, scrollWheelZoom: false }).map;
    api('/api/v1/reference').then((r) => addDistrictLabels(state.map, r.districts)).catch(() => {});
    state.plots.forEach((p) => L.circleMarker([p.latitude, p.longitude], { radius: 5, weight: 1.5, color: '#fff', fillColor: CAT[p.category].hex, fillOpacity: 0.95 })
      .bindTooltip(`${esc(p.code)} · ${esc(CAT[p.category].label)}`).addTo(state.map));
  }
  Object.values(state.circles).forEach((c) => c.remove());
  state.circles = {};
  list.forEach((d) => {
    const hex = CAT[d.category].hex;
    const mk = L.marker([d.latitude + (d.id % 3 - 1) * 0.03, d.longitude + (d.id % 4 - 1.5) * 0.03], {
      icon: L.divIcon({ className: 'dm-pin', html: `<span style="background:${hex}">${icon(CAT[d.category].icon, 'i-sm')}</span>`, iconSize: [30, 30], iconAnchor: [15, 15] }),
    }).bindPopup(`<b>${esc(d.company)}</b><br>${esc(d.productLabel)}: ${fmt.num(d.volumeTons)} t/il<br>Qəbul radiusu: ${d.radiusKm} km`).addTo(state.map);
    mk.on('click', () => focus(d.id));
    state.circles[d.id] = mk;
  });
}

let radius = null;
function focus(id) {
  const c = state.circles[id];
  const d = state.demands.find((x) => x.id === id);
  if (!c || !d) return;
  const hex = CAT[d.category].hex;
  const next = L.circle([d.latitude, d.longitude], { radius: d.radiusKm * 1000, color: hex, weight: 2, dashArray: '6 6', fillColor: hex, fillOpacity: 0.07 }).addTo(state.map);
  radius?.remove();
  radius = next;
  // Hamar keçid: xəritə yenidən qurulmur, köhnə kafellər yeni zoom yüklənənə qədər qalır
  state.map.flyToBounds(radius.getBounds(), { padding: [16, 16], duration: 0.7, maxZoom: 10 });
  state.map.once('moveend', () => c.openPopup());
  $$('.demand-card').forEach((x) => x.classList.toggle('flash', x.dataset.d === String(id)));
}

async function requestLoi(id) {
  if (!role) { location.href = '/giris.html?next=' + encodeURIComponent('/alicilar.html?demand=' + id); return; }
  const d = state.demands.find((x) => x.id === id);
  const [apps, mine] = await Promise.all([api('/api/v1/applications/my').catch(() => []), api('/api/v1/projects/mine').catch(() => [])]);
  const appOpts = apps.filter((a) => a.appNumber).map((a) => `<option value="a${a.id}">Müraciət ${esc(a.appNumber)} · ${esc(a.plotCode)}</option>`);
  const prjOpts = mine.map((m) => `<option value="p${m.project.id}">Layihə ${esc(m.project.code)} · ${esc(m.project.title)}</option>`);
  const plotOpts = state.plots.filter((p) => p.category === d.category).map((p) => `<option value="s${p.id}">Sahə ${esc(p.code)} · ${esc(p.name)}</option>`);
  const m = modal({
    title: `Niyyət məktubu: ${esc(d.company)}`,
    body: `<p class="small muted" style="margin-top:0">${esc(d.productLabel)}, ${fmt.num(d.volumeTons)} t/il, ${fmt.num(d.priceMin)}-${fmt.num(d.priceMax)} ₼/t. İmzalanan məktub müraciətinizə və ya layihənizə əlavə olunur, komissiya bunu görür.</p>
      <div class="field"><label>Nəyə əlavə olunsun?</label><select class="input" data-src>${appOpts.join('')}${prjOpts.join('')}${plotOpts.join('')}</select></div>
      <div class="field"><label>İllik həcm (ton)</label><input class="input num" type="number" data-vol min="1" value="${Math.round(Number(d.volumeTons) * 0.25)}"></div>
      <div class="field" style="margin:0"><label>Alıcıya mesaj</label><textarea class="input" rows="3" data-msg placeholder="Sahə, məhsul və keyfiyyət barədə qısa məlumat"></textarea></div>`,
    foot: `<button class="btn" data-close>Ləğv et</button><button class="btn btn-primary" data-ok>${icon('send', 'i-sm')}Sorğu göndər</button>`,
  });
  m.el.querySelector('[data-ok]').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    const src = m.el.querySelector('[data-src]').value;
    const body = { volumeTons: Number(m.el.querySelector('[data-vol]').value), message: m.el.querySelector('[data-msg]').value };
    if (src.startsWith('a')) body.applicationId = Number(src.slice(1));
    if (src.startsWith('p')) body.projectId = Number(src.slice(1));
    if (src.startsWith('s')) body.plotId = Number(src.slice(1));
    try {
      await api(`/api/v1/demands/${id}/letters`, { method: 'POST', body });
      m.close();
      toast('Sorğu alıcıya göndərildi');
      if (role === 'INVESTOR') loadMyLetters();
    } catch (err) { toast(err.message, 'err'); }
  }));
}

// ------------------------------------------------------------------ investor: məktublarım

function letterRow(l, { buyer = false } = {}) {
  const st = l.status === 'SIGNED' ? 'badge-good' : l.status === 'DECLINED' ? 'badge-crit' : 'badge-info';
  const target = l.appNumber ? `müraciət ${esc(l.appNumber)}` : l.projectCode ? `layihə ${esc(l.projectCode)}` : l.plotCode ? `sahə ${esc(l.plotCode)}` : '';
  return `<div class="int-row">
    <div><b>${esc(buyer ? l.requesterName : l.company)}</b> · ${esc(l.productLabel)} ${fmt.num(l.volumeTons)} t/il${l.price ? ` · ${fmt.num(l.price)} ₼/t` : ''}
      <div class="small muted">${target} · ${fmt.rel(l.createdAt)} · <span class="badge ${st}">${esc(l.statusLabel)}</span></div>
      ${l.message ? `<div class="small" style="margin-top:4px">"${esc(l.message)}"</div>` : ''}${l.buyerNote ? `<div class="small" style="margin-top:4px">Alıcının qeydi: "${esc(l.buyerNote)}"</div>` : ''}</div>
    ${buyer && l.status === 'REQUESTED' ? `<div class="stack" style="gap:6px"><button class="btn btn-primary btn-sm" data-sign="${l.id}">${icon('check', 'i-sm')}İmzala</button><button class="btn btn-sm" data-decline="${l.id}">${icon('x', 'i-sm')}İmtina</button></div>` : ''}
  </div>`;
}

async function loadMyLetters() {
  const el = $('#myLetters');
  if (!el) return;
  const list = await api('/api/v1/letters');
  el.innerHTML = list.length ? list.map((l) => letterRow(l)).join('') : emptyState('Hələ niyyət məktubu sorğunuz yoxdur. Təsdiqlənmiş tələbdə "Niyyət məktubu istə" düyməsini basın.', 'file-check');
}

// ------------------------------------------------------------------ alıcı kabineti

async function loadBuyer() {
  const [mine, letters] = await Promise.all([api('/api/v1/buyer/demands'), api('/api/v1/buyer/letters')]);
  $('#mineList').innerHTML = mine.length ? mine.map((d) => demandCard(d, { mine: true })).join('') : emptyState('Hələ tələbiniz yoxdur', 'hand-coins');
  const pending = letters.filter((l) => l.status === 'REQUESTED').length;
  $('#lettersTab').innerHTML = `${icon('file-check', 'i-sm')}Niyyət məktubu sorğuları${pending ? ` <span class="badge badge-warn">${pending}</span>` : ''}`;
  $('#lettersList').innerHTML = letters.length ? letters.map((l) => letterRow(l, { buyer: true })).join('') : emptyState('Hələ sorğu yoxdur', 'inbox');
}

function demandForm() {
  return `<form id="dForm" class="card card-pad" novalidate style="margin-bottom:16px">
    <h4 style="margin:0 0 10px">Yeni tələb</h4>
    <div class="form-grid">
      <div class="field"><label class="req">Şirkət</label><input class="input" name="company" required></div>
      <div class="field"><label>Növü</label><select class="input" name="buyerType">${Object.entries(TYPES).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></div>
      <div class="field"><label>Məhsul</label><select class="input" name="product">${Object.entries(PRODUCTS).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></div>
      <div class="field"><label class="req">İllik həcm (ton)</label><input class="input num" type="number" name="volumeTons" min="1"></div>
      <div class="field"><label>Qiymət: min (₼/t)</label><input class="input num" type="number" name="priceMin" min="0"></div>
      <div class="field"><label>Qiymət: maks (₼/t)</label><input class="input num" type="number" name="priceMax" min="0"></div>
      <div class="field"><label>Müqavilə (il)</label><input class="input num" type="number" name="contractYears" min="1" max="15" value="3"></div>
      <div class="field"><label>Qəbul məntəqəsi</label><select class="input" name="district">${Object.entries(DISTRICTS).map(([k, v]) => `<option value="${k}">${v}</option>`).join('')}</select></div>
      <div class="field"><label>Radius (km)</label><input class="input num" type="number" name="radiusKm" min="10" max="500" value="120"></div>
      <div class="field"><label>Mövsüm</label><input class="input" name="season" placeholder="məs. Sentyabr-oktyabr"></div>
      <div class="field full"><label>Keyfiyyət tələbləri</label><textarea class="input" name="quality" rows="2"></textarea></div>
    </div>
    <button class="btn btn-primary" type="submit" data-loading="Göndərilir...">${icon('send', 'i-sm')}Yoxlamaya göndər</button>
    <span class="small muted" style="margin-left:10px">Nazirlik yoxladıqdan sonra fermerlərə "təsdiqlənmiş tələb" kimi görünəcək.</span>
  </form>`;
}

function setupTabs() {
  const bar = $('#tabBar');
  if (role === 'BUYER') {
    bar.insertAdjacentHTML('beforeend', `<button data-tab="mine">${icon('briefcase', 'i-sm')}Mənim tələblərim</button><button data-tab="letters" id="lettersTab">${icon('file-check', 'i-sm')}Niyyət məktubu sorğuları</button>`);
    $('#tabsRoot').insertAdjacentHTML('beforeend', `<div class="tab-panel card-body" data-panel="mine">${demandForm()}<div class="stack" id="mineList"></div></div><div class="tab-panel card-body" data-panel="letters"><div id="lettersList"></div></div>`);
    $('#dForm').addEventListener('submit', (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      const body = Object.fromEntries(['company', 'buyerType', 'product', 'district', 'season', 'quality'].map((k) => [k, f.get(k)]));
      ['volumeTons', 'priceMin', 'priceMax', 'contractYears', 'radiusKm'].forEach((k) => { body[k] = f.get(k) ? Number(f.get(k)) : null; });
      withLoading(e.submitter, async () => {
        try { await api('/api/v1/buyer/demands', { method: 'POST', body }); toast('Tələb yoxlamaya göndərildi'); e.target.reset(); loadBuyer(); } catch (err) { toast(err.message, 'err'); }
      });
    });
    $('#lettersList').addEventListener('click', async (e) => {
      const s = e.target.closest('[data-sign]'), d = e.target.closest('[data-decline]');
      if (!s && !d) return;
      const btn = s || d;
      await withLoading(btn, async () => {
        try {
          await api(`/api/v1/buyer/letters/${btn.dataset.sign || btn.dataset.decline}/${s ? 'sign' : 'decline'}`, { method: 'POST', body: { note: s ? 'Keyfiyyət şərtlərinə əməl olunarsa alırıq.' : null } });
          toast(s ? 'Niyyət məktubu imzalandı' : 'İmtina edildi');
          loadBuyer();
        } catch (err) { toast(err.message, 'err'); }
      });
    });
    loadBuyer();
  } else if (role === 'INVESTOR') {
    bar.insertAdjacentHTML('beforeend', `<button data-tab="myletters">${icon('file-check', 'i-sm')}Mənim niyyət məktublarım</button>`);
    $('#tabsRoot').insertAdjacentHTML('beforeend', '<div class="tab-panel card-body" data-panel="myletters"><div id="myLetters"></div></div>');
    loadMyLetters();
  }
  setTabs($('#tabsRoot'), (t) => { if (t === 'all') setTimeout(() => state.map?.invalidateSize(), 50); });
  const h = location.hash.slice(1);
  const tab = h === 'mine' || h === 'letters' ? h : h === 'myletters' ? 'myletters' : null;
  if (tab) $(`[data-tab="${tab}"]`)?.click();
}

async function init() {
  $('#heroBtns').innerHTML = role === 'BUYER' ? `<a class="btn btn-gold" href="#mine" id="goMine">${icon('plus')}Tələb yerləşdir</a>`
    : `<a class="btn btn-gold" href="/qeydiyyat.html">${icon('user-plus')}Alıcı kimi qeydiyyat</a><a class="btn" href="/layiheler.html" style="--b-bg:rgba(255,255,255,.12);--b-ink:#fff;--b-line:rgba(255,255,255,.3)">${icon('handshake')}Layihələr</a>`;
  $('#goMine')?.addEventListener('click', () => $('[data-tab="mine"]')?.click());
  [state.demands, state.plots] = await Promise.all([api('/api/v1/demands'), api('/api/v1/plots')]);
  const vol = state.demands.reduce((a, d) => a + Number(d.volumeTons), 0);
  const value = state.demands.reduce((a, d) => a + Number(d.volumeTons) * (Number(d.priceMin || 0) + Number(d.priceMax || 0)) / 2, 0);
  const contracted = state.demands.reduce((a, d) => a + Number(d.contractedTons), 0);
  $('#kpis').innerHTML = `
    <div class="kpi"><span class="ico">${icon('badge-check')}</span><div class="lbl">Təsdiqlənmiş tələb</div><div class="val">${state.demands.length}</div><div class="sub">${new Set(state.demands.map((d) => d.company)).size} alıcı</div></div>
    <div class="kpi"><span class="ico">${icon('truck')}</span><div class="lbl">İllik həcm</div><div class="val">${fmt.num(vol)} t</div></div>
    <div class="kpi"><span class="ico">${icon('coins')}</span><div class="lbl">İllik bazar dəyəri</div><div class="val">${fmt.compactMoney(value)}</div><div class="sub">orta qiymətlə</div></div>
    <div class="kpi"><span class="ico">${icon('file-check')}</span><div class="lbl">Niyyət məktubu ilə bağlanıb</div><div class="val">${fmt.num(contracted)} t</div></div>`;
  $('#catFilter').innerHTML = `<button class="chip active" data-c="">Hamısı</button>${Object.entries(CAT).map(([k, v]) => `<button class="chip" data-c="${k}">${icon(v.icon, 'i-sm')}${esc(v.label)}</button>`).join('')}`;
  $$('#catFilter .chip').forEach((b) => b.addEventListener('click', () => { state.cat = b.dataset.c || null; $$('#catFilter .chip').forEach((x) => x.classList.toggle('active', x === b)); renderList(); }));
  renderList();
  $('#demandList').addEventListener('click', (e) => {
    const s = e.target.closest('[data-show]'), l = e.target.closest('[data-loi]');
    if (s) {
      focus(Number(s.dataset.show));
      // Tək sütunda xəritə siyahının üstündədir: ona sürüşdür
      if (getComputedStyle($('.dm-side')).position !== 'sticky') $('#dmap').scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
    if (l) requestLoi(Number(l.dataset.loi));
  });
  setupTabs();
  const dq = Number(params().get('demand'));
  if (dq) setTimeout(() => { focus(dq); $(`#d${dq}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }); }, 400);
}

onLive('notification', () => { if (role === 'BUYER') loadBuyer(); if (role === 'INVESTOR') loadMyLetters(); });
init().catch((e) => { $('#demandList').innerHTML = emptyState(e.message); });
