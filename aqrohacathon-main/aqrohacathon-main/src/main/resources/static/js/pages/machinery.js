import { api, renderLayout, $, $$, esc, icon, fmt, session, modal, toast, withLoading, showFieldErrors, emptyState, DISTRICTS, CAT, params } from '../core.js';
import { equipmentCard, rentDialog } from '../machinery-ui.js';

renderLayout('machinery');
const state = { type: params().get('type') || null, list: [], types: [] };

$('#district').innerHTML += Object.entries(DISTRICTS).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('');
$('#category').innerHTML += Object.entries(CAT).map(([k, c]) => `<option value="${k}">${esc(c.label)}</option>`).join('');

async function load() {
  const q = new URLSearchParams();
  if (state.type) q.set('type', state.type);
  if ($('#district').value) q.set('district', $('#district').value);
  if ($('#category').value) q.set('category', $('#category').value);
  state.list = await api('/api/v1/equipment?' + q);
  $('#eqGrid').innerHTML = state.list.length ? state.list.map((e) => equipmentCard(e)).join('') : emptyState('Bu filtrlərə uyğun texnika yoxdur', 'tractor');
}

async function kpis() {
  const all = await api('/api/v1/equipment');
  const owners = new Set(all.map((e) => e.ownerId)).size;
  const rented = all.reduce((s, e) => s + e.acceptedRequests, 0);
  $('#eqKpis').innerHTML = `
    <div class="kpi"><div class="lbl">${icon('tractor', 'i-sm')}Elan</div><div class="val num">${all.length}</div><div class="sub">${new Set(all.map((e) => e.type)).size} növ texnika</div></div>
    <div class="kpi"><div class="lbl">${icon('users', 'i-sm')}Texnika sahibi</div><div class="val num">${owners}</div><div class="sub">aqroservis şirkətləri və fermerlər</div></div>
    <div class="kpi"><div class="lbl">${icon('map-pin', 'i-sm')}Rayon</div><div class="val num">${new Set(all.map((e) => e.district)).size}</div><div class="sub">azad edilmiş ərazilərdə</div></div>
    <div class="kpi"><div class="lbl">${icon('handshake', 'i-sm')}Razılaşma</div><div class="val num">${rented}</div><div class="sub">qəbul edilmiş icarə</div></div>`;
}

async function types() {
  state.types = await api('/api/v1/equipment/types');
  const chip = (code, label, ic) => `<button type="button" class="chip chip-tg ${state.type === code ? 'active' : ''}" data-type="${code || ''}" aria-pressed="${state.type === code}">${ic ? icon(ic, 'i-sm') : ''}${esc(label)}</button>`;
  $('#typeChips').innerHTML = chip(null, 'Hamısı') + state.types.map((t) => chip(t.code, t.label, t.icon)).join('');
}

$('#typeChips').addEventListener('click', (e) => {
  const b = e.target.closest('[data-type]');
  if (!b) return;
  state.type = b.dataset.type || null;
  $$('#typeChips [data-type]').forEach((x) => { const on = (x.dataset.type || null) === state.type; x.classList.toggle('active', on); x.setAttribute('aria-pressed', String(on)); });
  load();
});
['#district', '#category'].forEach((s) => $(s).addEventListener('change', load));
document.addEventListener('click', (e) => {
  const b = e.target.closest('[data-rent]');
  if (!b) return;
  const eq = state.list.find((x) => x.id === Number(b.dataset.rent));
  if (eq) rentDialog(eq);
});

