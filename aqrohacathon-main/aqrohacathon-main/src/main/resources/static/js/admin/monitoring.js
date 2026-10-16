import { api, $, esc, icon, catBadge, plotStatusBadge, emptyState } from '../core.js';
import { sparkline } from '../timemachine.js';

const ST = {
  ALERT: ['badge-crit', 'triangle-alert'],
  ACTIVE: ['badge-good', 'circle-check'],
  BASELINE: ['badge-info', 'circle-dot'],
  NOT_APPLICABLE: ['', 'leaf'],
  NO_DATA: ['', 'circle-help'],
};

export async function render(el) {
  el.innerHTML = '<div class="loader"></div>';
  const rows = await api('/api/v1/admin/monitoring');
  const count = (s) => rows.filter((r) => r.status === s).length;
  el.innerHTML = `<div class="admin-title"><div><h1>Peykdən nəzarət</h1><p class="muted" style="margin:0;max-width:820px">Nazirlik icarəyə verilmiş torpağın təyinatı üzrə istifadəsinə nəzarət etməlidir. Bu bölmə hər sahə üçün Landsat peyk arxivindən illik bitki örtüyü indeksini (NDVI) göstərir. Fəaliyyət əlaməti zəif olan icarə sahələri avtomatik qeyd olunur və mütəxəssisi yerə göndərməzdən əvvəl ilkin yoxlama kimi istifadə edilir.</p></div></div>
    <div class="kpis">
      <div class="kpi"><span class="ico" style="background:var(--st-crit-bg);color:var(--st-crit-ink)">${icon('triangle-alert')}</span><div class="lbl">Diqqət tələb edir</div><div class="val">${count('ALERT')}</div><div class="sub">icarə sahəsində fəaliyyət zəifdir</div></div>
      <div class="kpi"><span class="ico" style="background:var(--st-good-bg);color:var(--st-good-ink)">${icon('circle-check')}</span><div class="lbl">Becərilir</div><div class="val">${count('ACTIVE')}</div><div class="sub">icarəyə verilmiş sahələr</div></div>
      <div class="kpi"><span class="ico">${icon('circle-dot')}</span><div class="lbl">İlkin vəziyyət (baza)</div><div class="val">${count('BASELINE')}</div><div class="sub">müsabiqədə və ya hazır sahələr</div></div>
      <div class="kpi"><span class="ico">${icon('satellite')}</span><div class="lbl">İzlənilən sahələr</div><div class="val">${rows.length}</div><div class="sub">Landsat 1990-2026</div></div>
    </div>
    <div class="card" style="margin-top:20px"><div class="table-wrap">${rows.length ? `<table class="table"><thead><tr><th>Sahə</th><th>Təyinat və status</th><th>NDVI 1990 → bu gün</th><th class="r">Son il</th><th class="r">Əvvəlki il</th><th>Peyk qiymətləndirməsi</th><th></th></tr></thead><tbody>
      ${rows.map((r) => {
        const st = ST[r.status] || ['', 'circle-dot'];
        return `<tr><td style="min-width:210px"><b class="mono">${esc(r.code)}</b><div class="small muted">${esc(r.name)} · ${esc(r.districtLabel)}</div></td>
          <td><div style="display:flex;flex-direction:column;gap:6px;align-items:flex-start">${catBadge(r.category)}${plotStatusBadge(r.plotStatus)}</div></td>
          <td>${sparkline(r.points)}</td>
          <td class="r num"><b>${r.latest != null ? r.latest.toFixed(2) : '-'}</b><div class="small muted">${r.latestYear ?? ''}</div></td>
          <td class="r num">${r.previous != null ? r.previous.toFixed(2) : '-'}</td>
          <td><span class="badge ${st[0]}">${icon(st[1])}${esc(r.statusLabel)}</span><div class="small muted" style="max-width:260px;margin-top:4px">${esc(r.note)}</div></td>
          <td class="r"><a class="btn btn-sm" href="/sahe.html?id=${r.plotId}#tm" target="_blank" rel="noopener" title="Zaman maşını" aria-label="Zaman maşını">${icon('satellite', 'i-sm')}</a></td></tr>`;
      }).join('')}</tbody></table>` : emptyState('Məlumat yoxdur')}</div>
      <div class="card-foot small muted">${icon('info', 'i-sm')} NDVI: 0.2-dən aşağı - çılpaq və ya boş torpaq; 0.2-0.4 - seyrək; 0.4-dən yuxarı - sıx bitki örtüyü. Boz zolaq - işğal dövrü (1993-2020). Otlaq və arıçılıq sahələrində NDVI təbii örtüyü göstərdiyi üçün fəaliyyət meyarı kimi istifadə olunmur. Peyk qiymətləndirməsi yerində yoxlamanı əvəz etmir, onu prioritetləşdirir.</div></div>`;
}
