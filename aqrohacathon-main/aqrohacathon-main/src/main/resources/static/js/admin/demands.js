import { api, esc, icon, fmt, CAT, toast, withLoading, emptyState } from '../core.js';

export async function render(el) {
  el.innerHTML = '<div class="loader"></div>';
  const rows = await api('/api/v1/admin/demands');
  const pending = rows.filter((r) => r.status === 'PENDING');
  el.innerHTML = `<div class="admin-title"><div><h1>Alıcı tələbləri</h1><p class="muted" style="margin:0;max-width:820px">Emal müəssisələri, ixracatçılar və şəbəkələr məhsul tələbi yerləşdirir. Nazirlik alıcının real ehtiyacını və ödəmə qabiliyyətini yoxlayır, sonra tələb fermerlərə "təsdiqlənmiş tələb" kimi göstərilir. Bu zəmanət deyil, yoxlanılmış ehtiyacdır.</p></div></div>
    <div class="kpis">
      <div class="kpi"><span class="ico" style="background:var(--st-warn-bg);color:var(--st-warn-ink)">${icon('hourglass')}</span><div class="lbl">Yoxlama gözləyir</div><div class="val">${pending.length}</div></div>
      <div class="kpi"><span class="ico">${icon('badge-check')}</span><div class="lbl">Təsdiqlənmiş</div><div class="val">${rows.filter((r) => r.status === 'VERIFIED').length}</div></div>
      <div class="kpi"><span class="ico">${icon('truck')}</span><div class="lbl">Təsdiqlənmiş illik həcm</div><div class="val">${fmt.num(rows.filter((r) => r.status === 'VERIFIED').reduce((a, r) => a + Number(r.volumeTons), 0))} t</div></div>
      <div class="kpi"><span class="ico">${icon('file-check')}</span><div class="lbl">Niyyət məktubu ilə bağlanıb</div><div class="val">${fmt.num(rows.reduce((a, r) => a + Number(r.contractedTons), 0))} t</div></div>
    </div>
    <div class="card" style="margin-top:20px"><div class="table-wrap">${rows.length ? `<table class="table"><thead><tr><th>Tələb</th><th>Alıcı</th><th class="r">Həcm</th><th class="r">Qiymət</th><th>Qəbul</th><th>Status</th><th></th></tr></thead><tbody>
      ${rows.map((r) => `<tr><td><b>${esc(r.productLabel)}</b><div class="small muted">${esc(CAT[r.category].label)} · ${esc(r.code)}</div></td>
        <td>${esc(r.company)}<div class="small muted">${esc(r.buyerTypeLabel)}</div></td>
        <td class="r num">${fmt.num(r.volumeTons)} t<div class="small muted">${r.contractYears} il</div></td>
        <td class="r num">${r.priceMin ? `${fmt.num(r.priceMin)}-${fmt.num(r.priceMax)}` : '-'} ₼/t</td>
        <td>${esc(r.districtLabel)}, ${r.radiusKm} km</td>
        <td><span class="badge ${r.status === 'VERIFIED' ? 'badge-good' : r.status === 'PENDING' ? 'badge-warn' : r.status === 'REJECTED' ? 'badge-crit' : ''}">${esc(r.statusLabel)}</span></td>
        <td class="r">${r.status === 'PENDING' ? `<div class="row" style="gap:6px;justify-content:flex-end"><button class="btn btn-primary btn-sm" data-ok="${r.id}">${icon('check', 'i-sm')}Təsdiqlə</button><button class="btn btn-sm" data-no="${r.id}">${icon('x', 'i-sm')}Rədd et</button></div>` : ''}</td></tr>`).join('')}
    </tbody></table>` : emptyState('Tələb yoxdur')}</div></div>`;
  el.querySelectorAll('[data-ok],[data-no]').forEach((b) => b.addEventListener('click', () => withLoading(b, async () => {
    try {
      await api(`/api/v1/admin/demands/${b.dataset.ok || b.dataset.no}/verify?ok=${!!b.dataset.ok}`, { method: 'POST' });
      toast(b.dataset.ok ? 'Tələb təsdiqləndi' : 'Tələb rədd edildi');
      render(el);
    } catch (e) { toast(e.message, 'err'); }
  })));
}
