import { api, renderLayout, $, $$, esc, icon, session, params, withLoading, showFieldErrors, toast, DISTRICTS, bindMoneyInput, moneyValue, homeFor } from '../core.js';

renderLayout('');
document.querySelector('.site-footer')?.remove();

// mygov ID ilə ilk girişdən sonra: hüquqi status (fiziki və ya hüquqi şəxs), məqsəd və ona aid sahələr soruşulur
const completing = params().get('tamamla') === '1' && session.loggedIn();
const nextUrl = () => {
  const n = params().get('next');
  return n && n.startsWith('/') && !n.startsWith('//') ? n : null;
};

if (completing) {
  const u = session.user() || {};
  $('#formTitle').textContent = 'Hesabınızı tamamlayın';
  $('#formSub').innerHTML = `${icon('shield-check', 'i-sm')} mygov ID ilə təsdiqləndiniz: <b>${esc(u.fullName || '')}</b>${u.fin ? ` · FİN ${esc(u.fin)}` : ''}. İndi platformadan necə istifadə edəcəyinizi seçin.`;
  ['#pwField', '#consentRow', '#mygovReg', '#orLine'].forEach((s) => $(s)?.remove());
  ['#fullName', '#email', '#phone', '#fin'].forEach((s) => $(s)?.closest('.field')?.remove());
  // mygov yalnız Azərbaycan vətəndaşları üçündür: xarici vətəndaş seçimi yoxdur
  $('#foreignType')?.remove();
  $('#legalTypes').classList.remove('three');
  $('[name=accountType][value=BUYER]')?.closest('.acct-type')?.remove();
  $$('.acct-types.three').forEach((g) => g.classList.remove('three'));
  $('#submitBtn').textContent = 'Davam et';
} else if (session.loggedIn()) {
  location.href = homeFor(session.role());
}

const districtOpts = Object.entries(DISTRICTS).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('');
$('#entDistrict').innerHTML += districtOpts;
$('#homeDistrict').innerHTML += districtOpts;
api('/api/v1/projects/sectors').then((list) => {
  $('#entSector').innerHTML += list.map((s) => `<option value="${s.code}">${esc(s.label)}</option>`).join('');
  $('#invSectors').innerHTML = list.map((s) => `<button type="button" class="chip chip-tg" data-s="${s.code}" aria-pressed="false">${icon(s.icon, 'i-sm')}${esc(s.label)}</button>`).join('');
}).catch(() => {});
$('#invSectors').addEventListener('click', (e) => {
  const b = e.target.closest('[data-s]');
  if (!b) return;
  const on = !b.classList.contains('active');
  b.classList.toggle('active', on);
  b.setAttribute('aria-pressed', String(on));
});
$$('.seg').forEach((g) => g.addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  g.querySelectorAll('button').forEach((x) => x.classList.toggle('active', x === b));
}));
['#invMin', '#invMax', '#entNeed'].forEach((s) => bindMoneyInput($(s)));
$('#localResident').addEventListener('change', () => { $('#homeDistrictField').hidden = !$('#localResident').checked; });

const type = () => document.querySelector('[name=accountType]:checked')?.value || 'INVESTOR';
const legal = () => document.querySelector('[name=legalType]:checked')?.value || 'INDIVIDUAL';
const ART = {
  INDIVIDUAL: 'Fiziki şəxs',
  COMPANY: 'Hüquqi şəxs',
  FOREIGN: 'Xarici vətəndaş',
};
const sync = () => {
  const lt = legal();
  $$('.type-box').forEach((f) => { f.hidden = f.dataset.type !== type(); });
  $$('.lg').forEach((el) => { el.hidden = !el.classList.contains(`lg-${lt}`); });
  $('#nameLabel') && ($('#nameLabel').textContent = lt === 'COMPANY' ? 'Səlahiyyətli nümayəndənin adı və soyadı' : 'Ad və soyad');
  // Fiziki şəxs üçün FİN məcburidir, hüquqi şəxsin nümayəndəsi üçün könüllü
  $('#finLabel')?.classList.toggle('req', lt === 'INDIVIDUAL');
  // mygov yalnız Azərbaycan vətəndaşları üçündür
  if ($('#mygovReg')) { $('#mygovReg').hidden = lt === 'FOREIGN'; $('#orLine').hidden = lt === 'FOREIGN'; }
  const who = type() === 'ENTREPRENEUR' ? 'sahibkar' : type() === 'BUYER' ? 'alıcı' : 'investor';
  $('#artTitle').textContent = `${ART[lt]} · ${who}`;
};
$$('[name=accountType], [name=legalType]').forEach((r) => r.addEventListener('change', sync));
const pre = params().get('tip');
if (pre && document.querySelector(`[name=accountType][value=${pre}]`)) document.querySelector(`[name=accountType][value=${pre}]`).checked = true;
const preLegal = params().get('status');
if (preLegal && document.querySelector(`[name=legalType][value=${preLegal}]`)) document.querySelector(`[name=legalType][value=${preLegal}]`).checked = true;
sync();

