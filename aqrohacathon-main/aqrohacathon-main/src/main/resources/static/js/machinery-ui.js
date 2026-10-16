// Texnika icarəsi: kart, icarə sorğusu forması və "al və ya icarə" müqayisəsi (texnika səhifəsi və sahə səhifəsi üçün ortaq)
import { api, esc, icon, fmt, session, modal, toast, withLoading, showFieldErrors, CAT } from './core.js';

const MONTHS = ['yan', 'fev', 'mar', 'apr', 'may', 'iyn', 'iyl', 'avq', 'sen', 'okt', 'noy', 'dek'];
export const UNIT = { DAY: 'gün', HA: 'ha', MONTH: 'ay' };
const season = (e) => (e.seasonFrom && e.seasonTo ? `${MONTHS[e.seasonFrom - 1]}-${MONTHS[e.seasonTo - 1]}` : 'il boyu');

export function equipmentCard(e, { compact = false } = {}) {
  return `<article class="eq-card">
    <div class="eq-top"><span class="eq-ico">${icon(e.icon)}</span><div style="min-width:0"><div class="small muted">${esc(e.typeLabel)}${e.madeYear ? ` · ${e.madeYear}` : ''}</div><h3>${esc(e.title)}</h3></div></div>
    <div class="eq-price"><b class="num">${esc(e.priceLabel)}</b><span>${icon('calendar', 'i-sm')}${season(e)}</span></div>
    <div class="eq-meta"><span>${icon('map-pin', 'i-sm')}${esc(e.districtLabel)}${e.distanceKm != null ? ` · ${fmt.num1(e.distanceKm)} km` : ''}</span><span>${icon('user', 'i-sm')}${esc(e.ownerName)}</span></div>
    ${compact ? '' : `<div class="row-wrap" style="gap:4px">${e.suitable.map((c) => `<span class="badge">${esc(CAT[c]?.label || c)}</span>`).join('')}</div>${e.description ? `<p class="small muted" style="margin:0">${esc(e.description)}</p>` : ''}`}
    <div class="eq-foot">${e.acceptedRequests ? `<span class="small muted">${icon('handshake', 'i-sm')} ${e.acceptedRequests} icarə</span>` : '<span></span>'}<button class="btn btn-primary btn-sm" type="button" data-rent="${e.id}">${icon('send', 'i-sm')}İcarəyə götür</button></div>
  </article>`;
}

/** İcarə sorğusu: sahə, başlama tarixi, miqdar (gün, ha və ya ay), qeyd. */
export async function rentDialog(e, { plotId = null } = {}) {
  if (!session.loggedIn()) { location.href = '/giris.html?next=' + encodeURIComponent(location.pathname + location.search); return; }
  const plots = await api('/api/v1/plots').catch(() => []);
  const fit = plots.filter((p) => e.suitable.includes(p.category));
  const today = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
  const m = modal({
    title: `İcarə sorğusu: ${esc(e.title)}`,
    body: `<form id="rentForm" novalidate>
      <p class="small muted" style="margin-top:0">${esc(e.ownerName)} · ${esc(e.districtLabel)} · <b>${esc(e.priceLabel)}</b></p>
      <div class="field"><label for="rPlot">Hansı sahə üçün</label><select class="input" id="rPlot" name="plotId"><option value="">Sahə seçilməyib</option>${fit.map((p) => `<option value="${p.id}" ${Number(plotId) === p.id ? 'selected' : ''}>${esc(p.code)} · ${esc(p.name)} (${fmt.ha(p.areaHa)})</option>`).join('')}</select></div>
      <div class="form-grid">
        <div class="field"><label class="req" for="rStart">Başlama tarixi</label><input class="input" type="date" id="rStart" name="startDate" min="${today}" value="${today}"></div>
        <div class="field"><label class="req" for="rQty">Miqdar (${UNIT[e.priceUnit]})</label><input class="input num" type="number" id="rQty" name="quantity" min="0.5" step="0.5" value="${e.priceUnit === 'HA' ? 50 : e.priceUnit === 'MONTH' ? 3 : 10}"></div>
      </div>
      <div class="eq-total" id="rTotal"></div>
      <div class="field"><label for="rMsg">Qeyd</label><input class="input" id="rMsg" name="message" maxlength="500" placeholder="məs. operatorla, səhər növbəsi"></div>
      <div id="rErr"></div>
    </form>`,
    foot: `<button class="btn btn-ghost" data-close type="button">Ləğv et</button><button class="btn btn-primary" id="rSend" type="button">${icon('send', 'i-sm')}Sorğu göndər</button>`,
  });
  const qty = m.el.querySelector('#rQty');
  const total = () => { m.el.querySelector('#rTotal').innerHTML = `Təxmini məbləğ: <b class="num">${fmt.money(Math.round(Number(qty.value || 0) * Number(e.price)))}</b> <span class="small muted">(son qiymət sahibi ilə razılaşdırılır)</span>`; };
  qty.addEventListener('input', total);
  total();
  m.el.querySelector('#rSend').addEventListener('click', (ev) => withLoading(ev.currentTarget, async () => {
    const f = m.el.querySelector('#rentForm');
    try {
      await api(`/api/v1/equipment/${e.id}/requests`, { method: 'POST', body: { plotId: f.plotId.value ? Number(f.plotId.value) : null, startDate: f.startDate.value, quantity: Number(f.quantity.value), message: f.message.value } });
      m.close();
      toast('Sorğu göndərildi: texnika sahibi bildiriş aldı');
      document.dispatchEvent(new CustomEvent('equipment-changed'));
    } catch (err) {
      if (!showFieldErrors(f, err)) m.el.querySelector('#rErr').innerHTML = `<div class="alert alert-crit">${icon('circle-x')}<div>${esc(err.message)}</div></div>`;
    }
  }));
}

