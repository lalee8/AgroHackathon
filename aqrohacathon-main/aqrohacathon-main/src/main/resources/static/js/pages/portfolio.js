// Portfel qurucusu: büdcənin korrelyasiyalı Monte-Carlo ilə diversifikasiya olunmuş bölgüsü
import { api, renderLayout, $, $$, esc, icon, fmt, params, DISTRICTS, errorToast } from '../core.js';
import { USE } from '../potential.js';
import { md } from '../advisor.js';

renderLayout('portfolio');

// Kateqoriya palitrası (validator: qonşu cütlər CVD ΔE ≥ 8), sabit ardıcıllıq; 8-dən artıq mövqe "Digər"
const SERIES = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'];
const OTHER = '#c3c2b7';
const C = { band90: 'rgba(158, 197, 244, .55)', band50: 'rgba(85, 152, 231, .55)', median: '#1c5cab', grid: '#e1e0d9', axis: '#c3c2b7', muted: '#898781', ink: '#52514e', single: '#b9b8b0', front: '#2a78d6', rec: '#eb6834', naive: '#e34948' };
const MAX_SLICES = 7;
// Hazır portfellər: büdcə + risk + üfüq bir kliklə
const PRESETS = [
  { key: 'cons', icon: 'shield-check', title: 'Konservativ', budget: 250_000, risk: 20, horizon: 10, sub: '250 min ₼ · risk 20' },
  { key: 'bal', icon: 'scale', title: 'Balanslı', budget: 1_000_000, risk: 50, horizon: 15, sub: '1 mln ₼ · risk 50' },
  { key: 'aggr', icon: 'trending-up', title: 'Aqressiv', budget: 5_000_000, risk: 85, horizon: 20, sub: '5 mln ₼ · risk 85' },
];
const presetOf = () => PRESETS.find((x) => x.budget === st.budget && x.risk === st.risk && x.horizon === st.horizon)?.key;
const riskHint = (r) => (r <= 40 ? 'Pis illərdə itkini azaltmağa üstünlük verilir' : r <= 60 ? 'Gəlir və risk arasında tarazlıq' : 'Ən yüksək gözlənilən gəlirə üstünlük verilir');
const USES = ['BAGCILIQ', 'UZUMCULUK', 'ISTIXANA', 'ARICILIQ', 'EKINCILIK', 'HEYVANDARLIQ', 'SOYUDUCU', 'GUNES', 'AQROTURIZM'];

const q = params();
const st = {
  budget: Number(q.get('budget')) || 1_000_000,
  risk: q.has('risk') ? Number(q.get('risk')) : 50,
  horizon: Number(q.get('horizon')) || 15,
  maxShare: Number(q.get('maxShare')) || 35,
  maxSector: Number(q.get('maxSector')) || 40,
  districts: (q.get('districts') || '').split(',').filter(Boolean),
  uses: (q.get('uses') || '').split(',').filter(Boolean),
  res: null,
  flowMode: 'cum',
};
const charts = {};

const money = (v) => (v < 0 ? '−' : '') + fmt.compactMoney(Math.abs(v));
const signedPct = (v) => `${v > 0 ? '+' : v < 0 ? '−' : ''}${Math.abs(Math.round(v))}%`;
const riskWord = (r) => (r <= 20 ? 'çox ehtiyatlı' : r <= 40 ? 'ehtiyatlı' : r <= 60 ? 'balanslı' : r <= 80 ? 'cəsarətli' : 'çox cəsarətli');
const useLabel = (u) => ({ BAGCILIQ: 'Bağ', UZUMCULUK: 'Üzüm', ISTIXANA: 'İstixana', ARICILIQ: 'Arıçılıq', EKINCILIK: 'Taxıl', HEYVANDARLIQ: 'Ferma', SOYUDUCU: 'Anbar', GUNES: 'Günəş', AQROTURIZM: 'Turizm' }[u] || u);
const scaleTxt = (p) => (p.unit === 'ha' ? fmt.ha(p.scale) : p.unit === 'MWp' ? `${fmt.num1(p.scale)} MWp` : `${fmt.num(p.scale)} ${p.unit}`);

// ------------------------------------------------------------------ idarəetmə

