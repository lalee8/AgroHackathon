import { api, $, esc, icon, fmt, statusBadge, emptyState } from '../core.js';

export async function render(el, rest, ctx) {
  const comps = (await api(ctx.role === 'ADMIN' ? '/api/v1/admin/competitions' : '/api/v1/competitions')).filter((c) => ['PUBLISHED', 'CLOSED'].includes(c.status));
  const sel = rest[0] || comps[0]?.id;
  el.innerHTML = `<div class="admin-title"><div><h1>Müsabiqə reytinqi</h1><p class="muted" style="margin:0">Yekun bal = avtomatik bal × 40% + ekspertlərin orta balı × 60%. Hər sahə üzrə lider ayrıca qeyd olunur.</p></div>
    <select class="input" id="comp" style="max-width:420px">${comps.map((c) => `<option value="${c.id}" ${String(c.id) === String(sel) ? 'selected' : ''}>${esc(c.code)} - ${esc(c.title)}</option>`).join('')}</select></div>
    <div class="card"><div class="table-wrap" id="tbl"><div class="loader"></div></div></div>`;
  $('#comp').addEventListener('change', () => { location.hash = `ranking/${$('#comp').value}`; });
  if (!sel) { $('#tbl').innerHTML = emptyState('Müsabiqə yoxdur'); return; }
  const list = await api(`/api/v1/review/competitions/${sel}/ranking`);
  const leaders = new Set();
  const seen = new Set();
  list.forEach((a) => { if (!seen.has(a.plotCode)) { seen.add(a.plotCode); leaders.add(a.id); } });
  $('#tbl').innerHTML = list.length ? `<table class="table"><thead><tr><th>#</th><th>Nömrə</th><th>Müraciətçi</th><th>Sahə</th><th class="r">İnvestisiya</th><th class="r">İş yeri</th><th class="r">Avto</th><th class="r">Ekspert</th><th class="r">Yekun</th><th>Status</th></tr></thead>
    <tbody>${list.map((a, i) => `<tr class="clickable" data-id="${a.id}">
      <td><b>${i + 1}</b></td><td class="mono nowrap">${esc(a.appNumber)}</td><td>${esc(a.applicantName)}</td>
      <td><b>${esc(a.plotCode)}</b> ${leaders.has(a.id) ? `<span class="badge badge-gold">${icon('star')}Sahə üzrə lider</span>` : ''}<div class="small muted">${esc(a.plotName)}</div></td>
      <td class="r num nowrap">${fmt.compactMoney(a.investmentAmount)}</td><td class="r num">${a.jobsPlanned ?? '-'}</td>
      <td class="r num">${a.autoScore ?? '-'}</td><td class="r num">${a.expertAverage ?? '-'}</td>
      <td class="r"><div class="row" style="gap:8px;justify-content:flex-end"><div class="meter" style="width:70px"><span style="width:${a.finalScore || 0}%"></span></div><b class="num">${a.finalScore ?? '-'}</b></div></td>
      <td>${statusBadge(a.status, a.decision)}</td></tr>`).join('')}</tbody></table>` : emptyState('Bu müsabiqəyə hələ müraciət daxil olmayıb');
  $('#tbl').addEventListener('click', (e) => { const tr = e.target.closest('tr[data-id]'); if (tr) location.hash = `applications/${tr.dataset.id}`; });
}
