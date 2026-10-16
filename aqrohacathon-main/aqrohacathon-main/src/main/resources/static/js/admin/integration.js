import { api, $, on, esc, icon, fmt, modal, toast, errorToast, withLoading, emptyState } from '../core.js';

const ST = {
  SENT: ['badge-good', 'circle-check', 'Çatdırıldı'],
  PENDING: ['badge-info', 'clock', 'Növbədə'],
  FAILED: ['badge-warn', 'refresh-cw', 'Təkrar cəhd'],
  DEAD: ['badge-crit', 'circle-x', 'Uğursuz'],
};
const badge = (s) => `<span class="badge ${ST[s][0]}">${icon(ST[s][1])}${ST[s][2]}</span>`;

export async function render(el) {
  const [h, ev, inbox] = await Promise.all([
    api('/api/v1/admin/integration/health'),
    api('/api/v1/admin/integration/events?size=40'),
    api('/api/v1/admin/integration/ektis-inbox'),
  ]);
  el.innerHTML = `<div class="admin-title"><div><h1>EKTİS inteqrasiya monitorinqi</h1><p class="muted" style="margin:0">Kənd Təsərrüfatı Nazirliyinin Elektron Kənd Təsərrüfatı İnformasiya Sistemi ilə məlumat mübadiləsi</p></div>
    <div class="row-wrap"><button class="btn" id="refresh">${icon('refresh-cw')}Yenilə</button><button class="btn btn-primary" id="flush" data-loading="Göndərilir...">${icon('send')}Növbəni indi göndər</button></div></div>

  <div class="kpis">
    <div class="kpi hero-kpi"><span class="ico">${icon('network')}</span><div class="lbl">Bağlantı rejimi</div><div class="val" style="font-size:24px">${esc(h.mode)}</div><div class="sub">${h.reachable ? `Əlçatandır · ${h.latencyMs} ms` : 'Əlçatan deyil'}</div></div>
    <div class="kpi"><span class="ico" style="background:var(--st-good-bg);color:var(--st-good-ink)">${icon('circle-check')}</span><div class="lbl">Çatdırılıb</div><div class="val">${h.sent}</div><div class="sub">EKTİS qəbul etdi: ${h.mockReceived}</div></div>
    <div class="kpi"><span class="ico" style="background:var(--st-warn-bg);color:var(--st-warn-ink)">${icon('refresh-cw')}</span><div class="lbl">Növbədə / təkrar</div><div class="val">${h.pending + h.failed}</div><div class="sub">eksponensial gecikmə ilə</div></div>
    <div class="kpi"><span class="ico" style="background:var(--st-crit-bg);color:var(--st-crit-ink)">${icon('circle-x')}</span><div class="lbl">Uğursuz (DEAD)</div><div class="val">${h.dead}</div><div class="sub">${h.maxAttempts} cəhddən sonra · əl ilə təkrar</div></div>
  </div>

  <div class="card" style="margin-top:20px"><div class="card-head"><h3>${icon('workflow')}Məlumat axını arxitekturası</h3><span class="small muted mono">${esc(h.endpoint)}</span></div><div class="card-body">
    <div class="flow">
      <div class="node"><b>Portal əməliyyatı</b><span>müraciət, status, qərar</span></div><div class="arrow">${icon('arrow-right')}eyni tranzaksiya</div>
      <div class="node hl"><b>Outbox cədvəli</b><span>integration_events</span></div><div class="arrow">${icon('arrow-right')}hər 5 san</div>
      <div class="node"><b>Outbox prosessoru</b><span>retry · backoff · DEAD</span></div><div class="arrow">${icon('arrow-right')}HMAC-SHA256 + idempotency</div>
      <div class="node hl"><b>EKTİS API</b><span>${esc(h.mode === 'SİMULYATOR' ? 'simulyator (mock)' : 'real sistem')}</span></div>
    </div>
    <div class="flow" style="margin-top:6px">
      <div class="node hl"><b>EKTİS</b><span>dövlət sistemi</span></div><div class="arrow">${icon('arrow-right')}X-API-Key</div>
      <div class="node"><b>/api/v1/integration/*</b><span>GeoJSON, müraciətlər, callback</span></div><div class="arrow">${icon('arrow-right')}</div>
      <div class="node"><b>Portal</b><span>fermer statusu, istinad nömrəsi</span></div>
    </div>
    <p class="small muted" style="margin:10px 0 0;text-align:center">Simulyator retry mexanizmini nümayiş etdirmək üçün sorğuların ${Math.round(h.mockFailureRate * 100)}%-nə 503 qaytarır · etibarsız imza: ${h.mockRejectedSignatures}</p>
  </div></div>

  <div class="grid" style="grid-template-columns:minmax(0,1.6fr) minmax(0,1fr);margin-top:20px">
    <div class="card"><div class="card-head"><h3>${icon('database')}Outbox hadisələri</h3><span class="small muted">son 40</span></div>
      <div class="table-wrap">${ev.items.length ? `<table class="table"><thead><tr><th>#</th><th>Hadisə</th><th>Obyekt</th><th>Status</th><th class="r">Cəhd</th><th>EKTİS istinadı</th><th></th></tr></thead><tbody>
      ${ev.items.map((e) => `<tr><td class="small muted">${e.id}</td><td><code>${esc(e.eventType)}</code><div class="small muted">${fmt.rel(e.createdAt)}</div></td><td class="small">${esc(e.aggregateType)} #${esc(e.aggregateId)}</td>
        <td>${badge(e.status)}${e.lastError ? `<div class="small" style="color:var(--st-crit-ink);max-width:220px">${esc(e.lastError.slice(0, 80))}</div>` : ''}</td><td class="r num">${e.attempts}</td><td class="mono small">${esc(e.externalRef || '-')}</td>
        <td class="r nowrap"><button class="btn btn-sm btn-ghost" data-payload="${e.id}">${icon('eye', 'i-sm')}</button>${['FAILED', 'DEAD'].includes(e.status) ? `<button class="btn btn-sm" data-retry="${e.id}">${icon('refresh-cw', 'i-sm')}</button>` : ''}</td></tr>`).join('')}
      </tbody></table>` : emptyState('Hadisə yoxdur')}</div></div>
    <div class="stack">
      <div class="card"><div class="card-head"><h3>${icon('fingerprint')}Fermer reyestri yoxlaması</h3></div><div class="card-body">
        <form id="fc" class="row"><input class="input mono" id="tax" placeholder="VÖEN və ya FİN" value="1904567813"><button class="btn btn-primary" type="submit" data-loading="...">${icon('search', 'i-sm')}</button></form>
        <div id="fcOut" style="margin-top:12px"></div></div></div>
      <div class="card"><div class="card-head"><h3>${icon('inbox')}EKTİS tərəfində qəbul edilənlər</h3><span class="badge">${inbox.length}</span></div><div class="card-body" style="max-height:360px;overflow:auto;padding-top:6px">
        ${inbox.slice(0, 30).map((r) => `<div class="row between" style="padding:8px 0;border-bottom:1px dashed var(--line)"><div><code>${esc(r.eventType)}</code><div class="small muted">obyekt #${esc(r.aggregateId || '-')} · ${fmt.rel(r.receivedAt)}</div></div><span class="mono small">${esc(r.reference)}</span></div>`).join('') || emptyState('Hələ heç nə qəbul edilməyib')}
      </div></div>
    </div>
  </div>

  <div class="card" style="margin-top:20px"><div class="card-head"><h3>${icon('webhook')}EKTİS üçün açıq API (nümunə sorğular)</h3><a class="btn btn-sm" href="/swagger-ui.html" target="_blank" rel="noopener">OpenAPI</a></div><div class="card-body">
<pre class="code"># Bütün sahələr (GeoJSON, EPSG:4326)
curl -H "X-API-Key: ektis-demo-api-key-2026" ${esc(location.origin)}/api/v1/integration/plots

# Son dəyişən müraciətlər (artımlı sinxronizasiya)
curl -H "X-API-Key: ektis-demo-api-key-2026" "${esc(location.origin)}/api/v1/integration/applications?updatedSince=2026-01-01T00:00:00Z"

# EKTİS-dən geri-çağırış: fermer qeydiyyatı tamamlandı
curl -X POST -H "X-API-Key: ektis-demo-api-key-2026" -H "Content-Type: application/json" \\
  -d '{"applicationNumber":"AQR-2026-000003","type":"FARMER_REGISTERED","reference":"EKTIS-FRM-77812","message":"Fermer reyestrinə daxil edildi"}' \\
  ${esc(location.origin)}/api/v1/integration/callbacks</pre>
  </div></div>`;

  $('#refresh').addEventListener('click', () => render(el));
  $('#flush').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try { const r = await api('/api/v1/admin/integration/flush', { method: 'POST' }); toast(`${r.processed} hadisə emal edildi`); render(el); } catch (err) { errorToast(err); }
  }));
  on(el, 'click', '[data-retry]', async (e, b) => {
    try { await api(`/api/v1/admin/integration/events/${b.dataset.retry}/retry`, { method: 'POST' }); toast('Yenidən növbəyə qoyuldu'); render(el); } catch (err) { errorToast(err); }
  });
  on(el, 'click', '[data-payload]', (e, b) => {
    const item = ev.items.find((x) => String(x.id) === b.dataset.payload);
    let pretty = item.payload;
    try { pretty = JSON.stringify(JSON.parse(item.payload), null, 2); } catch { /* raw */ }
    modal({ title: `${item.eventType} - ${item.eventId}`, wide: true, body: `<pre class="code">${esc(pretty)}</pre><p class="small muted">Başlıqlar: X-Idempotency-Key, X-Event-Type, X-Timestamp, X-Signature: sha256=HMAC(timestamp + "." + body)</p>` });
  });
  $('#fc').addEventListener('submit', (e) => {
    e.preventDefault();
    withLoading(e.submitter, async () => {
      try {
        const r = await api(`/api/v1/admin/integration/farmer/${encodeURIComponent($('#tax').value.trim())}`);
        $('#fcOut').innerHTML = `<div class="alert ${r.status === 'REGISTERED' ? 'alert-good' : r.available ? 'alert-warn' : 'alert-crit'}">${icon(r.status === 'REGISTERED' ? 'circle-check' : 'info')}<div><b>${esc(r.status)}</b>${r.farmerId ? ` · <span class="mono">${esc(r.farmerId)}</span>` : ''}<div class="small">${esc(r.message || '')}</div>${r.registeredLandHa ? `<div class="small">Reyestrdə torpaq: ${r.registeredLandHa} ha · alınmış subsidiyalar: ${r.subsidiesReceived}</div>` : ''}</div></div>`;
      } catch (err) { errorToast(err); }
    });
  });
}
