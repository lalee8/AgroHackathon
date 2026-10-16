import { api, renderLayout, $, on, esc, icon, session, params, homeFor, withLoading } from '../core.js';

renderLayout('');
document.querySelector('.site-footer')?.remove();
if (session.loggedIn()) location.href = homeFor(session.role());
// mygov ID-dən sonra da eyni səhifəyə qayıtmaq üçün
const nx = params().get('next');
if (nx) $('#mygovLogin').href = `/mygov-id.html?next=${encodeURIComponent(nx)}`;

const DEMO = [
  { g: 'Müraciət edənlər' },
  ['investor@aqroinvest.az', 'Investor123!', 'Kamran Əhmədov', 'Fiziki şəxs, yerli sakin (Ağdam) · investor: müraciətlər, kabinet', 'user'],
  ['zengilan.baglari@example.az', 'Investor123!', 'Zəngilan Bağları MMC', 'Hüquqi şəxs · sahibkar: layihə, investor axtarışı', 'building-2'],
  ['qarabag.vineyards@example.az', 'Investor123!', 'Mehmet Kaya', 'Xarici vətəndaş (Türkiyə) · investor: pasportla müraciət', 'globe'],
  ['alici@aqroinvest.az', 'Alici123!', 'Nicat Əlizadə', 'Alıcı (Qarabağ Meyvə Emalı): tələblər, niyyət məktubları', 'hand-coins'],
  { g: 'Qəbul edən tərəf: dövlət komissiyası' },
  ['nazirlik@aqroinvest.az', 'Admin123!', 'Leyla Həsənova', 'Komissiyanın sədri (KTN): səsvermə, "dörd göz" təsdiqi', 'gavel'],
  ['ekspert@aqroinvest.az', 'Ekspert123!', 'Dr. Elçin Quliyev', 'Komissiya üzvü (KTN, aqronom): qiymət və səs', 'scale'],
  ['admin@aqroinvest.az', 'Admin123!', 'Rəşad Məmmədov', 'Komissiyanın katibi, Nazirlik operatoru: qərar, protokol', 'chart-column'],
];
$('#demo').innerHTML = DEMO.map((d) => (d.g ? `<div class="demo-grp">${esc(d.g)}</div>` : `<button type="button" class="demo-acc" data-e="${d[0]}" data-p="${d[1]}"><span class="avatar">${icon(d[4], 'i-sm')}</span><div><b>${esc(d[2])}</b> <span class="small muted mono">${esc(d[0])}</span><div class="r">${esc(d[3])}</div></div></button>`)).join('');

async function login(email, password, btn) {
  $('#err').innerHTML = '';
  await withLoading(btn, async () => {
    try {
      const r = await api('/api/v1/auth/login', { method: 'POST', body: { email, password } });
      session.set(r.token, r.user);
      const next = params().get('next');
      location.href = next && next.startsWith('/') && !next.startsWith('//') ? next : homeFor(r.user.role);
    } catch (e) {
      $('#err').innerHTML = `<div class="alert alert-crit" style="margin-bottom:14px">${icon('circle-x')}<div>${esc(e.message)}</div></div>`;
    }
  });
}

$('#form').addEventListener('submit', (e) => {
  e.preventDefault();
  login($('#email').value.trim(), $('#password').value, e.submitter);
});
on($('#demo'), 'click', '.demo-acc', (e, b) => {
  $('#email').value = b.dataset.e;
  $('#password').value = b.dataset.p;
  login(b.dataset.e, b.dataset.p, $('#form button[type=submit]'));
});