function renderControls() {
  $('#controls').innerHTML = `
    <div class="field"><label>Hazır portfel</label>
      <div class="mt-presets pf-presets">${PRESETS.map((x) => `<button type="button" class="mt-preset ${x.key === presetOf() ? 'active' : ''}" data-preset="${x.key}" aria-pressed="${x.key === presetOf()}">${icon(x.icon)}<b>${x.title}</b><small>${x.sub}</small></button>`).join('')}</div>
    </div>
    <div class="field"><label for="budget">Büdcə</label>
      <div class="pf-budget"><input class="input num" id="budget" inputmode="numeric" autocomplete="off" value="${fmt.num(st.budget)}"><span>₼</span></div>
    </div>
    <div class="field"><label for="risk">Risk meyli: <b id="riskLbl"></b></label>
      <input type="range" id="risk" min="0" max="100" step="5" value="${st.risk}">
      <div class="pf-scale"><span>Ehtiyatlı</span><span>Balanslı</span><span>Cəsarətli</span></div>
      <div class="hint" id="riskHint"></div>
    </div>
    <div class="field"><label>Üfüq</label>
      <div class="seg" id="hz">${[10, 15, 20, 25].map((h) => `<button type="button" data-h="${h}" class="${h === st.horizon ? 'active' : ''}">${h} il</button>`).join('')}</div>
      <div class="hint">Bağ, üzüm və günəş stansiyası 25 il gəlir verir: uzun üfüq onların dəyərini göstərir</div>
    </div>
    <details class="pf-adv" ${st.districts.length || st.uses.length ? 'open' : ''}><summary><span class="pf-adv-ico">${icon('sliders-horizontal', 'i-sm')}</span><span><b>Əlavə məhdudiyyətlər</b><small id="advSum">${advSummary()}</small></span></summary>
      <div class="form-grid">
        <div class="field"><label for="maxShare">Bir mövqe ən çox</label><select class="input" id="maxShare">${[20, 35, 50, 100].map((v) => `<option value="${v}" ${v === st.maxShare ? 'selected' : ''}>${v === 100 ? 'limitsiz' : v + '%'}</option>`).join('')}</select></div>
        <div class="field"><label for="maxSector">Bir istiqamət ən çox</label><select class="input" id="maxSector">${[30, 40, 60, 100].map((v) => `<option value="${v}" ${v === st.maxSector ? 'selected' : ''}>${v === 100 ? 'limitsiz' : v + '%'}</option>`).join('')}</select></div>
      </div>
      <div class="field"><label>Yalnız bu rayonlar</label><div class="pf-chips" id="dists">${Object.entries(DISTRICTS).map(([k, l]) => `<button type="button" class="chip ${st.districts.includes(k) ? 'active' : ''}" data-d="${k}">${esc(l)}</button>`).join('')}</div></div>
      <div class="field"><label>Yalnız bu istiqamətlər</label><div class="pf-chips" id="uses">${USES.map((u) => `<button type="button" class="chip ${st.uses.includes(u) ? 'active' : ''}" data-u="${u}">${icon(USE[u].icon, 'i-sm')}${useLabel(u)}</button>`).join('')}</div></div>
    </details>
    <div class="sim-run">
      <button class="btn btn-primary btn-block btn-lg" id="go" type="button">${icon('sparkles')}Portfeli qur</button>
      <p class="small muted">400 ortaq ssenaridə seçilir, 1000 ssenaridə yoxlanılır</p>
    </div>`;

  const riskLbl = () => { $('#riskLbl').textContent = `${st.risk}/100 · ${riskWord(st.risk)}`; $('#riskHint').textContent = riskHint(st.risk); };
  riskLbl();
  $('#risk').addEventListener('input', (e) => { st.risk = Number(e.target.value); riskLbl(); });
  $('#risk').addEventListener('change', run);
  $('#budget').addEventListener('change', (e) => {
    const v = Number(String(e.target.value).replace(/[^\d]/g, ''));
    if (v) st.budget = v;
    e.target.value = fmt.num(st.budget);
  });
  $('#budget').addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.target.dispatchEvent(new Event('change')); run(); } });
  $('#controls').addEventListener('click', (e) => {
    const pr = e.target.closest('[data-preset]');
    if (pr) {
      const x = PRESETS.find((k) => k.key === pr.dataset.preset);
      Object.assign(st, { budget: x.budget, risk: x.risk, horizon: x.horizon });
      $('#budget').value = fmt.num(st.budget);
      $('#risk').value = String(st.risk);
      riskLbl();
      $$('#hz button').forEach((b) => b.classList.toggle('active', Number(b.dataset.h) === st.horizon));
      syncPresets();
      run();
      return;
    }
    const h = e.target.closest('[data-h]');
    if (h) { st.horizon = Number(h.dataset.h); $$('#hz button').forEach((x) => x.classList.toggle('active', x === h)); syncPresets(); run(); return; }
    const d = e.target.closest('[data-d]');
    if (d) { toggle(st.districts, d.dataset.d); d.classList.toggle('active'); $('#advSum').textContent = advSummary(); return; }
    const u = e.target.closest('[data-u]');
    if (u) { toggle(st.uses, u.dataset.u); u.classList.toggle('active'); $('#advSum').textContent = advSummary(); }
  });
  $('#risk').addEventListener('input', syncPresets);
  $('#budget').addEventListener('change', syncPresets);
  ['#maxShare', '#maxSector'].forEach((sel) => $(sel).addEventListener('change', () => { $('#advSum').textContent = advSummary(); }));
  $('#maxShare').addEventListener('change', (e) => { st.maxShare = Number(e.target.value); });
  $('#maxSector').addEventListener('change', (e) => { st.maxSector = Number(e.target.value); });
  $('#go').addEventListener('click', run);
}

