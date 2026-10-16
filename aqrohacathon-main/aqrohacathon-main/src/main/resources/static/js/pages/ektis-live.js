// EKTİS simulyatorunun qəbul ekranı: portaldan gələn imzalı hadisələr real vaxtda (SSE) görünür.
import { api, requireAuth, startLive, onLive, $, esc, fmt } from '../core.js';

if (!requireAuth(['ADMIN'])) throw new Error('auth');
let accepted = 0;
let rejected = 0;

function highlight(obj) {
  const json = esc(JSON.stringify(obj, null, 2));
  return json
    .replace(/(&quot;[^&]*?&quot;)(\s*:)/g, '<span class="k">$1</span>$2')
    .replace(/:\s(&quot;.*?&quot;)/g, ': <span class="s">$1</span>')
    .replace(/:\s(-?\d+(\.\d+)?)/g, ': <span class="n">$1</span>')
    .replace(/:\s(true|false|null)/g, ': <span class="b">$1</span>');
}

function card(r, isNew) {
  const kind = /SUBMITTED/.test(r.eventType) ? 'SUBMITTED' : /DECIDED/.test(r.eventType) ? 'DECIDED' : '';
  const data = r.payload?.data || {};
  return `<article class="ek-card ${isNew ? 'new' : ''}">
    <div class="ek-card-head">
      <span class="ek-type ${kind}">${esc(r.eventType)}</span>
      <div class="ek-meta"><span>İstinad: <b>${esc(r.reference)}</b></span>${data.applicationNumber ? `<span>Müraciət: <b>${esc(data.applicationNumber)}</b></span>` : ''}<span>${fmt.dateTime(r.receivedAt)}</span><span>Idempotency: <b>${esc((r.eventId || '').slice(0, 8))}…</b></span></div>
      <span class="ek-ok">✓ imza etibarlıdır</span>
    </div>
    ${r.payload ? `<pre class="ek-json">${highlight(r.payload)}</pre>` : ''}
  </article>`;
}

function stats(last) {
  $('#sAccepted').textContent = accepted;
  $('#sRejected').textContent = rejected;
  if (last) $('#sLast').textContent = fmt.dateTime(last).split(', ')[1] || '-';
}

async function init() {
  const list = await api('/api/v1/admin/integration/ektis-inbox');
  accepted = list.length;
  $('#feed').innerHTML = list.length ? list.slice(0, 25).map((r) => card(r, false)).join('') : '<div class="ek-empty">Hadisə gözlənilir...</div>';
  stats(list[0]?.receivedAt);
  onLive('ektis', (r) => {
    accepted++;
    $('#feed .ek-empty')?.remove();
    $('#feed').insertAdjacentHTML('afterbegin', card(r, true));
    stats(r.receivedAt);
  });
  onLive('ektis-rejected', (r) => {
    rejected++;
    $('#feed').insertAdjacentHTML('afterbegin', `<div class="ek-rej">✗ ${esc(r.eventType || '')} - ${esc(r.reason)} · ${fmt.dateTime(r.at)}</div>`);
    stats();
  });
  await startLive({ silent: true });
  const obs = new MutationObserver(() => {
    const on = document.body.classList.contains('live-on');
    document.body.classList.toggle('ek-on', on);
    $('#conn').textContent = on ? 'Canlı bağlantı' : 'Bağlanır...';
  });
  obs.observe(document.body, { attributes: true, attributeFilter: ['class'] });
}
init().catch((e) => { $('#feed').innerHTML = `<div class="ek-empty">${esc(e.message)}</div>`; });
