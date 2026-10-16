import { api, renderLayout, $, esc, icon, fmt, params, session, toast, withLoading, onLive, emptyState } from '../core.js';
import { credMeter, riskBadge, verifiedBadge, checksList, scenariosTable } from '../market.js';
import { mountPlan, renderTemplate } from '../tranches.js';

renderLayout('projects');
const id = Number(params().get('id'));
const root = $('#root');

function section(title, text, ic) {
  return text ? `<div><h4 style="display:flex;gap:8px;align-items:center;margin:0 0 6px">${icon(ic, 'i-sm')}${esc(title)}</h4><p style="margin:0;white-space:pre-line">${esc(text)}</p></div>` : '';
}

function render(d) {
  const c = d.card, a = d.analysis, t = a.terms;
  document.title = `${c.title} - Əkinçi 2.0`;
  const ownShare = Math.min(100, Number(c.ownFunds) / Number(c.capex) * 100);
  const askShare = Math.min(100 - ownShare, Number(c.askAmount) / Number(c.capex) * 100);
  const sim = c.plot && c.sector !== 'AQROTEXNOLOGIYA' ? `/simulyator.html?plot=${c.plot.id}&use=${({ ENERJI: 'GUNES', TURIZM: 'AQROTURIZM', EMAL: 'SOYUDUCU' })[c.sector] || c.sector}` : null;
  root.innerHTML = `
  <section class="mk-hero"><div class="container">
    <div class="crumbs"><a href="/index.html">Ana səhifə</a> / <a href="/layiheler.html">Layihələr</a> / ${esc(c.code)}</div>
    <div class="row-wrap" style="gap:8px;margin:10px 0 8px"><span class="badge badge-on-dark">${icon(c.sectorIcon)}${esc(c.sectorLabel)}</span><span class="badge badge-on-dark">${esc(c.stageLabel)}</span>${c.verified ? `<span class="badge badge-on-dark">${icon('badge-check')}Nazirlik tərəfindən yoxlanılıb</span>` : ''}</div>
    <h1>${esc(c.title)}</h1>
    <p style="font-size:16px">${esc(c.tagline || '')}</p>
    <div class="row-wrap" style="gap:18px;font-size:14px">
      <span>${icon('map-pin')} ${c.plot ? `<a href="/sahe.html?id=${c.plot.id}" style="color:#fff;text-decoration:underline">${esc(c.plot.code)} · ${esc(c.plot.name)}</a> (${esc(c.plot.designationLabel)})` : esc(c.districtLabel || 'Qarabağ')}</span>
      <span>${icon('user')} ${esc(c.ownerName)}</span><span>${icon('eye')} ${d.views} baxış</span><span>${icon('hexagon')} ${esc(c.code)}</span>
    </div>
  </div></section>
  <section class="section-sm"><div class="container detail-layout">
    <div class="stack">
      <div class="card card-pad" style="border:1px solid #f0d78a;background:linear-gradient(180deg,#fffaf0,#fff)">
        <span class="overline" style="color:#9a6b00">İnvestor dilində</span>
        <h3 style="margin:0 0 12px">${icon('coins')} Siz nə qoyursunuz və nə alırsınız?</h3>
        <ul class="inv-lang">${a.investorLanguage.map((l) => `<li>${icon('arrow-right', 'i-sm')}<span>${esc(l)}</span></li>`).join('')}</ul>
        <h4 style="margin:18px 0 8px">Təklif: ${esc(t.offer)}</h4>
        ${scenariosTable(t)}
        <p class="small muted" style="margin:8px 0 0">Ssenarilər ${fmt.num(1500)} simulyasiyadan götürülüb: məhsuldarlıq, qiymət, təbii risklər və xərclər təsadüfi dəyişir. ${sim ? `<a href="${sim}">Bu biznesin ${a.horizon} ilini simulyatorda yaşayın →</a>` : ''}</p>
      </div>
      <div class="card card-pad">
        <div class="row between" style="flex-wrap:wrap;gap:16px;align-items:flex-start">
          <div style="flex:1;min-width:240px"><h3 style="margin:0 0 4px">Reallıq yoxlaması</h3><p class="small muted" style="margin:0">Sahibkarın iddiaları regional istinad göstəriciləri və simulyasiya ilə müqayisə olunub.</p></div>
          <div style="width:260px;max-width:100%">${credMeter(c.credibility, c.credibilityLabel, { big: true })}</div>
        </div>
        <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:10px;margin:16px 0">
          <div class="spec"><div class="k">Sahibkarın iddiası: gəlir</div><div class="v">${d.claimedRevenue ? fmt.compactMoney(d.claimedRevenue) : '-'}</div></div>
          <div class="spec"><div class="k">Simulyasiya: gəlir (orta)</div><div class="v">${fmt.compactMoney(a.benchmarkRevenue)}</div></div>
          <div class="spec"><div class="k">Sahibkarın iddiası: mənfəət</div><div class="v">${d.claimedProfit ? fmt.compactMoney(d.claimedProfit) : '-'}</div></div>
          <div class="spec"><div class="k">Simulyasiya: mənfəət (orta)</div><div class="v">${fmt.compactMoney(a.benchmarkNet)}</div></div>
        </div>
        ${checksList(a.checks)}
      </div>
      <div class="card card-pad" id="trCard"></div>
      ${a.impact ? `<div class="card card-pad">
        <div class="between" style="gap:10px;flex-wrap:wrap"><h3 style="margin:0;display:flex;gap:8px;align-items:center">${icon('house')} Böyük Qayıdış təsiri</h3><a class="small" href="/regionlar.html">Rayonlar paneli →</a></div>
        <p class="small muted" style="margin:4px 0 12px">Layihə tam gücə çıxanda yaratdığı sosial və ekoloji dəyər (təxmini, mənbələr panelin metodologiyasındadır).</p>
        <div class="pf-impact">
          <div class="pf-fact"><span class="pf-fact-ico">${icon('users', 'i-sm')}</span><b class="num">${fmt.num(a.impact.directJobs)}</b><span>daimi iş yeri</span><small>+ ${fmt.num(a.impact.indirectJobs)} dolayı · ${fmt.num1(a.jobsPerMillion)} / 1 mln ₼</small></div>
          <div class="pf-fact"><span class="pf-fact-ico">${icon('house', 'i-sm')}</span><b class="num">${fmt.num(a.impact.families)}</b><span>ailənin qayıdışı</span><small>≈ ${fmt.num(a.impact.people)} nəfər</small></div>
          <div class="pf-fact"><span class="pf-fact-ico">${icon('banknote', 'i-sm')}</span><b class="num">${fmt.compactMoney(a.impact.wageFund)}</b><span>əmək haqqı/il</span><small>yerli iqtisadiyyata</small></div>
          ${a.impact.foodTons > 0 ? `<div class="pf-fact"><span class="pf-fact-ico">${icon('wheat', 'i-sm')}</span><b class="num">${fmt.num(Math.round(a.impact.foodTons))} t</b><span>ərzaq/il</span><small>yetkin ildə</small></div>` : ''}
          ${a.impact.gasM3 > 0 ? `<div class="pf-fact"><span class="pf-fact-ico">${icon('flame', 'i-sm')}</span><b class="num">${fmt.num1(a.impact.gasM3 / 1e6)} mln m³</b><span>qaz/il</span><small>ixraca qalır</small></div>` : ''}
          ${a.impact.co2Tons > 0 ? `<div class="pf-fact"><span class="pf-fact-ico">${icon('leaf', 'i-sm')}</span><b class="num">${fmt.num(Math.round(a.impact.co2Tons))} t</b><span>CO₂/il</span><small>udulan və ya qarşısı alınan</small></div>` : ''}
        </div>
      </div>` : ''}
      <div class="card card-pad stack">
        ${section('Problem', d.problem, 'triangle-alert')}
        ${section('Həll', d.solution, 'lightbulb')}
        ${section('Bazar və alıcılar', d.market, 'hand-coins')}
        ${section('Komanda', d.team, 'users')}
      </div>
      ${c.plot ? '<div class="card card-pad" id="buyers"></div>' : ''}
    </div>
    <aside class="stack sticky-side">
      <div class="card card-pad deal-box">
        <h4 style="margin:0 0 6px">Maliyyələşmə</h4>
        <div class="fund-bar" title="Öz vəsait və investordan istənən"><span class="own" style="width:${ownShare}%"></span><span class="ask" style="width:${askShare}%"></span></div>
        <div class="chart-legend small" style="margin:4px 0 0"><span><i style="background:#1c5cab"></i>Sahibkarın öz vəsaiti</span><span><i style="background:#86b6ef"></i>İnvestordan</span></div>
        <div class="kv2"><span>Ümumi investisiya</span><b>${fmt.money(c.capex)}</b></div>
        <div class="kv2"><span>Sahibkarın öz vəsaiti</span><b>${fmt.money(c.ownFunds)}</b></div>
        <div class="kv2"><span>İnvestordan istənir</span><b>${fmt.money(c.askAmount)}</b></div>
        <div class="kv2"><span>Təklifin növü</span><b>${esc(c.dealLabel)}</b></div>
        <div class="kv2"><span>İş yerləri</span><b>${c.jobs}</b></div>
        <div class="row-wrap" style="gap:6px;margin-top:6px">${verifiedBadge(c.verified)}${riskBadge(c.risk, c.riskLabel)}</div>
        <a class="btn btn-block memo-btn" href="/memo.html?id=${c.id}" target="_blank" rel="noopener">${icon('file-text', 'i-sm')}İnvestisiya memorandumu (PDF)</a>
      </div>
      <div class="card card-pad" id="action"></div>
      ${d.owner ? `<div class="card card-pad" id="interests" style="scroll-margin-top:90px"><h4 style="margin:0 0 6px">Gələn investor maraqları (${d.interests.length})</h4>${d.interests.length ? d.interests.map(interestRow).join('') : '<p class="small muted">Hələ maraq yoxdur.</p>'}</div>` : ''}
    </aside>
  </div></section>`;
  renderAction(d);
  renderTranches(d);
  if (c.plot) loadBuyers(c.plot.id);
  if (d.owner) bindInterests();
  if (location.hash === '#interests') setTimeout(() => $('#interests')?.scrollIntoView({ behavior: 'smooth' }), 300);
}