function syncPresets() {
  const k = presetOf();
  $$('[data-preset]').forEach((b) => { b.classList.toggle('active', b.dataset.preset === k); b.setAttribute('aria-pressed', String(b.dataset.preset === k)); });
}

function advSummary() {
  const parts = [];
  if (st.maxShare !== 35) parts.push(`mövqe ≤ ${st.maxShare === 100 ? 'limitsiz' : st.maxShare + '%'}`);
  if (st.maxSector !== 40) parts.push(`istiqamət ≤ ${st.maxSector === 100 ? 'limitsiz' : st.maxSector + '%'}`);
  if (st.districts.length) parts.push(`${st.districts.length} rayon`);
  if (st.uses.length) parts.push(`${st.uses.length} istiqamət`);
  return parts.length ? parts.join(' · ') : 'Standart: mövqe ≤ 35%, istiqamət ≤ 40%';
}

function toggle(arr, v) { const i = arr.indexOf(v); if (i >= 0) arr.splice(i, 1); else arr.push(v); }

function query() {
  const p = new URLSearchParams({ budget: st.budget, risk: st.risk, horizon: st.horizon, maxShare: st.maxShare, maxSector: st.maxSector });
  if (st.districts.length) p.set('districts', st.districts.join(','));
  if (st.uses.length) p.set('uses', st.uses.join(','));
  return p;
}

let seq = 0;
async function run() {
  const my = ++seq;
  const p = query();
  history.replaceState(null, '', `?${p}`);
  $('#main').classList.add('pf-busy');
  $('#go').disabled = true;
  try {
    const res = await api(`/api/v1/portfolio?${p}`);
    if (my !== seq) return;
    st.res = res;
    render();
  } catch (e) {
    if (my !== seq) return;
    $('#main').innerHTML = `<div class="card"><div class="sim-idle"><div class="sim-idle-ico">${icon('circle-help')}</div><h2>Portfel qurulmadı</h2><p>${esc(e.message || 'Xəta')}</p></div></div>`;
    errorToast(e);
  } finally {
    if (my === seq) { $('#main').classList.remove('pf-busy'); $('#go').disabled = false; }
  }
}

// ------------------------------------------------------------------ nəticə

