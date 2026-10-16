// mygov ID ilə giriş (test mühiti): kompüterdə QR kod, telefonda təsdiq; və ya "digər üsullar" ilə test vətəndaşı.
import { api, $, esc, icon, session, params, homeFor, toast } from '../core.js';
import { qrSvg } from '../receipt.js';

const root = $('#root');
const q = params();
const approveState = q.get('approve');
const nextUrl = (() => { const n = q.get('next'); return n && n.startsWith('/') && !n.startsWith('//') ? n : null; })();

async function citizensList(state, { phone = false } = {}) {
  const list = await api('/api/v1/mock/mygov/citizens');
  return `<div class="mg-cits">${list.map((c) => `<button type="button" class="mg-cit" data-c="${c.id}" data-state="${esc(state)}"><span class="avatar">${esc(c.fullName.split(' ').map((x) => x[0]).join(''))}</span><span><b>${esc(c.fullName)}</b><small>FİN ${esc(c.fin)} · ${esc(c.note)}</small></span>${icon('chevron-right', 'i-sm')}</button>`).join('')}</div>
    <p class="small muted" style="margin:10px 0 0">${phone ? 'Real mygov tətbiqində burada SİMA və ya ASAN İmza ilə təsdiq olur.' : 'Real mygov ID-də: ASAN İmza, SİMA imza və ya FİN və şifrə ilə.'}</p>`;
}

// ---------------------------------------------------------------- telefon: təsdiq ekranı
async function renderApprove(state) {
  root.innerHTML = `
    <h2 class="mg-h">Giriş sorğusu</h2>
    <p class="small"><b>Əkinçi 2.0</b> sizin adınızdan daxil olmaq istəyir və bu məlumatları alacaq: <b>ad, soyad, FİN, telefon</b>.</p>
    <p class="small muted" style="margin:0 0 10px">Test vətəndaşını seçin:</p>
    ${await citizensList(state, { phone: true })}
    <button class="btn btn-ghost btn-block" type="button" id="deny" style="margin-top:10px">İmtina et</button>`;
  root.addEventListener('click', async (e) => {
    const b = e.target.closest('.mg-cit');
    if (b) {
      await api('/api/v1/mock/mygov/approve', { method: 'POST', body: { state, citizenId: b.dataset.c, allow: true } }).catch((err) => toast(err.message, 'err'));
      root.innerHTML = `<div class="mg-done">${icon('circle-check')}<h2 class="mg-h">Təsdiqləndi</h2><p class="small">Kompüterinizə qayıdın: giriş avtomatik tamamlanır.</p></div>`;
    }
    if (e.target.closest('#deny')) {
      await api('/api/v1/mock/mygov/approve', { method: 'POST', body: { state, allow: false } }).catch(() => {});
      root.innerHTML = `<div class="mg-done deny">${icon('circle-x')}<h2 class="mg-h">İmtina edildi</h2></div>`;
    }
  });
}

// ---------------------------------------------------------------- kompüter: QR və gözləmə
async function renderLogin() {
  const s = await api('/api/v1/auth/mygov/start', { method: 'POST' });
  const approveUrl = `${location.origin}/mygov-id.html?approve=${encodeURIComponent(s.state)}`;
  root.innerHTML = `
    <div class="seg mg-tabs" role="tablist"><button class="active" data-tab="qr" type="button">QR kod</button><button data-tab="other" type="button">Digər üsullar</button></div>
    <div data-panel="qr" class="mg-qr">
      <div>
        <h3 class="mg-h">mygov mobil tətbiqi ilə daxil olun</h3>
        <ol class="mg-steps"><li>Telefonda kameranı və ya mygov tətbiqini açın.</li><li>QR kodu skan edin.</li><li>Açılan səhifədə girişi təsdiqləyin.</li></ol>
        <p class="small muted" id="wait">${icon('hourglass', 'i-sm')} Təsdiq gözlənilir...</p>
        <a class="small" href="${esc(approveUrl)}" target="_blank" rel="noopener">Telefon yoxdur? Təsdiq səhifəsini yeni pəncərədə açın</a>
      </div>
      <div class="mg-code">${qrSvg(approveUrl)}</div>
    </div>
    <div data-panel="other" hidden>${await citizensList(s.state)}</div>`;
  root.querySelector('.mg-tabs').addEventListener('click', (e) => {
    const b = e.target.closest('[data-tab]');
    if (!b) return;
    root.querySelectorAll('[data-tab]').forEach((x) => x.classList.toggle('active', x === b));
    root.querySelectorAll('[data-panel]').forEach((p) => { p.hidden = p.dataset.panel !== b.dataset.tab; });
  });
  root.querySelector('[data-panel=other]').addEventListener('click', async (e) => {
    const b = e.target.closest('.mg-cit');
    if (!b) return;
    b.disabled = true;
    await api('/api/v1/mock/mygov/approve', { method: 'POST', body: { state: s.state, citizenId: b.dataset.c, allow: true } }).catch((err) => toast(err.message, 'err'));
  });
  poll(s.state);
}

async function poll(state) {
  const started = Date.now();
  for (;;) {
    await new Promise((r) => setTimeout(r, 1500));
    let st;
    try { st = await api(`/api/v1/auth/mygov/status?state=${encodeURIComponent(state)}`); } catch { continue; }
    if (st.status === 'APPROVED') return finish(st.code);
    if (st.status === 'DENIED') { root.innerHTML = `<div class="mg-done deny">${icon('circle-x')}<h2 class="mg-h">Giriş rədd edildi</h2><a class="btn btn-primary" href="/mygov-id.html">Yenidən cəhd et</a></div>`; return; }
    if (st.status === 'EXPIRED' || Date.now() - started > 5 * 60_000) { root.innerHTML = `<div class="mg-done deny">${icon('hourglass')}<h2 class="mg-h">Sessiyanın vaxtı bitdi</h2><a class="btn btn-primary" href="/mygov-id.html">Yeni QR kod</a></div>`; return; }
  }
}

async function finish(code) {
  root.innerHTML = `<div class="mg-done">${icon('circle-check')}<h2 class="mg-h">Təsdiqləndi</h2><p class="small">Portala yönləndirilirsiniz...</p></div>`;
  const r = await api('/api/v1/auth/mygov/exchange', { method: 'POST', body: { code } });
  session.set(r.token, r.user);
  // Yeni istifadəçi: hesabın növü (investor və ya sahibkar) seçilir
  if (r.user.role === 'INVESTOR' && !r.user.userType) {
    location.href = `/qeydiyyat.html?tamamla=1${nextUrl ? '&next=' + encodeURIComponent(nextUrl) : ''}`;
    return;
  }
  location.href = nextUrl || homeFor(r.user.role);
}

(approveState ? renderApprove(approveState) : renderLogin()).catch((e) => {
  root.innerHTML = `<div class="alert alert-crit">${icon('circle-x')}<div>${esc(e.message)}</div></div>`;
});
