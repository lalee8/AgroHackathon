import { api, $, $$, on, esc, icon, fmt, DISTRICTS, statusBadge, downloadFile, toast, errorToast, withLoading, ring, emptyState } from '../core.js';
import { statusTrack, timeline, scoreBreakdown } from '../status.js';

const STATUSES = [
  ['', 'Bütün statuslar'], ['SUBMITTED', 'Qəbul edilib'], ['UNDER_REVIEW', 'Baxılır'],
  ['INFO_REQUESTED', 'Əlavə məlumat tələb olunur'], ['DECIDED', 'Qərar verilib'], ['WITHDRAWN', 'Geri çağırılıb'],
];

export async function render(el, rest, ctx) {
  if (rest[0]) return renderDetail(el, rest[0], ctx);
  const comps = await api(ctx.role === 'ADMIN' ? '/api/v1/admin/competitions' : '/api/v1/competitions');
  const saved = JSON.parse(sessionStorage.getItem('adm.app.filters') || '{}');
  el.innerHTML = `
    <div class="admin-title"><div><h1>Daxil olan müraciətlər</h1><p class="muted" style="margin:0">Filtrləyin, müraciəti açın, qiymətləndirin və statusu dəyişin</p></div>
      ${ctx.role === 'ADMIN' ? `<button class="btn" id="export">${icon('download')}CSV ixrac</button>` : ''}</div>
    <div class="filterbar" style="margin-bottom:16px"><div class="row1" style="grid-template-columns:1.4fr 1fr 1.4fr 1fr">
      <div class="search-input">${icon('search')}<input class="input" id="q" placeholder="Nömrə, şirkət, şəxs və ya sahə kodu" value="${esc(saved.q || '')}"></div>
      <select class="input" id="status">${STATUSES.map(([v, l]) => `<option value="${v}" ${saved.status === v ? 'selected' : ''}>${l}</option>`).join('')}</select>
      <select class="input" id="comp"><option value="">Bütün müsabiqələr</option>${comps.map((c) => `<option value="${c.id}" ${String(saved.comp) === String(c.id) ? 'selected' : ''}>${esc(c.code)} - ${esc(c.title)}</option>`).join('')}</select>
      <select class="input" id="district"><option value="">Bütün rayonlar</option>${Object.entries(DISTRICTS).map(([k, v]) => `<option value="${k}" ${saved.district === k ? 'selected' : ''}>${v}</option>`).join('')}</select>
    </div></div>
    <div class="card"><div class="table-wrap" id="tbl"><div class="loader"></div></div></div>`;

  const load = async () => {
    const f = { q: $('#q').value.trim(), status: $('#status').value, comp: $('#comp').value, district: $('#district').value };
    sessionStorage.setItem('adm.app.filters', JSON.stringify(f));
    const qs = new URLSearchParams();
    if (f.q) qs.set('q', f.q);
    if (f.status) qs.set('status', f.status);
    if (f.comp) qs.set('competitionId', f.comp);
    if (f.district) qs.set('district', f.district);
    const list = await api('/api/v1/review/applications?' + qs);
    $('#tbl').innerHTML = list.length ? `<table class="table"><thead><tr>
        <th>Nömrə</th><th>Müraciətçi</th><th>Sahə</th><th class="r">İnvestisiya</th><th class="r">İş yeri</th><th class="r">Avto</th><th class="r">Ekspert</th><th class="r">Yekun</th><th>Status</th><th>Göndərilib</th></tr></thead>
      <tbody>${list.map((a) => `<tr class="clickable" data-id="${a.id}">
        <td class="mono nowrap"><b>${esc(a.appNumber)}</b></td><td>${esc(a.applicantName)}</td>
        <td><div style="font-weight:600">${esc(a.plotCode)}</div><div class="small muted">${esc(a.districtLabel)} · ${esc(a.categoryLabel)}</div></td>
        <td class="r num nowrap">${fmt.compactMoney(a.investmentAmount)}</td><td class="r num">${a.jobsPlanned ?? '-'}</td>
        <td class="r num">${a.autoScore ?? '-'}</td><td class="r num">${a.expertAverage ?? '-'}</td><td class="r num"><b>${a.finalScore ?? '-'}</b></td>
        <td>${statusBadge(a.status, a.decision)}</td><td class="small muted nowrap">${fmt.date(a.submittedAt)}</td></tr>`).join('')}</tbody></table>
        <div class="card-foot small muted">${list.length} müraciət</div>` : emptyState('Filtrlərə uyğun müraciət yoxdur');
  };
  on($('#tbl'), 'click', 'tr[data-id]', (e, tr) => { location.hash = `applications/${tr.dataset.id}`; });
  ['#status', '#comp', '#district'].forEach((s) => $(s).addEventListener('change', load));
  let t;
  $('#q').addEventListener('input', () => { clearTimeout(t); t = setTimeout(load, 250); });
  $('#export')?.addEventListener('click', () => {
    const qs = new URLSearchParams();
    if ($('#status').value) qs.set('status', $('#status').value);
    if ($('#comp').value) qs.set('competitionId', $('#comp').value);
    downloadFile('/api/v1/admin/applications/export.csv?' + qs, 'muracietler.csv').catch(errorToast);
  });
  await load();
}

