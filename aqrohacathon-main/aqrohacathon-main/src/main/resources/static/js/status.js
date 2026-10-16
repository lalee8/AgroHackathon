// Müraciət statusunun vizual göstəriciləri: addım xətti və tarixçə.
import { esc, icon, fmt } from './core.js';

export function statusTrack(status, decision) {
  const steps = [
    ['SUBMITTED', 'Qəbul edilib', 'inbox'],
    ['UNDER_REVIEW', 'Baxılır', 'hourglass'],
    ['INFO_REQUESTED', 'Əlavə məlumat', 'triangle-alert'],
    ['DECIDED', 'Qərar verilib', 'award'],
  ];
  if (status === 'DRAFT') return `<div class="alert alert-info">${icon('pencil')}<div>Müraciət hələ qaralamadır və göndərilməyib.</div></div>`;
  if (status === 'WITHDRAWN') return `<div class="alert alert-warn">${icon('circle-x')}<div>Müraciət geri çağırılıb.</div></div>`;
  const order = { SUBMITTED: 0, UNDER_REVIEW: 1, INFO_REQUESTED: 2, DECIDED: 3 };
  const cur = order[status];
  return `<div class="status-track">${steps.map(([k, label, ic], i) => {
    let cls = '';
    if (i < cur) cls = 'done';
    if (i === cur) cls = 'current';
    if (k === 'INFO_REQUESTED' && status !== 'INFO_REQUESTED' && cur > 2) cls = 'done';
    if (i === cur && k === 'INFO_REQUESTED') cls += ' attn';
    if (k === 'DECIDED' && status === 'DECIDED') cls = decision === 'REJECTED' ? 'current bad' : 'done';
    let lbl = label;
    if (k === 'DECIDED' && decision === 'APPROVED') lbl = 'Təsdiqləndi';
    if (k === 'DECIDED' && decision === 'REJECTED') lbl = 'İmtina';
    const ico = cls.includes('done') ? 'check' : k === 'DECIDED' && decision === 'REJECTED' ? 'x' : ic;
    return `<div class="st ${cls}"><div class="dot">${icon(ico, 'i-sm')}</div>${lbl}</div>`;
  }).join('')}</div>`;
}

const TL_ICON = { DRAFT: 'pencil', SUBMITTED: 'inbox', UNDER_REVIEW: 'hourglass', INFO_REQUESTED: 'triangle-alert', DECIDED: 'award', WITHDRAWN: 'circle-x' };

export function timeline(history = []) {
  if (!history.length) return '<p class="muted small">Tarixçə boşdur</p>';
  return `<ul class="timeline">${[...history].reverse().map((h) => `<li>
      <span class="tdot">${icon(h.fromStatus === h.toStatus ? 'network' : TL_ICON[h.toStatus] || 'circle-dot')}</span>
      <div class="tt">${h.fromStatus === h.toStatus ? 'Bildiriş' : esc(h.toLabel)}${h.actor ? `<span class="small muted" style="font-weight:500">· ${esc(h.actor)}</span>` : ''}</div>
      <div class="tm">${fmt.dateTime(h.createdAt)}</div>
      ${h.comment ? `<div class="tc">${esc(h.comment)}</div>` : ''}
    </li>`).join('')}</ul>`;
}

export function scoreBreakdown(a) {
  if (!a.scoreBreakdown) return '';
  return `<div class="card"><div class="card-head"><h3>${icon('scale')}İlkin avtomatik qiymətləndirmə</h3><b class="num" style="font-size:20px">${a.autoScore}/100</b></div>
    <div class="card-body"><div class="bar-list">${a.scoreBreakdown.map((c) => `<div class="bl" style="grid-template-columns:200px 1fr 70px"><div><div class="small" style="font-weight:620">${esc(c.label)}</div><div class="small muted">${esc(c.explanation)}</div></div>
      <div class="track"><span style="width:${c.ratio * 100}%"></span></div><span class="v small">${fmt.num1(c.points)}/${c.weight}</span></div>`).join('')}</div>
      <p class="small muted" style="margin:14px 0 0">${icon('info', 'i-sm')} Bal müsabiqədə əvvəlcədən elan edilmiş meyar çəkiləri əsasında hesablanır və yalnız ilkin sıralama üçündür. Yekun qərarı komissiya verir.</p></div></div>`;
}