function render() {
  const r = st.res, t = r.totals, d = r.diversification, im = r.impact;
  Object.values(charts).forEach((c) => c.destroy());
  const pos = r.positions;
  const shown = pos.slice(0, MAX_SLICES + (pos.length === MAX_SLICES + 1 ? 1 : 0));
  const rest = pos.slice(shown.length);
  const colorOf = (i) => (i < shown.length ? SERIES[i] : OTHER);

  $('#main').innerHTML = `
    ${r.notice ? `<div class="alert alert-warn">${icon('triangle-alert')}<div>${esc(r.notice)}</div></div>` : ''}
    <div class="kpis pf-kpis">
      <div class="kpi hero-kpi"><div class="lbl">${icon('banknote', 'i-sm')}Ayda orta xalis gəlir</div><div class="val num">${money(t.monthlyNetP50)}</div><div class="sub">ildə ${money(t.annualNetP50)} · pis ssenaridə ildə ${money(t.annualNetP10)}</div></div>
      <div class="kpi"><div class="lbl">${icon('rotate-ccw', 'i-sm')}Pul qayıdır</div><div class="val num">${t.paybackP50 ? `${t.paybackP50}-ci il` : `${r.horizon}+ il`}</div><div class="sub">IRR ${t.irrP50 == null ? '-' : fmt.num1(t.irrP50) + '%'} · ${r.horizon} illik üfüq</div></div>
      <div class="kpi"><div class="lbl">${icon('shield', 'i-sm')}Zərər ehtimalı</div><div class="val num">${fmt.num1(t.probLoss * 100)}%</div><div class="sub">pis ssenaridə xalis dəyər ${money(t.npvP10)}</div></div>
      <div class="kpi"><div class="lbl">${icon('house', 'i-sm')}Böyük Qayıdış</div><div class="val num">${fmt.num(im.families)} ailə</div><div class="sub">${fmt.num(im.directJobs)} daimi iş yeri · ${fmt.num(im.people)} nəfər</div></div>
    </div>

    <div class="card card-pad pf-summary">
      <div class="between pf-sumhead"><h3>${icon('lightbulb')}Bu portfel nə deyir</h3><button class="btn btn-ai btn-sm" id="aiBtn" type="button">${icon('sparkles', 'i-sm')}AI ilə izah et</button></div>
      <ul class="pf-bullets">${r.summary.map((s) => `<li>${esc(s)}</li>`).join('')}</ul>
      <div id="aiBox" class="pf-ai hidden"></div>
    </div>

    <div class="card">
      <div class="card-head"><h3>${icon('chart-pie')}Büdcənin bölgüsü</h3><span class="small muted">${pos.length} mövqe · effektiv diversifikasiya ${fmt.num1(d.effectiveN)} · ${r.candidates} variant arasından</span></div>
      <div class="pf-alloc">
        <div class="pf-donut"><canvas id="donut" aria-label="Büdcənin mövqelər üzrə bölgüsü" role="img"></canvas><div class="pf-donut-c"><b class="num">${money(t.invested)}</b><span>yerləşdirildi${t.cash > 0 ? `<br>${money(t.cash)} nağd` : ''}</span></div></div>
        <div class="pf-mix">
          ${mix('İstiqamətlər üzrə', group(pos, (p) => p.use), (k) => `${icon(USE[k]?.icon || 'map-pin', 'i-sm')}${esc(useLabel(k))}`)}
          ${mix('Zona üzrə', group(pos, (p) => p.zone), (k) => `${icon(k === 'dağlıq' ? 'mountain' : 'sprout', 'i-sm')}${k === 'dağlıq' ? 'Dağlıq zona' : 'Aran zona'}`)}
          ${mix('Rayonlar üzrə', group(pos, (p) => p.district).slice(0, 4), (k) => `${icon('map-pin', 'i-sm')}${esc(k)}`)}
        </div>
      </div>
      <div class="table-wrap"><table class="table pf-table">
        <thead><tr><th>Mövqe</th><th class="r">İnvestisiya</th><th class="r">İllik xalis (P50)</th><th class="r">Zərər ehtimalı</th><th></th></tr></thead>
        <tbody>${pos.map((p, i) => `<tr>
          <td><div class="pf-pos"><span class="pf-sw" style="background:${colorOf(i)}"></span><span class="pf-ico">${icon(USE[p.use]?.icon || 'map-pin', 'i-sm')}</span><div><b>${esc(p.code)} · ${esc(p.useLabel)}</b><div class="small muted">${esc(p.plotName)} · ${esc(p.district)}, ${esc(p.zone)} · ${scaleTxt(p)}</div></div></div></td>
          <td class="r num"><b>${money(p.amount)}</b><div class="pf-share"><i style="width:${Math.min(100, p.sharePct)}%;background:${colorOf(i)}"></i></div><span class="small muted">${fmt.num1(p.sharePct)}% büdcənin</span></td>
          <td class="r num">${money(p.annualNetP50)}</td>
          <td class="r num">${fmt.num1(p.probLoss * 100)}%</td>
          <td class="r"><a class="btn btn-ghost btn-sm" href="${esc(p.url)}&horizon=${r.horizon}" title="Bu mövqeni simulyatorda il-il yaşa">${icon('play', 'i-sm')}<span class="hide-sm">Simulyator</span></a></td>
        </tr>`).join('')}</tbody>
      </table></div>
    </div>

    ${d.naiveLabel ? `<div class="pf-vs">
      <div class="card card-pad pf-vs-card bad"><div class="overline">${icon('triangle-alert', 'i-sm')} Hamısı bir varianta</div><h3>${esc(d.naiveLabel)}</h3>
        <div class="pf-vs-row"><span>İnvestisiya</span><b class="num">${money(d.naiveInvested)}</b></div>
        <div class="pf-vs-row"><span>Gözlənilən xalis dəyər</span><b class="num">${fmt.num1(d.naiveRetPct)}% büdcənin</b></div>
        <div class="pf-vs-row"><span>Pis ssenaridə (P10) xalis dəyər</span><b class="num">${money(d.naiveNpvP10)}</b></div>
        <p class="small muted">Ən yüksək gözlənilən gəlirli tək variant. Bir quraqlıq və ya qiymət düşüşü bütün pulu birdən vurur.</p></div>
      <div class="card card-pad pf-vs-card good"><div class="overline">${icon('shield-check', 'i-sm')} Tövsiyə olunan portfel</div><h3>${pos.length} mövqe, ${new Set(pos.map((p) => p.use)).size} istiqamət, ${new Set(pos.map((p) => p.district)).size} rayon</h3>
        <div class="pf-vs-row"><span>İnvestisiya</span><b class="num">${money(t.invested)}</b></div>
        <div class="pf-vs-row"><span>Gözlənilən xalis dəyər</span><b class="num">${fmt.num1(t.npvMean / r.budget * 100)}% büdcənin</b></div>
        <div class="pf-vs-row"><span>Pis ssenaridə (P10) xalis dəyər</span><b class="num">${money(t.npvP10)}</b></div>
        <p class="small">Diversifikasiya pis ssenari itkisini <b>${Math.round(d.benefitPct)}%</b> azaldır${d.hedgeLabel ? `: <b>${esc(d.hedgeLabel)}</b> və <b>${esc(d.hedgeWith)}</b> arasında korrelyasiya ${fmt.num1(d.hedgeCorrelation)}` : ''}.</p></div>
    </div>` : ''}

    <div class="card card-pad">
      <div class="between pf-h"><div><h3>${icon('target')}Səmərəli sərhəd</h3><p class="small muted">Hər nöqtə bir portfeldir. Sağa getdikcə pis ssenaridəki itki, yuxarı getdikcə gözlənilən xalis dəyər artır (hər ikisi büdcənin faizi ilə). Boz nöqtələr: bütün büdcə tək variantda.</p></div></div>
      <div class="chart-legend"><span><i class="pf-dot" style="background:${C.single}"></i>Tək variant</span><span><i class="pf-line" style="background:${C.front}"></i>Səmərəli portfellər</span><span><i class="pf-dot lg" style="background:${C.rec}"></i>Sizin portfel</span>${d.naiveLabel ? `<span><i class="pf-dot ring" style="border-color:${C.naive}"></i>Hamısı bir varianta</span>` : ''}</div>
      <div class="chart-box"><canvas id="frontier" role="img" aria-label="Risk və gəlir: səmərəli sərhəd"></canvas></div>
    </div>

    <div class="card card-pad">
      <div class="between pf-h"><h3>${icon('chart-line')}Pul axını: ${r.horizon} il, 1000 ssenari</h3>
        <div class="seg" id="flowSeg"><button type="button" data-f="cum" class="${st.flowMode === 'cum' ? 'active' : ''}">Məcmu pul</button><button type="button" data-f="pos" class="${st.flowMode === 'pos' ? 'active' : ''}">Mövqelər üzrə illik</button></div></div>
      <div class="chart-legend" id="flowLegend"></div>
      <div class="chart-box"><canvas id="flow" role="img" aria-label="Portfelin pul axını"></canvas></div>
    </div>

    ${r.stress.length ? `<div class="card card-pad">
      <h3>${icon('trending-down')}Stress test: pis il gələndə</h3>
      <p class="small muted">Ortaq ssenarilərdə həmin hadisənin baş verdiyi illər seçilir və portfelin gəliri adi illə müqayisə olunur.</p>
      <div class="pf-stress">${r.stress.map((s) => `<div class="pf-st">
        <div class="pf-st-top"><b>${esc(s.title)}</b><span class="pf-st-val num">${signedPct(s.portfolioChangePct)}</span></div>
        <div class="small muted">${esc(s.description)}</div>
        <div class="pf-st-bar"><i style="width:${Math.min(100, Math.abs(s.portfolioChangePct))}%"></i></div>
        ${s.worstLabel ? `<div class="small">${icon('trending-down', 'i-sm')} Ən çox təsirlənən: <b>${esc(s.worstLabel)}</b> ${signedPct(s.worstChangePct)}</div>` : ''}
        ${s.bestLabel ? `<div class="small">${icon('shield-check', 'i-sm')} Ən dayanıqlı: <b>${esc(s.bestLabel)}</b> ${signedPct(s.bestChangePct)}</div>` : ''}
      </div>`).join('')}</div>
    </div>` : ''}

    <div class="pf-two">
      <div class="card card-pad"><h3>${icon('network')}Mövqelər bir-birini necə sığortalayır</h3>
        <p class="small muted">Xalis dəyərin ssenarilər üzrə korrelyasiyası. 0-a yaxın və ya mənfi: birinin pis ili digərinin pis ili deyil.</p>
        <div id="heat"></div>
        <div class="pf-heat-legend small muted"><span>−1 bir-birini sığortalayır</span><i></i><span>+1 birlikdə düşür</span></div>
      </div>
      <div class="card card-pad"><h3>${icon('users')}Böyük Qayıdış: sosial təsir</h3>
        <div class="pf-impact">
          ${fact('users', fmt.num(im.directJobs + im.indirectJobs), 'iş yeri', `${fmt.num(im.directJobs)} daimi, ${fmt.num(im.indirectJobs)} dolayı`)}
          ${fact('house', fmt.num(im.families), 'ailənin qayıdışı', `≈ ${fmt.num(im.people)} nəfər`)}
          ${fact('banknote', money(im.wageFund), 'əmək haqqı/il', 'yerli iqtisadiyyata')}
          ${fact('wheat', fmt.num(Math.round(im.foodTons)), 't ərzaq/il', 'yetkin ildə')}
          ${fact('leaf', fmt.num(Math.round(im.co2Tons)), 't CO₂/il', 'udulan və ya qarşısı alınan')}
          ${fact('flame', im.gasM3 >= 1e6 ? fmt.num1(im.gasM3 / 1e6) + ' mln' : fmt.num(Math.round(im.gasM3 / 1000)) + ' min', 'm³ qaz/il', 'ixraca qalır')}
          ${fact('map', fmt.num(Math.round(im.areaHa)), 'ha torpaq', 'yenidən istifadəyə')}
          ${fact('gauge', fmt.num1(r.jobsPerMillion), 'iş yeri / 1 mln ₼', 'inkişaf göstəricisi')}
        </div>
      </div>
    </div>

    <details class="card card-pad pf-assume"><summary>${icon('info', 'i-sm')} Model necə işləyir: fərziyyələr və mənbələr</summary><ul>${r.assumptions.map((a) => `<li>${esc(a)}</li>`).join('')}</ul></details>`;

  drawDonut(shown, rest, colorOf);
  drawFrontier();
  drawFlow(shown, rest);
  drawHeat(shown);
  $('#flowSeg').addEventListener('click', (e) => { const b = e.target.closest('[data-f]'); if (!b) return; st.flowMode = b.dataset.f; $$('#flowSeg button').forEach((x) => x.classList.toggle('active', x === b)); charts.flow?.destroy(); drawFlow(shown, rest); });
  $('#aiBtn').addEventListener('click', explain);
}