/** Mərhələli maliyyələşmə: tərəflər canlı planı, digərləri açıq şablonu görür. */
async function renderTranches(d) {
  const box = $('#trCard');
  const head = (title, sub) => `<div class="between" style="gap:10px;flex-wrap:wrap"><h3 style="margin:0;display:flex;gap:8px;align-items:center">${icon('satellite')} ${title}</h3><span class="badge badge-info">${icon('shield-check', 'i-sm')}Escrow + peyk nəzarəti</span></div><p class="small muted" style="margin:4px 0 14px">${sub}</p>`;
  const mine = d.myInterest?.status === 'ACCEPTED' ? d.myInterest : null;
  const accepted = d.owner ? (d.interests || []).filter((i) => i.status === 'ACCEPTED') : [];
  if (mine || accepted.length) {
    const list = mine ? [mine] : accepted;
    box.innerHTML = head('Mərhələli maliyyələşmə', mine ? 'Razılaşdığınız məbləğ escrow hesabındadır. Hər hissə mərhələ təsdiqlənəndə sahibkara köçürülür: peyk mərhələlərini sistem özü yoxlayır.' : 'İnvestorların vəsaiti escrow hesabındadır və mərhələlər təsdiqləndikcə sizə köçürülür.')
      + list.map((i) => `${list.length > 1 || !mine ? `<h4 style="margin:12px 0 6px">${esc(i.investorName || '')} · ${fmt.compactMoney(i.amount)}</h4>` : ''}<div data-plan="${i.id}"></div>`).join('');
    box.querySelectorAll('[data-plan]').forEach((el) => mountPlan(el, el.dataset.plan));
    return;
  }
  try {
    const tpl = await api(`/api/v1/projects/${d.card.id}/tranches`);
    box.innerHTML = head('Pulunuz necə qorunur', `İnvestordan istənilən ${fmt.compactMoney(d.card.askAmount)} bir dəfəyə verilmir: escrow hesabında saxlanılır və ${tpl.length} mərhələdə, hər biri təsdiqlənəndə ödənilir.`) + '<div id="trTpl"></div>';
    renderTemplate($('#trTpl'), tpl);
  } catch { box.remove(); }
}

