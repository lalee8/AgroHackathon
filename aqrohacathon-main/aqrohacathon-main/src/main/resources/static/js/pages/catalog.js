import { api, renderLayout, $, on, esc, icon, CAT, DISTRICTS, params, renderCompareTray, emptyState, toast } from '../core.js';
import { plotCard, bindCompare } from '../components.js';

renderLayout('catalog');
const cats = new Set();
$('#catChips').innerHTML = Object.entries(CAT).map(([k, c]) =>
  `<button class="chip chip-cat" data-cat="${k}" aria-pressed="false" style="--c:${c.hex}"><span class="sw" style="background:${c.color}"></span>${icon(c.icon, 'i-sm')}${c.label}</button>`).join('');
$('#district').innerHTML += Object.entries(DISTRICTS).map(([k, v]) => `<option value="${k}">${v}</option>`).join('');

const DEFAULT_SORT = 'deadline';
const paintChips = () => document.querySelectorAll('[data-cat]').forEach((b) => {
  const on = cats.has(b.dataset.cat);
  b.classList.toggle('active', on);
  b.setAttribute('aria-pressed', String(on));
});

/** Filtrlər URL-dən oxunur (paylaşılan link eyni nəticəni açır). Köhnə parametr adları da qəbul edilir. */
function readUrl() {
  const p = params();
  (p.get('kateqoriya') || p.getAll('category').join(',')).split(',').filter((k) => CAT[k]).forEach((k) => cats.add(k));
  const set = (sel, v) => { if (v && [...$(sel).options].some((o) => o.value === v)) $(sel).value = v; };
  set('#district', p.get('rayon') || p.get('district'));
  set('#area', p.get('olcu'));
  set('#status', p.get('status'));
  set('#sort', p.get('sirala') || DEFAULT_SORT);
  $('#q').value = p.get('q') || '';
  $('#irr').checked = p.get('suvarma') === '1';
  $('#elec').checked = p.get('elektrik') === '1';
  $('#open').checked = p.get('aciq') === '1';
  paintChips();
}

function syncUrl() {
  const u = new URLSearchParams();
  if (cats.size) u.set('kateqoriya', [...cats].join(','));
  if ($('#district').value) u.set('rayon', $('#district').value);
  if ($('#area').value) u.set('olcu', $('#area').value);
  if ($('#status').value) u.set('status', $('#status').value);
  if ($('#sort').value !== DEFAULT_SORT) u.set('sirala', $('#sort').value);
  if ($('#q').value.trim()) u.set('q', $('#q').value.trim());
  if ($('#irr').checked) u.set('suvarma', '1');
  if ($('#elec').checked) u.set('elektrik', '1');
  if ($('#open').checked) u.set('aciq', '1');
  history.replaceState(null, '', u.toString() ? `?${u.toString().replace(/%2C/g, ',')}` : location.pathname);
  const filtered = [...u.keys()].some((k) => k !== 'sirala');
  $('#reset').hidden = !filtered;
}

let seq = 0;
async function load() {
  syncUrl();
  const my = ++seq;
  const qs = new URLSearchParams();
  cats.forEach((c) => qs.append('category', c));
  if ($('#district').value) qs.set('district', $('#district').value);
  const [amin, amax] = ($('#area').value || '-').split('-');
  if (amin) qs.set('minArea', amin);
  if (amax) qs.set('maxArea', amax);
  if ($('#status').value) qs.set('status', $('#status').value);
  qs.set('sort', $('#sort').value || DEFAULT_SORT);
  if ($('#q').value.trim()) qs.set('q', $('#q').value.trim());
  if ($('#irr').checked) qs.set('irrigation', 'true');
  if ($('#elec').checked) qs.set('electricity', 'true');
  if ($('#open').checked) qs.set('openOnly', 'true');
  $('#grid').classList.add('is-loading');
  try {
    const list = await api('/api/v1/plots?' + qs);
    if (my !== seq) return;
    $('#count').textContent = `${list.length} sahə`;
    $('#grid').innerHTML = list.length ? list.map((x) => plotCard(x)).join('') : emptyState('Filtrlərə uyğun sahə tapılmadı', 'search');
  } catch (e) {
    $('#grid').innerHTML = emptyState(e.message);
  } finally {
    if (my === seq) $('#grid').classList.remove('is-loading');
  }
}

on($('#catChips'), 'click', '[data-cat]', (e, b) => {
  const k = b.dataset.cat;
  cats.has(k) ? cats.delete(k) : cats.add(k);
  paintChips();
  load();
});
['#district', '#area', '#status', '#sort', '#irr', '#elec', '#open'].forEach((s) => $(s).addEventListener('change', load));
// Yazdıqca axtarış: hər hərfdə yox, yazmağa 300 ms ara verəndə
let t;
$('#q').addEventListener('input', () => { clearTimeout(t); t = setTimeout(load, 300); });
$('#q').addEventListener('keydown', (e) => { if (e.key === 'Enter') { clearTimeout(t); load(); } });
$('#reset').addEventListener('click', () => {
  cats.clear();
  ['#district', '#area', '#status'].forEach((s) => { $(s).value = ''; });
  $('#q').value = '';
  ['#irr', '#elec', '#open'].forEach((s) => { $(s).checked = false; });
  paintChips();
  load();
});
$('#share').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(location.href);
    toast('Link kopyalandı: bu filtrlərlə açılır');
  } catch {
    toast(location.href);
  }
});
bindCompare(document.body, renderCompareTray);
document.addEventListener('compare-changed', load);
readUrl();
load();