/** "Texnikanı alıram" və "İcarəyə götürürəm": eyni Monte-Carlo modelində iki variant. */
export function compareBlock(c) {
  if (!c) return `<p class="small muted">Bu istiqamətdə texnika investisiyanın kiçik hissəsidir: müqayisə aparılmır.</p>`;
  const b = c.buy, r = c.rent;
  const pay = (o) => (o.paybackP50 ? `${o.paybackP50}-ci il` : `${c.horizon || 15} ildən sonra`);
  const better = r.roiPct > b.roiPct;
  const col = (o, title, ic, cls) => `<div class="eq-opt ${cls}">
      <div class="eq-opt-h">${icon(ic)}<b>${title}</b></div>
      <div class="eq-roi"><span title="Bağ və ya ferma tam gücə çatdığı ildə illik xalis gəlirin ilkin investisiyaya nisbəti">ROI (tam gücdə, illik)</span><b class="num">${fmt.num1(o.roiPct)}%</b></div>
      <div class="pf-vs-row"><span>İlkin investisiya</span><b class="num">${fmt.compactMoney(o.capex)}</b></div>
      <div class="pf-vs-row"><span>Pul qayıdır (orta)</span><b>${pay(o)}</b></div>
      <div class="pf-vs-row"><span>Xalis dəyər (NPV, orta)</span><b class="num">${fmt.compactMoney(o.npvP50)}</b></div>
      ${o.irrPct != null ? `<div class="pf-vs-row"><span>IRR (orta ssenari)</span><b class="num">${fmt.num1(o.irrPct)}%</b></div>` : ''}
      <div class="pf-vs-row"><span>Geri qayıtma ehtimalı</span><b class="num">${Math.round(o.probPayback * 100)}%</b></div>
    </div>`;
  return `<div class="eq-compare">
      ${col(b, 'Texnikanı alıram', 'banknote', 'buy')}
      <div class="eq-vs">${icon('arrow-right')}<span>${better ? `+${fmt.num1(r.roiPct - b.roiPct)} faiz bəndi` : 'fərq azdır'}</span></div>
      ${col(r, 'İcarəyə götürürəm', 'tractor', 'rent')}
    </div>
    <p class="small" style="margin:10px 0 0"><b>${c.useLabel}, ${fmt.num(c.scale)} ${esc(c.unit)}:</b> texnika ${fmt.compactMoney(c.machineryCapex)} dəyərindədir; icarədə bu məbləğ ilkin investisiyadan çıxır, əvəzinə ildə təxminən ${fmt.compactMoney(c.rentPerYear)} icarə haqqı ödənilir.</p>
    <ul class="small muted" style="margin:6px 0 0;padding-left:18px">${c.notes.map((n) => `<li>${esc(n)}</li>`).join('')}</ul>`;
}