function fact(ic, val, lbl, sub) {
  return `<div class="pf-fact"><span class="pf-fact-ico">${icon(ic, 'i-sm')}</span><b class="num">${val}</b><span>${lbl}</span><small>${sub}</small></div>`;
}

/** Mövqeləri açara görə qruplaşdırır, məbləğə görə azalan. */
function group(pos, key) {
  const m = new Map();
  pos.forEach((p) => m.set(key(p), (m.get(key(p)) || 0) + p.amount));
  return [...m.entries()].sort((a, b) => b[1] - a[1]);
}

function mix(title, rows, label) {
  const total = st.res.totals.invested || 1;
  return `<div class="pf-mix-g"><div class="pf-mix-h">${title}</div>${rows.map(([k, v]) => `<div class="pf-mix-r"><span class="pf-mix-l">${label(k)}</span><span class="pf-mix-b"><i style="width:${(v / total) * 100}%"></i></span><span class="num pf-mix-v">${Math.round((v / total) * 100)}%</span></div>`).join('')}</div>`;
}

function drawDonut(shown, rest, colorOf) {
  const data = shown.map((p) => p.amount).concat(rest.length ? [rest.reduce((s, p) => s + p.amount, 0)] : []);
  const labels = shown.map((p) => `${p.code} · ${p.useLabel}`).concat(rest.length ? [`Digər ${rest.length} mövqe`] : []);
  charts.donut = new Chart($('#donut'), {
    type: 'doughnut',
    data: { labels, datasets: [{ data, backgroundColor: data.map((_, i) => colorOf(i)), borderColor: '#fff', borderWidth: 2, hoverOffset: 6 }] },
    options: { cutout: '68%', maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: (i) => ` ${money(i.raw)} (${fmt.num1(i.raw / st.res.budget * 100)}%)` } } } },
  });
}

