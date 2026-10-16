import { api, renderLayout, $, $$, esc, icon, fmt, session, params, toast, emptyState, moneyValue, bindMoneyInput } from '../core.js';
import { projectCard } from '../market.js';

renderLayout('projects');
const state = { sector: params().get('sector') || null, sectors: [], profile: { sectors: [], ticketMin: null, ticketMax: null, risk: 'MID', horizonYears: 10 }, matched: false };
const isInvestor = session.loggedIn() && session.role() === 'INVESTOR';

async function loadSectors() {
  state.sectors = await api('/api/v1/projects/sectors');
  // Boş istiqamət: solğun xana, kliklədikdə həmin istiqamətdə layihə təqdim etmə forması açılır
  $('#sectors').innerHTML = state.sectors.map((s) => (s.projects
    ? `<button class="sector-tile ${s.code === state.sector ? 'active' : ''}" data-s="${s.code}">
      <span class="st-ico">${icon(s.icon)}</span><b>${esc(s.label)}</b>
      <small>${s.projects} layihə · ${fmt.compactMoney(s.totalAsk)}</small></button>`
    : `<a class="sector-tile st-empty" href="/layihe-yeni.html?sector=${s.code}">
      <span class="st-ico">${icon(s.icon)}</span><b>${esc(s.label)}</b>
      <small>Hələ layihə yoxdur</small><span class="st-cta">${icon('plus', 'i-sm')}Bu istiqamətdə ilk layihəni sən təqdim et!</span></a>`)).join('');
  $$('button.sector-tile').forEach((b) => b.addEventListener('click', () => {
    state.sector = state.sector === b.dataset.s ? null : b.dataset.s;
    $$('.sector-tile').forEach((x) => x.classList.toggle('active', x.dataset.s === state.sector));
    loadList();
  }));
  $('#pfSectors').innerHTML = state.sectors.map((s) => `<button type="button" class="chip ${state.profile.sectors.includes(s.code) ? 'active' : ''}" data-s="${s.code}">${icon(s.icon, 'i-sm')}${esc(s.label)}</button>`).join('');
  $$('#pfSectors .chip').forEach((b) => b.addEventListener('click', () => b.classList.toggle('active')));
}

const money = moneyValue;
$$('.money-input input').forEach((el) => bindMoneyInput(el));

function readProfile() {
  return {
    sectors: $$('#pfSectors .chip.active').map((b) => b.dataset.s),
    ticketMin: money($('#pfMin')),
    ticketMax: money($('#pfMax')),
    risk: $('#pfRisk .active')?.dataset.v || 'MID',
    horizonYears: Number($('#pfHorizon').value),
  };
}

function fillProfile(p) {
  state.profile = { ...state.profile, ...p, sectors: p.sectors || [] };
  $$('#pfSectors .chip').forEach((b) => b.classList.toggle('active', state.profile.sectors.includes(b.dataset.s)));
  $('#pfMin').value = p.ticketMin ? fmt.num(p.ticketMin) : '';
  $('#pfMax').value = p.ticketMax ? fmt.num(p.ticketMax) : '';
  $$('#pfRisk button').forEach((b) => b.classList.toggle('active', b.dataset.v === (p.risk || 'MID')));
  if (p.horizonYears) $('#pfHorizon').value = String(p.horizonYears);
}

async function loadList() {
  const q = new URLSearchParams();
  if (state.sector) q.set('sector', state.sector);
  q.set('sort', $('#sort').value);
  if (state.matched && !isInvestor) {
    const p = state.profile;
    p.sectors.forEach((s) => q.append('sectors', s));
    if (p.ticketMin) q.set('ticketMin', p.ticketMin);
    if (p.ticketMax) q.set('ticketMax', p.ticketMax);
    q.set('risk', p.risk);
    q.set('horizon', p.horizonYears);
  }
  $('#list').innerHTML = '<div class="loader"></div>';
  const list = await api('/api/v1/projects?' + q);
  const total = list.reduce((a, p) => a + Number(p.askAmount), 0);
  const label = state.sector ? state.sectors.find((s) => s.code === state.sector)?.label : 'Bütün istiqamətlər';
  $('#listHead').innerHTML = `<b>${esc(label)}</b> · ${list.length} layihə · investorlardan ümumi tələb ${fmt.compactMoney(total)}`;
  $('#list').innerHTML = list.length ? list.map(projectCard).join('') : `<div class="card card-pad" style="grid-column:1/-1">${emptyState('Bu istiqamətdə hələ layihə yoxdur. İlk siz təqdim edin!', 'lightbulb')}<div style="text-align:center"><a class="btn btn-primary" href="/layihe-yeni.html">Layihə təqdim et</a></div></div>`;
}

$$('#pfRisk button').forEach((b) => b.addEventListener('click', () => { $$('#pfRisk button').forEach((x) => x.classList.toggle('active', x === b)); }));
$('#sort').addEventListener('change', loadList);
$('#pfApply').addEventListener('click', async () => {
  state.profile = readProfile();
  state.matched = true;
  if (isInvestor) {
    try { await api('/api/v1/projects/profile', { method: 'PUT', body: state.profile }); toast('Profiliniz yadda saxlanıldı'); } catch (e) { toast(e.message, 'err'); }
  }
  $('#sort').value = 'match';
  loadList();
  $('#listHead').scrollIntoView({ behavior: 'smooth', block: 'center' });
});

(async () => {
  if (isInvestor) {
    try { fillProfile(await api('/api/v1/projects/profile')); state.matched = true; $('#pfNote').textContent = 'Profiliniz hesabınızda saxlanılır.'; } catch { /* boş */ }
  } else {
    $('#pfNote').innerHTML = session.loggedIn() ? '' : `Profili yadda saxlamaq üçün <a href="/giris.html?next=/layiheler.html">daxil olun</a>.`;
    $('#sort').value = 'new';
  }
  await loadSectors();
  if (isInvestor) fillProfile(state.profile);
  await loadList();
})().catch((e) => { $('#list').innerHTML = emptyState(e.message); });