function legalFields() {
  const lt = legal();
  const val = (s) => ($(s)?.value || '').trim();
  if (lt === 'COMPANY') return { legalType: lt, companyName: val('#companyName'), companyForm: val('#companyForm'), voen: val('#voenC'), repPosition: val('#repPosition') };
  if (lt === 'FOREIGN') return { legalType: lt, citizenship: val('#citizenship'), passportNo: val('#passportNo'), companyName: val('#foreignCompany'), prefLang: $('#prefLang .active')?.dataset.v || 'en' };
  return { legalType: lt, voen: val('#voenI'), localResident: $('#localResident').checked, homeDistrict: $('#localResident').checked ? val('#homeDistrict') : '' };
}

function typeFields() {
  const t = type();
  if (t === 'INVESTOR') {
    return { investor: { ticketMin: moneyValue($('#invMin')), ticketMax: moneyValue($('#invMax')), sectors: $$('#invSectors .active').map((b) => b.dataset.s), risk: $('#invRisk .active')?.dataset.v || 'MID' } };
  }
  if (t === 'ENTREPRENEUR') {
    return { entrepreneur: { district: $('#entDistrict').value || null, sector: $('#entSector').value || null, landStatus: $('#entLand .active')?.dataset.v || null, landHa: $('#entHa').value ? Number($('#entHa').value) : null, needAmount: moneyValue($('#entNeed')), idea: $('#entIdea').value.trim() || null } };
  }
  return {};
}

// Qeydiyyatdan sonra hər növ öz başlanğıc nöqtəsinə gedir
function landing(user) {
  const n = nextUrl();
  if (n) return n;
  if (user.role === 'BUYER') return '/alicilar.html#mine';
  if (type() === 'ENTREPRENEUR') {
    const q = new URLSearchParams();
    if ($('#entSector').value) q.set('sector', $('#entSector').value);
    return `/layihe-yeni.html${q.toString() ? '?' + q : ''}`;
  }
  return '/layiheler.html#profile';
}

// Fiziki şəxsin VÖEN sahəsi ayrıca inputdadır: serverin xəta adını formadakı sahəyə uyğunlaşdırır
const errAlias = (k) => (k === 'legal.voen' && legal() === 'INDIVIDUAL' ? 'legal.voenI' : k);

$('#form').addEventListener('submit', (e) => {
  e.preventDefault();
  const form = e.target;
  $('#err').innerHTML = '';
  withLoading(e.submitter, async () => {
    try {
      let user;
      if (completing) {
        user = await api('/api/v1/auth/me/type', { method: 'PUT', body: { accountType: type(), ...typeFields(), legal: legalFields() } });
        session.set(session.token(), user);
        toast('Hesabınız hazırdır');
      } else {
        const val = (s) => ($(s)?.value || '').trim();
        const body = { fullName: val('#fullName'), email: val('#email'), phone: val('#phone'), fin: legal() === 'FOREIGN' ? '' : val('#fin'), password: $('#password').value,
          consent: $('#consent').checked, accountType: type(), ...typeFields(), legal: legalFields() };
        const r = await api('/api/v1/auth/register', { method: 'POST', body });
        session.set(r.token, r.user);
        user = r.user;
        toast('Hesab yaradıldı. Xoş gəlmisiniz!');
      }
      setTimeout(() => { location.href = landing(user); }, 400);
    } catch (err) {
      if (err.fieldErrors) {
        const fe = {};
        Object.entries(err.fieldErrors).forEach(([k, v]) => { fe[errAlias(k)] = v; });
        err = Object.assign(err, { fieldErrors: fe });
      }
      if (!showFieldErrors(form, err) || err.fieldErrors?.consent) {
        $('#err').innerHTML = `<div class="alert alert-crit" style="margin-bottom:14px">${icon('circle-x')}<div>${esc(err.fieldErrors?.consent || err.message)}</div></div>`;
      }
    }
  });
});