function drawFrontier() {
  const r = st.res, d = r.diversification;
  const singles = r.singles.map((s) => ({ x: s.riskPct, y: s.retPct, label: s.label }));
  const eff = r.frontier.filter((f) => f.efficient).sort((a, b) => a.riskPct - b.riskPct).map((f) => ({ x: f.riskPct, y: f.retPct, f }));
  const others = r.frontier.filter((f) => !f.efficient && !f.recommended).map((f) => ({ x: f.riskPct, y: f.retPct, f }));
  const rec = r.frontier.filter((f) => f.recommended).map((f) => ({ x: f.riskPct, y: f.retPct, f }));
  const naive = d.naiveLabel ? [{ x: d.naiveRiskPct, y: d.naiveRetPct, label: d.naiveLabel }] : [];
  charts.frontier = new Chart($('#frontier'), {
    type: 'scatter',
    data: {
      datasets: [
        { key: 'rec', data: rec, backgroundColor: C.rec, borderColor: '#fff', borderWidth: 2, pointRadius: 9, pointHoverRadius: 11, order: 0 },
        { key: 'naive', data: naive, backgroundColor: 'rgba(255,255,255,0)', borderColor: C.naive, borderWidth: 2.5, pointRadius: 8, pointHoverRadius: 10, order: 1 },
        { key: 'eff', data: eff, showLine: true, borderColor: C.front, backgroundColor: C.front, borderWidth: 2, pointRadius: 4.5, pointHoverRadius: 7, pointBorderColor: '#fff', pointBorderWidth: 1.5, tension: 0.15, order: 2 },
        { key: 'other', data: others, backgroundColor: 'rgba(42,120,214,.35)', pointRadius: 4, order: 3 },
        { key: 'single', data: singles, backgroundColor: C.single, pointRadius: 4, pointHoverRadius: 6, order: 4 },
      ],
    },
    options: {
      maintainAspectRatio: false, animation: { duration: 300 },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (i) => {
              const p = i.raw, k = i.dataset.key;
              if (k === 'single' || k === 'naive') return ` ${p.label}: gəlir ${fmt.num1(p.y)}%, risk ${fmt.num1(p.x)}%`;
              return ` ${k === 'rec' ? 'Sizin portfel' : 'Portfel'}: ${p.f.positions} mövqe, ${money(p.f.invested)} · gəlir ${fmt.num1(p.y)}%, risk ${fmt.num1(p.x)}%`;
            },
          },
        },
      },
      scales: {
        x: { title: { display: true, text: 'Risk: pis 10% ssenaridə geri qalma, büdcənin %-i', color: C.ink }, grid: { color: C.grid }, border: { color: C.axis }, ticks: { color: C.muted, callback: (v) => `${v}%` } },
        y: { title: { display: true, text: 'Gözlənilən xalis dəyər (NPV), büdcənin %-i', color: C.ink }, grid: { color: C.grid }, border: { display: false }, ticks: { color: C.muted, callback: (v) => `${v}%` } },
      },
    },
  });
}

