import { api, renderLayout, $, $$, on, esc, icon, fmt, CAT, DISTRICTS, ring, catBadge, withLoading, bindMoneyInput, moneyValue } from '../core.js';
import { satCover } from '../components.js';

renderLayout('match');

// Hazır profillər: bir kliklə formanı doldurur və nəticəni dərhal göstərir
const PRESETS = [
  { key: 'small', icon: 'sprout', title: 'Kiçik sahibkar', sub: '100-300 min ₼', hint: 'arıçılıq, kiçik sahə', budget: 300000, cats: ['ARICILIQ'], minArea: null, maxArea: 50, irr: false, elec: false },
  { key: 'orchard', icon: 'cherry', title: 'Orta intensiv bağ', sub: '0.5-1.5 mln ₼', hint: 'bağ və üzüm, 20-150 ha, suvarma', budget: 1500000, cats: ['BAGCILIQ', 'UZUMCULUK'], minArea: 20, maxArea: 150, irr: true, elec: false },
  { key: 'park', icon: 'factory', title: 'Böyük aqro-park', sub: '3 mln ₼ +', hint: 'taxıl, ferma, istixana, tam infrastruktur', budget: 5000000, cats: ['EKINCILIK', 'HEYVANDARLIQ', 'ISTIXANA'], minArea: null, maxArea: null, irr: true, elec: true },
];
$('#presets').innerHTML = PRESETS.map((p) => `<button type="button" class="mt-preset" data-preset="${p.key}" aria-pressed="false" title="${p.title}: ${p.hint}">${icon(p.icon)}<b>${p.title}</b><small>${p.sub}</small></button>`).join('');
$('#cats').innerHTML = Object.entries(CAT).map(([k, c]) => `<button type="button" class="chip chip-cat" data-cat="${k}" aria-pressed="false" style="--c:${c.hex}"><span class="sw" style="background:${c.color}"></span>${c.label}</button>`).join('');
$('#districts').innerHTML = Object.entries(DISTRICTS).map(([k, v]) => `<button type="button" class="chip chip-tg" data-d="${k}" aria-pressed="false">${v}</button>`).join('');

const press = (b, onState) => { b.classList.toggle('active', onState); b.setAttribute('aria-pressed', String(onState)); };
const need = (k) => $(`[data-need="${k}"]`).classList.contains('active');

// Büdcə: slayder və rəqəm sahəsi bir-birini izləyir
const budget = () => moneyValue($('#budgetNum')) || Number($('#budget').value);
const syncBudgetText = () => { $('#budgetNum').value = fmt.num(Number($('#budget').value)); };
$('#budget').addEventListener('input', () => { syncBudgetText(); changed(); });
bindMoneyInput($('#budgetNum'), (v) => {
  if (v) $('#budget').value = String(Math.min(Number($('#budget').max), Math.max(Number($('#budget').min), v)));
  changed();
});
syncBudgetText();

function request() {
  return {
    budget: budget(),
    categories: $$('[data-cat].active').map((b) => b.dataset.cat),
    districts: $$('[data-d].active').map((b) => b.dataset.d),
    minArea: $('#minArea').value ? Number($('#minArea').value) : null,
    maxArea: $('#maxArea').value ? Number($('#maxArea').value) : null,
    needIrrigation: need('irr'),
    needElectricity: need('elec'),
  };
}

// Canlı sayğac: forma dəyişdikcə (300 ms sonra) düymədə neçə uyğun sahə olduğu yazılır
let t, cseq = 0;
function changed({ keepPreset = false } = {}) {
  if (!keepPreset) $$('.mt-preset').forEach((b) => press(b, false));
  const n = $$('[data-d].active').length, a = $('#minArea').value || $('#maxArea').value;
  $('#distCount').textContent = [a ? `${$('#minArea').value || 0}-${$('#maxArea').value || '∞'} ha` : '', n ? `${n} rayon` : ''].filter(Boolean).join(' · ') || 'istənilən';
  clearTimeout(t);
  t = setTimeout(async () => {
    const my = ++cseq;
    try {
      const s = await api('/api/v1/recommendations/summary', { method: 'POST', body: request() });
      if (my !== cseq) return;
      $('#goLbl').textContent = s.suitable ? `${s.suitable} uyğun sahəni göstər` : 'Ən yaxın variantları göstər';
      $('#goHint').textContent = s.suitable ? `${s.total} sahədən ${s.suitable}-i profilinizə 60+ bal uyğundur` : 'Bu profilə 60+ bal uyğun sahə yoxdur: məhdudiyyətləri yumşaldın';
    } catch { /* sayğac vacib deyil */ }
  }, 300);
}

