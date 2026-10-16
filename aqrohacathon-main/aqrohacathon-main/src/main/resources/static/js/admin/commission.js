// Komissiya: dövlət qurumlarının nümayəndələri, səsvermə qaydası və müsabiqələr üzrə protokollar.
import { api, esc, icon, fmt } from '../core.js';

export async function render(el) {
  const [members, comps] = await Promise.all([api('/api/v1/review/commission'), api('/api/v1/competitions').catch(() => [])]);
  const voting = members.filter((m) => m.votes);
  const quorum = Math.ceil((voting.length * 2) / 3);
  el.innerHTML = `
    <div class="admin-title"><div><h1>${icon('gavel')} Müsabiqə komissiyası</h1>
      <p class="muted" style="margin:4px 0 0">Torpağı dövlət verir: qərarı dövlət qurumlarının nümayəndələrindən ibarət komissiya səs çoxluğu ilə qəbul edir. Katib iclası aparır və qərarı rəsmiləşdirir.</p></div></div>
    <div class="kpis" style="margin-bottom:16px">
      <div class="kpi"><div class="lbl">${icon('users', 'i-sm')}Səs hüququ olan üzv</div><div class="val num">${voting.length}</div><div class="sub">${new Set(members.map((m) => m.institution)).size} dövlət qurumu</div></div>
      <div class="kpi"><div class="lbl">${icon('vote', 'i-sm')}Kvorum</div><div class="val num">${quorum}</div><div class="sub">üzvlərin ən azı 2/3-si</div></div>
      <div class="kpi"><div class="lbl">${icon('award', 'i-sm')}Qərar</div><div class="val" style="font-size:20px">Sadə çoxluq</div><div class="sub">bitərəf yoxdur, bərabərlikdə sədr</div></div>
      <div class="kpi"><div class="lbl">${icon('scroll-text', 'i-sm')}Protokol</div><div class="val num">${comps.length}</div><div class="sub">müsabiqə üzrə</div></div>
    </div>
    <div class="card" style="margin-bottom:16px"><div class="card-head"><h3>${icon('landmark')}Tərkib</h3></div><div class="card-body"><div class="vote-list">
      ${members.map((m) => `<div class="vote-row"><span class="vote-inst" title="${esc(m.institutionLabel)}">${esc(m.institutionAbbr)}</span>
        <div class="vote-who"><b>${esc(m.fullName)}</b><span>${esc(m.position || '')} · ${esc(m.institutionLabel)}</span></div>
        <span class="badge ${m.role === 'CHAIR' ? 'badge-gold' : m.role === 'SECRETARY' ? 'badge-info' : ''}">${esc(m.roleLabel)}${m.hasAccount ? ' · portalda' : ''}</span></div>`).join('')}
    </div><p class="small muted" style="margin:10px 0 0">${icon('info', 'i-sm')} Portal hesabı olan üzvlər səsini müraciət səhifəsində özü verir; qalanlarının səsini katib iclas protokolundan daxil edir. Əleyhinə səs əsaslandırılmalıdır, hər səs audit jurnalına yazılır. Tərkib Qaydaların 3.1-ci bəndinə uyğundur (adlar nümunədir).</p></div></div>
    <div class="card"><div class="card-head"><h3>${icon('scroll-text')}İclas protokolları</h3></div><div class="card-body"><div class="table-wrap"><table class="table">
      <thead><tr><th>Müsabiqə</th><th>Status</th><th>Son tarix</th><th></th></tr></thead>
      <tbody>${comps.map((c) => `<tr><td><b class="mono">${esc(c.code)}</b><div class="small muted">${esc(c.title)}</div></td><td><span class="badge">${esc(c.statusLabel || c.status || '')}</span></td><td>${fmt.date(c.deadlineAt)}</td>
        <td class="r"><a class="btn btn-sm" href="/protokol.html?competition=${c.id}" target="_blank" rel="noopener">${icon('printer', 'i-sm')}Protokol</a></td></tr>`).join('')}</tbody></table></div></div></div>`;
}