function drawFlow(shown, rest) {
  const r = st.res, base = new Date().getFullYear();
  const legend = $('#flowLegend');
  if (st.flowMode === 'cum') {
    legend.innerHTML = `<span><i class="pf-sq" style="background:${C.band90}"></i>80% ssenari</span><span><i class="pf-sq" style="background:${C.band50}"></i>50% ssenari</span><span><i class="pf-line" style="background:${C.median}"></i>Mediana</span>`;
    const labels = r.fan.map((f) => base + f.year);
    const zero = { id: 'zero', afterDatasetsDraw(c) { const y = c.scales.y.getPixelForValue(0); const { left, right } = c.chartArea; const ctx = c.ctx; ctx.save(); ctx.strokeStyle = C.axis; ctx.setLineDash([4, 4]); ctx.beginPath(); ctx.moveTo(left, y); ctx.lineTo(right, y); ctx.stroke(); ctx.restore(); } };
    charts.flow = new Chart($('#flow'), {
      type: 'line',
      data: {
        labels,
        datasets: [
          { key: 'p10', data: r.fan.map((f) => f.p10), borderWidth: 0, pointRadius: 0, fill: false, order: 5 },
          { key: 'p90', data: r.fan.map((f) => f.p90), borderWidth: 0, pointRadius: 0, fill: '-1', backgroundColor: C.band90, order: 4 },
          { key: 'p25', data: r.fan.map((f) => f.p25), borderWidth: 0, pointRadius: 0, fill: false, order: 3 },
          { key: 'p75', data: r.fan.map((f) => f.p75), borderWidth: 0, pointRadius: 0, fill: '-1', backgroundColor: C.band50, order: 2 },
          { key: 'p50', data: r.fan.map((f) => f.p50), borderColor: C.median, borderWidth: 2, pointRadius: 0, pointHoverRadius: 4, tension: 0.25, order: 1 },
        ],
      },
      options: {
        maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: false }, tooltip: { filter: (i) => ['p50', 'p90'].includes(i.dataset.key), callbacks: { label: (i) => { const f = r.fan[i.dataIndex]; return i.dataset.key === 'p90' ? ` 80% ssenari: ${money(f.p10)} ... ${money(f.p90)}` : ` Mediana: ${money(f.p50)}`; } } } },
        scales: { x: { grid: { display: false }, ticks: { color: C.muted }, border: { color: C.axis } }, y: { grid: { color: C.grid }, border: { display: false }, ticks: { color: C.muted, callback: (v) => money(v), maxTicksLimit: 6 } } },
      },
      plugins: [zero],
    });
    return;
  }
  const labels = r.flows[0].netP50.map((_, i) => base + i + 1);
  const series = shown.map((p, i) => ({ label: `${p.code} · ${p.useLabel}`, data: r.flows[i].netP50, color: SERIES[i] }));
  if (rest.length) {
    const sum = labels.map((_, t) => rest.reduce((s, _p, j) => s + r.flows[shown.length + j].netP50[t], 0));
    series.push({ label: `Digər ${rest.length} mövqe`, data: sum, color: OTHER });
  }
  legend.innerHTML = series.map((s) => `<span><i class="pf-sq" style="background:${s.color}"></i>${esc(s.label)}</span>`).join('');
  charts.flow = new Chart($('#flow'), {
    type: 'bar',
    data: { labels, datasets: series.map((s) => ({ label: s.label, data: s.data, backgroundColor: s.color, borderColor: '#fff', borderWidth: 1.5, barPercentage: 0.82, categoryPercentage: 0.9 })) },
    options: {
      maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
      plugins: { legend: { display: false }, tooltip: { callbacks: { label: (i) => ` ${i.dataset.label}: ${money(i.raw)}`, footer: (it) => `Cəmi: ${money(it.reduce((s, x) => s + x.raw, 0))}` } } },
      scales: { x: { stacked: true, grid: { display: false }, ticks: { color: C.muted }, border: { color: C.axis } }, y: { stacked: true, grid: { color: C.grid }, border: { display: false }, ticks: { color: C.muted, callback: (v) => money(v), maxTicksLimit: 6 } } },
    },
  });
}

