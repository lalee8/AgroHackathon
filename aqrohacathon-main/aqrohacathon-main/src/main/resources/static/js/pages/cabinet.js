import { api, renderLayout, requireAuth, onLive, $, esc, icon, fmt, CAT, session, statusBadge, setTabs, withLoading, showFieldErrors, toast, emptyState, catSymbol, favButton } from '../core.js';
import { qrSvg } from '../receipt.js';
import { mountPlan } from '../tranches.js';

renderLayout('');
// Hesab növünə görə başlanğıc düymələri: sahibkar layihə təqdim edir, investor uyğun layihələrə baxır
{
  const u = session.user();
  const box = document.getElementById('cabActions');
  if (u?.role === 'INVESTOR' && !u.userType) location.href = '/qeydiyyat.html?tamamla=1&next=/kabinet.html';
  if (box && u?.userType === 'ENTREPRENEUR') {
    box.innerHTML = `<a class="btn" href="/texnika.html">${icon('tractor')}Texnika icarəsi</a><a class="btn" href="/kataloq.html?status=IN_COMPETITION">${icon('map')}Torpaq tap</a><a class="btn btn-primary" href="/layihe-yeni.html">${icon('plus')}Layihəmi təqdim et</a>`;
  } else if (box && u?.userType === 'INVESTOR') {
    box.innerHTML = `<a class="btn" href="/portfel.html">${icon('chart-pie')}Portfel qur</a><a class="btn" href="/layiheler.html#profile">${icon('target')}Mənə uyğun layihələr</a><a class="btn btn-primary" href="/kataloq.html?status=IN_COMPETITION">${icon('plus')}Yeni müraciət</a>`;
  }
}
if (!requireAuth(['INVESTOR'])) throw new Error('auth');
const user = session.user();
$('#hello').textContent = `Salam, ${user.fullName.split(' ')[0]}!`;
const catByLabel = (label) => Object.keys(CAT).find((k) => CAT[k].label === label || label?.startsWith(CAT[k].label)) || 'EKINCILIK';

function action(a) {
  if (a.status === 'DRAFT') return `<a class="btn btn-primary btn-sm" href="/muraciet.html?id=${a.id}">${icon('pencil', 'i-sm')}Davam et</a>`;
  if (a.status === 'INFO_REQUESTED') return `<a class="btn btn-sm" style="--b-bg:var(--st-serious-bg);--b-ink:var(--st-serious-ink);--b-line:#f5c9b5" href="/muraciet.html?id=${a.id}">${icon('triangle-alert', 'i-sm')}Cavab ver</a>`;
  return `<a class="btn btn-sm" href="/muraciet-detal.html?id=${a.id}">${icon('eye', 'i-sm')}Bax</a>`;
}

async function loadApps() {
  const list = await api('/api/v1/applications/my');
  const cnt = (f) => list.filter(f).length;
  $('#kpis').innerHTML = `
    <div class="kpi"><span class="ico">${icon('file-text')}</span><div class="lbl">Bütün müraciətlər</div><div class="val">${list.length}</div></div>
    <div class="kpi"><span class="ico">${icon('hourglass')}</span><div class="lbl">Baxılır</div><div class="val">${cnt((a) => ['SUBMITTED', 'UNDER_REVIEW'].includes(a.status))}</div></div>
    <div class="kpi"><span class="ico" style="background:var(--st-serious-bg);color:var(--st-serious-ink)">${icon('triangle-alert')}</span><div class="lbl">Diqqət tələb edir</div><div class="val">${cnt((a) => ['DRAFT', 'INFO_REQUESTED'].includes(a.status))}</div><div class="sub">qaralama və sorğular</div></div>
    <div class="kpi"><span class="ico" style="background:var(--st-good-bg);color:var(--st-good-ink)">${icon('award')}</span><div class="lbl">Təsdiqlənib</div><div class="val">${cnt((a) => a.decision === 'APPROVED')}</div></div>`;
  const info = list.filter((a) => a.status === 'INFO_REQUESTED');
  $('#attention').innerHTML = info.map((a) => `<div class="alert alert-serious" style="margin-bottom:10px">${icon('triangle-alert')}<div style="flex:1"><b>${esc(a.appNumber)}</b> üzrə komissiya əlavə məlumat tələb edir (${esc(a.plotName)}).</div><a class="btn btn-sm" href="/muraciet.html?id=${a.id}">Cavab ver</a></div>`).join('');
  $('#apps').innerHTML = list.length ? list.map((a) => `
    <div class="app-row" data-href="${a.status === 'DRAFT' || a.status === 'INFO_REQUESTED' ? '/muraciet.html?id=' + a.id : '/muraciet-detal.html?id=' + a.id}" role="link" tabindex="0">
      ${catSymbol(catByLabel(a.categoryLabel), 46)}
      <div style="min-width:0">
        <div class="row-wrap" style="gap:8px"><b class="mono">${esc(a.appNumber || 'Qaralama')}</b>${statusBadge(a.status, a.decision)}</div>
        <div style="font-weight:620;margin-top:2px">${esc(a.plotName)} <span class="muted small">· ${esc(a.plotCode)} · ${esc(a.districtLabel)}</span></div>
        <div class="small muted">${a.investmentAmount ? fmt.money(a.investmentAmount) + ' · ' : ''}${a.jobsPlanned ? a.jobsPlanned + ' iş yeri · ' : ''}${a.submittedAt ? 'göndərilib ' + fmt.date(a.submittedAt) : 'yenilənib ' + fmt.rel(a.updatedAt)}${a.autoScore != null ? ' · ilkin bal ' + a.autoScore : ''}</div>
      </div>
      <div>${action(a)}</div>
    </div>`).join('') : `<div class="card-body">${emptyState('Hələ müraciətiniz yoxdur', 'file-text')}<div style="text-align:center"><a class="btn btn-primary" href="/xerite.html">Sahələrə bax</a></div></div>`;
}

