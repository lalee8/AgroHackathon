// Böyük Qayıdış paneli: rayonlar üzrə potensial, sosial təsir və investisiyaya hazırlıq
import { api, renderLayout, $, esc, icon, fmt, errorToast } from '../core.js';
import { USE } from '../potential.js';
import { createMap } from '../basemap.js';

renderLayout('regions');

// Ardıcıl göy şkala (hazırlıq, açıqdan tünd) və hazırlıq tərkibi üçün kateqoriya rəngləri (qonşu cütlər CVD-təhlükəsiz)
const SEQ = ['#cde2fb', '#9ec5f4', '#5598e7', '#2a78d6', '#1c5cab'];
const PARTS = [['demining', 'Minatəmizləmə', '#2a78d6', 30], ['infrastructure', 'İnfrastruktur', '#eb6834', 25], ['economics', 'İqtisadi potensial', '#1baf7a', 25], ['market', 'Bazar marağı', '#eda100', 20]];
const money = (v) => fmt.compactMoney(v);
const seq = (v) => SEQ[Math.max(0, Math.min(SEQ.length - 1, Math.floor((v - 40) / 12)))];

async function init() {
  let r;
  try {
    r = await api('/api/v1/impact/regions');
  } catch (e) {
    $('#root').innerHTML = `<div class="alert alert-crit">${icon('circle-x')}<div>${esc(e.message)}</div></div>`;
    errorToast(e);
    return;
  }
  const t = r.totals, im = t.potential;
  $('#root').innerHTML = `
    <div class="kpis">
      <div class="kpi hero-kpi"><div class="lbl">${icon('coins', 'i-sm')}Potensial investisiya</div><div class="val num">${money(t.potentialCapex)}</div><div class="sub">${fmt.num(t.plots)} sahə, ${fmt.ha(t.areaHa)} · yetkin ildə ${money(t.potentialAnnualNet)} xalis gəlir</div></div>
      <div class="kpi"><div class="lbl">${icon('users', 'i-sm')}Daimi iş yeri</div><div class="val num">${fmt.num(im.directJobs)}</div><div class="sub">+ ${fmt.num(im.indirectJobs)} dolayı · hər 1 mln ₼-a ${fmt.num1(t.jobsPerMillion)}</div></div>
      <div class="kpi"><div class="lbl">${icon('house', 'i-sm')}Ailələrin qayıdışı</div><div class="val num">${fmt.num(im.families)}</div><div class="sub">≈ ${fmt.num(im.people)} nəfər · ildə ${money(im.wageFund)} əmək haqqı</div></div>
      <div class="kpi"><div class="lbl">${icon('wheat', 'i-sm')}Ərzaq istehsalı</div><div class="val num">${fmt.num(Math.round(im.foodTons))} t</div><div class="sub">ildə · ${fmt.num(Math.round(im.co2Tons))} t CO₂ udulur və ya azalır</div></div>
    </div>

    <div class="rg-pulse card card-pad">
      <div><span class="rg-pulse-n num">${fmt.num(t.applications)}</span><span>müraciət · ${money(t.applicationInvestment)} təklif olunan investisiya</span></div>
      <div><span class="rg-pulse-n num">${fmt.num(t.projects)}</span><span>layihə investor axtarır · ${money(t.projectAsk)}</span></div>
      <div><span class="rg-pulse-n num">${fmt.num(Math.round(t.demandTons))} t</span><span>təsdiqlənmiş alıcı tələbi · ${fmt.num(t.demands)} müqavilə təklifi</span></div>
    </div>

    <div class="rg-layout">
      <div class="stack">
      <div class="card rg-mapcard">
        <div class="card-head"><h3>${icon('map')}Rayonlar xəritəsi</h3><span class="small muted">Dairənin ölçüsü: daimi iş yeri · rəng: hazırlıq indeksi</span></div>
        <div id="rgMap" class="rg-map" role="img" aria-label="Rayonlar üzrə potensial iş yerləri və hazırlıq indeksi"></div>
        <div class="rg-scale small muted"><span>Hazırlıq</span>${SEQ.map((c, i) => `<i style="background:${c}" title="${40 + i * 12}+"></i>`).join('')}<span>40 → 88+</span></div>
      </div>
      <div class="card card-pad">
        <h3 class="rg-h">${icon('layers')}Potensial istiqamətlər üzrə</h3>
        <p class="small muted">Hər sahədə ən uyğun istiqamət seçiləndə investisiyanın bölgüsü</p>
        <div class="rg-uses-agg">${usesAgg(r.districts)}</div>
      </div>
      </div>
      <div class="card card-pad">
        <h3 class="rg-h">${icon('trophy')}Hazırlıq reytinqi</h3>
        <p class="small muted">Növbəti müsabiqə üçün ən hazır rayonlar. Zolaq 100 baldan ibarətdir.</p>
        <div class="chart-legend">${PARTS.map(([, l, c, max]) => `<span><i style="background:${c}"></i>${l} (${max})</span>`).join('')}</div>
        <div class="rg-rank">${r.districts.map((d, i) => `
          <div class="rg-row">
            <span class="rg-pos">${i + 1}</span>
            <div class="rg-main"><div class="rg-top"><b>${esc(d.label)}</b><span class="small muted">${esc(d.region)}</span><b class="num rg-score">${d.readiness.total}</b></div>
              <div class="rg-bar">${PARTS.map(([k, l, c]) => `<i style="width:${d.readiness[k]}%;background:${c}" title="${l}: ${d.readiness[k]}"></i>`).join('')}</div>
              <div class="small muted rg-head">${esc(d.headline)}</div>
            </div>
          </div>`).join('')}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-head"><h3>${icon('list')}Rayonlar üzrə ətraflı</h3><span class="small muted">Ən uyğun istiqamətdə, standart miqyasda tam istifadə</span></div>
      <div class="table-wrap"><table class="table rg-table">
        <thead><tr><th>Rayon</th><th class="r">Torpaq</th><th class="r">Minatəmizləmə</th><th>Əsas imkanlar</th><th class="r">Potensial investisiya</th><th class="r">İş yeri</th><th class="r">Ailə</th><th class="r">Maraq</th><th class="r">Hazırlıq</th></tr></thead>
        <tbody>${r.districts.map((d) => `<tr>
          <td><b>${esc(d.label)}</b><div class="small muted">${d.plots} sahə · ${d.awardedPlots} icarədə</div></td>
          <td class="r num">${fmt.ha(d.areaHa)}</td>
          <td class="r">${deminingBadge(d.clearedPct)}</td>
          <td><div class="rg-uses">${d.uses.slice(0, 3).map((u) => `<span class="badge">${icon(USE[u.useCase]?.icon || 'leaf')}${esc(u.label)} × ${u.plots}</span>`).join('')}</div></td>
          <td class="r num">${money(d.potentialCapex)}</td>
          <td class="r num">${fmt.num(d.potential.directJobs)}</td>
          <td class="r num">${fmt.num(d.potential.families)}</td>
          <td class="r small">${d.applications} müraciət<br>${d.projects} layihə · ${fmt.num(Math.round(d.demandTons))} t tələb</td>
          <td class="r"><span class="rg-pill" style="background:${seq(d.readiness.total)};color:${d.readiness.total >= 64 ? '#fff' : '#0b2a4a'}">${d.readiness.total}</span></td>
        </tr>`).join('')}</tbody>
      </table></div>
    </div>

    <div class="grid-2">
      <a class="card card-pad rg-cta" href="/portfel.html">${icon('chart-pie', 'i-lg')}<div><b>Bu potensialdan portfel qurun</b><div class="small muted">Büdcəni rayonlar və istiqamətlər arasında riskə görə bölün</div></div>${icon('arrow-right')}</a>
      <a class="card card-pad rg-cta" href="/xerite.html">${icon('map-pin', 'i-lg')}<div><b>Sahələri xəritədə görün</b><div class="small muted">Hər sahənin təyinatı, infrastrukturu və müsabiqəsi</div></div>${icon('arrow-right')}</a>
    </div>

    <details class="card card-pad pf-assume"><summary>${icon('info', 'i-sm')} Metodologiya və mənbələr</summary><ul>${r.method.map((m) => `<li>${esc(m)}</li>`).join('')}</ul></details>`;
  drawMap(r.districts);
}

