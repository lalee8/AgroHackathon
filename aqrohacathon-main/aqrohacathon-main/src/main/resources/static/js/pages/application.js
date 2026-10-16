import { api, renderLayout, requireAuth, onLive, $, on, esc, icon, fmt, params, statusBadge, downloadFile, confirmDialog, toast, errorToast } from '../core.js';
import { statusTrack, timeline, scoreBreakdown } from '../status.js';
import { receipt } from '../receipt.js';

renderLayout('');
if (!requireAuth()) throw new Error('auth');
const id = params().get('id');
const root = $('#root');

function render(a) {
  $('#title').textContent = a.appNumber || 'Qaralama müraciət';
  $('#badge').innerHTML = statusBadge(a.status, a.decision);
  $('#sub').textContent = `${a.plot.code} - ${a.plot.name} · ${a.competition.title}`;
  const lastInfo = [...a.history].reverse().find((h) => h.toStatus === 'INFO_REQUESTED');
  let banner = '';
  if (a.status === 'DRAFT') banner = `<div class="alert alert-info">${icon('pencil')}<div style="flex:1">Müraciət hələ göndərilməyib.</div><a class="btn btn-sm btn-primary" href="/muraciet.html?id=${a.id}">Davam et</a></div>`;
  if (a.status === 'INFO_REQUESTED') banner = `<div class="alert alert-serious">${icon('triangle-alert')}<div style="flex:1"><b>Komissiya əlavə məlumat tələb edir:</b> ${esc(lastInfo?.comment || '')}</div><a class="btn btn-sm" href="/muraciet.html?id=${a.id}">Cavab ver</a></div>`;
  if (a.decision === 'APPROVED') banner = `<div class="alert alert-good">${icon('award')}<div><b>Təbriklər! Müraciətiniz təsdiqləndi.</b> ${esc(a.decisionNote || '')}</div></div>`;
  if (a.decision === 'REJECTED') banner = `<div class="alert alert-crit">${icon('circle-x')}<div><b>Müraciət üzrə imtina qərarı verilib.</b> ${esc(a.decisionNote || '')}</div></div>`;

  root.innerHTML = `<div class="stack">
    <div class="card card-pad no-print">${statusTrack(a.status, a.decision)}</div>
    ${banner}
    <div class="detail-layout">
      <div class="stack">
        <div class="card"><div class="card-head"><h3>${icon('user')}Müraciətçi</h3><span class="badge">${esc(a.applicantTypeLabel || '')}</span></div><div class="card-body"><div class="kv">
          ${a.companyName ? `<div>Şirkət</div><div>${esc(a.companyName)}</div>` : ''}
          <div>Müraciətçi</div><div>${esc(a.applicantName)}</div>
          ${a.voen ? `<div>VÖEN</div><div class="mono">${esc(a.voen)}</div>` : ''}
          ${a.fin ? `<div>FİN</div><div class="mono">${esc(a.fin)}</div>` : ''}
          ${a.citizenship ? `<div>Vətəndaşlıq</div><div>${esc(a.citizenship)}</div>` : ''}
          ${a.passportNo ? `<div>Pasport</div><div class="mono">${esc(a.passportNo)}</div>` : ''}
          <div>Əlaqə</div><div>${esc(a.contactPhone || '-')} · ${esc(a.contactEmail || '-')}</div>
          ${a.legalAddress ? `<div>Ünvan</div><div>${esc(a.legalAddress)}</div>` : ''}
          <div>Sosial prioritet</div><div>${a.idpStatus ? 'Yerli sakin: məcburi köçkün / Qarabağ əsilli' : '-'}</div>
        </div></div></div>
        <div class="card"><div class="card-head"><h3>${icon('coins')}İnvestisiya planı</h3><span class="badge badge-gold">${esc(a.activityLabel || '')}</span></div><div class="card-body">
          <div class="spec-grid" style="margin-bottom:16px">
            <div class="spec"><div class="k">İnvestisiya</div><div class="v">${fmt.compactMoney(a.investmentAmount)}</div></div>
            <div class="spec"><div class="k">Öz vəsaiti</div><div class="v">${a.ownFundsPercent ?? '-'}%</div></div>
            <div class="spec"><div class="k">İş yerləri</div><div class="v">${a.jobsPlanned ?? '-'}</div></div>
            <div class="spec"><div class="k">İcra müddəti</div><div class="v">${a.implementationMonths ?? '-'} ay</div></div>
            <div class="spec"><div class="k">Təcrübə</div><div class="v">${a.experienceYears ?? '-'} il</div></div>
            <div class="spec"><div class="k">Gözlənilən gəlir</div><div class="v">${fmt.compactMoney(a.expectedAnnualRevenue)}</div></div>
          </div>
          <h4>Texnologiyalar</h4><div class="row-wrap" style="margin-bottom:14px">${a.technologies.map((t) => `<span class="badge badge-green">${esc(t.label)}</span>`).join('') || '<span class="muted small">Göstərilməyib</span>'}</div>
          <h4>Biznes planın xülasəsi</h4><p style="white-space:pre-wrap;font-size:14px">${esc(a.businessPlan || '-')}</p>
        </div></div>
        <div class="card"><div class="card-head"><h3>${icon('file-text')}Sənədlər (${a.documents.length})</h3></div><div class="card-body">
          ${a.documents.map((d) => `<div class="doc-file" style="margin-bottom:6px">${icon('file-check', 'i-sm')}<span class="nm">${esc(d.name)}</span><span class="badge">${esc(d.typeLabel)}</span><span class="small muted">${fmt.bytes(d.size)}</span><button class="btn btn-sm btn-ghost" data-doc="${d.id}" data-name="${esc(d.name)}">${icon('download', 'i-sm')}</button></div>`).join('') || '<p class="muted small">Sənəd yoxdur</p>'}
        </div></div>
        <div class="card" id="loiCard"></div>
        ${scoreBreakdown(a)}
      </div>
      <aside class="stack">
        ${a.appNumber ? receipt(a) : ''}
        <div class="card no-print"><div class="card-head"><h3>${icon('network')}EKTİS inteqrasiyası</h3></div><div class="card-body"><div class="kv" style="grid-template-columns:1fr auto;font-size:13.5px">
          <div>Fermer reyestri</div><div>${a.ektisFarmerStatus === 'REGISTERED' ? '<span class="badge badge-good">Qeydiyyatdadır</span>' : a.ektisFarmerStatus === 'NOT_REGISTERED' ? '<span class="badge badge-warn">Tapılmadı</span>' : a.ektisFarmerStatus === 'NOT_APPLICABLE' ? '<span class="badge">Xarici vətəndaş: qalibiyyətdən sonra qeydiyyat</span>' : a.ektisFarmerStatus ? `<span class="badge">${esc(a.ektisFarmerStatus)}</span>` : '-'}</div>
          <div>EKTİS istinadı</div><div class="mono">${esc(a.ektisReference || 'gözlənilir')}</div>
        </div></div></div>
        <div class="card no-print"><div class="card-head"><h3>${icon('history')}Müraciət tarixçəsi</h3></div><div class="card-body">${timeline(a.history)}</div></div>
        <div class="row-wrap no-print">
          ${a.appNumber ? `<button class="btn" id="print">${icon('printer')}Qəbzi çap et</button>` : ''}
          ${['SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'DRAFT'].includes(a.status) ? `<button class="btn btn-danger" id="withdraw">${icon('circle-x')}${a.status === 'DRAFT' ? 'Qaralamanı sil' : 'Geri çağır'}</button>` : ''}
        </div>
      </aside>
    </div></div>`;
  loadLetters(a);
  $('#print')?.addEventListener('click', () => window.print());
  $('#withdraw')?.addEventListener('click', async () => {
    if (!(await confirmDialog(a.status === 'DRAFT' ? 'Qaralama silinsin?' : 'Müraciəti geri çağırmaq istədiyinizə əminsiniz? Bu əməliyyat geri qaytarılmır.', { ok: 'Bəli', danger: true }))) return;
    try { await api(`/api/v1/applications/${a.id}/withdraw`, { method: 'POST' }); toast('Əməliyyat icra olundu'); location.href = '/kabinet.html'; } catch (e) { errorToast(e); }
  });
  on(root, 'click', '[data-doc]', (e, b) => downloadFile(`/api/v1/applications/${a.id}/documents/${b.dataset.doc}`, b.dataset.name).catch(errorToast));
}

