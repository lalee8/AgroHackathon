import { api, renderLayout, $, esc, icon, fmt, CAT, params, compareStore, renderCompareTray, catBadge, demining, emptyState, leaseText } from '../core.js';
import { satCover } from '../components.js';

renderLayout('catalog');
const ids = (params().get('ids') || compareStore.get().map((x) => x.id).join(',')).split(',').filter(Boolean);

const ROWS = [
  ['Təyinat', (p) => catBadge(p.category)],
  ['Rayon / kənd', (p) => `${esc(p.districtLabel)}, ${esc(p.village || '')}`],
  ['Sahə', (p) => fmt.ha(p.areaHa), (p) => p.areaHa, 'max'],
  ['Torpaq keyfiyyəti (bonitet)', (p) => `${p.soilQuality}/100`, (p) => p.soilQuality, 'max'],
  ['İnfrastruktur indeksi', (p) => `${p.infrastructureScore}/100`, (p) => p.infrastructureScore, 'max'],
  ['Suvarma', (p) => (p.irrigation ? '✓ ' + esc(p.waterSource) : '✗ yoxdur'), (p) => (p.irrigation ? 1 : 0), 'max'],
  ['Elektrik / qaz', (p) => `${p.electricity ? '✓' : '✗'} / ${p.gas ? '✓' : '✗'}`, (p) => (p.electricity ? 1 : 0) + (p.gas ? 1 : 0), 'max'],
  ['Yola məsafə', (p) => `${fmt.num1(p.roadDistanceKm)} km`, (p) => p.roadDistanceKm, 'min'],
  ['Hündürlük', (p) => `${fmt.num(p.altitudeM)} m`],
  ['Meyillik', (p) => `${fmt.num1(p.slopePercent)}%`, (p) => p.slopePercent, 'min'],
  ['Minatəmizləmə', (p) => demining(p.deminingStatus)],
  ['İcarə müddəti', (p) => leaseText(p, true), (p) => p.leaseYears, 'max'],
  ['İcarə haqqı (ha/il)', (p) => `${fmt.num(p.annualRentPerHa)} ₼`, (p) => p.annualRentPerHa, 'min'],
  ['Ümumi illik icarə', (p) => fmt.money(p.annualRentTotal), (p) => p.annualRentTotal, 'min'],
  ['Minimum investisiya', (p) => fmt.money(p.minInvestment), (p) => p.minInvestment, 'min'],
  ['Minimum iş yeri', (p) => p.minJobs],
  ['Rəqabət (müraciət)', (p) => p.applicationCount, (p) => p.applicationCount, 'min'],
  ['Son müraciət tarixi', (p) => (p.competition ? fmt.date(p.competition.deadlineAt) : '-')],
  ['Tövsiyə olunan bitkilər', (p) => (p.recommendedCrops || []).map(esc).join(', ')],
];

async function load() {
  if (!ids.length) {
    $('#root').innerHTML = emptyState('Müqayisə üçün sahə seçilməyib. Kataloqda sahələrin yanındakı "Müqayisə" qutusunu işarələyin.', 'git-compare') + '<div style="text-align:center"><a class="btn btn-primary" href="/kataloq.html">Kataloqa keç</a></div>';
    return;
  }
  const plots = await api('/api/v1/plots/compare?ids=' + ids.join(','));
  $('#root').innerHTML = `<div class="card"><div class="table-wrap"><table class="table cmp-table">
    <thead><tr><th style="width:220px">Göstərici</th>${plots.map((p) => `<th style="min-width:220px;text-transform:none;letter-spacing:0">
      <div class="art-fill" style="height:90px;border-radius:10px;margin-bottom:8px">${satCover(p)}</div>
      <div style="font-size:11px;color:var(--ink-400)">${esc(p.code)}</div><a href="/sahe.html?id=${p.id}" style="font-size:14.5px;color:var(--ink-900)">${esc(p.name)}</a></th>`).join('')}</tr></thead>
    <tbody>${ROWS.map(([label, show, val, best]) => {
      let bestVal = null;
      if (val && plots.length > 1) {
        const vals = plots.map(val).map(Number);
        bestVal = best === 'max' ? Math.max(...vals) : Math.min(...vals);
        if (vals.every((v) => v === bestVal)) bestVal = null;
      }
      return `<tr><td>${label}</td>${plots.map((p) => `<td class="${bestVal !== null && Number(val(p)) === bestVal ? 'best' : ''}">${show(p)}</td>`).join('')}</tr>`;
    }).join('')}
    <tr><td></td>${plots.map((p) => `<td><div class="row-wrap"><a class="btn btn-sm btn-primary" href="/sahe.html?id=${p.id}">Ətraflı</a><button class="btn btn-sm btn-ghost" data-rm="${p.id}">${icon('x', 'i-sm')}Çıxar</button></div></td>`).join('')}</tr>
    </tbody></table></div></div>`;
  $('#root').addEventListener('click', (e) => {
    const b = e.target.closest('[data-rm]');
    if (!b) return;
    const rest = ids.filter((x) => x !== b.dataset.rm);
    compareStore.set(compareStore.get().filter((x) => String(x.id) !== b.dataset.rm));
    location.href = '/muqayise.html?ids=' + rest.join(',');
  });
}
renderCompareTray();
load().catch((e) => { $('#root').innerHTML = emptyState(e.message); });