function interestRow(i) {
  return `<div class="int-row">
    <div><b>${esc(i.investorName)}</b> · ${fmt.compactMoney(i.amount)}<div class="small muted">${fmt.rel(i.createdAt)} · ${esc(i.statusLabel)}</div>${i.message ? `<div class="small" style="margin-top:4px">"${esc(i.message)}"</div>` : ''}
      ${i.contact ? `<div class="contact-card" style="margin-top:8px">${icon('phone', 'i-sm')} ${esc(i.contact.phone || '-')}<span>${icon('mail', 'i-sm')} ${esc(i.contact.email)}</span></div>` : ''}</div>
    ${i.status === 'NEW' ? `<div class="stack" style="gap:6px"><button class="btn btn-primary btn-sm" data-acc="${i.id}">${icon('check', 'i-sm')}Görüşə razıyam</button><button class="btn btn-sm" data-dec="${i.id}">${icon('x', 'i-sm')}İmtina</button></div>` : ''}
  </div>`;
}

function bindInterests() {
  $('#interests').addEventListener('click', async (e) => {
    const acc = e.target.closest('[data-acc]'), dec = e.target.closest('[data-dec]');
    if (!acc && !dec) return;
    const btn = acc || dec;
    await withLoading(btn, async () => {
      try {
        await api(`/api/v1/projects/interests/${btn.dataset.acc || btn.dataset.dec}/respond`, { method: 'POST', body: { accept: !!acc, reply: acc ? 'Görüşə hazıram, əlaqə saxlayaq.' : null } });
        toast(acc ? 'Razılıq verildi: əlaqə məlumatları açıldı' : 'İmtina göndərildi');
        load();
      } catch (err) { toast(err.message, 'err'); }
    });
  });
}