async function loadFavs() {
  const box = $('#favs');
  box.innerHTML = '<div class="loader"></div>';
  const list = await api('/api/v1/me/watches');
  const open = list.filter((w) => w.status === 'IN_COMPETITION').length;
  box.innerHTML = `<div class="alert alert-info" style="margin-bottom:12px">${icon('bell')}<div>Sevimli sahə müsabiqəyə çıxanda portalda, Telegram bağlıdırsa orada da xəbər veririk. ${open ? `<b>${open} sahə hazırda müsabiqədədir</b>, müraciət edə bilərsiniz.` : ''}</div></div>` +
    (list.length ? list.map((w) => `
    <div class="app-row" data-href="/sahe.html?id=${w.plotId}" role="link" tabindex="0">
      ${catSymbol(w.category, 46)}
      <div style="min-width:0">
        <div class="row-wrap" style="gap:8px"><b class="mono">${esc(w.code)}</b><span class="badge ${w.status === 'IN_COMPETITION' ? 'badge-good' : ''}">${esc(w.statusLabel)}</span></div>
        <div style="font-weight:620;margin-top:2px">${esc(w.name)}</div>
        <div class="small muted">${esc(w.district)} · ${esc(w.categoryLabel)} · ${fmt.num(w.areaHa)} ha · əlavə edilib ${fmt.rel(w.since)}</div>
      </div>
      <div class="row" style="gap:8px">${w.status === 'IN_COMPETITION' ? `<a class="btn btn-primary btn-sm" href="/sahe.html?id=${w.plotId}">${icon('send', 'i-sm')}Müraciət et</a>` : `<a class="btn btn-sm" href="/plan.html?plot=${w.plotId}">${icon('calendar-range', 'i-sm')}Plan</a>`}${favButton(w.plotId)}</div>
    </div>`).join('') : `${emptyState('Sevimli sahə yoxdur', 'heart')}<div style="text-align:center"><a class="btn btn-primary" href="/kataloq.html">Kataloqa bax</a></div>`);
  box.querySelectorAll('[data-href]').forEach((r) => r.addEventListener('click', (e) => { if (!e.target.closest('a,button')) location.href = r.dataset.href; }));
}

async function loadNotifs() {
  const d = await api('/api/v1/notifications');
  $('#notifs').innerHTML = d.items.length ? `<div class="row between" style="margin-bottom:10px"><b>${d.unread} oxunmamış</b><button class="btn btn-sm" id="ra">Hamısını oxunmuş et</button></div>` +
    d.items.map((n) => `<a class="dropdown-item ${n.read ? '' : 'unread'}" href="${esc(n.link || '#')}" style="border-radius:12px;margin-bottom:6px;border:1px solid var(--line)">${icon('bell')}<div><div class="t">${esc(n.title)}</div><div class="m">${esc(n.message || '')}</div><div class="m">${fmt.dateTime(n.createdAt)}</div></div></a>`).join('')
    : emptyState('Bildiriş yoxdur', 'bell');
  $('#ra')?.addEventListener('click', async () => { await api('/api/v1/notifications/read-all', { method: 'POST' }); loadNotifs(); });
}

