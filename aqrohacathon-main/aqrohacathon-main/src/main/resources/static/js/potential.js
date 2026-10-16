// "Bu torpaqda nə etmək olar?" - torpağın ən yaxşı istifadəsi (xanalar)
import { api, esc, icon, fmt } from './core.js';

export const USE = {
  EKINCILIK: { icon: 'wheat', short: 'Taxıl' },
  HEYVANDARLIQ: { icon: 'milk', short: 'Ferma' },
  BAGCILIQ: { icon: 'cherry', short: 'Bağ' },
  UZUMCULUK: { icon: 'grape', short: 'Üzüm' },
  ISTIXANA: { icon: 'warehouse', short: 'İstixana' },
  ARICILIQ: { icon: 'hexagon', short: 'Bal' },
  GUNES: { icon: 'sun', short: 'Günəş' },
  AQROTURIZM: { icon: 'house', short: 'Turizm' },
  SOYUDUCU: { icon: 'snowflake', short: 'Anbar' },
};

const unitLabel = (u, v) => (u === 'ha' ? fmt.ha(v) : `${fmt.num(v)} ${u}`);

export function verdictBadge(o) {
  const cls = o.suitability >= 75 ? 'badge-good' : o.suitability >= 55 ? 'badge-info' : o.suitability >= 35 ? 'badge-warn' : '';
  const ic = o.suitability >= 55 ? 'circle-check' : o.suitability >= 35 ? 'triangle-alert' : 'circle-x';
  return `<span class="badge ${cls}">${icon(ic)}${esc(o.verdict)}</span>`;
}

export function potentialTile(o, plotId, { compact = false } = {}) {
  const pos = o.reasons.filter((r) => r.positive).slice(0, compact ? 1 : 2);
  const neg = o.reasons.filter((r) => !r.positive).slice(0, compact ? 1 : 2);
  const pay = o.paybackP50 ? `${o.paybackP50} il` : `${o.horizon} ildən çox`;
  return `<a class="pot-tile ${o.suitability < 35 ? 'dim' : ''}" href="/simulyator.html?plot=${plotId}&use=${o.useCase}">
    <div class="pt-head"><span class="pt-ico">${icon(USE[o.useCase]?.icon || 'sprout')}</span><div style="min-width:0"><b>${esc(o.label)}</b><div class="small muted">${esc(o.hint)}</div></div></div>
    <div class="pt-score"><div class="pt-bar"><span style="width:${o.suitability}%"></span></div><span class="num"><b>${o.suitability}</b>/100</span></div>
    <div class="row-wrap" style="gap:6px">${verdictBadge(o)}${o.permitted ? '' : `<span class="badge">${icon('lock')}Təyinat dəyişikliyi lazımdır</span>`}</div>
    <ul class="pt-reasons">${pos.map((r) => `<li class="ok">${icon('check')}${esc(r.text)}</li>`).join('')}${neg.map((r) => `<li class="no">${icon('x')}${esc(r.text)}</li>`).join('')}</ul>
    <div class="pt-facts">
      <div><span>Miqyas</span><b>${unitLabel(o.unit, o.scale)}</b></div>
      <div><span>İnvestisiya</span><b>${fmt.compactMoney(o.capex)}</b></div>
      <div><span>İllik xalis gəlir</span><b>${fmt.compactMoney(o.annualNet)}</b></div>
      <div><span>Geri qayıtma (orta)</span><b>${pay}</b></div>
    </div>
    <div class="pt-foot"><span>${o.horizon} ildə qaytarma ehtimalı <b>${Math.round(o.probPayback * 100)}%</b> · ${o.jobs} iş yeri</span><span class="pt-go">${icon('play', 'i-sm')}Gələcəyi yaşa</span></div>
  </a>`;
}

/** Sahə səhifəsində potensial bölməsini çəkir. */
export async function renderPotential(el, plotId) {
  el.innerHTML = '<div class="loader"></div>';
  let d;
  try { d = await api(`/api/v1/plots/${plotId}/potential`); } catch (e) { el.innerHTML = `<p class="muted">${esc(e.message)}</p>`; return null; }
  const top = d.options.filter((o) => o.suitability >= 35);
  const rest = d.options.filter((o) => o.suitability < 35);
  el.innerHTML = `
    <div class="row between" style="align-items:flex-start;gap:16px;flex-wrap:wrap;margin-bottom:14px">
      <div style="min-width:0;flex:1">
        <span class="overline">Ən yaxşı istifadə · ${d.options.length} ssenari</span>
        <h3 style="margin:2px 0 6px">${icon('lightbulb')} Bu torpaqda nə etmək olar?</h3>
        <p class="small muted" style="margin:0;max-width:720px">Torpağın təyinatı: <b>${esc(d.designationLabel)}</b>. ${esc(d.designationDescription)} Hər ssenari üçün uyğunluq balı və 600 ssenarilik Monte-Carlo simulyasiyası hesablanıb.</p>
      </div>
      <a class="btn btn-gold" href="/simulyator.html?plot=${plotId}&use=${d.options[0].useCase}">${icon('play', 'i-sm')}Gələcəyi 30 saniyədə yaşa</a>
      <a class="btn" href="/plan.html?plot=${plotId}&use=${d.options[0].useCase}">${icon('calendar-range', 'i-sm')}Fəaliyyət planı: nə vaxt, nə lazımdır</a>
    </div>
    <div class="pot-grid">${top.map((o) => potentialTile(o, plotId)).join('')}</div>
    ${rest.length ? `<details class="pot-more"><summary>${icon('chevron-down', 'i-sm')}Tövsiyə olunmayan ${rest.length} ssenari (niyə?)</summary><div class="pot-grid" style="margin-top:12px">${rest.map((o) => potentialTile(o, plotId, { compact: true })).join('')}</div></details>` : ''}
    ${d.solar?.measured ? `<p class="small muted" style="margin-top:12px">${icon('sun', 'i-sm')} Günəş istehsalı bu koordinat üçün real ölçülüb: ${fmt.num(d.solar.kwhPerKwp)} kVts/kVtp/il. Mənbə: ${esc(d.solar.source)}.</p>` : ''}`;
  return d;
}
