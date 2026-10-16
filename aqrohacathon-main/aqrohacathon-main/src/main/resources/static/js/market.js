// Sahibkar ↔ investor bazarı üçün ortaq komponentlər
import { esc, icon, fmt } from './core.js';

export function credMeter(score, label, { big = false } = {}) {
  // 0-40 qırmızı, 41-75 sarı, 76-100 yaşıl (rəng tək deyil: ikon və yazı da səviyyəni deyir)
  const cls = score >= 76 ? 'good' : score >= 41 ? 'warn' : 'crit';
  const ic = cls === 'good' ? 'circle-check' : cls === 'warn' ? 'triangle-alert' : 'circle-x';
  return `<div class="cred ${cls} ${big ? 'big' : ''}" title="Reallıq yoxlaması: ${score}/100"><div class="cred-top"><span>${icon('clipboard-check', 'i-sm')}Reallıq yoxlaması</span><b>${score}<small>/100</small></b></div><div class="cred-bar"><span style="width:${score}%"></span></div>${label ? `<div class="cred-lbl">${icon(ic, 'i-sm')}${esc(label)}</div>` : ''}</div>`;
}

export function riskBadge(risk, label) {
  const cls = risk === 'LOW' ? 'badge-good' : risk === 'MID' ? 'badge-info' : 'badge-warn';
  return `<span class="badge ${cls}">${icon(risk === 'HIGH' ? 'triangle-alert' : 'shield-check')}${esc(label)}</span>`;
}

export function verifiedBadge(v) {
  return v ? `<span class="badge badge-good" title="Nazirlik tərəfindən yoxlanılıb">${icon('badge-check')}Yoxlanılıb</span>` : '';
}

export function projectCard(p) {
  const loc = p.plot ? `${esc(p.plot.code)} · ${esc(p.plot.districtLabel)}` : esc(p.districtLabel || 'Qarabağ');
  const m = p.match;
  return `<a class="proj-card" href="/layihe.html?id=${p.id}">
    <div class="pc-head">
      <span class="pc-ico">${icon(p.sectorIcon)}</span>
      <div style="min-width:0;flex:1"><div class="small muted">${esc(p.sectorLabel)} · ${loc}</div><h3>${esc(p.title)}</h3></div>
      ${m ? `<div class="pc-match" title="${esc(m.reasons.join('\n'))}"><b>${m.score}%</b><small>uyğunluq</small></div>` : ''}
    </div>
    <p class="pc-tag">${esc(p.tagline || '')}</p>
    <div class="row-wrap" style="gap:6px">${verifiedBadge(p.verified)}<span class="badge">${esc(p.stageLabel)}</span>${riskBadge(p.risk, p.riskLabel)}</div>
    <div class="pc-facts">
      <div><span>İnvestordan istənir</span><b>${fmt.compactMoney(p.askAmount)}</b></div>
      <div><span>Təklif</span><b>${esc(p.dealLabel)}</b></div>
      <div><span>Geri qayıtma (orta)</span><b>${p.paybackP50 ? p.paybackP50 + ' il' : 'uzunmüddətli'}</b></div>
    </div>
    ${credMeter(p.credibility, p.credibilityLabel)}
    ${m && m.reasons.length ? `<ul class="pc-why">${m.reasons.slice(0, 2).map((r) => `<li>${icon('check', 'i-sm')}${esc(r)}</li>`).join('')}</ul>` : ''}
    <div class="pc-foot"><span>${icon('users', 'i-sm')}${p.jobs} iş yeri</span><span>${icon('handshake', 'i-sm')}${p.interestCount} investor marağı</span><span>${esc(p.ownerName)}</span></div>
  </a>`;
}

export function checkIcon(sev) {
  return sev === 'CRIT' ? icon('circle-x') : sev === 'WARN' ? icon('triangle-alert') : icon('circle-check');
}

export function checksList(checks) {
  return `<ul class="checks">${checks.map((c) => `<li class="ck ck-${c.severity.toLowerCase()}">${checkIcon(c.severity)}<div><b>${esc(c.title)}</b><div>${esc(c.detail)}</div></div></li>`).join('')}</ul>`;
}

export function scenariosTable(t, { compact = false } = {}) {
  if (compact) {
    return `<div class="scen-compact">${t.scenarios.map((s) => `<div><b>${esc(s.label)}</b><span>${fmt.compactMoney(s.investorReceived)} · ${fmt.num1(s.multiple)}×</span><small>${s.paybackYear ? s.paybackYear + '-ci ildə qayıdır' : 'qayıtmır'}${s.irr != null ? ' · ' + s.irr + '%/il' : ''}</small></div>`).join('')}</div>`;
  }
  return `<div class="table-wrap"><table class="table scen"><thead><tr><th>Ssenari</th><th class="r">Siz alırsınız</th><th class="r">Neçə qat</th><th class="r">Pulun qayıtması</th><th class="r">İllik gəlirlilik</th></tr></thead><tbody>
    ${t.scenarios.map((s) => `<tr><td><b>${esc(s.label)}</b></td><td class="r num">${fmt.compactMoney(s.investorReceived)}</td><td class="r num">${fmt.num1(s.multiple)}×</td><td class="r">${s.paybackYear ? s.paybackYear + '-ci il' : 'qayıtmır'}</td><td class="r num">${s.irr != null ? s.irr + '%' : '-'}</td></tr>`).join('')}
  </tbody></table></div>`;
}

export const STAGES = { IDEA: 'İdeya', PLAN: 'Biznes plan hazırdır', LAND: 'Torpaq təmin olunub', OPERATING: 'Fəaliyyətdədir' };
export const DEALS = { EQUITY: 'Pay (şirkətdə hissə)', LOAN: 'Borc (faizlə)', REVENUE_SHARE: 'Gəlirdən pay' };