async function loadProfile() {
  const me = await api('/api/v1/auth/me');
  const legalRows = me.legalType ? `<div class="card card-pad" style="max-width:620px;margin-bottom:16px;background:var(--surface-2,#fafaf7)">
      <div class="row between" style="flex-wrap:wrap;gap:8px"><b>${icon(me.legalType === 'COMPANY' ? 'building-2' : me.legalType === 'FOREIGN' ? 'globe' : 'user', 'i-sm')} Hüquqi status</b>
        <span><span class="legal-badge ${esc(me.legalType)}">${esc(me.legalTypeLabel)}</span>${me.localResident ? ` <span class="legal-badge local">Yerli sakin${me.homeDistrictLabel ? ': ' + esc(me.homeDistrictLabel) : ''}</span>` : ''}${me.mygov ? ' <span class="legal-badge">mygov ID ilə təsdiqlənib</span>' : ''}</span></div>
      <div class="kv" style="margin-top:10px">
        ${me.companyName ? `<div>${me.legalType === 'FOREIGN' ? 'Şirkət' : 'Hüquqi şəxs'}</div><div><b>${esc(me.companyName)}</b>${me.companyForm ? ` (${esc(me.companyForm)})` : ''}</div>` : ''}
        ${me.voen ? `<div>VÖEN</div><div class="mono">${esc(me.voen)}</div>` : ''}
        ${me.repPosition ? `<div>Nümayəndə</div><div>${esc(me.fullName)}, ${esc(me.repPosition)}</div>` : ''}
        ${me.citizenship ? `<div>Vətəndaşlıq</div><div>${esc(me.citizenship)}</div>` : ''}
        ${me.passportNo ? `<div>Pasport</div><div class="mono">${esc(me.passportNo)}</div>` : ''}
      </div>
      <p class="small muted" style="margin:10px 0 0">Bu məlumat müsabiqəyə müraciət yaradanda avtomatik doldurulur. Torpağı yalnız dövlət verir: müraciətə dövlət qurumlarından ibarət komissiya baxır.</p>
    </div>` : '';
  $('#profile').innerHTML = `${legalRows}<form id="pf" style="max-width:620px" novalidate>
    <div class="form-grid">
      <div class="field full"><label class="req">Ad və soyad</label><input class="input" name="fullName" value="${esc(me.fullName)}"></div>
      <div class="field"><label>Telefon</label><input class="input" name="phone" value="${esc(me.phone || '')}"></div>
      <div class="field"><label>FİN kod</label><input class="input mono" name="fin" maxlength="7" value="${esc(me.fin || '')}"></div>
      <div class="field full"><label>E-poçt</label><input class="input" value="${esc(me.email)}" disabled><div class="hint">E-poçt hesabın identifikatorudur və dəyişdirilmir</div></div>
    </div>
    <button class="btn btn-primary" type="submit" data-loading="Yadda saxlanılır...">${icon('check', 'i-sm')}Yadda saxla</button>
    <p class="small muted" style="margin-top:14px">${icon('lock', 'i-sm')} Hesab yaradılıb: ${fmt.date(me.createdAt)} · Rol: ${esc(me.roleLabel)}</p>
  </form>`;
  $('#pf').addEventListener('submit', (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    withLoading(e.submitter, async () => {
      try {
        const u = await api('/api/v1/auth/me', { method: 'PUT', body: { fullName: f.get('fullName'), phone: f.get('phone'), fin: f.get('fin') } });
        session.updateUser(u);
        toast('Profil yeniləndi');
      } catch (err) { if (!showFieldErrors(e.target, err)) toast(err.message, 'err'); }
    });
  });
}