// Qaydaların əlavəsi: hər meyar uyğunluq faizinə görə 0, 2, 3, 4 və ya 5 balla qiymətləndirilir
const SCALE = [[0, 'Uyğun deyil', '10%-dək'], [2, 'Qismən uyğundur', '11-20%'], [3, 'Əsasən uyğundur', '21-60%'], [4, 'Uyğundur', '61-80%'], [5, 'Tam uyğundur', '81-100%']];
const toPts = (v) => (v === undefined || v === null ? 3 : v >= 9 ? 5 : v >= 7 ? 4 : v >= 5 ? 3 : v >= 3 ? 2 : 0);

async function renderDetail(el, id, ctx) {
  const a = await api(`/api/v1/review/applications/${id}`);
  const comp = await api(`/api/v1/competitions/${a.competition.id}`).catch(() => null);
  const criteria = comp?.criteria || [];
  const mine = a.evaluations.find((e) => e.expert === JSON.parse(localStorage.getItem('aqroinvest.user') || '{}').fullName);
  const myScores = mine ? mine.scores : {};
  const isAdmin = ctx.role === 'ADMIN';

  el.innerHTML = `
    <div class="admin-title"><div><a href="#applications" class="small">${icon('arrow-left', 'i-sm')} Müraciətlər</a>
      <h1 class="mono" style="margin-top:6px">${esc(a.appNumber || 'Qaralama')}</h1>
      <div class="row-wrap">${statusBadge(a.status, a.decision)}<span class="muted small">${esc(a.plot.code)} - ${esc(a.plot.name)} · ${esc(a.competition.code)}</span></div></div>
      <div class="row" style="gap:14px"><div style="text-align:center">${ring(a.autoScore)}<div class="small muted">Avto</div></div><div style="text-align:center">${ring(a.expertAverage ?? 0)}<div class="small muted">Ekspert</div></div><div style="text-align:center">${ring(a.finalScore, 'lg')}<div class="small muted">Yekun</div></div></div>
    </div>
    <div class="card card-pad" style="margin-bottom:16px">${statusTrack(a.status, a.decision)}</div>
    <div class="drawer-grid">
      <div class="stack">
        <div class="card"><div class="card-head"><h3>${icon('user')}Müraciətçi</h3><span class="badge">${esc(a.applicantTypeLabel || '')}</span></div><div class="card-body"><div class="kv">
          ${a.companyName ? `<div>Şirkət</div><div><b>${esc(a.companyName)}</b></div>` : ''}
          <div>Hesab sahibi</div><div>${esc(a.applicantName)} · ${esc(a.applicantEmail)}</div>
          ${a.voen ? `<div>VÖEN</div><div class="mono">${esc(a.voen)}</div>` : ''}
          ${a.applicantType === 'FOREIGN' ? `<div>Vətəndaşlıq</div><div><b>${esc(a.citizenship || '-')}</b></div><div>Pasport</div><div class="mono">${esc(a.passportNo || '-')}</div>`
            : `<div>FİN</div><div class="mono">${esc(a.fin || '-')} ${!isAdmin ? '<span class="badge" title="Ekspert üçün maskalanıb">' + icon('lock') + 'maskalanıb</span>' : ''}</div>`}
          <div>Əlaqə</div><div>${esc(a.contactPhone || '-')} · ${esc(a.contactEmail || '-')}</div>
          ${a.legalAddress ? `<div>Ünvan</div><div>${esc(a.legalAddress)}</div>` : ''}
          <div>Sosial prioritet</div><div>${a.idpStatus ? '<span class="badge badge-green">Yerli sakin: məcburi köçkün / Qarabağ əsilli</span>' : '-'}</div>
        </div></div></div>
        <div class="card"><div class="card-head"><h3>${icon('coins')}İnvestisiya planı</h3><span class="badge badge-gold">${esc(a.activityLabel || '')}</span></div><div class="card-body">
          <div class="spec-grid" style="margin-bottom:14px">
            <div class="spec"><div class="k">İnvestisiya</div><div class="v">${fmt.money(a.investmentAmount)}</div><div class="small muted">min. ${fmt.money(a.plot.minInvestment)}</div></div>
            <div class="spec"><div class="k">İş yerləri</div><div class="v">${a.jobsPlanned ?? '-'}</div><div class="small muted">min. ${a.plot.minJobs}</div></div>
            <div class="spec"><div class="k">Öz vəsaiti</div><div class="v">${a.ownFundsPercent ?? '-'}%</div></div>
            <div class="spec"><div class="k">İcra müddəti</div><div class="v">${a.implementationMonths ?? '-'} ay</div></div>
            <div class="spec"><div class="k">Təcrübə</div><div class="v">${a.experienceYears ?? '-'} il</div></div>
            <div class="spec"><div class="k">İllik gəlir</div><div class="v">${fmt.compactMoney(a.expectedAnnualRevenue)}</div></div>
          </div>
          <div class="row-wrap" style="margin-bottom:12px">${a.technologies.map((t) => `<span class="badge badge-green">${esc(t.label)}</span>`).join('')}</div>
          <h4>Biznes planın xülasəsi</h4><p style="white-space:pre-wrap;font-size:14px;margin:0">${esc(a.businessPlan || '-')}</p>
        </div></div>
        <div class="card"><div class="card-head"><h3>${icon('file-text')}Sənədlər (${a.documents.length})</h3></div><div class="card-body">
          ${a.documents.map((d) => `<div class="doc-file" style="margin-bottom:6px">${icon('file-check', 'i-sm')}<span class="nm">${esc(d.name)}</span><span class="badge">${esc(d.typeLabel)}</span><span class="small muted mono" title="SHA-256">${esc(d.sha256.slice(0, 10))}…</span><button class="btn btn-sm btn-ghost" data-doc="${d.id}" data-name="${esc(d.name)}">${icon('eye', 'i-sm')}Aç</button></div>`).join('') || '<p class="muted small">Sənəd yoxdur</p>'}
        </div></div>
        ${scoreBreakdown(a)}
        <div class="card"><div class="card-head"><h3>${icon('scale')}Komissiya qiymətləndirmələri</h3><span class="small muted">orta: <b>${a.expertAverage ?? '-'}</b></span></div><div class="card-body">
          ${a.evaluations.length ? a.evaluations.map((e) => `<div style="padding:10px 0;border-bottom:1px dashed var(--line)"><div class="row between"><b>${esc(e.expert)}</b><span class="badge badge-info">${e.total}/100</span></div>
            <div class="small muted">${fmt.dateTime(e.createdAt)}</div>${e.comment ? `<div class="small" style="margin-top:4px">${esc(e.comment)}</div>` : ''}</div>`).join('') : '<p class="muted small">Hələ qiymətləndirmə yoxdur</p>'}
        </div></div>
      </div>
      <aside class="stack">
        <div class="card" id="votes"><div class="card-body"><div class="loader"></div></div></div>
        ${isAdmin && a.allowedTransitions.length ? `<div class="card"><div class="card-head"><h3>${icon('workflow')}Statusu dəyiş</h3></div><div class="card-body">
          <div class="row-wrap" id="trans">${a.allowedTransitions.map((t) => `<button class="chip" data-st="${t.status}">${esc(t.label)}</button>`).join('')}</div>
          <form id="stForm" class="hidden" style="margin-top:14px">
            <div id="decisionBox" class="field hidden"><label class="req">Qərar</label><div class="seg"><button type="button" data-dec="APPROVED">${icon('award', 'i-sm')}Təsdiq (qalib)</button><button type="button" data-dec="REJECTED">${icon('circle-x', 'i-sm')}İmtina</button></div></div>
            <div class="field"><label id="cLabel">Şərh</label><textarea class="input" id="comment" rows="4" placeholder="Investora göndəriləcək izah..."></textarea></div>
            <button class="btn btn-primary btn-block" type="submit" data-loading="Yadda saxlanılır...">${icon('check', 'i-sm')}Təsdiqlə</button>
            <p class="small muted" style="margin:8px 0 0">İnvestora avtomatik bildiriş göndəriləcək və hadisə EKTİS-ə ötürüləcək.</p>
          </form></div></div>` : ''}
        ${['SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED'].includes(a.status) && criteria.length ? `<div class="card"><div class="card-head"><h3>${icon('clipboard-check')}${mine ? 'Qiymətimi yenilə' : 'Qiymətləndir'}</h3><span class="small muted">Qaydalar, əlavə: 0-5 bal</span></div><div class="card-body">
          <form id="evForm">${criteria.map((c) => `<div class="field" style="margin-bottom:10px"><label><span>${esc(c.label)} <span class="muted">(${c.weight}%)</span></span></label>
            <div class="scale5" data-key="${c.key}">${SCALE.map(([pts, lbl, pct]) => `<button type="button" data-v="${pts * 2}" class="${toPts(myScores[c.key]) === pts ? 'active' : ''}" title="${lbl}: uyğunluq ${pct}"><b>${pts}</b><span>${lbl}</span></button>`).join('')}</div></div>`).join('')}
            <div class="field"><label>Rəy</label><textarea class="input" name="comment" rows="3">${esc(mine?.comment || '')}</textarea></div>
            <button class="btn btn-primary btn-block" type="submit" data-loading="Göndərilir...">${icon('send', 'i-sm')}Qiyməti göndər</button></form></div></div>` : ''}
        <div class="card"><div class="card-head"><h3>${icon('network')}EKTİS</h3></div><div class="card-body">
          <div class="kv" style="grid-template-columns:1fr auto;font-size:13.5px">
            <div>Fermer reyestri</div><div id="farmer">${a.ektisFarmerStatus === 'REGISTERED' ? '<span class="badge badge-good">Qeydiyyatdadır</span>' : a.ektisFarmerStatus === 'NOT_REGISTERED' ? '<span class="badge badge-warn">Tapılmadı</span>' : a.ektisFarmerStatus === 'NOT_APPLICABLE' ? '<span class="badge">Xarici vətəndaş: qalibiyyətdən sonra qeydiyyat</span>' : esc(a.ektisFarmerStatus || '-')}</div>
            <div>İstinad</div><div class="mono">${esc(a.ektisReference || 'gözlənilir')}</div>
          </div>
          ${isAdmin && (a.voen || a.fin) ? `<button class="btn btn-sm btn-block" id="recheck" style="margin-top:12px" data-loading="Sorğu göndərilir...">${icon('refresh-cw', 'i-sm')}Reyestrdə yenidən yoxla</button><div id="farmerOut" class="small" style="margin-top:8px"></div>` : ''}
        </div></div>
        <div class="card"><div class="card-head"><h3>${icon('history')}Tarixçə</h3></div><div class="card-body">${timeline(a.history)}</div></div>
      </aside>
    </div>`;

  renderVotes(a, ctx);

  on(el, 'click', '[data-doc]', (e, b) => downloadFile(`/api/v1/applications/${a.id}/documents/${b.dataset.doc}`, b.dataset.name).catch(errorToast));

  let target = null;
  let decision = null;
  on(el, 'click', '[data-st]', (e, b) => {
    target = b.dataset.st;
    $$('[data-st]', el).forEach((x) => x.classList.toggle('active', x === b));
    $('#stForm').classList.remove('hidden');
    $('#decisionBox').classList.toggle('hidden', target !== 'DECIDED');
    $('#cLabel').classList.toggle('req', target === 'INFO_REQUESTED');
    $('#cLabel').textContent = target === 'INFO_REQUESTED' ? 'Hansı məlumat tələb olunur?' : 'Şərh';
  });
  on(el, 'click', '[data-dec]', (e, b) => { decision = b.dataset.dec; $$('[data-dec]', el).forEach((x) => x.classList.toggle('active', x === b)); });
  $('#stForm')?.addEventListener('submit', (e) => {
    e.preventDefault();
    withLoading(e.submitter, async () => {
      try {
        await api(`/api/v1/admin/applications/${a.id}/status`, { method: 'POST', body: { status: target, comment: $('#comment').value, decision } });
        toast('Status yeniləndi, investora bildiriş göndərildi');
        renderDetail(el, id, ctx);
      } catch (err) { errorToast(err); }
    });
  });

  const ev = $('#evForm');
  if (ev) {
    ev.addEventListener('click', (e) => {
      const b = e.target.closest('.scale5 button');
      if (!b) return;
      b.parentElement.querySelectorAll('button').forEach((x) => x.classList.toggle('active', x === b));
    });
    ev.addEventListener('submit', (e) => {
      e.preventDefault();
      const scores = {};
      // Rəsmi şkala 0/2/3/4/5 daxildə 0-10-a çevrilir (bal x 2), yekun hesablama dəyişmir
      criteria.forEach((c) => { scores[c.key] = Number(ev.querySelector(`.scale5[data-key="${c.key}"] .active`)?.dataset.v ?? 6); });
      withLoading(e.submitter, async () => {
        try {
          await api(`/api/v1/review/applications/${a.id}/evaluations`, { method: 'POST', body: { scores, comment: ev.comment.value } });
          toast('Qiymətləndirmə qeydə alındı');
          renderDetail(el, id, ctx);
        } catch (err) { errorToast(err); }
      });
    });
  }

  $('#recheck')?.addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      const r = await api(`/api/v1/admin/integration/farmer/${encodeURIComponent(a.voen || a.fin)}`);
      $('#farmerOut').innerHTML = r.available
        ? `${r.status === 'REGISTERED' ? '<span class="badge badge-good">Qeydiyyatdadır</span>' : '<span class="badge badge-warn">Tapılmadı</span>'} ${r.farmerId ? `<span class="mono">${esc(r.farmerId)}</span>` : ''}<div class="muted" style="margin-top:4px">${esc(r.message || '')}${r.registeredLandHa ? ` · reyestrdə ${r.registeredLandHa} ha torpaq, ${r.subsidiesReceived} subsidiya` : ''}</div>`
        : `<span class="badge badge-crit">Əlçatan deyil</span> ${esc(r.message || '')}`;
    } catch (err) { errorToast(err); }
  }));
}

