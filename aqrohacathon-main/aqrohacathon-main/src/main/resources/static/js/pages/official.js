// Keçirilmiş investisiya müsabiqələrinin rəsmi göstəriciləri (Nazirlik) və bazar həcmi: TAM / SAM / SOM.
import { api, $, esc, icon, fmt } from '../core.js';
import { renderDeliveryTable } from '../delivery.js';

const nf = (v, d = 0) => Number(v).toLocaleString('az-AZ', { maximumFractionDigits: d, minimumFractionDigits: 0 }).replace(/,/g, ' ');
const mln = (v) => `${nf(v / 1_000_000, v >= 1e9 ? 0 : 1)} mln ₼`;
const big = (v) => (v >= 1e9 ? `${nf(v / 1e9, 2)} mlrd ₼` : mln(v));

export async function renderOfficial(el) {
  const o = await api('/api/v1/impact/official');
  const maxHa = Math.max(...o.byDistrict.map((d) => d.leasedHa));
  const maxInv = Math.max(...o.byDistrict.map((d) => d.minInvestment));
  const byHa = [...o.byDistrict].sort((a, b) => b.leasedHa - a.leasedHa);
  const [tam, sam, som] = o.market;
  const perHa = o.byDistrict.map((d) => ({ d: d.district, v: d.minInvestment / d.leasedHa })).sort((a, b) => a.v - b.v);
  const perHaMin = perHa[0], perHaMax = perHa[perHa.length - 1];
  // Dairələrin sahəsi dəyərə mütənasibdir (radius kvadrat kökə görə)
  const R = 120, r = (v) => Math.max(7, R * Math.sqrt(v / tam.value));
  el.innerHTML = `
  <div class="card card-pad off-card">
    <div class="row between" style="flex-wrap:wrap;gap:10px"><div><span class="overline">Rəsmi göstəricilər · Kənd Təsərrüfatı Nazirliyi</span>
      <h2 style="margin:2px 0 0;font-size:24px">${icon('landmark')} Keçirilmiş ${o.competitions} investisiya müsabiqəsi</h2></div>
      <a class="btn btn-sm" href="/qaydalar.html">${icon('scroll-text', 'i-sm')}Müsabiqə qaydaları</a></div>
    <div class="kpis off-kpis">
      <div class="kpi"><div class="lbl">${icon('map', 'i-sm')}Müsabiqəyə çıxarılıb</div><div class="val num">${nf(o.offeredHa)} ha</div><div class="sub">${o.districts} rayon üzrə</div></div>
      <div class="kpi"><div class="lbl">${icon('users', 'i-sm')}İştirakçı</div><div class="val num">${nf(o.participants)}</div><div class="sub">sahibkarlıq subyekti</div></div>
      <div class="kpi"><div class="lbl">${icon('award', 'i-sm')}Qalib</div><div class="val num">${nf(o.winners)}</div><div class="sub">${nf(o.winRate * 100, 1)}% qalib olur</div></div>
      <div class="kpi"><div class="lbl">${icon('stamp', 'i-sm')}Müqavilə (I-II)</div><div class="val num">${nf(o.contracts)}</div><div class="sub">III-IV üzrə davam edir</div></div>
      <div class="kpi"><div class="lbl">${icon('sprout', 'i-sm')}İcarəyə verilib</div><div class="val num">${nf(o.leasedHa)} ha</div><div class="sub">${mln(o.minInvestmentTotal)} minimum investisiya</div></div>
    </div>
    <div class="off-grid">
      <div>
        <h3 class="rg-h">${icon('chart-bar')}Rayonlar üzrə: icarəyə verilmiş sahə və investisiya öhdəliyi</h3>
        <div class="table-wrap"><table class="table off-table"><thead><tr><th>Rayon</th><th>İcarə (ha)</th><th>Minimum investisiya</th><th class="r">₼ / ha</th></tr></thead><tbody>
          ${byHa.map((d) => `<tr><td><b>${esc(d.district)}</b></td>
            <td><div class="ibar"><i style="width:${(d.leasedHa / maxHa) * 100}%"></i><span class="num">${nf(d.leasedHa, 2)}</span></div></td>
            <td><div class="ibar inv"><i style="width:${(d.minInvestment / maxInv) * 100}%"></i><span class="num">${mln(d.minInvestment)}</span></div></td>
            <td class="r num">${nf(d.minInvestment / d.leasedHa)}</td></tr>`).join('')}
        </tbody><tfoot><tr><td><b>Cəmi</b></td><td class="num"><b>${nf(o.leasedHa, 2)} ha</b></td><td class="num"><b>${mln(o.minInvestmentTotal)}</b></td><td class="r num"><b>${nf(o.avgInvestmentPerHa)}</b></td></tr></tfoot></table></div>
        <p class="small muted" style="margin:8px 0 0">${icon('info', 'i-sm')} Hektara düşən investisiya rayondan rayona ${nf(perHaMin.v)} ₼ (${esc(perHaMin.d)}) ilə ${nf(perHaMax.v)} ₼ (${esc(perHaMax.d)}) arasında dəyişir: layihənin növü (əkinçilik, intensiv bağ, ferma) investisiyanın həcmini müəyyən edir.</p>
      </div>
      <div>
        <h3 class="rg-h">${icon('calendar-range')}Müsabiqələrin xronologiyası</h3>
        <ol class="off-tl">${o.timeline.map((c) => `<li><span class="off-no">${esc(c.no)}</span><div><b>${esc(c.period)}</b>
          <div class="small">${esc(c.districts)}${c.offeredHa ? ` · ${nf(c.offeredHa)} ha` : ''}${c.applications ? ` · ${c.applications} müraciət` : ''}</div>
          <div class="small muted">${esc(c.result)}</div></div></li>`).join('')}</ol>
      </div>
    </div>
  </div>

  <div class="card card-pad off-card">
    <div class="row between" style="flex-wrap:wrap;gap:10px"><div><span class="overline">Bazar həcmi</span>
      <h2 style="margin:2px 0 0;font-size:24px">${icon('target')} TAM · SAM · SOM</h2></div>
      <span class="small muted">Orta investisiya: ${nf(o.avgInvestmentPerHa)} ₼/ha (Nazirliyin müsabiqə göstəriciləri)</span></div>
    <div class="tam-grid">
      <svg class="tam-svg" viewBox="0 0 260 260" role="img" aria-label="TAM, SAM və SOM dairələri">
        <circle cx="130" cy="${130 + R - R}" r="${R}" class="c1"/>
        <circle cx="130" cy="${130 + R - r(sam.value)}" r="${r(sam.value)}" class="c2"/>
        <circle cx="130" cy="${130 + R - r(som.value)}" r="${r(som.value)}" class="c3"/>
        <text x="130" y="44" class="t">TAM</text><text x="130" y="${130 + R - 2 * r(sam.value) + 22}" class="t">SAM</text><text x="130" y="${130 + R - r(som.value) + 4}" class="t s">SOM</text>
      </svg>
      <div class="tam-list">${o.market.map((m, i) => `<div class="tam-row l${i}">
        <div class="tam-lvl">${esc(m.level)}</div>
        <div class="tam-body"><div class="row between" style="gap:8px;flex-wrap:wrap"><b>${esc(m.title)}</b><b class="num tam-val">${big(m.value)}</b></div>
          <div class="small">${esc(m.what)}: <b>${nf(m.ha)} ha</b></div>
          <div class="small muted">${esc(m.formula)} · ${esc(m.source)}</div></div></div>`).join('')}
        <p class="small muted" style="margin:6px 0 0">${esc(o.note)} SOM portalın sahələri artdıqca avtomatik yenilənir.</p>
      </div>
    </div>
    <div class="off-context">${o.context.map((f) => `<div class="off-fact"><b class="num">${nf(f.value, 1)} ${esc(f.unit)}</b><span>${esc(f.label)}</span>
      <em>${f.url ? `<a href="${esc(f.url)}" target="_blank" rel="noopener">${esc(f.source)}</a>` : esc(f.source)}, ${esc(f.date)}</em></div>`).join('')}</div>
  </div>`;
}

const host = $('#official');
if (host) {
  renderOfficial(host).catch((e) => { host.innerHTML = ''; console.warn(e); }).then(() => {
    if (!location.pathname.startsWith('/regionlar')) return;
    const box = document.createElement('div');
    box.className = 'card card-pad';
    box.id = 'delivery';
    box.style.scrollMarginTop = '90px';
    host.after(box);
    renderDeliveryTable(box);
  });
}
