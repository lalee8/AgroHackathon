import { api, esc, icon, fmt, CAT, statusBadge } from '../core.js';
import { baseOptions, barDataset, chart, destroyAll, } from './charts.js';

export async function render(el) {
  destroyAll();
  const s = await api('/api/v1/admin/stats');
  const integ = s.integration || {};
  const maxStatus = Math.max(1, ...s.byStatus.map((x) => x.count));
  const districts = [...s.byDistrict].sort((a, b) => b.applications - a.applications || b.plots - a.plots);
  el.innerHTML = `
  <div class="admin-title"><div><h1>Analitika paneli</h1><p class="muted" style="margin:0">Müsabiqələr, müraciətlər və inteqrasiya üzrə real vaxt göstəriciləri</p></div>
    <div class="row-wrap"><a class="btn" href="/api/v1/admin/applications/export.csv" id="exportBtn">${icon('download')}Hesabat (CSV)</a><a class="btn btn-primary" href="#applications">${icon('inbox')}Müraciətlərə keç</a></div></div>

  <div class="kpis kpis-dash">
    <div class="kpi hero-kpi"><span class="ico">${icon('banknote')}</span><div class="lbl">Təklif olunan investisiya</div><div class="val">${fmt.compactMoney(s.proposedInvestment)}</div><div class="sub">Təsdiqlənmiş: ${fmt.compactMoney(s.approvedInvestment)} · ${fmt.num(s.approvedJobs)} iş yeri</div></div>
    <div class="kpi"><span class="ico">${icon('inbox')}</span><div class="lbl">Müraciətlər</div><div class="val">${fmt.num(s.applications)}</div><div class="sub">${s.investors} qeydiyyatlı investor</div></div>
    <div class="kpi"><span class="ico">${icon('users')}</span><div class="lbl">Planlaşdırılan iş yerləri</div><div class="val">${fmt.num(s.plannedJobs)}</div><div class="sub">bütün aktiv müraciətlər üzrə</div></div>
    <div class="kpi"><span class="ico">${icon('clock')}</span><div class="lbl">Orta qərar müddəti</div><div class="val">${s.avgDecisionDays ?? '-'} <span style="font-size:15px;font-weight:600">gün</span></div><div class="sub">göndərilmədən qərara qədər</div></div>
  </div>

  <div class="kpis" style="margin-top:16px">
    <div class="kpi"><div class="lbl">${icon('map', 'i-sm')}Sahələr</div><div class="val">${s.plots}</div><div class="sub">${fmt.ha(s.totalAreaHa)} · ${s.districts} rayon</div></div>
    <div class="kpi"><div class="lbl">${icon('circle-check', 'i-sm')}Müsabiqədə</div><div class="val">${s.plotsInCompetition}</div><div class="sub">${s.plotsAvailable} sahə müsabiqəyə hazır</div></div>
    <div class="kpi"><div class="lbl">${icon('award', 'i-sm')}İcarəyə verilib</div><div class="val">${s.plotsAwarded}</div><div class="sub">qalib müəyyən edilib</div></div>
    <div class="kpi"><div class="lbl">${icon('clipboard-check', 'i-sm')}Təsdiq gözləyən müsabiqə</div><div class="val">${s.pendingCompetitionApprovals}</div><div class="sub"><a href="#competitions">Müsabiqələrə bax →</a></div></div>
  </div>

  <div class="grid dash-2a" style="margin-top:20px">
    <div class="card"><div class="card-head"><h3>${icon('activity')}Son 30 gündə daxil olan müraciətlər</h3><span class="small muted">gündəlik</span></div><div class="card-body"><div class="chart-box sm"><canvas id="cTimeline" aria-label="Gündəlik müraciət sayı"></canvas></div></div></div>
    <div class="card"><div class="card-head"><h3>${icon('list-checks')}Statuslar üzrə bölgü</h3></div><div class="card-body">
      <div class="bar-list">${s.byStatus.map((x) => `<div class="bl" style="grid-template-columns:minmax(0,1fr) 110px 40px"><span>${statusBadge(x.code)}</span><div class="track"><span style="width:${(x.count / maxStatus) * 100}%;background:#2a78d6"></span></div><span class="v">${x.count}</span></div>`).join('')}</div>
    </div></div>
  </div>

  <div class="grid grid-2" style="margin-top:20px">
    <div class="card"><div class="card-head"><h3>${icon('map-pin')}Rayonlar üzrə müraciətlər</h3></div><div class="card-body"><div class="chart-box"><canvas id="cDistrictApps"></canvas></div></div></div>
    <div class="card"><div class="card-head"><h3>${icon('coins')}Rayonlar üzrə təklif olunan investisiya</h3></div><div class="card-body"><div class="chart-box"><canvas id="cDistrictInv"></canvas></div></div></div>
  </div>

  <div class="grid dash-2b" style="margin-top:20px">
    <div class="card"><div class="card-head"><h3>${icon('sprout')}Fəaliyyət istiqamətləri üzrə sahələr</h3><span class="small muted">sahə sayı</span></div><div class="card-body"><div class="chart-box sm"><canvas id="cCats"></canvas></div>
      <div class="table-wrap" style="margin-top:12px"><table class="table"><thead><tr><th>İstiqamət</th><th class="r">Sahə</th><th class="r">Ərazi</th><th class="r">Müraciət</th><th class="r">İnvestisiya</th></tr></thead><tbody>
      ${s.byCategory.map((c) => `<tr><td><span class="cat-dot" style="background:${CAT[c.code].hex}"></span> ${esc(c.label)}</td><td class="r num">${c.plots}</td><td class="r num">${fmt.ha(c.areaHa)}</td><td class="r num">${c.applications}</td><td class="r num">${fmt.compactMoney(c.investment)}</td></tr>`).join('')}
      </tbody></table></div></div></div>
    <div class="stack">
      <div class="card"><div class="card-head"><h3>${icon('network')}EKTİS inteqrasiyası</h3><a class="btn btn-sm" href="#integration">Monitorinq</a></div><div class="card-body">
        <div class="facts facts-4">
          <div class="fact"><div class="k">Göndərilib</div><div class="v" style="color:var(--st-good-ink)">${integ.SENT ?? 0}</div></div>
          <div class="fact"><div class="k">Növbədə</div><div class="v">${integ.PENDING ?? 0}</div></div>
          <div class="fact"><div class="k">Təkrar</div><div class="v" style="color:var(--st-warn-ink)">${integ.FAILED ?? 0}</div></div>
          <div class="fact"><div class="k">Uğursuz</div><div class="v" style="color:var(--st-crit-ink)">${integ.DEAD ?? 0}</div></div>
        </div></div></div>
      <div class="card"><div class="card-head"><h3>${icon('history')}Son fəaliyyət</h3></div><div class="card-body" style="padding-top:8px">
        ${(s.recentActivity || []).slice(0, 8).map((r) => `<a href="#applications/${r.applicationId}" class="row between" style="padding:9px 0;border-bottom:1px dashed var(--line);color:var(--ink-900);flex-wrap:wrap;gap:6px 10px"><div style="min-width:0"><b class="mono small">${esc(r.appNumber || '-')}</b> <span class="small muted">· ${esc(r.actor || '')}</span></div><div class="row" style="gap:8px">${statusBadge(r.toStatus)}<span class="small muted nowrap">${fmt.rel(r.createdAt)}</span></div></a>`).join('')}
      </div></div>
    </div>
  </div>`;

  el.querySelector('#exportBtn').addEventListener('click', async (e) => {
    e.preventDefault();
    const { downloadFile } = await import('../core.js');
    downloadFile('/api/v1/admin/applications/export.csv', 'muracietler.csv');
  });

  const tl = s.timeline;
  chart(el.querySelector('#cTimeline'), {
    type: 'bar',
    data: { labels: tl.map((d) => fmt.date(d.date).slice(0, 6)), datasets: [barDataset('Müraciət', tl.map((d) => d.count))] },
    options: { ...baseOptions(), scales: { ...baseOptions().scales, x: { ...baseOptions().scales.x, ticks: { color: '#5a6860', maxTicksLimit: 10, autoSkip: true, maxRotation: 0 } }, y: { ...baseOptions().scales.y, ticks: { color: '#5a6860', precision: 0, maxTicksLimit: 5 } } } },
  });
  chart(el.querySelector('#cDistrictApps'), {
    type: 'bar',
    data: { labels: districts.map((d) => d.label), datasets: [barDataset('Müraciət', districts.map((d) => d.applications))] },
    options: baseOptions({ horizontal: true }),
  });
  const byInv = [...s.byDistrict].sort((a, b) => b.investment - a.investment);
  chart(el.querySelector('#cDistrictInv'), {
    type: 'bar',
    data: { labels: byInv.map((d) => d.label), datasets: [barDataset('İnvestisiya', byInv.map((d) => d.investment))] },
    options: baseOptions({ horizontal: true, money: true }),
  });
  chart(el.querySelector('#cCats'), {
    type: 'bar',
    data: { labels: s.byCategory.map((c) => c.label), datasets: [{ ...barDataset('Sahə', s.byCategory.map((c) => c.plots)), backgroundColor: s.byCategory.map((c) => CAT[c.code].hex) }] },
    options: baseOptions(),
  });
}
