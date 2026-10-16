// "Vəd və reallıq": qalib müraciətin öhdəlikləri ilə icranın müqayisəsi (EKTİS aylıq hesabatları + peyk NDVI)
import { api, esc, icon, fmt } from './core.js';

const ST = {
  OK: ['badge-good', 'circle-check', 'Uyğundur'],
  LAG: ['badge-warn', 'triangle-alert', 'Geri qalır'],
  ALERT: ['badge-crit', 'circle-x', 'Diqqət'],
  PENDING: ['badge-info', 'hourglass', 'Gözlənilir'],
  NA: ['', 'minus', 'Tətbiq olunmur'],
};
const VERDICT = { ON_TRACK: 'OK', LAG: 'LAG', ALERT: 'ALERT', STARTING: 'PENDING' };

const badge = (s, label) => {
  const [cls, ic, def] = ST[s] || ST.PENDING;
  return `<span class="badge ${cls}">${icon(ic, 'i-sm')}${esc(label || def)}</span>`;
};

function bar(label, reported, expected, promised, money) {
  const f = (v) => (money ? fmt.compactMoney(v) : fmt.num(v));
  const pct = promised ? Math.min(100, (reported / promised) * 100) : 0;
  const mark = promised ? Math.min(100, (expected / promised) * 100) : 0;
  return `<div class="dl-bar">
    <div class="row between"><b>${esc(label)}</b><span class="small"><b>${f(reported)}</b> <span class="muted">/ ${f(promised)} vəd</span></span></div>
    <div class="dl-track" role="img" aria-label="${esc(label)}: ${f(reported)} reallaşıb, plan üzrə bu günə ${f(expected)}, vəd ${f(promised)}">
      <i style="width:${pct}%"></i><span class="dl-mark" style="left:${mark}%" title="Plan üzrə bu günə: ${f(expected)}"></span>
    </div>
    <div class="small muted">Plan üzrə bu günə: ${f(expected)} · reallaşan ${promised ? Math.round((reported / promised) * 100) : 0}%</div>
  </div>`;
}

export async function renderDelivery(el, plotId) {
  let d;
  try {
    d = await api(`/api/v1/impact/delivery/${plotId}`);
  } catch {
    el.remove();
    return null;
  }
  const v = VERDICT[d.verdict] || 'PENDING';
  const ndvi = d.ndviBefore != null && d.ndviNow != null;
  el.innerHTML = `
    <div class="row between" style="flex-wrap:wrap;gap:12px">
      <div><span class="overline" style="margin:0">Vəd və reallıq</span><h3 style="margin:2px 0 4px">Qalib nə vəd etdi, nə reallaşdı</h3>
        <div class="small muted">${esc(d.winner)} · ${esc(d.appNumber || '')} · Müqavilə: ${fmt.date(d.contractOn)} · İcra: ${d.monthsElapsed} / ${d.promised.months} ay</div></div>
      <div>${badge(v, d.verdictLabel)}</div>
    </div>
    <div class="dl-grid">
      <div>
        ${bar('İnvestisiya', d.reportedInvestment, d.expectedInvestment, d.promised.investment, true)}
        ${bar('Daimi iş yerləri', d.reportedJobs, d.expectedJobs, d.promised.jobs, false)}
        <div class="dl-ndvi">
          <div class="small" style="font-weight:650">${icon('satellite', 'i-sm')} Peykdən bitki örtüyü (NDVI)</div>
          ${ndvi ? `<div class="dl-ndvi-row"><div><div class="k">${d.ndviBeforeYear}, qərardan əvvəl</div><div class="v">${d.ndviBefore.toFixed(2)}</div></div>${icon('arrow-right')}<div><div class="k">${d.ndviNowYear}</div><div class="v">${d.ndviNow.toFixed(2)}</div></div></div>`
    : '<div class="small muted">İlk vegetasiya mövsümü gözlənilir</div>'}
        </div>
      </div>
      <div>
        <div class="chart-legend"><span><i class="line" style="background:#9aa5a0"></i>Plan (vəd əsasında)</span><span><i class="line" style="background:#2a78d6"></i>Reallaşan (aylıq hesabat)</span></div>
        <div class="chart-box" style="height:230px"><canvas id="dlChart" aria-label="Məcmu investisiya: plan və reallaşan"></canvas></div>
      </div>
    </div>
    <div class="table-wrap" style="margin-top:14px"><table class="table dl-table"><thead><tr><th>Öhdəlik</th><th>Vəd / plan</th><th>Reallıq</th><th>Vəziyyət</th></tr></thead>
      <tbody>${d.checks.map((c) => `<tr><td><b>${esc(c.label)}</b><div class="small muted">${esc(c.note)}</div></td><td>${esc(c.promised)}</td><td>${esc(c.actual)}</td><td>${badge(c.status)}</td></tr>`).join('')}</tbody></table></div>
    <div class="alert ${v === 'OK' ? 'alert-good' : v === 'ALERT' ? 'alert-crit' : v === 'LAG' ? 'alert-warn' : 'alert-info'}" style="margin-top:12px">${icon('info')}<div>${esc(d.note)}</div></div>
    <p class="small muted" style="margin-top:8px">Vəd edilən texnologiyalar: ${d.promised.technologies.map(esc).join(', ') || '-'}. ${esc(d.method)}</p>`;

  const Chart = window.Chart;
  if (Chart) {
    const rows = d.months;
    new Chart(el.querySelector('#dlChart'), {
      type: 'line',
      data: {
        labels: rows.map((r) => r.month),
        datasets: [
          { label: 'Plan', data: rows.map((r) => r.plannedInvestment), borderColor: '#9aa5a0', borderDash: [5, 4], borderWidth: 2, pointRadius: 0, tension: 0.25 },
          { label: 'Reallaşan', data: rows.map((r) => r.reportedInvestment), borderColor: '#2a78d6', backgroundColor: '#2a78d6', borderWidth: 2, pointRadius: 4, pointBorderColor: '#fff', pointBorderWidth: 2, tension: 0.25, spanGaps: false },
        ],
      },
      options: {
        responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: false }, tooltip: { callbacks: { label: (ctx) => (ctx.parsed.y == null ? null : `${ctx.dataset.label}: ${fmt.money(ctx.parsed.y)}`) } } },
        scales: {
          y: { beginAtZero: true, grid: { color: '#eef0ec' }, border: { display: false }, ticks: { color: '#5a6860', callback: (x) => fmt.compactMoney(x) } },
          x: { grid: { display: false }, ticks: { color: '#5a6860', maxRotation: 0, autoSkip: true, maxTicksLimit: 8 } },
        },
      },
    });
  }
  return d;
}

