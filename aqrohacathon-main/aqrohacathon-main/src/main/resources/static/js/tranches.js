// Peyk təsdiqli mərhələli maliyyələşmə: canlı plan (investor/sahibkar) və açıq şablon
import { api, esc, icon, fmt, toast, withLoading } from './core.js';

const ST = {
  RELEASED: { cls: 'tr-ok', ico: 'circle-check' },
  VERIFIED: { cls: 'tr-ready', ico: 'badge-check' },
  FLAGGED: { cls: 'tr-flag', ico: 'triangle-alert' },
  WAITING: { cls: 'tr-wait', ico: 'hourglass' },
  PENDING: { cls: 'tr-wait', ico: 'clock' },
  PLANNED: { cls: 'tr-wait', ico: 'circle-dot' },
};
const signed = (v) => `${v > 0 ? '+' : v < 0 ? '−' : ''}${Math.abs(v).toFixed(2)}`;

/** Fərqlərin fərqi: sahənin, qonşuların və təmiz fərqin sıfır ətrafında üfüqi zolaqları. */
function evidence(ev, status) {
  if (!ev) return '';
  const max = 0.4;
  const bar = (v, color) => {
    const w = Math.min(50, (Math.abs(v) / max) * 50);
    return `<span class="tr-ev-track"><i style="${v >= 0 ? 'left:50%' : `left:${50 - w}%`};width:${w}%;background:${color}"></i></span>`;
  };
  const pass = status === 'VERIFIED' || status === 'RELEASED';
  const isLevel = ev.rule.startsWith('NDVI');
  return `<div class="tr-ev">
    <div class="tr-ev-h">${icon('satellite', 'i-sm')} Landsat ${ev.season} · ${esc(ev.zone)} zona · ${ev.controls} nəzarət sahəsi</div>
    ${isLevel ? `
      <div class="tr-ev-r"><span>Sahənin pik NDVI-si</span><b class="num">${ev.plotNdvi.toFixed(2)}</b></div>
      <div class="tr-ev-r"><span>Zonanın medianı</span><b class="num">${ev.controlChange.toFixed(2)}</b></div>
      <div class="tr-ev-r"><span>Fərq</span>${bar(ev.excess, pass ? '#0ca30c' : '#e34948')}<b class="num">${signed(ev.excess)}</b></div>`
    : `
      <div class="tr-ev-r"><span>Sahə: ${ev.plotBaseline.toFixed(2)} → ${ev.plotNdvi.toFixed(2)}</span>${bar(ev.plotChange, '#2a78d6')}<b class="num">${signed(ev.plotChange)}</b></div>
      <div class="tr-ev-r"><span>Qonşu sahələr (median)</span>${bar(ev.controlChange, '#b9b8b0')}<b class="num">${signed(ev.controlChange)}</b></div>
      <div class="tr-ev-r tr-ev-x"><span>Hava effekti çıxılandan sonra</span>${bar(ev.excess, pass ? '#0ca30c' : '#e34948')}<b class="num">${signed(ev.excess)}</b></div>`}
    <div class="tr-ev-rule small muted">Qayda: ${esc(ev.rule)} · baza: ${ev.baselineFrom}-${ev.baselineTo} mövsümləri</div>
  </div>`;
}

function step(m, { live, investor }) {
  const st = ST[m.status] || ST.PENDING;
  return `<li class="tr-step ${st.cls}">
    <span class="tr-dot">${icon(st.ico, 'i-sm')}</span>
    <div class="tr-body">
      <div class="tr-top"><b>${m.seq}. ${esc(m.title)}</b><span class="tr-amt num">${fmt.compactMoney(m.amount)} <small>${m.sharePct}%</small></span></div>
      <div class="tr-meta"><span class="badge">${icon(m.icon, 'i-sm')}${esc(m.methodLabel)}</span>${live ? `<span class="tr-status">${esc(m.statusLabel)}</span>` : ''}${m.releasedAt && m.status === 'RELEASED' ? `<span class="small muted">${fmt.date(m.releasedAt)}</span>` : ''}</div>
      <div class="small muted">${esc(live && m.note ? m.note : m.description)}</div>
      ${live ? evidence(m.evidence, m.status) : ''}
      ${live && investor && m.canRelease ? `<button class="btn btn-primary btn-sm tr-pay" data-seq="${m.seq}" type="button">${icon('banknote', 'i-sm')}Escrow-dan ödə: ${fmt.compactMoney(m.amount)}</button>` : ''}
    </div>
  </li>`;
}

export function renderPlan(el, plan, { onChange } = {}) {
  const pct = (v) => (plan.amount ? (v / plan.amount) * 100 : 0);
  el.innerHTML = `
    <div class="tr-sum">
      <div class="tr-bar"><i class="tr-b-ok" style="width:${pct(plan.released)}%"></i><i class="tr-b-ready" style="width:${pct(plan.releasable)}%"></i></div>
      <div class="tr-legend small"><span><i class="tr-sw tr-b-ok"></i>Ödənilib <b class="num">${fmt.compactMoney(plan.released)}</b></span><span><i class="tr-sw tr-b-ready"></i>Ödənişə hazır <b class="num">${fmt.compactMoney(plan.releasable)}</b></span><span><i class="tr-sw tr-b-lock"></i>Escrow-da qorunur <b class="num">${fmt.compactMoney(plan.locked)}</b></span></div>
    </div>
    <ol class="tr-list">${plan.milestones.map((m) => step(m, { live: true, investor: plan.viewerIsInvestor })).join('')}</ol>
    <p class="small muted tr-method">${icon('info', 'i-sm')} ${esc(plan.method)}</p>`;
  el.querySelectorAll('.tr-pay').forEach((b) => b.addEventListener('click', () => withLoading(b, async () => {
    try {
      const next = await api(`/api/v1/projects/interests/${plan.interestId}/tranches/${b.dataset.seq}/release`, { method: 'POST' });
      toast('Hissə sahibkara köçürüldü');
      renderPlan(el, next, { onChange });
      onChange?.(next);
    } catch (e) { toast(e.message, 'err'); }
  })));
}

export async function mountPlan(el, interestId, opts) {
  el.innerHTML = '<div class="loader"></div>';
  try {
    renderPlan(el, await api(`/api/v1/projects/interests/${interestId}/tranches`), opts);
  } catch (e) {
    el.innerHTML = `<p class="small muted">${esc(e.message)}</p>`;
  }
}

export function renderTemplate(el, milestones) {
  el.innerHTML = `<ol class="tr-list">${milestones.map((m) => step(m, { live: false })).join('')}</ol>`;
}