// Minatəmizləmə: rəng + ikon + yazı (rəng tək deyil)
function deminingBadge(pct) {
  const v = Number(pct);
  if (v >= 99.95) return `<span class="badge badge-good nowrap" title="Sahələr minalardan tam təmizlənib">${icon('shield-check', 'i-sm')}100% · Təmiz</span>`;
  if (v >= 80) return `<span class="badge badge-warn nowrap" title="Təmizləmə davam edir">${icon('triangle-alert', 'i-sm')}${fmt.num1(v)}% · Davam edir</span>`;
  return `<span class="badge badge-crit nowrap" title="Təmizləmə erkən mərhələdədir">${icon('circle-x', 'i-sm')}${fmt.num1(v)}% · Erkən mərhələ</span>`;
}

function usesAgg(ds) {
  const m = new Map();
  ds.forEach((d) => d.uses.forEach((u) => { const x = m.get(u.useCase) || { label: u.label, plots: 0, capex: 0 }; x.plots += u.plots; x.capex += u.capex; m.set(u.useCase, x); }));
  const rows = [...m.entries()].sort((a, b) => b[1].capex - a[1].capex);
  const max = Math.max(...rows.map(([, x]) => x.capex), 1);
  return rows.map(([k, x]) => `<div class="rg-ua"><span class="rg-ua-l">${icon(USE[k]?.icon || 'leaf', 'i-sm')}${esc(x.label)} <span class="muted">× ${x.plots}</span></span><span class="rg-ua-b"><i style="width:${(x.capex / max) * 100}%"></i></span><b class="num">${money(x.capex)}</b></div>`).join('');
}

function drawMap(ds) {
  const { map } = createMap('rgMap', { center: [39.75, 46.75], zoom: 8, scrollWheelZoom: false });
  const maxJobs = Math.max(...ds.map((d) => d.potential.directJobs), 1);
  const group = [];
  ds.forEach((d) => {
    const radius = 10 + 26 * Math.sqrt(d.potential.directJobs / maxJobs);
    const m = L.circleMarker([d.lat, d.lng], { radius, color: '#ffffff', weight: 2, fillColor: seq(d.readiness.total), fillOpacity: 0.88 }).addTo(map);
    m.bindTooltip(`${d.label} · ${d.readiness.total}`, { permanent: true, direction: 'top', offset: [0, -radius + 2], className: 'rg-tip' });
    m.bindPopup(`<b>${esc(d.label)}</b><br>${d.plots} sahə, ${fmt.ha(d.areaHa)}<br>Potensial investisiya: <b>${money(d.potentialCapex)}</b><br>Daimi iş yeri: <b>${fmt.num(d.potential.directJobs)}</b> · ailə: <b>${fmt.num(d.potential.families)}</b><br>Hazırlıq indeksi: <b>${d.readiness.total}/100</b>`);
    group.push([d.lat, d.lng]);
  });
  if (group.length > 1) map.fitBounds(group, { padding: [40, 40] });
}

init();
