// Bir səhifəlik investisiya memorandumu (çap / PDF): platformanın hesabladığı rəqəmlər + AI icmalı
import { api, $, esc, icon, fmt, params } from '../core.js';
import { qrSvg } from '../receipt.js';
import { md } from '../advisor.js';

const id = Number(params().get('id'));
const money = (v) => (v < 0 ? '−' : '') + fmt.compactMoney(Math.abs(Number(v)));
const pct = (v) => `${Math.round(v * 100)}%`;

async function init() {
  if (!id) { $('#memo').innerHTML = '<p>Layihə seçilməyib.</p>'; return; }
  $('#back').href = `/layihe.html?id=${id}`;
  let d, tpl;
  try {
    [d, tpl] = await Promise.all([api(`/api/v1/projects/${id}`), api(`/api/v1/projects/${id}/tranches`).catch(() => [])]);
  } catch (e) {
    $('#memo').innerHTML = `<p>${esc(e.message)}</p>`;
    return;
  }
  const c = d.card, a = d.analysis, s = a.summary, t = a.terms, im = a.impact;
  const url = `${location.origin}/layihe.html?id=${c.id}`;
  document.title = `${c.code} memorandum - Əkinçi 2.0`;
  const MONTHS = ['yanvar', 'fevral', 'mart', 'aprel', 'may', 'iyun', 'iyul', 'avqust', 'sentyabr', 'oktyabr', 'noyabr', 'dekabr'];
  const now = new Date();
  const today = `${now.getDate()} ${MONTHS[now.getMonth()]} ${now.getFullYear()}`;
  const sev = (x) => (x === 'CRIT' ? 'm-crit' : x === 'WARN' ? 'm-warn' : 'm-ok');
  $('#memo').innerHTML = `
    <header class="m-head">
      <div class="m-brand"><img src="/img/logo.png" alt="" width="34" height="34"><div><b>Əkinçi 2.0</b><span>İnvestisiya memorandumu · ${esc(today)}</span></div></div>
      <div class="m-qr"><div class="m-qr-box">${qrSvg(url)}</div><span>${esc(c.code)}<br>Canlı məlumat</span></div>
    </header>
    <section class="m-title">
      <div class="m-tags"><span>${esc(c.sectorLabel)}</span>${c.districtLabel ? `<span>${esc(c.districtLabel)}</span>` : ''}${c.plot ? `<span>Sahə ${esc(c.plot.code)}</span>` : ''}<span>${esc(c.stageLabel)}</span>${c.verified ? '<span class="m-ok">Nazirlik tərəfindən yoxlanılıb</span>' : ''}</div>
      <h1>${esc(c.title)}</h1>
      ${c.tagline ? `<p>${esc(c.tagline)}</p>` : ''}
    </section>
    <section class="m-kpis">
      <div><span>Ümumi investisiya</span><b>${money(c.capex)}</b><small>sahibkarın payı ${money(c.ownFunds)}</small></div>
      <div><span>İnvestordan istənilən</span><b>${money(c.askAmount)}</b><small>${esc(c.dealLabel)}</small></div>
      <div><span>Orta ssenaridə illik xalis</span><b>${money(s.annualNetMature)}</b><small>P10 ${money(s.annualNetMatureP10)} · P90 ${money(s.annualNetMatureP90)}</small></div>
      <div><span>Reallıq yoxlaması</span><b>${a.credibility}/100</b><small>${esc(a.credibilityLabel)}</small></div>
    </section>
    <section class="m-sec m-ai"><div class="m-sec-h">${icon('sparkles', 'i-sm')} Analitik icmal <span id="aiMode" class="m-mode"></span></div><div id="aiText" class="m-ai-text"><span class="adv-typing"><i></i><i></i><i></i></span></div></section>
    <div class="m-grid">
      <section class="m-sec"><div class="m-sec-h">${icon('activity', 'i-sm')} Monte-Carlo: ${fmt.num(1500)} ssenari, ${a.horizon} il</div>
        <table class="m-table"><tbody>
          <tr><td>Xalis dəyər (NPV): pis / orta / yaxşı</td><td>${money(s.npvP10)} / <b>${money(s.npvP50)}</b> / ${money(s.npvP90)}</td></tr>
          <tr><td>İnvestisiyanın qayıtması (mediana)</td><td><b>${s.paybackP50 ? s.paybackP50 + '-ci il' : a.horizon + '+ il'}</b> · ehtimal ${pct(s.probPayback)}</td></tr>
          <tr><td>Daxili gəlirlilik (IRR, orta ssenari)</td><td><b>${s.irrP50 == null ? '-' : fmt.num1(s.irrP50) + '%'}</b></td></tr>
          <tr><td>Xalis dəyərin mənfi olma ehtimalı</td><td>${pct(1 - s.probPositiveNpv)}</td></tr>
          <tr><td>Yetkin ildə gəlir (orta)</td><td>${money(s.annualRevenueMature)}</td></tr>
        </tbody></table>
      </section>
      <section class="m-sec"><div class="m-sec-h">${icon('handshake', 'i-sm')} İnvestorun nəticəsi: ${esc(t.offer)}</div>
        <table class="m-table"><thead><tr><th>Ssenari</th><th>Alınan</th><th>Misli</th><th>Qayıdış</th></tr></thead><tbody>
          ${t.scenarios.map((x) => `<tr><td>${esc(x.label)}</td><td>${money(x.investorReceived)}</td><td>${fmt.num1(x.multiple)}×</td><td>${x.paybackYear ? x.paybackYear + '-ci il' : '-'}</td></tr>`).join('')}
        </tbody></table>
        ${t.dscr ? `<p class="m-note">Borcun ödəmə əmsalı (DSCR): ${fmt.num1(t.dscr)}</p>` : ''}
      </section>
    </div>
    <section class="m-sec"><div class="m-sec-h">${icon('clipboard-check', 'i-sm')} Reallıq yoxlaması: sahibkarın rəqəmləri regional göstəricilərlə</div>
      <ul class="m-checks">${a.checks.map((x) => `<li class="${sev(x.severity)}"><b>${esc(x.title)}.</b> ${esc(x.detail)}</li>`).join('')}</ul>
    </section>
    <div class="m-grid">
      ${tpl.length ? `<section class="m-sec"><div class="m-sec-h">${icon('satellite', 'i-sm')} Mərhələli maliyyələşmə (escrow + peyk)</div>
        <table class="m-table"><tbody>${tpl.map((m) => `<tr><td>${m.seq}. ${esc(m.title)}<div class="m-sub">${esc(m.methodLabel)}</div></td><td><b>${money(m.amount)}</b><div class="m-sub">${m.sharePct}%</div></td></tr>`).join('')}</tbody></table>
      </section>` : ''}
      ${im ? `<section class="m-sec"><div class="m-sec-h">${icon('house', 'i-sm')} Böyük Qayıdış təsiri</div>
        <table class="m-table"><tbody>
          <tr><td>Daimi / dolayı iş yeri</td><td><b>${fmt.num(im.directJobs)}</b> / ${fmt.num(im.indirectJobs)}</td></tr>
          <tr><td>Qayıdışına imkan yaranan ailə</td><td><b>${fmt.num(im.families)}</b> (≈ ${fmt.num(im.people)} nəfər)</td></tr>
          <tr><td>Yerli iqtisadiyyata əmək haqqı</td><td>${money(im.wageFund)}/il</td></tr>
          ${im.foodTons > 0 ? `<tr><td>Ərzaq istehsalı</td><td>${fmt.num(Math.round(im.foodTons))} t/il</td></tr>` : ''}
          ${im.gasM3 > 0 ? `<tr><td>İxraca qalan qaz</td><td>${fmt.num1(im.gasM3 / 1e6)} mln m³/il</td></tr>` : ''}
          ${im.co2Tons > 0 ? `<tr><td>CO₂ azalması / udulması</td><td>${fmt.num(Math.round(im.co2Tons))} t/il</td></tr>` : ''}
          <tr><td>Hər 1 mln ₼-a iş yeri</td><td>${fmt.num1(a.jobsPerMillion)}</td></tr>
        </tbody></table>
      </section>` : ''}
    </div>
    ${d.problem || d.solution ? `<section class="m-sec m-two"><div><div class="m-sec-h">${icon('triangle-alert', 'i-sm')} Problem</div><p>${esc(d.problem || '')}</p></div><div><div class="m-sec-h">${icon('lightbulb', 'i-sm')} Həll</div><p>${esc(d.solution || '')}</p></div></section>` : ''}
    <footer class="m-foot">Rəqəmlər Əkinçi 2.0 platformasının Monte-Carlo modelinə və regional istinad göstəricilərinə əsaslanan təxminlərdir, zəmanət deyil. Fərziyyələr və mənbələr: ${esc(url)} · Simulyasiya üfüqü ${a.horizon} il · Sahibkar: ${esc(c.ownerName)}</footer>`;
  loadAi();
}

async function loadAi() {
  try {
    const r = await api(`/api/v1/projects/${id}/memo`, { method: 'POST' });
    $('#aiMode').textContent = r.mode === 'offline' ? '· platforma məlumatından avtomatik' : '· Əkinçi AI tərəfindən yazılıb, rəqəmlər platformadandır';
    $('#aiText').innerHTML = md(r.text);
    $('#aiState').textContent = '';
  } catch (e) {
    $('#aiText').innerHTML = `<p class="small">${esc(e.message)}</p>`;
  }
}

$('#print').addEventListener('click', () => window.print());
init();