async function loadProjects() {
  const box = $('#projects');
  box.innerHTML = '<div class="loader"></div>';
  const [mine, sent, letters] = await Promise.all([api('/api/v1/projects/mine'), api('/api/v1/projects/interests/mine'), api('/api/v1/letters').catch(() => [])]);
  const st = (s) => (s === 'ACCEPTED' ? 'badge-good' : s === 'DECLINED' ? 'badge-crit' : 'badge-info');
  box.innerHTML = `
    <div class="row between" style="flex-wrap:wrap;gap:10px;margin-bottom:12px"><div><h4 style="margin:0">Sahibkar kimi: mənim layihələrim</h4><div class="small muted">İnvestorlar maraq bildirəndə bildiriş alırsınız. Razılıq verəndə əlaqə məlumatları açılır.</div></div><a class="btn btn-primary btn-sm" href="/layihe-yeni.html">${icon('plus', 'i-sm')}Layihə təqdim et</a></div>
    ${mine.length ? mine.map((m) => {
    const p = m.project; const fresh = m.interests.filter((i) => i.status === 'NEW').length;
    return `<div class="app-row" data-href="/layihe.html?id=${p.id}#interests" role="link" tabindex="0"><span class="pc-ico">${icon(p.sectorIcon)}</span><div style="min-width:0"><div class="row-wrap" style="gap:8px"><b class="mono">${esc(p.code)}</b><span class="badge">${esc(p.statusLabel)}</span>${fresh ? `<span class="badge badge-warn">${fresh} yeni maraq</span>` : ''}</div><div style="font-weight:620;margin-top:2px">${esc(p.title)}</div><div class="small muted">${fmt.compactMoney(p.askAmount)} · ${esc(p.dealLabel)} · reallıq yoxlaması ${p.credibility}/100 · ${m.interests.length} investor marağı</div></div><div><a class="btn btn-sm" href="/layihe.html?id=${p.id}#interests">${icon('eye', 'i-sm')}Bax</a></div></div>`;
  }).join('') : emptyState('Hələ layihəniz yoxdur', 'lightbulb')}
    <h4 style="margin:22px 0 8px">İnvestor kimi: maraq bildirdiyim layihələr</h4>
    ${sent.length ? sent.map((i) => `<div class="int-row"><div style="flex:1;min-width:0"><a href="/layihe.html?id=${i.projectId}"><b>${esc(i.projectTitle)}</b></a> · ${fmt.compactMoney(i.amount)}<div class="small muted">${fmt.rel(i.createdAt)} · <span class="badge ${st(i.status)}">${esc(i.statusLabel)}</span></div>${i.contact ? `<div class="contact-card" style="margin-top:8px"><b>${esc(i.contact.name)}</b><span>${icon('phone', 'i-sm')} ${esc(i.contact.phone || '-')}</span><span>${icon('mail', 'i-sm')} ${esc(i.contact.email)}</span></div>` : ''}
      ${i.status === 'ACCEPTED' ? `<details class="tr-cab"><summary>${icon('satellite', 'i-sm')} Mərhələli maliyyələşmə: escrow və peyk nəzarəti</summary><div data-plan="${i.id}"></div></details>` : ''}</div></div>`).join('') : `<p class="small muted">Hələ maraq bildirməmisiniz. <a href="/layiheler.html">Uyğun layihələrə baxın</a></p>`}
    <h4 style="margin:22px 0 8px">Alıcılarla niyyət məktubları</h4>
    ${letters.length ? letters.map((l) => `<div class="int-row"><div><b>${esc(l.company)}</b> · ${esc(l.productLabel)} ${fmt.num(l.volumeTons)} t/il${l.price ? ` · ${fmt.num(l.price)} ₼/t` : ''}<div class="small muted">${l.appNumber ? 'müraciət ' + esc(l.appNumber) : l.projectCode ? 'layihə ' + esc(l.projectCode) : ''} · <span class="badge ${l.status === 'SIGNED' ? 'badge-good' : l.status === 'DECLINED' ? 'badge-crit' : 'badge-info'}">${esc(l.statusLabel)}</span></div></div></div>`).join('') : `<p class="small muted">Hələ yoxdur. <a href="/alicilar.html">Təsdiqlənmiş alıcı tələbləri</a></p>`}`;
  box.querySelectorAll('[data-href]').forEach((r) => r.addEventListener('click', (e) => { if (!e.target.closest('a')) location.href = r.dataset.href; }));
  box.querySelectorAll('details.tr-cab').forEach((d, n) => {
    const load = () => { const el = d.querySelector('[data-plan]'); if (!el.dataset.loaded) { el.dataset.loaded = '1'; mountPlan(el, el.dataset.plan); } };
    d.addEventListener('toggle', () => { if (d.open) load(); });
    if (n === 0) { d.open = true; load(); }
  });
}

