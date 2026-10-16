import { api, renderLayout, $, esc, icon, fmt, params, statusBadge, withLoading } from '../core.js';
import { statusTrack, timeline } from '../status.js';

renderLayout('track');
const p = params();
if (p.get('number')) $('#number').value = p.get('number');
if (p.get('code')) $('#code').value = p.get('code');

async function check() {
  const out = $('#out');
  try {
    const t = await api(`/api/v1/track?number=${encodeURIComponent($('#number').value.trim())}&code=${encodeURIComponent($('#code').value.trim())}`);
    out.innerHTML = `<div class="card">
      <div class="card-head"><h3>${icon('file-check')}<span class="mono">${esc(t.appNumber)}</span></h3>${statusBadge(t.status, t.decision)}</div>
      <div class="card-body">
        ${statusTrack(t.status, t.decision)}
        <div class="kv" style="margin-top:18px">
          <div>Müraciətçi</div><div>${esc(t.applicant)}</div>
          <div>Sahə</div><div>${esc(t.plot)}</div>
          <div>Müsabiqə</div><div>${esc(t.competition)}</div>
          <div>Göndərilib</div><div>${fmt.dateTime(t.submittedAt)}</div>
          <div>Son yenilənmə</div><div>${fmt.dateTime(t.updatedAt)}</div>
          ${t.decisionLabel ? `<div>Qərar</div><div><b>${esc(t.decisionLabel)}</b></div>` : ''}
        </div>
        <h4 style="margin-top:22px">Tarixçə</h4>
        ${timeline(t.history)}
        <p class="small muted" style="margin-top:16px">${icon('lock', 'i-sm')} Ətraflı məlumat və sənədlər üçün <a href="/giris.html">şəxsi kabinetə daxil olun</a>.</p>
      </div></div>`;
  } catch (e) {
    out.innerHTML = `<div class="alert alert-crit">${icon('circle-x')}<div>${esc(e.message)}</div></div>`;
  }
}
$('#form').addEventListener('submit', (e) => { e.preventDefault(); withLoading(e.submitter, check); });
if (p.get('number') && p.get('code')) check();