function renderAction(d) {
  const box = $('#action');
  const c = d.card;
  if (d.owner) {
    box.innerHTML = `<h4 style="margin:0 0 6px">Bu sizin layihənizdir</h4><p class="small muted">Status: <b>${esc(c.statusLabel)}</b>. Rəqəmləri dəqiqləşdirdikcə reallıq yoxlaması yenilənir.</p><a class="btn btn-block" href="/layihe-yeni.html?id=${c.id}">${icon('pencil', 'i-sm')}Redaktə et</a>`;
    return;
  }
  if (!session.loggedIn()) {
    box.innerHTML = `<h4 style="margin:0 0 6px">Bu layihə ilə maraqlanırsınız?</h4><p class="small muted">Maraq bildirmək üçün investor hesabı ilə daxil olun. Sahibkar razılıq verəndə əlaqə məlumatları hər iki tərəfə açılır.</p><a class="btn btn-primary btn-block" href="/giris.html?next=${encodeURIComponent(location.pathname + location.search)}">${icon('log-in', 'i-sm')}Daxil ol</a>`;
    return;
  }
  if (session.role() !== 'INVESTOR') { box.innerHTML = `<p class="small muted">Maraq bildirmək yalnız investor hesabları üçündür.</p>`; return; }
  const mi = d.myInterest;
  if (mi) {
    box.innerHTML = `<h4 style="margin:0 0 6px">Sizin marağınız: ${fmt.compactMoney(mi.amount)}</h4>
      <p><span class="badge ${mi.status === 'ACCEPTED' ? 'badge-good' : mi.status === 'DECLINED' ? 'badge-crit' : 'badge-info'}">${esc(mi.statusLabel)}</span></p>
      ${mi.reply ? `<p class="small">Sahibkarın cavabı: "${esc(mi.reply)}"</p>` : ''}
      ${d.ownerContact ? `<div class="contact-card"><b>${esc(d.ownerContact.name)}</b><span>${icon('phone', 'i-sm')} ${esc(d.ownerContact.phone || '-')}</span><span>${icon('mail', 'i-sm')} ${esc(d.ownerContact.email)}</span></div>` : '<p class="small muted">Sahibkar razılıq verəndə əlaqə məlumatları burada açılacaq.</p>'}`;
    return;
  }
  box.innerHTML = `<h4 style="margin:0 0 6px">Maraq bildir</h4><p class="small muted">Sahibkar bildiriş alacaq. Razılıq verərsə əlaqə məlumatlarınız qarşılıqlı açılacaq.</p>
    <form id="intForm" novalidate>
      <div class="field"><label>Məbləğ (₼)</label><input class="input num" type="number" name="amount" min="1000" step="1000" value="${Math.round(Number(c.askAmount) / 2 / 1000) * 1000}"></div>
      <div class="field"><label>Sahibkara mesaj</label><textarea class="input" name="message" rows="3" placeholder="Məsələn: sənədlərlə tanış olmaq istəyirəm"></textarea></div>
      <button class="btn btn-gold btn-block" type="submit" data-loading="Göndərilir...">${icon('handshake', 'i-sm')}Maraq bildir</button>
    </form>`;
  $('#intForm').addEventListener('submit', (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    withLoading(e.submitter, async () => {
      try {
        await api(`/api/v1/projects/${c.id}/interest`, { method: 'POST', body: { amount: Number(f.get('amount')), message: f.get('message') } });
        toast('Marağınız sahibkara göndərildi');
        load();
      } catch (err) { toast(err.message, 'err'); }
    });
  });
}