/** Divergent rəng: −1 mavi, 0 neytral boz, +1 qırmızı. */
function corrColor(v) {
  const neg = [42, 120, 214], pos = [227, 73, 72], mid = [240, 239, 236];
  const e = v < 0 ? neg : pos, k = Math.min(1, Math.abs(v));
  const c = mid.map((m, i) => Math.round(m + (e[i] - m) * k));
  return `rgb(${c.join(',')})`;
}

function drawHeat(shown) {
  const r = st.res, n = shown.length;
  if (n < 2) { $('#heat').innerHTML = `<p class="small muted">Portfeldə bir mövqe var: korrelyasiya üçün ən azı iki mövqe lazımdır.</p>`; return; }
  const lbl = shown.map((p) => `${p.code} ${useLabel(p.use)}`);
  let html = `<div class="pf-heat" style="grid-template-columns: minmax(110px, auto) repeat(${n}, minmax(40px, 1fr))"><span></span>`;
  shown.forEach((p, j) => { html += `<span class="pf-hc" title="${esc(lbl[j])}">${j + 1}</span>`; });
  for (let i = 0; i < n; i++) {
    html += `<span class="pf-hr" title="${esc(lbl[i])}"><b>${i + 1}</b> ${esc(lbl[i])}</span>`;
    for (let j = 0; j < n; j++) {
      const v = r.corr[i][j];
      html += `<span class="pf-cell" style="background:${i === j ? '#ffffff' : corrColor(v)}" title="${esc(lbl[i])} ↔ ${esc(lbl[j])}: ${v.toFixed(2)}">${i === j ? '' : v.toFixed(2)}</span>`;
    }
  }
  $('#heat').innerHTML = `${html}</div>`;
}

async function explain() {
  const box = $('#aiBox'), btn = $('#aiBtn');
  box.classList.remove('hidden');
  box.innerHTML = '<span class="adv-typing"><i></i><i></i><i></i></span> <span class="small muted">AI portfeli təhlil edir...</span>';
  btn.disabled = true;
  try {
    const body = { budget: st.budget, risk: st.risk, horizon: st.horizon, maxSharePct: st.maxShare, maxSectorPct: st.maxSector, districts: st.districts, uses: st.uses };
    const res = await api('/api/v1/portfolio/explain', { method: 'POST', body });
    const ai = res.mode !== 'offline';
    box.innerHTML = `<div class="pf-ai-head small">${icon(ai ? 'sparkles' : 'cpu', 'i-sm')} ${ai ? 'Əkinçi AI' : 'Əkinçi AI · oflayn izah'}</div>${md(res.text)}`;
  } catch (e) {
    box.innerHTML = `<p class="small">${esc(e.message || 'AI cavab vermədi')}</p>`;
  }
  btn.disabled = false;
}

renderControls();
run();
