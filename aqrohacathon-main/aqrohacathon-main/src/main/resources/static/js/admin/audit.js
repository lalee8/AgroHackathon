import { api, $, esc, icon, fmt, emptyState } from '../core.js';

export async function render(el, rest) {
  const page = Number(rest[0] || 0);
  const d = await api(`/api/v1/admin/audit?page=${page}&size=50`);
  el.innerHTML = `<div class="admin-title"><div><h1>Audit jurnalı</h1><p class="muted" style="margin:0">Bütün kritik əməliyyatlar dəyişməz şəkildə qeydə alınır: kim, nə vaxt, hansı obyekt, hansı IP ünvanından.</p></div><span class="badge">${d.total} qeyd</span></div>
    <div class="card"><div class="table-wrap">${d.items.length ? `<table class="table"><thead><tr><th>Vaxt</th><th>İstifadəçi</th><th>Əməliyyat</th><th>Obyekt</th><th>Təfərrüat</th><th>IP</th></tr></thead><tbody>
      ${d.items.map((x) => `<tr><td class="nowrap small">${fmt.dateTime(x.createdAt)}</td><td class="small">${esc(x.actorEmail || 'sistem')}</td><td><code>${esc(x.action)}</code></td>
        <td class="small">${esc(x.entityType || '')}${x.entityId ? ' #' + esc(x.entityId) : ''}</td><td class="small" style="max-width:380px">${esc(x.details || '')}</td><td class="mono small">${esc(x.ipAddress || '')}</td></tr>`).join('')}
      </tbody></table>` : emptyState('Qeyd yoxdur')}</div>
      <div class="card-foot row between"><span class="small muted">Səhifə ${page + 1} / ${Math.max(1, d.pages)}</span><div class="row">
        ${page > 0 ? `<a class="btn btn-sm" href="#audit/${page - 1}">${icon('arrow-left', 'i-sm')}Əvvəlki</a>` : ''}
        ${page + 1 < d.pages ? `<a class="btn btn-sm" href="#audit/${page + 1}">Növbəti${icon('arrow-right', 'i-sm')}</a>` : ''}</div></div></div>`;
}