async function loadTelegram() {
  const box = $('#tg');
  let st;
  try { st = await api('/api/v1/telegram/status'); } catch { box.innerHTML = ''; return; }
  const head = `<div class="row" style="gap:12px;align-items:flex-start"><span class="tg-ico">${icon('send')}</span><div style="flex:1;min-width:0"><b>Telegram bildirişləri</b>`;
  if (!st.enabled) {
    box.innerHTML = `<div class="tg-card">${head}<div class="small muted">Status dəyişən kimi telefonunuza mesaj. Bu serverdə bot hələ qoşulmayıb (administrator <span class="mono">TELEGRAM_BOT_TOKEN</span> təyin etməlidir).</div></div></div></div>`;
    return;
  }
  if (st.linked) {
    box.innerHTML = `<div class="tg-card">${head}<div class="small muted"><span class="badge badge-good">${icon('circle-check', 'i-sm')}Qoşulub</span> Müraciətinizin statusu dəyişən kimi <b>@${esc(st.bot || '')}</b> sizə yazacaq.</div>
      <div class="row-wrap" style="gap:8px;margin-top:10px"><button class="btn btn-sm" id="tgTest">${icon('send', 'i-sm')}Test mesajı</button><button class="btn btn-sm btn-ghost" id="tgOff">${icon('x', 'i-sm')}Ayır</button></div></div></div></div>`;
    $('#tgTest').addEventListener('click', (e) => withLoading(e.currentTarget, async () => { await api('/api/v1/telegram/test', { method: 'POST' }); toast('Test mesajı göndərildi'); }));
    $('#tgOff').addEventListener('click', async () => { await api('/api/v1/telegram/unlink', { method: 'POST' }); loadTelegram(); });
    return;
  }
  box.innerHTML = `<div class="tg-card">${head}<div class="small muted">Status dəyişən kimi telefonunuza mesaj gəlsin. Kodu telefonla skan edin və ya düyməni basın, sonra Telegram-da <b>Start</b> düyməsinə toxunun.</div>
    <div class="row-wrap" style="gap:8px;margin-top:10px"><button class="btn btn-primary btn-sm" id="tgLink">${icon('qr-code', 'i-sm')}Telegram-ı qoş</button></div><div id="tgQr"></div></div></div></div>`;
  $('#tgLink').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      const { url } = await api('/api/v1/telegram/link', { method: 'POST' });
      $('#tgQr').innerHTML = `<div class="tg-qr"><div class="qr">${qrSvg(url)}</div><div class="small"><a class="btn btn-sm" href="${esc(url)}" target="_blank" rel="noopener">${icon('external-link', 'i-sm')}Telegram-da aç</a><div class="muted" style="margin-top:8px">Kod 10 dəqiqə etibarlıdır.<br>Qoşulan kimi bu kart özü yenilənəcək.</div></div></div>`;
      let n = 0;
      const t = setInterval(async () => {
        n += 1;
        const s = await api('/api/v1/telegram/status').catch(() => null);
        if (s?.linked) { clearInterval(t); toast('Telegram qoşuldu'); loadTelegram(); }
        if (n > 200) clearInterval(t);
      }, 3000);
    } catch (err) { toast(err.message, 'err'); }
  }));
}

$('#apps').addEventListener('click', (e) => {
  if (e.target.closest('a')) return;
  const row = e.target.closest('[data-href]');
  if (row) location.href = row.dataset.href;
});
$('#apps').addEventListener('keydown', (e) => { const row = e.target.closest('[data-href]'); if (row && e.key === 'Enter') location.href = row.dataset.href; });
setTabs($('#tabsRoot'), (t) => { if (t === 'projects') loadProjects(); if (t === 'favs') loadFavs(); if (t === 'notifs') { loadTelegram(); loadNotifs(); } if (t === 'profile') loadProfile(); });
if (location.hash === '#profile') document.querySelector('[data-tab="profile"]').click();
if (location.hash === '#projects') document.querySelector('[data-tab="projects"]').click();
if (location.hash === '#favs') document.querySelector('[data-tab="favs"]').click();
loadApps().catch((e) => { $('#apps').innerHTML = emptyState(e.message); });
onLive('notification', () => { loadApps().then(() => document.querySelector('.app-row')?.classList.add('flash')).catch(() => {}); });
