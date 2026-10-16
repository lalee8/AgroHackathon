import { api, esc, icon, fmt, toast, withLoading, emptyState } from '../core.js';

export async function render(el) {
  el.innerHTML = '<div class="loader"></div>';
  const rows = await api('/api/v1/projects?sort=new');
  el.innerHTML = `<div class="admin-title"><div><h1>Sahibkar layihələri</h1><p class="muted" style="margin:0;max-width:820px">Sahibkarların investor axtardığı layihələr. Reallıq yoxlaması avtomatikdir. Nazirlik sənədlərlə tanış olduqdan sonra layihəni "yoxlanılıb" kimi işarələyə bilər: investorlar bu nişanı görür.</p></div></div>
    <div class="kpis">
      <div class="kpi"><span class="ico">${icon('handshake')}</span><div class="lbl">Dərc edilmiş layihə</div><div class="val">${rows.length}</div></div>
      <div class="kpi"><span class="ico">${icon('coins')}</span><div class="lbl">İnvestorlardan tələb</div><div class="val">${fmt.compactMoney(rows.reduce((a, r) => a + Number(r.askAmount), 0))}</div></div>
      <div class="kpi"><span class="ico">${icon('users')}</span><div class="lbl">Planlaşdırılan iş yeri</div><div class="val">${fmt.num(rows.reduce((a, r) => a + r.jobs, 0))}</div></div>
      <div class="kpi"><span class="ico">${icon('triangle-alert')}</span><div class="lbl">Reallıq yoxlaması 60-dan aşağı</div><div class="val">${rows.filter((r) => r.credibility < 60).length}</div></div>
    </div>
    <div class="card" style="margin-top:20px"><div class="table-wrap">${rows.length ? `<table class="table"><thead><tr><th>Layihə</th><th>İstiqamət</th><th class="r">Tələb</th><th>Təklif</th><th class="r">Reallıq</th><th class="r">Maraq</th><th></th></tr></thead><tbody>
      ${rows.map((r) => `<tr><td style="min-width:240px"><a href="/layihe.html?id=${r.id}" target="_blank" rel="noopener"><b>${esc(r.title)}</b></a><div class="small muted">${esc(r.code)} · ${esc(r.ownerName)} · ${esc(r.stageLabel)}</div></td>
        <td>${esc(r.sectorLabel)}<div class="small muted">${r.plot ? esc(r.plot.code) : esc(r.districtLabel || '')}</div></td>
        <td class="r num">${fmt.compactMoney(r.askAmount)}</td><td>${esc(r.dealLabel)}</td>
        <td class="r"><b class="num">${r.credibility}</b>/100<div class="small muted">${esc(r.credibilityLabel)}</div></td>
        <td class="r num">${r.interestCount}</td>
        <td class="r">${r.verified ? `<button class="btn btn-sm" data-v="${r.id}" data-to="false">${icon('badge-check', 'i-sm')}Yoxlanılıb</button>` : `<button class="btn btn-primary btn-sm" data-v="${r.id}" data-to="true">${icon('check', 'i-sm')}Yoxlanıldı kimi işarələ</button>`}</td></tr>`).join('')}
    </tbody></table>` : emptyState('Layihə yoxdur')}</div></div>`;
  el.querySelectorAll('[data-v]').forEach((b) => b.addEventListener('click', () => withLoading(b, async () => {
    try { await api(`/api/v1/admin/projects/${b.dataset.v}/verify?verified=${b.dataset.to}`, { method: 'POST' }); toast('Yeniləndi'); render(el); } catch (e) { toast(e.message, 'err'); }
  })));
}