/** Bütün icarəyə verilmiş sahələr üzrə qısa cədvəl (Böyük Qayıdış paneli). */
export async function renderDeliveryTable(el) {
  let list;
  try { list = await api('/api/v1/impact/delivery'); } catch { el.remove(); return; }
  if (!list.length) { el.remove(); return; }
  const totInv = list.reduce((s, x) => s + x.promisedInvestment, 0), repInv = list.reduce((s, x) => s + x.reportedInvestment, 0);
  const totJobs = list.reduce((s, x) => s + x.promisedJobs, 0), repJobs = list.reduce((s, x) => s + x.reportedJobs, 0);
  el.innerHTML = `
    <span class="overline" style="margin:0">Vəd və reallıq</span>
    <h3 style="margin:2px 0 6px">Qaliblər vədlərini necə yerinə yetirir</h3>
    <p class="small muted" style="margin:0 0 12px">İcarəyə verilmiş sahələr üzrə: vəd edilən investisiya və iş yerləri, EKTİS aylıq hesabatları və peykdən görünən fəaliyyət.</p>
    <div class="row-wrap" style="gap:18px;margin-bottom:12px">
      <div><div class="small muted">Vəd edilən investisiya</div><b style="font-size:1.25rem">${fmt.compactMoney(totInv)}</b></div>
      <div><div class="small muted">Reallaşıb</div><b style="font-size:1.25rem">${fmt.compactMoney(repInv)}</b> <span class="small muted">(${totInv ? Math.round((repInv / totInv) * 100) : 0}%)</span></div>
      <div><div class="small muted">İş yerləri</div><b style="font-size:1.25rem">${fmt.num(repJobs)}</b> <span class="small muted">/ ${fmt.num(totJobs)} vəd</span></div>
    </div>
    <div class="table-wrap"><table class="table"><thead><tr><th>Sahə</th><th>Qalib</th><th class="r">İnvestisiya</th><th class="r">İş yerləri</th><th class="r">NDVI</th><th>Vəziyyət</th></tr></thead>
    <tbody>${list.map((x) => `<tr><td><a href="/sahe.html?id=${x.plotId}#delivery"><b>${esc(x.plotCode)}</b></a><div class="small muted">${esc(x.district)} · ${x.monthsElapsed}/${x.months} ay</div></td><td>${esc(x.winner)}</td>
      <td class="r num">${fmt.compactMoney(x.reportedInvestment)}<div class="small muted">/ ${fmt.compactMoney(x.promisedInvestment)}</div></td>
      <td class="r num">${x.reportedJobs}<div class="small muted">/ ${x.promisedJobs}</div></td>
      <td class="r num">${x.ndviBefore != null && x.ndviNow != null ? `${x.ndviBefore.toFixed(2)} → ${x.ndviNow.toFixed(2)}` : '-'}</td>
      <td>${badge(VERDICT[x.verdict] || 'PENDING', x.verdictLabel)}</td></tr>`).join('')}</tbody></table></div>`;
}