on($('#form'), 'click', '[data-cat],[data-d],[data-need]', (e, b) => { press(b, !b.classList.contains('active')); changed(); });
['#minArea', '#maxArea'].forEach((s) => $(s).addEventListener('input', () => changed()));
on($('#presets'), 'click', '[data-preset]', (e, b) => {
  const p = PRESETS.find((x) => x.key === b.dataset.preset);
  $('#budget').value = String(Math.min(Number($('#budget').max), p.budget));
  $('#budgetNum').value = fmt.num(p.budget);
  $$('[data-cat]').forEach((x) => press(x, p.cats.includes(x.dataset.cat)));
  $('#minArea').value = p.minArea ?? '';
  $('#maxArea').value = p.maxArea ?? '';
  $$('[data-d]').forEach((x) => press(x, false));
  press($('[data-need="irr"]'), p.irr);
  press($('[data-need="elec"]'), p.elec);
  $$('.mt-preset').forEach((x) => press(x, x === b));
  changed({ keepPreset: true });
  search({ scroll: true });
});

// Balın izahı: hər meyar neçə bal verdi (rəng + işarə + yazı)
function scoreChips(parts = []) {
  return `<div class="score-chips">${parts.filter((x) => x.points !== 0 || x.max > 0).map((x) => {
    const cls = x.points < 0 ? 'neg' : x.points >= x.max ? 'full' : x.points > 0 ? 'part' : 'zero';
    const val = x.points > 0 ? `+${x.points}` : String(x.points);
    return `<span class="sc ${cls}" title="${esc(x.label)}: ${val}${x.max ? ' / ' + x.max : ''} bal">${esc(x.label)} <b>${val}</b>${x.max ? `<small>/${x.max}</small>` : ''}</span>`;
  }).join('')}</div>`;
}

function render(list) {
  $('#results').innerHTML = `<div class="row between" style="margin-bottom:14px;flex-wrap:wrap;gap:8px"><h3 style="margin:0">${list.filter((m) => m.score >= 60).length || list.length} ${list.some((m) => m.score >= 60) ? 'uyğun sahə' : 'ən yaxın variant'}</h3><span class="small muted">Uyğunluq balına görə · bal 100 üzərindən, hər kartda nədən yığıldığı göstərilir</span></div>` +
    list.map((m, i) => {
      const p = m.plot;
      return `<div class="card" style="margin-bottom:14px;overflow:hidden">
        <div class="match-card">
          <a href="/sahe.html?id=${p.id}" style="display:block;min-height:160px;position:relative">${satCover(p)}<span class="badge badge-dark" style="position:absolute;left:10px;top:10px">#${i + 1}</span></a>
          <div style="padding:16px 18px;min-width:0">
            <div class="row-wrap" style="margin-bottom:6px">${catBadge(p.category)}<span class="small muted">${esc(p.code)} · ${esc(p.districtLabel)} · ${fmt.ha(p.areaHa)} · min. ${fmt.compactMoney(p.minInvestment)}</span></div>
            <h3 style="margin-bottom:8px"><a href="/sahe.html?id=${p.id}" style="color:var(--ink-900)">${esc(p.name)}</a></h3>
            ${scoreChips(m.breakdown)}
            <ul class="reasons">${m.reasons.slice(0, 4).map((r) => `<li class="ok">${icon('circle-check', 'i-sm')}${esc(r)}</li>`).join('')}${m.warnings.slice(0, 3).map((r) => `<li class="warn">${icon('triangle-alert', 'i-sm')}${esc(r)}</li>`).join('')}</ul>
          </div>
          <div class="match-score">
            ${ring(m.score, 'lg')}<span class="small muted" style="font-weight:600">uyğunluq</span>
            <a class="btn btn-sm btn-primary" href="/sahe.html?id=${p.id}">Bax</a>
          </div>
        </div></div>`;
    }).join('');
}

let sseq = 0;
async function search({ scroll = false } = {}) {
  const my = ++sseq;
  const list = await api('/api/v1/recommendations', { method: 'POST', body: request() });
  if (my !== sseq) return;
  render(list);
  if (scroll && window.matchMedia('(max-width: 980px)').matches) $('#results').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

$('#form').addEventListener('submit', (e) => {
  e.preventDefault();
  withLoading($('#go'), () => search({ scroll: true })).catch((err) => { $('#results').innerHTML = `<div class="alert alert-crit">${esc(err.message)}</div>`; });
});

// İlk açılışda boş səhifə yox: standart profil üzrə nəticələr dərhal görünür
press($('[data-need="irr"]'), true);
changed();
search().catch((err) => { $('#results').innerHTML = `<div class="alert alert-crit">${esc(err.message)}</div>`; });