// ---------------------------------------------------------------- komissiyanın səsverməsi
const VOTE = { FOR: ['badge-good', 'thumbs-up'], AGAINST: ['badge-crit', 'thumbs-down'] };

async function renderVotes(a, ctx) {
  const box = $('#votes');
  if (!box) return;
  let v;
  try { v = await api(`/api/v1/review/applications/${a.id}/votes`); } catch (e) { box.remove(); return; }
  const t = v.tally;
  const seg = (n, cls) => (n ? `<i class="${cls}" style="flex:${n}"></i>` : '');
  const outcomeCls = t.outcome === 'APPROVED' ? 'badge-good' : t.outcome === 'REJECTED' ? 'badge-crit' : t.outcome === 'NO_QUORUM' ? 'badge-warn' : '';
  box.innerHTML = `<div class="card-head"><h3>${icon('vote')}Komissiyanın səsverməsi</h3><span class="badge ${outcomeCls}">${esc(t.outcomeLabel)}</span></div>
    <div class="card-body">
      <div class="vote-bar" title="Lehinə ${t.forVotes}, əleyhinə ${t.against}, səs verməyib ${t.pending}">${seg(t.forVotes, 'f')}${seg(t.against, 'a')}${seg(t.pending, 'p')}</div>
      <div class="vote-legend"><span><i class="f"></i>Lehinə <b>${t.forVotes}</b></span><span><i class="a"></i>Əleyhinə <b>${t.against}</b></span><span><i class="p"></i>Gözlənilir <b>${t.pending}</b></span></div>
      <p class="small muted" style="margin:8px 0 10px">${t.votingMembers} üzv · kvorum ${t.quorum} · açıq səsvermə, sadə səs çoxluğu, bitərəf yoxdur, bərabərlikdə sədrin səsi həlledicidir (Qaydalar 3.5)</p>
      <div class="vote-list">${v.votes.map((m) => `<div class="vote-row${m.memberId === v.myMemberId ? ' me' : ''}">
        <span class="vote-inst" title="${esc(m.institutionLabel)}">${esc(m.institutionAbbr)}</span>
        <div class="vote-who"><b>${esc(m.fullName)}</b><span>${esc(m.roleLabel)} · ${esc(m.institutionLabel)}</span>${m.comment ? `<em>"${esc(m.comment)}"</em>` : ''}</div>
        ${m.vote ? `<span class="badge ${VOTE[m.vote][0]}">${icon(VOTE[m.vote][1], 'i-sm')}${esc(m.voteLabel)}</span>` : '<span class="badge">gözlənilir</span>'}</div>`).join('')}</div>
      ${v.canVote ? `<form id="voteForm" class="vote-form">
        <div class="small" style="font-weight:650;margin-bottom:6px">${v.myVote ? 'Səsinizi dəyişin' : 'Səsiniz'}</div>
        <div class="seg seg-wrap" id="voteSeg"><button type="button" data-v="FOR" class="${v.myVote === 'FOR' ? 'active' : ''}">${icon('thumbs-up', 'i-sm')}Lehinə</button><button type="button" data-v="AGAINST" class="${v.myVote === 'AGAINST' ? 'active' : ''}">${icon('thumbs-down', 'i-sm')}Əleyhinə</button></div>
        <div class="field" style="margin:8px 0"><textarea class="input" name="comment" rows="2" placeholder="Əsaslandırma (əleyhinə səs üçün məcburidir)"></textarea></div>
        <button class="btn btn-primary btn-block btn-sm" type="submit" data-loading="Göndərilir...">${icon('check', 'i-sm')}Səsi qeydə al</button></form>`
        : v.myMemberId ? `<p class="small muted" style="margin:10px 0 0">${icon('info', 'i-sm')} ${['DECIDED', 'WITHDRAWN'].includes(a.status) ? 'Səsvermə bağlıdır.' : 'Katib səs vermir: kvorum və səs çoxluğu toplananda qərarı rəsmiləşdirir.'}</p>` : ''}
      <a class="btn btn-sm btn-ghost btn-block" style="margin-top:10px" href="/protokol.html?competition=${a.competition.id}" target="_blank" rel="noopener">${icon('scroll-text', 'i-sm')}Komissiya protokolu</a>
    </div>`;
  const f = $('#voteForm');
  if (f) {
    let choice = v.myVote;
    f.querySelector('#voteSeg').addEventListener('click', (e) => {
      const b = e.target.closest('button');
      if (!b) return;
      choice = b.dataset.v;
      f.querySelectorAll('#voteSeg button').forEach((x) => x.classList.toggle('active', x === b));
    });
    f.addEventListener('submit', (e) => {
      e.preventDefault();
      if (!choice) { toast('Səs seçin', 'err'); return; }
      withLoading(e.submitter, async () => {
        try {
          await api(`/api/v1/review/applications/${a.id}/votes`, { method: 'POST', body: { vote: choice, comment: f.comment.value } });
          toast('Səsiniz qeydə alındı');
          renderVotes(a, ctx);
        } catch (err) { errorToast(err); }
      });
    });
  }
  // Qərar bloku: səsvermənin nəticəsini göstərir
  const dec = $('#decisionBox');
  if (dec && !dec.querySelector('.vote-hint') && t.outcome !== 'NONE') {
    dec.insertAdjacentHTML('beforeend', `<div class="vote-hint small ${outcomeCls}">${icon('vote', 'i-sm')} Komissiya: ${esc(t.outcomeLabel)} (lehinə ${t.forVotes}, əleyhinə ${t.against})</div>`);
  }
}