// ---------------------------------------------------------------- elan vermək
$('#addBtn').addEventListener('click', () => {
  if (!session.loggedIn()) { location.href = '/giris.html?next=/texnika.html'; return; }
  const m = modal({
    title: 'Texnikanı icarəyə vermək',
    body: `<form id="addForm" novalidate>
      <div class="form-grid">
        <div class="field"><label class="req" for="aType">Növ</label><select class="input" id="aType" name="type">${state.types.map((t) => `<option value="${t.code}">${esc(t.label)}</option>`).join('')}</select></div>
        <div class="field"><label class="req" for="aDist">Rayon</label><select class="input" id="aDist" name="district">${Object.entries(DISTRICTS).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('')}</select></div>
      </div>
      <div class="field"><label class="req" for="aTitle">Ad və model</label><input class="input" id="aTitle" name="title" maxlength="160" placeholder="məs. Belarus 1221 traktor"></div>
      <div class="form-grid">
        <div class="field"><label class="req" for="aPrice">Qiymət (₼)</label><input class="input num" type="number" id="aPrice" name="price" min="1" step="1" placeholder="150"></div>
        <div class="field"><label for="aUnit">Nəyə görə</label><select class="input" id="aUnit" name="priceUnit"><option value="DAY">gün</option><option value="HA">hektar</option><option value="MONTH">ay</option></select></div>
      </div>
      <div class="form-grid">
        <div class="field"><label for="aFrom">Mövsüm (başlanğıc ayı)</label><input class="input num" type="number" id="aFrom" name="seasonFrom" min="1" max="12" value="3"></div>
        <div class="field"><label for="aTo">Mövsüm (son ay)</label><input class="input num" type="number" id="aTo" name="seasonTo" min="1" max="12" value="10"></div>
      </div>
      <div class="field"><label for="aYear">Buraxılış ili</label><input class="input num" type="number" id="aYear" name="madeYear" min="1980" max="2030" placeholder="2021"></div>
      <div class="field"><label for="aDesc">Təsvir</label><input class="input" id="aDesc" name="description" maxlength="600" placeholder="operator daxildir, minimum sifariş və s."></div>
      <div id="aErr"></div>
    </form>`,
    foot: `<button class="btn btn-ghost" data-close type="button">Ləğv et</button><button class="btn btn-primary" id="aSave" type="button">${icon('check', 'i-sm')}Elan et</button>`,
  });
  m.el.querySelector('#aSave').addEventListener('click', (ev) => withLoading(ev.currentTarget, async () => {
    const f = m.el.querySelector('#addForm');
    const num = (v) => (v === '' ? null : Number(v));
    try {
      await api('/api/v1/equipment', { method: 'POST', body: { type: f.type.value, district: f.district.value, title: f.title.value, price: num(f.price.value), priceUnit: f.priceUnit.value, seasonFrom: num(f.seasonFrom.value), seasonTo: num(f.seasonTo.value), madeYear: num(f.madeYear.value), description: f.description.value } });
      m.close();
      toast('Texnika elan edildi');
      load(); kpis(); mine();
    } catch (err) {
      if (!showFieldErrors(f, err)) m.el.querySelector('#aErr').innerHTML = `<div class="alert alert-crit">${icon('circle-x')}<div>${esc(err.message)}</div></div>`;
    }
  }));
});

// ---------------------------------------------------------------- mənim bölməm
const stBadge = (s, l) => `<span class="badge ${s === 'ACCEPTED' ? 'badge-good' : s === 'PENDING' ? 'badge-warn' : ''}">${esc(l)}</span>`;
async function mine() {
  if (!session.loggedIn()) { $('#mine').innerHTML = ''; return; }
  const r = await api('/api/v1/equipment/mine').catch(() => null);
  if (!r) return;
  const row = (q, inc) => `<tr><td><b>${esc(q.equipmentTitle)}</b><div class="small muted">${esc(inc ? q.requesterName : q.ownerName)}${q.plotCode ? ` · ${esc(q.plotCode)}` : ''}</div></td>
    <td>${fmt.date(q.startDate)}</td><td class="r num">${fmt.num1(q.quantity)} ${esc(q.unitLabel)}</td><td class="r num">${fmt.money(q.totalPrice)}</td><td>${stBadge(q.status, q.statusLabel)}</td>
    <td class="r">${q.status === 'PENDING' ? (inc ? `<button class="btn btn-sm btn-primary" data-dec="${q.id}" data-ok="1">Qəbul et</button> <button class="btn btn-sm btn-ghost" data-dec="${q.id}" data-ok="0">Rədd et</button>` : `<button class="btn btn-sm btn-ghost" data-cancel="${q.id}">Ləğv et</button>`) : ''}</td></tr>`;
  const table = (rows, inc) => (rows.length ? `<div class="table-wrap"><table class="table" style="min-width:680px"><thead><tr><th>Texnika</th><th>Başlama</th><th class="r">Miqdar</th><th class="r">Məbləğ</th><th>Status</th><th></th></tr></thead><tbody>${rows.map((q) => row(q, inc)).join('')}</tbody></table></div>` : `<p class="small muted">Hələ sorğu yoxdur.</p>`);
  $('#mine').innerHTML = `
    <div class="grid-2">
      <div class="card card-pad"><h3 class="rg-h">${icon('send')}Göndərdiyim sorğular</h3>${table(r.outgoing, false)}</div>
      <div class="card card-pad"><h3 class="rg-h">${icon('inbox')}Texnikama gələn sorğular</h3>${r.listings.length ? table(r.incoming, true) : `<p class="small muted">Texnikanız varsa elan edin: mövsümlük icarə əlavə gəlirdir.</p>`}</div>
    </div>`;
}
$('#mine').addEventListener('click', async (e) => {
  const d = e.target.closest('[data-dec]'), c = e.target.closest('[data-cancel]');
  try {
    if (d) { await api(`/api/v1/equipment/requests/${d.dataset.dec}/decide`, { method: 'POST', body: { accept: d.dataset.ok === '1' } }); toast(d.dataset.ok === '1' ? 'Sorğu qəbul edildi' : 'Sorğu rədd edildi'); }
    if (c) { await api(`/api/v1/equipment/requests/${c.dataset.cancel}/cancel`, { method: 'POST' }); toast('Sorğu ləğv edildi'); }
    if (d || c) { mine(); kpis(); }
  } catch (err) { toast(err.message, 'err'); }
});
document.addEventListener('equipment-changed', mine);

(async () => {
  await types();
  await Promise.all([load(), kpis(), mine()]);
  if (location.hash === '#mine') $('#mine').scrollIntoView({ behavior: 'smooth' });
})().catch((e) => { $('#eqGrid').innerHTML = emptyState(e.message); });