async function loadLetters(a) {
  const el = $('#loiCard');
  if (!el) return;
  const list = await api(`/api/v1/letters?applicationId=${a.id}`).catch(() => []);
  const signed = list.filter((l) => l.status === 'SIGNED');
  el.innerHTML = `<div class="card-head"><h3>${icon('hand-coins')}Satış bazarı: alıcı niyyət məktubları</h3>${signed.length ? `<span class="badge badge-good">${signed.length} imzalanıb</span>` : ''}</div><div class="card-body">
    ${list.length ? list.map((l) => `<div class="int-row"><div><b>${esc(l.company)}</b> · ${esc(l.productLabel)} ${fmt.num(l.volumeTons)} t/il${l.price ? ` · ${fmt.num(l.price)} ₼/t` : ''}<div class="small muted"><span class="badge ${l.status === 'SIGNED' ? 'badge-good' : l.status === 'DECLINED' ? 'badge-crit' : 'badge-info'}">${esc(l.statusLabel)}</span>${l.signedAt ? ' · ' + fmt.date(l.signedAt) : ''}${l.buyerNote ? ` · "${esc(l.buyerNote)}"` : ''}</div></div></div>`).join('')
    : `<p class="small muted" style="margin:0 0 10px">Məhsulun alıcısı əvvəlcədən məlumdursa, komissiya bunu güclü tərəf kimi görür.</p>`}
    <a class="btn btn-sm no-print" href="/alicilar.html">${icon('file-check', 'i-sm')}Təsdiqlənmiş alıcıdan niyyət məktubu istə</a></div>`;
}

onLive('notification', (d) => {
  if (d.link && d.link.endsWith('id=' + id)) api(`/api/v1/applications/${id}`).then((a) => { render(a); root.classList.add('flash'); setTimeout(() => root.classList.remove('flash'), 1700); }).catch(() => {});
});
api(`/api/v1/applications/${id}`).then(render).catch((e) => { root.innerHTML = `<div class="alert alert-crit">${icon('circle-x')}<div>${esc(e.message)}</div></div>`; });