async function loadBuyers(plotId) {
  const el = $('#buyers');
  const list = await api(`/api/v1/plots/${plotId}/demands`).catch(() => []);
  el.innerHTML = `<h4 style="margin:0 0 4px">${icon('hand-coins', 'i-sm')} Satış bazarı: bu sahənin məhsuluna təsdiqlənmiş tələblər</h4>
    <p class="small muted" style="margin:0 0 10px">Alıcı tələbi Nazirlik tərəfindən yoxlanılıb. Bu zəmanət deyil, real ehtiyacın təsdiqidir.</p>
    ${list.length ? list.map((m) => `<div class="int-row"><div><b>${esc(m.demand.company)}</b> · ${esc(m.demand.productLabel)} ${fmt.num(m.demand.volumeTons)} t/il<div class="small muted">${fmt.num(m.demand.priceMin)}-${fmt.num(m.demand.priceMax)} ₼/t · ${m.demand.contractYears} il · ${fmt.num1(m.distanceKm)} km · sahə tələbin ${m.coveragePercent}%-ni ödəyə bilər</div></div><a class="btn btn-sm" href="/alicilar.html?demand=${m.demand.id}">Bax</a></div>`).join('') : '<p class="small muted">Hələ bu məhsul və məsafə üzrə təsdiqlənmiş tələb yoxdur.</p>'}`;
}

async function load() {
  try { render(await api(`/api/v1/projects/${id}`)); } catch (e) { root.innerHTML = `<div class="container section">${emptyState(e.message)}</div>`; }
}
onLive('notification', (n) => { if (n.link && n.link.includes(`layihe.html?id=${id}`)) load(); });
load();
