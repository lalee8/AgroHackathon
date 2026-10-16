import { api, renderLayout, $, esc, icon, fmt, CAT, countdown, competitionBadge, catBadge, plotStatusBadge, session, emptyState } from '../core.js';

renderLayout('competitions');

function applyHref(c, p) {
  const t = `/muraciet.html?plot=${p.id}&competition=${c.id}`;
  return session.loggedIn() ? t : '/giris.html?next=' + encodeURIComponent(t);
}

const clock = (cd) => [[cd.d, 'gün'], [cd.h, 'saat'], [cd.m, 'dəq'], [cd.s, 'san']].map(([v, l]) => `<div><b>${String(v).padStart(2, '0')}</b><span>${l}</span></div>`).join('');

// Sahənin vəziyyəti: rəng + ikon + yazı (rəng tək deyil)
function statusBadge(label) {
  if (label === 'Müsabiqədə') return `<span class="badge badge-good nowrap">${icon('circle-dot', 'i-sm')}${esc(label)}</span>`;
  if (label === 'İcarəyə verilib') return `<span class="badge nowrap">${icon('lock', 'i-sm')}${esc(label)}</span>`;
  return `<span class="badge nowrap">${esc(label)}</span>`;
}

function section(c) {
  const cd = countdown(c.deadlineAt);
  const maxW = Math.max(...c.criteria.map((x) => x.weight));
  return `<article class="card" id="c${c.id}" style="scroll-margin-top:90px">
    <div class="card-body comp-grid">
      <div>
        <div class="row-wrap" style="margin-bottom:10px"><span class="small muted" style="font-weight:700">${esc(c.code)}</span>${competitionBadge(c.status)}${c.open ? `<span class="badge badge-good">${icon('circle-check')}Müraciət qəbulu açıqdır</span>` : ''}</div>
        <h2 style="font-size:24px">${esc(c.title)}</h2>
        <p class="muted">${esc(c.description || '')}</p>
        <div class="facts facts-4" style="margin:16px 0">
          <div class="fact"><div class="k">Başlama</div><div class="v">${fmt.date(c.startAt)}</div></div>
          <div class="fact"><div class="k">Son tarix</div><div class="v">${fmt.date(c.deadlineAt)}</div></div>
          <div class="fact"><div class="k">Sahələr</div><div class="v">${c.plotCount} · ${fmt.ha(c.totalAreaHa)}</div></div>
          <div class="fact"><div class="k">Müraciətlər</div><div class="v">${c.applicationCount}</div></div>
        </div>
        ${c.approvedBy ? `<p class="small muted">${icon('clipboard-check', 'i-sm')} Təsdiqləyib: ${esc(c.approvedBy)} · ${fmt.date(c.approvedAt)} ("dörd göz" prinsipi)</p>` : ''}
      </div>
      <div>
        ${c.open ? `<div class="deadline-box ${cd.urgent ? 'urgent' : ''}" style="margin-bottom:16px">
          <div class="small" style="font-weight:650">${cd.urgent ? `${icon('hourglass', 'i-sm')} Tələsin: son ${cd.d ? cd.d + ' gün' : 'saatlar'}!` : 'Müraciətin bitməsinə'}</div>
          <div class="cd" data-deadline="${esc(c.deadlineAt)}">${clock(cd)}</div>
          <a class="btn btn-primary btn-block" style="margin-top:12px" href="#c${c.id}-plots">${icon('file-pen-line', 'i-sm')}Sahə seçib müraciət et</a>
        </div>` : `<div class="deadline-box closed" style="margin-bottom:16px"><div class="small" style="font-weight:650">${icon('lock', 'i-sm')} Müsabiqə bağlanıb</div><div class="small muted">Son tarix: ${fmt.date(c.deadlineAt)}</div></div>`}
        <h4>Qiymətləndirmə meyarları</h4>
        <div class="bar-list">${c.criteria.map((k) => `<div class="bl" style="grid-template-columns:1fr 70px 40px"><span class="small">${esc(k.label)}</span><div class="track"><span style="width:${(k.weight / maxW) * 100}%"></span></div><span class="v small">${k.weight}%</span></div>`).join('')}</div>
      </div>
    </div>
    <div class="table-wrap" id="c${c.id}-plots" style="border-top:1px solid var(--line);scroll-margin-top:90px"><table class="table comp-table">
      <thead><tr><th>Kod</th><th>Sahə</th><th>Rayon</th><th>Təyinat</th><th class="r">Ölçü</th><th class="r">Min. investisiya</th><th>Status</th><th></th></tr></thead>
      <tbody>${c.plots.map((p) => `<tr>
        <td class="mono">${esc(p.code)}</td><td><a href="/sahe.html?id=${p.id}">${esc(p.name)}</a></td><td>${esc(p.districtLabel)}</td>
        <td>${catBadge(p.category)}</td><td class="r num">${fmt.ha(p.areaHa)}</td><td class="r num">${fmt.money(p.minInvestment)}</td><td>${statusBadge(p.status)}</td>
        <td class="r">${c.open && p.status !== 'İcarəyə verilib' ? `<a class="btn btn-sm btn-primary nowrap" href="${applyHref(c, p)}">${icon('send', 'i-sm')}Müraciət et</a>` : `<a class="btn btn-sm btn-ghost nowrap" href="/sahe.html?id=${p.id}">${icon('eye', 'i-sm')}Bax</a>`}</td></tr>`).join('')}</tbody>
    </table></div>
  </article>`;
}

api('/api/v1/competitions').then((list) => {
  $('#root').innerHTML = list.length ? list.map(section).join('') : emptyState('Hazırda elan edilmiş müsabiqə yoxdur', 'trophy');
  // Canlı geri sayım
  setInterval(() => document.querySelectorAll('[data-deadline]').forEach((el) => {
    const cd = countdown(el.dataset.deadline);
    el.innerHTML = clock(cd);
    el.closest('.deadline-box').classList.toggle('urgent', !!cd.urgent);
  }), 1000);
  if (location.hash) document.querySelector(location.hash)?.scrollIntoView();
}).catch((e) => { $('#root').innerHTML = emptyState(e.message); });

// Qəbul edən tərəf: komissiyada təmsil olunan dövlət qurumları
api('/api/v1/competitions/commission').then((list) => {
  if (!list.length) return;
  $('#commission').innerHTML = `<div class="card card-pad">
    <div class="row between" style="flex-wrap:wrap;gap:8px;margin-bottom:12px"><div><span class="overline">Qəbul edən tərəf</span><h2 style="margin:2px 0 0;font-size:22px">${icon('gavel')} Müsabiqə komissiyası</h2></div>
      <span class="small muted">Kvorum: üzvlərin 2/3-si · sadə səs çoxluğu · bitərəf yoxdur</span></div>
    <p class="muted" style="margin:0 0 14px">Qaydalara görə komissiya 9 nəfərdən ibarətdir, sədri Kənd Təsərrüfatı Nazirliyi təyin edir. Səsvermə açıqdır, səslər bərabər olduqda sədrin səsi həlledicidir. Platformada hər üzvün səsi və əleyhinə səsin səbəbi qeydə alınır, iclasın protokolu avtomatik qurulur.</p>
    <div class="inst-grid">${list.map((i) => `<div class="inst-card"><span class="vote-inst">${esc(i.abbr)}</span><div><b>${esc(i.label)}</b><span>${i.chair ? 'Sədr · ' : ''}${i.seats} səs</span></div></div>`).join('')}</div>
  </div>`;
}).catch(() => {});
