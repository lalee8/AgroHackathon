// Komissiya iclasının protokolu: üzvlər, müraciətlər yekun bala görə, səsvermə nəticəsi və qərar. Çap üçün.
import { api, requireAuth, esc, fmt, params } from '../core.js';

if (!requireAuth(['ADMIN', 'EXPERT'])) throw new Error('auth');
document.getElementById('print').addEventListener('click', () => window.print());

const id = params().get('competition');
const p = await api(`/api/v1/review/competitions/${id}/protocol`);
const voting = p.members.filter((m) => m.votes);
const chair = p.members.find((m) => m.role === 'CHAIR');
const secretary = p.members.find((m) => m.role === 'SECRETARY');
const decided = p.rows.filter((r) => r.decision);
const tallyCell = (t) => (t.forVotes + t.against ? `${t.forVotes} / ${t.against}` : '-');
document.title = `Protokol ${p.competitionCode} - Əkinçi 2.0`;

document.getElementById('doc').innerHTML = `
  <header class="proto-head">
    <div class="small muted">Azərbaycan Respublikasının Kənd Təsərrüfatı Nazirliyi · İşğaldan azad edilmiş ərazilərdə kənd təsərrüfatı təyinatlı torpaqların icarəyə verilməsi üzrə investisiya müsabiqəsi</div>
    <h1>Müsabiqə komissiyasının iclas protokolu</h1>
    <div class="proto-meta"><span><b>Müsabiqə:</b> ${esc(p.competitionCode)} · ${esc(p.competitionTitle)}</span><span><b>Tarix:</b> ${fmt.dateTime(p.generatedAt)}</span></div>
  </header>
  <h2>1. Komissiyanın tərkibi</h2>
  <table class="proto-table"><thead><tr><th>№</th><th>Ad, soyad</th><th>Qurum</th><th>Vəzifə</th><th>Komissiyada</th></tr></thead>
    <tbody>${p.members.map((m, i) => `<tr><td>${i + 1}</td><td>${esc(m.fullName)}</td><td>${esc(m.institutionLabel)}</td><td>${esc(m.position || '')}</td><td>${esc(m.roleLabel)}</td></tr>`).join('')}</tbody></table>
  <p class="small">Səs hüququ olan üzvlər: ${voting.length}. ${esc(p.rule)}</p>
  <h2>2. Müraciətlərin reytinqi və səsvermənin nəticəsi</h2>
  <table class="proto-table"><thead><tr><th>Yer</th><th>Müraciət</th><th>Müraciətçi</th><th>Sahə</th><th class="r">Yekun bal</th><th class="r">Lehinə / Əleyhinə</th><th>Qərar</th></tr></thead>
    <tbody>${p.rows.map((r) => `<tr><td>${r.rank}</td><td class="mono">${esc(r.appNumber || '-')}</td><td>${esc(r.applicant)}<div class="small muted">${esc(r.applicantType || '')}</div></td>
      <td>${esc(r.plotCode)}</td><td class="r num">${r.finalScore ?? '-'}</td><td class="r num">${tallyCell(r.tally)}</td>
      <td>${r.decision ? `<b>${esc(r.decisionLabel)}</b>` : esc(r.tally.outcomeLabel)}</td></tr>`).join('') || '<tr><td colspan="7">Müraciət yoxdur</td></tr>'}</tbody></table>
  <h2>3. Qərar</h2>
  <p>${decided.length ? `Komissiya səs çoxluğu ilə ${decided.filter((r) => r.decision === 'APPROVED').length} müraciəti qalib, ${decided.filter((r) => r.decision === 'REJECTED').length} müraciəti imtina kimi qəbul etmişdir. Protokol təsdiq edildikdən sonra 20 gün ərzində qaliblərlə EKTİS-də elektron imza ilə ilkin olaraq 5 illik icarə müqaviləsi bağlanır (Qaydalar 4.16).` : 'Səsvermə davam edir, yekun qərar hələ qəbul edilməyib.'}</p>
  <div class="proto-sign">
    <div><span>Komissiyanın sədri</span><b>${esc(chair?.fullName || '')}</b><i></i></div>
    <div><span>Katib</span><b>${esc(secretary?.fullName || '')}</b><i></i></div>
  </div>
  <p class="small muted proto-foot">Sənəd Əkinçi 2.0 platformasında komissiya üzvlərinin elektron səslərindən avtomatik yaradılıb. Hər səs audit jurnalında saxlanılır.</p>`;
