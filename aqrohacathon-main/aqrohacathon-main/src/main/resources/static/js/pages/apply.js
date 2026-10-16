import { api, renderLayout, requireAuth, $, $$, on, esc, icon, fmt, CAT, params, session, toast, errorToast, withLoading, showFieldErrors, ring, countdown, ApiError, leaseText } from '../core.js';
import { satCover } from '../components.js';
import { receipt } from '../receipt.js';

renderLayout('');
if (!requireAuth(['INVESTOR'])) throw new Error('auth');

const root = $('#root');
const P = params();
const STEPS = ['Sahə', 'Müraciətçi', 'İnvestisiya planı', 'Sənədlər', 'Yoxla və göndər'];
const S = { step: 0, app: null, plot: null, comp: null, ref: null };

const TECH_HINT = 'Seçilən hər texnologiya "Texnologiya və innovasiya" meyarı üzrə bal artırır.';

async function init() {
  S.ref = await api('/api/v1/reference');
  const id = P.get('id');
  if (id) {
    S.app = await api(`/api/v1/applications/${id}`);
    if (!['DRAFT', 'INFO_REQUESTED'].includes(S.app.status)) { location.href = `/muraciet-detal.html?id=${id}`; return; }
    S.plot = await api(`/api/v1/plots/${S.app.plot.id}`);
    S.comp = S.app.competition;
    S.step = S.app.status === 'INFO_REQUESTED' ? 3 : 1;
  } else {
    const [plot, comp] = await Promise.all([api(`/api/v1/plots/${P.get('plot')}`), api(`/api/v1/competitions/${P.get('competition')}`)]);
    S.plot = plot;
    S.comp = comp;
  }
  render();
}

function stepsBar() {
  return `<div class="wizard-steps no-print">${STEPS.map((s, i) => `<div class="ws ${i < S.step ? 'done' : ''} ${i === S.step ? 'active' : ''}"><span class="n">${i < S.step ? icon('check', 'i-sm') : i + 1}</span>${s}</div>`).join('')}</div>`;
}

function side() {
  const p = S.plot;
  const cd = countdown(S.comp.deadlineAt);
  return `<aside class="sticky-side">
    <div class="card" style="overflow:hidden">
      <div class="art-fill" style="height:120px">${satCover(p)}</div>
      <div class="card-body">
        <div class="small muted" style="font-weight:700">${esc(p.code)} · ${esc(p.districtLabel)}</div>
        <h3 style="margin:4px 0 10px">${esc(p.name)}</h3>
        <div class="kv" style="grid-template-columns:1fr auto;font-size:13px">
          <div>Sahə</div><div><b>${fmt.ha(p.areaHa)}</b></div>
          <div>Min. investisiya</div><div><b>${fmt.money(p.minInvestment)}</b></div>
          <div>Min. iş yeri</div><div><b>${p.minJobs}</b></div>
          <div>Son tarix</div><div><b>${fmt.date(S.comp.deadlineAt)}</b></div>
        </div>
        <div class="countdown ${cd.urgent ? 'urgent' : ''}" style="margin-top:10px">${icon('hourglass', 'i-sm')}${cd.text}</div>
      </div>
    </div>
    ${S.app ? `<div class="card card-pad score-preview" id="scoreBox"><div class="loader"></div></div>` : ''}
  </aside>`;
}

function render() {
  if (S.app?.status === 'INFO_REQUESTED') {
    $('#title').textContent = `Əlavə məlumat: ${S.app.appNumber}`;
    $('#subtitle').textContent = 'Komissiya əlavə məlumat tələb edib. Məlumatları yeniləyin, sənəd əlavə edin və cavabınızı göndərin.';
  } else if (S.app) {
    $('#title').textContent = 'Elektron müraciət (qaralama)';
  }
  root.innerHTML = `${stepsBar()}<div class="wizard-layout"><div id="stepBody"></div>${side()}</div>`;
  const body = $('#stepBody');
  [step0, step1, step2, step3, step4][S.step](body);
  if (S.app) loadScore();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function loadScore() {
  try {
    const r = await api(`/api/v1/applications/${S.app.id}/score-preview`);
    $('#scoreBox').innerHTML = `<div class="row" style="gap:14px;margin-bottom:10px">${ring(r.total)}<div><b>Gözlənilən ilkin bal</b><div class="small muted">Avtomatik, izahlı qiymətləndirmə. Yekun qərarı komissiya verir.</div></div></div>
      ${r.breakdown.map((c) => `<div class="row-item"><span>${esc(c.label)}</span><b class="num">${fmt.num1(c.points)}/${c.weight}</b></div>`).join('')}`;
  } catch { $('#scoreBox')?.remove(); }
}

// ---------------------------------------------------------------- step 0: plot
function step0(body) {
  const p = S.plot;
  body.innerHTML = `<div class="card card-pad">
    <h2 style="font-size:22px">${icon('map-pin')} Seçilmiş sahə və müsabiqə</h2>
    <p class="muted">Müraciət <b>${esc(S.comp.title)}</b> (${esc(S.comp.code)}) çərçivəsində verilir.</p>
    <div class="spec-grid" style="margin:18px 0">
      <div class="spec"><div class="k">Təyinat</div><div class="v">${CAT[p.category].label}</div></div>
      <div class="spec"><div class="k">Sahə</div><div class="v">${fmt.ha(p.areaHa)}</div></div>
      <div class="spec"><div class="k">İcarə müddəti</div><div class="v">${leaseText(p, true)}</div><div class="small muted">ilk 5 il, uzadılma ilə</div></div>
      <div class="spec"><div class="k">İllik icarə</div><div class="v">${fmt.money(p.annualRentTotal)}</div></div>
      <div class="spec"><div class="k">Min. investisiya</div><div class="v">${fmt.compactMoney(p.minInvestment)}</div></div>
      <div class="spec"><div class="k">Min. iş yeri</div><div class="v">${p.minJobs}</div></div>
    </div>
    <div class="alert alert-info">${icon('info')}<div><b>Müraciət üçün tələb olunanlar:</b> sahibkar məlumatları (FİN və ya VÖEN), investisiya və biznes plan xülasəsi, biznes plan sənədi və şəxsiyyət vəsiqəsi/nizamnamə (PDF, JPG, PNG - maks. 10 MB).</div></div>
    <div class="row between" style="margin-top:22px"><a class="btn" href="/sahe.html?id=${p.id}">${icon('arrow-left', 'i-sm')}Sahəyə qayıt</a><button class="btn btn-primary btn-lg" id="start" data-loading="Yaradılır...">Müraciətə başla ${icon('arrow-right', 'i-sm')}</button></div>
  </div>`;
  $('#start').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    const u = session.user();
    try {
      // Qeydiyyatdakı hüquqi status və rekvizitlər müraciətə avtomatik köçürülür
      const type = u.legalType === 'COMPANY' ? 'LEGAL' : u.legalType === 'FOREIGN' ? 'FOREIGN' : 'INDIVIDUAL';
      S.app = await api('/api/v1/applications', { method: 'POST', body: {
        competitionId: S.comp.id, plotId: p.id, applicantType: type, fin: type === 'FOREIGN' ? '' : u.fin || '', contactEmail: u.email, contactPhone: u.phone || '',
        companyName: type !== 'INDIVIDUAL' ? u.companyName || '' : '', voen: u.voen || '',
        citizenship: type === 'FOREIGN' ? u.citizenship || '' : '', passportNo: type === 'FOREIGN' ? u.passportNo || '' : '',
        activityType: p.category, idpStatus: !!u.localResident, consentGiven: false,
      } });
      history.replaceState(null, '', `/muraciet.html?id=${S.app.id}`);
      S.step = 1;
      render();
      toast('Qaralama yaradıldı - məlumatlar avtomatik yadda saxlanılır');
    } catch (err) {
      if (err.code === 'DUPLICATE_APPLICATION') {
        body.querySelector('.card').insertAdjacentHTML('beforeend', `<div class="alert alert-warn" style="margin-top:16px">${icon('triangle-alert')}<div>${esc(err.message)} <a href="/kabinet.html">Kabinetə keç →</a></div></div>`);
      } else errorToast(err);
    }
  }));
}

// ---------------------------------------------------------------- shared save
function requestFrom(overrides = {}) {
  const a = S.app;
  return {
    competitionId: a.competition.id, plotId: a.plot.id, applicantType: a.applicantType || 'INDIVIDUAL',
    companyName: a.companyName || '', voen: a.voen || '', fin: a.fin || '', citizenship: a.citizenship || '', passportNo: a.passportNo || '', contactPhone: a.contactPhone || '',
    contactEmail: a.contactEmail || '', legalAddress: a.legalAddress || '', activityType: a.activityType,
    investmentAmount: a.investmentAmount, ownFundsPercent: a.ownFundsPercent, jobsPlanned: a.jobsPlanned,
    implementationMonths: a.implementationMonths, experienceYears: a.experienceYears,
    technologies: (a.technologies || []).map((t) => t.code), businessPlan: a.businessPlan || '',
    expectedAnnualRevenue: a.expectedAnnualRevenue, idpStatus: a.idpStatus, consentGiven: a.consentGiven, ...overrides,
  };
}

async function save(form, overrides, btn) {
  return withLoading(btn, async () => {
    try {
      S.app = await api(`/api/v1/applications/${S.app.id}`, { method: 'PUT', body: requestFrom(overrides) });
      return true;
    } catch (err) {
      if (!(err instanceof ApiError) || !showFieldErrors(form, err)) errorToast(err);
      return false;
    }
  });
}

function nav(back = true, nextLabel = 'Yadda saxla və davam et') {
  return `<div class="row between" style="margin-top:22px">${back ? `<button type="button" class="btn" data-back>${icon('arrow-left', 'i-sm')}Geri</button>` : '<span></span>'}
    <button type="submit" class="btn btn-primary btn-lg" data-loading="Yadda saxlanılır...">${nextLabel} ${icon('arrow-right', 'i-sm')}</button></div>`;
}

function bindNav(form, collect) {
  on(form, 'click', '[data-back]', async () => { const ok = await save(form, collect(), null); if (ok) { S.step -= 1; render(); } });
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (await save(form, collect(), e.submitter)) { S.step += 1; render(); }
  });
}

const num = (v) => (v === '' || v === null || v === undefined ? null : Number(v));

// ---------------------------------------------------------------- step 1: applicant
function step1(body) {
  const a = S.app;
  body.innerHTML = `<form class="card card-pad" novalidate>
    <h2 style="font-size:22px">${icon('user')} Müraciətçi haqqında məlumat</h2>
    <div class="choice-cards" style="margin:16px 0 20px">
      <div class="choice ${!a.applicantType || a.applicantType === 'INDIVIDUAL' ? 'active' : ''}" data-type="INDIVIDUAL"><span class="ic">${icon('user')}</span><div><b>Fiziki şəxs / fərdi sahibkar</b><div class="small muted">FİN kod ilə</div></div></div>
      <div class="choice ${a.applicantType === 'LEGAL' ? 'active' : ''}" data-type="LEGAL"><span class="ic">${icon('building-2')}</span><div><b>Hüquqi şəxs</b><div class="small muted">Şirkət, VÖEN ilə</div></div></div>
      <div class="choice ${a.applicantType === 'FOREIGN' ? 'active' : ''}" data-type="FOREIGN"><span class="ic">${icon('globe')}</span><div><b>Xarici vətəndaş</b><div class="small muted">Pasport ilə, icarə hüququ</div></div></div>
    </div>
    <div class="form-grid">
      <div class="field full legal foreign-opt"><label class="co-req req">Şirkətin adı</label><input class="input" name="companyName" value="${esc(a.companyName || '')}" placeholder="məs. Qarabağ Aqro MMC"></div>
      <div class="field"><label class="req" id="voenLabel">VÖEN</label><input class="input mono" name="voen" value="${esc(a.voen || '')}" maxlength="10" placeholder="10 rəqəm"><div class="hint" id="voenHint">Qaydalar 2.4.6: müraciətçi vergi ödəyicisi kimi qeydiyyatda olmalıdır</div></div>
      <div class="field foreign"><label class="req">Vətəndaşlıq</label><input class="input" name="citizenship" value="${esc(a.citizenship || '')}" placeholder="məs. Türkiyə"></div>
      <div class="field foreign"><label class="req">Pasport nömrəsi</label><input class="input mono" name="passportNo" value="${esc(a.passportNo || '')}" maxlength="20"><div class="hint">Pasportun notarial təsdiqli tərcüməsi "Sənədlər" addımında yüklənir</div></div>
      <div class="field not-foreign"><label class="ind-req">FİN kod</label><input class="input mono" name="fin" value="${esc(a.fin || '')}" maxlength="7" placeholder="7 simvol"><div class="hint">EKTİS fermer reyestrində avtomatik yoxlanılır</div></div>
      <div class="field"><label class="req">Əlaqə telefonu</label><input class="input" name="contactPhone" value="${esc(a.contactPhone || '')}" placeholder="+994 50 000 00 00"></div>
      <div class="field"><label class="req">E-poçt</label><input class="input" name="contactEmail" type="email" value="${esc(a.contactEmail || '')}"></div>
      <div class="field full"><label>Hüquqi / qeydiyyat ünvanı</label><input class="input" name="legalAddress" value="${esc(a.legalAddress || '')}"></div>
    </div>
    <label class="check not-foreign"><input type="checkbox" name="idpStatus" ${a.idpStatus ? 'checked' : ''}><span><b>Yerli sakinəm: məcburi köçkün və ya Qarabağ əsilliyəm</b><br><span class="small muted">Sosial təsir meyarı üzrə üstünlük verilir (sənədlə təsdiq tələb oluna bilər)</span></span></label>
    ${nav(false)}
  </form>`;
  const form = body.querySelector('form');
  let type = a.applicantType || 'INDIVIDUAL';
  const sync = () => {
    $$('.legal', form).forEach((el) => { el.style.display = type === 'LEGAL' || (type === 'FOREIGN' && el.classList.contains('foreign-opt')) ? '' : 'none'; });
    $$('.legal:not(.foreign-opt)', form).forEach((el) => { if (type === 'FOREIGN') el.style.display = 'none'; });
    $$('.foreign', form).forEach((el) => { el.style.display = type === 'FOREIGN' ? '' : 'none'; });
    $$('.not-foreign', form).forEach((el) => { el.style.display = type === 'FOREIGN' ? 'none' : ''; });
    form.querySelector('.co-req').classList.toggle('req', type === 'LEGAL');
    form.querySelector('.ind-req').classList.toggle('req', type === 'INDIVIDUAL');
    form.querySelector('#voenLabel').textContent = type === 'LEGAL' ? 'VÖEN' : type === 'FOREIGN' ? 'VÖEN (Azərbaycandakı filial və ya törəmə şirkət)' : 'VÖEN (fərdi sahibkar)';
    form.querySelector('#voenHint').textContent = type === 'FOREIGN'
      ? 'Xarici şəxs torpağı yalnız icarə ilə əldə edir (Torpaq Məcəlləsi 48.3). Müraciət üçün Azərbaycanda vergi qeydiyyatı lazımdır'
      : 'Qaydalar 2.4.6: müraciətçi vergi ödəyicisi kimi qeydiyyatda olmalıdır';
  };
  sync();
  on(form, 'click', '.choice', (e, c) => { type = c.dataset.type; $$('.choice', form).forEach((x) => x.classList.toggle('active', x === c)); sync(); });
  bindNav(form, () => {
    const f = new FormData(form);
    return { applicantType: type, companyName: type !== 'INDIVIDUAL' ? f.get('companyName') : '', voen: f.get('voen'),
      fin: type === 'FOREIGN' ? '' : f.get('fin'), citizenship: type === 'FOREIGN' ? f.get('citizenship') : '', passportNo: type === 'FOREIGN' ? f.get('passportNo') : '',
      contactPhone: f.get('contactPhone'), contactEmail: f.get('contactEmail'), legalAddress: f.get('legalAddress'),
      idpStatus: type !== 'FOREIGN' && form.idpStatus.checked };
  });
}

// ---------------------------------------------------------------- step 2: investment
function step2(body) {
  const a = S.app;
  const p = S.plot;
  const techs = new Set((a.technologies || []).map((t) => t.code));
  body.innerHTML = `<form class="card card-pad" novalidate>
    <h2 style="font-size:22px">${icon('coins')} İnvestisiya və biznes plan</h2>
    <div class="form-grid" style="margin-top:16px">
      <div class="field"><label class="req">Fəaliyyət istiqaməti</label><select class="input" name="activityType">${Object.entries(CAT).map(([k, c]) => `<option value="${k}" ${k === (a.activityType || p.category) ? 'selected' : ''}>${c.label}</option>`).join('')}</select></div>
      <div class="field"><label class="req">İnvestisiya məbləği</label><div class="input-group"><input class="input num" type="number" name="investmentAmount" min="0" step="1000" value="${a.investmentAmount ?? ''}"><span class="addon">₼</span></div><div class="hint">Minimum: ${fmt.money(p.minInvestment)}</div></div>
      <div class="field"><label class="req">Yaradılacaq daimi iş yerləri</label><input class="input num" type="number" name="jobsPlanned" min="0" value="${a.jobsPlanned ?? ''}"><div class="hint">Minimum: ${p.minJobs}</div></div>
      <div class="field"><label class="req">İcra müddəti</label><div class="input-group"><input class="input num" type="number" name="implementationMonths" min="1" max="120" value="${a.implementationMonths ?? ''}"><span class="addon">ay</span></div></div>
      <div class="field"><label class="req">Öz vəsaitinin payı: <b id="ownLbl">${a.ownFundsPercent ?? 40}%</b></label><input type="range" name="ownFundsPercent" min="0" max="100" step="5" value="${a.ownFundsPercent ?? 40}"><div class="hint">Qalan hissə kredit/qrant hesabına</div></div>
      <div class="field"><label>Sahədə təcrübə</label><div class="input-group"><input class="input num" type="number" name="experienceYears" min="0" max="80" value="${a.experienceYears ?? ''}"><span class="addon">il</span></div></div>
      <div class="field full"><label>Gözlənilən illik gəlir (tam gücdə)</label><div class="input-group"><input class="input num" type="number" name="expectedAnnualRevenue" min="0" step="1000" value="${a.expectedAnnualRevenue ?? ''}"><span class="addon">₼</span></div><div class="hint"><a href="/sahe.html?id=${p.id}" target="_blank" rel="noopener">Sahənin gəlirlilik kalkulyatoruna</a> baxa bilərsiniz</div></div>
    </div>
    <div class="field"><label>Tətbiq ediləcək müasir texnologiyalar</label><div class="hint" style="margin-bottom:6px">${TECH_HINT}</div>
      <div class="tech-grid">${S.ref.technologies.map((t) => `<label class="tech ${techs.has(t.code) ? 'active' : ''}"><input type="checkbox" value="${t.code}" ${techs.has(t.code) ? 'checked' : ''}>${esc(t.label)}<span class="pts">+${t.points}</span></label>`).join('')}</div></div>
    <div class="field"><label class="req">Biznes planın xülasəsi</label><textarea class="input" name="businessPlan" rows="8" maxlength="10000" placeholder="Layihənin məqsədi, istehsal həcmi, satış bazarları, mərhələlər, maliyyələşmə mənbələri, risklər...">${esc(a.businessPlan || '')}</textarea><div class="hint"><span id="bpCount">0</span> simvol (minimum 200; 1200+ simvol tam bal)</div></div>
    ${nav()}
  </form>`;
  const form = body.querySelector('form');
  form.ownFundsPercent.addEventListener('input', () => { $('#ownLbl').textContent = form.ownFundsPercent.value + '%'; });
  const bp = () => { $('#bpCount').textContent = form.businessPlan.value.length; };
  form.businessPlan.addEventListener('input', bp);
  bp();
  form.addEventListener('change', (e) => { const t = e.target.closest('.tech'); if (t) t.classList.toggle('active', t.querySelector('input').checked); });
  bindNav(form, () => ({
    activityType: form.activityType.value, investmentAmount: num(form.investmentAmount.value), jobsPlanned: num(form.jobsPlanned.value),
    implementationMonths: num(form.implementationMonths.value), ownFundsPercent: num(form.ownFundsPercent.value),
    experienceYears: num(form.experienceYears.value), expectedAnnualRevenue: num(form.expectedAnnualRevenue.value),
    technologies: $$('.tech input:checked', form).map((x) => x.value), businessPlan: form.businessPlan.value,
  }));
}

// ---------------------------------------------------------------- step 3: documents
function step3(body) {
  const a = S.app;
  const info = a.status === 'INFO_REQUESTED';
  const lastReq = [...a.history].reverse().find((h) => h.toStatus === 'INFO_REQUESTED');
  body.innerHTML = `<form class="card card-pad" novalidate>
    <h2 style="font-size:22px">${icon('file-up')} Sənədlər</h2>
    ${info && lastReq ? `<div class="alert alert-serious" style="margin-bottom:16px">${icon('triangle-alert')}<div><b>Komissiyanın sorğusu:</b> ${esc(lastReq.comment || '')}</div></div>` : ''}
    <div class="alert alert-info" style="margin-bottom:12px">${icon('scroll-text')}<div><b>Qaydalar 2.4.6 üzrə sənədlər.</b> ${a.applicantType === 'FOREIGN' ? 'Xarici vətəndaş pasportun notarial təsdiqli tərcüməsini yükləyir.' : a.applicantType === 'LEGAL' ? 'Hüquqi şəxs dövlət reyestrindən çıxarışı və nümayəndənin səlahiyyət sənədini yükləyir.' : 'Fiziki şəxs şəxsiyyət vəsiqəsinin surətini yükləyir.'} Elektron hökumət (EHİS) vasitəsilə əldə edilə bilən sənədlər (Qaydalar 2.5) pilotda avtomatik gələcək.</div></div>
    <p class="muted small">PDF, JPG və ya PNG; hər fayl maksimum 10 MB. Fayl növü məzmuna görə yoxlanılır və SHA-256 hash ilə qeydə alınır.</p>
    <div class="stack" id="docs"></div>
    ${info ? `<div class="field" style="margin-top:20px"><label class="req">Komissiyaya cavabınız</label><textarea class="input" id="respond" rows="4" placeholder="Hansı məlumatları əlavə etdiyinizi qeyd edin..."></textarea></div>
      <div class="row between" style="margin-top:12px"><a class="btn" href="/muraciet-detal.html?id=${a.id}">${icon('arrow-left', 'i-sm')}Müraciətə qayıt</a><button type="button" class="btn btn-primary btn-lg" id="respondBtn" data-loading="Göndərilir...">${icon('send')}Cavabı göndər</button></div>`
      : nav()}
  </form>`;
  const renderDocs = () => {
    $('#docs').innerHTML = S.ref.documentTypes.map((t) => {
      const files = S.app.documents.filter((d) => d.type === t.code);
      return `<div class="dropzone ${files.length ? 'has' : ''}" data-type="${t.code}">
        <span class="ic">${icon(files.length ? 'file-check' : 'upload')}</span>
        <div style="flex:1;min-width:0"><b class="${t.required ? 'req' : ''}">${esc(t.label)}</b>
          <div class="small muted">${files.length ? `${files.length} fayl yüklənib` : 'Faylı bura sürükləyin və ya seçin'}</div>
          ${files.length ? `<div class="doc-files">${files.map((d) => `<div class="doc-file">${icon('file-text', 'i-sm')}<span class="nm">${esc(d.name)}</span><span class="small muted">${fmt.bytes(d.size)}</span>${S.app.status === 'DRAFT' ? `<button type="button" class="btn btn-ghost btn-sm" data-del="${d.id}" aria-label="Sil">${icon('trash-2', 'i-sm')}</button>` : ''}</div>`).join('')}</div>` : ''}
        </div>
        ${t.code === 'FEE_RECEIPT' && !files.length && S.app.status === 'DRAFT' ? `<button type="button" class="btn btn-sm btn-gold" data-pay>${icon('banknote', 'i-sm')}50 ₼ ödə (demo)</button>` : ''}
        <label class="btn btn-sm">${icon('upload', 'i-sm')}Seç<input type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" hidden data-file="${t.code}"></label>
      </div>`;
    }).join('');
  };
  renderDocs();
  const upload = async (type, file) => {
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) { toast('Fayl 10 MB-dan böyükdür', 'err'); return; }
    const fd = new FormData();
    fd.append('type', type);
    fd.append('file', file);
    const zone = $(`.dropzone[data-type="${type}"] .small`);
    if (zone) zone.textContent = 'Yüklənir...';
    try {
      S.app = await api(`/api/v1/applications/${S.app.id}/documents`, { method: 'POST', form: fd });
      toast(`"${file.name}" yükləndi`);
      loadScore();
    } catch (err) { errorToast(err); }
    renderDocs();
  };
  const docs = $('#docs');
  docs.addEventListener('change', (e) => { const i = e.target.closest('[data-file]'); if (i) upload(i.dataset.file, i.files[0]); });
  // İştirak haqqı (Qaydalar 2.4.8): demoda ödəniş qəbzi yaradılır və sənəd kimi əlavə olunur
  on(docs, 'click', '[data-pay]', () => {
    const now = new Date().toISOString().slice(0, 19).replace('T', ' ');
    const text = `Istirak haqqi qebzi. Mebleg: 50 AZN. Alan: Kend Teserrufati Nazirliyi. Muraciet: ${S.app.id}. Tarix: ${now}. DEMO`;
    const pdf = `%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 200]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n4 0 obj<</Length ${text.length + 40}>>stream\nBT /F1 11 Tf 30 120 Td (${text}) Tj ET\nendstream endobj\n5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n`;
    upload('FEE_RECEIPT', new File([pdf], 'istirak-haqqi-50-azn.pdf', { type: 'application/pdf' }));
  });
  on(docs, 'click', '[data-del]', async (e, b) => {
    try { S.app = await api(`/api/v1/applications/${S.app.id}/documents/${b.dataset.del}`, { method: 'DELETE' }); renderDocs(); loadScore(); } catch (err) { errorToast(err); }
  });
  ['dragenter', 'dragover'].forEach((ev) => docs.addEventListener(ev, (e) => { const z = e.target.closest('.dropzone'); if (z) { e.preventDefault(); z.classList.add('drag'); } }));
  ['dragleave', 'drop'].forEach((ev) => docs.addEventListener(ev, (e) => { const z = e.target.closest('.dropzone'); if (z) { e.preventDefault(); z.classList.remove('drag'); } }));
  docs.addEventListener('drop', (e) => { const z = e.target.closest('.dropzone'); if (z && e.dataTransfer.files[0]) upload(z.dataset.type, e.dataTransfer.files[0]); });

  const form = body.querySelector('form');
  if (info) {
    $('#respondBtn').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
      const comment = $('#respond').value.trim();
      if (!comment) { toast('Cavab mətnini yazın', 'err'); return; }
      try {
        await api(`/api/v1/applications/${S.app.id}/respond`, { method: 'POST', body: { comment } });
        toast('Cavabınız komissiyaya göndərildi');
        location.href = `/muraciet-detal.html?id=${S.app.id}`;
      } catch (err) { errorToast(err); }
    }));
  } else {
    on(form, 'click', '[data-back]', () => { S.step -= 1; render(); });
    form.addEventListener('submit', (e) => { e.preventDefault(); S.step += 1; render(); });
  }
}

// ---------------------------------------------------------------- müraciət köməkçisi (Copilot)
/**
 * Göndərməzdən əvvəl qayda əsaslı yoxlama: boş və ya çatışmayan sahələr, ziddiyyətli rəqəmlər, balı artıran addımlar.
 * Copilot müraciəti təsdiq və ya rədd etmir: yalnız müraciətçiyə nəyi düzəltməli olduğunu göstərir.
 */
function copilot(a, missing) {
  const p = S.plot;
  const must = missing.map((m) => ({ t: m, go: /sənəd|fayl/i.test(m) ? 3 : /razılıq/i.test(m) ? null : 2 }));
  const warn = [], tip = [];
  const inv = Number(a.investmentAmount || 0), rev = Number(a.expectedAnnualRevenue || 0);
  if (inv && inv < Number(p.minInvestment)) must.push({ t: `İnvestisiya minimumdan azdır: ${fmt.money(inv)} < ${fmt.money(p.minInvestment)}`, go: 2 });
  if (a.jobsPlanned != null && a.jobsPlanned < p.minJobs) must.push({ t: `İş yerləri tələbdən azdır: ${a.jobsPlanned} < ${p.minJobs}`, go: 2 });
  if (!a.contactPhone && !a.contactEmail) must.push({ t: 'Əlaqə məlumatı (telefon və ya e-poçt) yoxdur', go: 1 });
  if (a.implementationMonths && p.leaseYears && a.implementationMonths > p.leaseYears * 12) warn.push({ t: `İcra müddəti (${a.implementationMonths} ay) icarə müddətindən (${p.leaseYears} il) uzundur`, go: 2 });
  if (a.activityType && a.activityType !== p.category) warn.push({ t: `Fəaliyyət (${CAT[a.activityType]?.label}) sahənin təyinatından (${CAT[p.category]?.label}) fərqlidir: komissiya əsaslandırma istəyəcək`, go: 2 });
  if (inv && rev && rev > inv * 1.2) warn.push({ t: `Gəlir iddiası çox optimistdir: illik gəlir investisiyadan ${fmt.num1(rev / inv)} dəfə çoxdur. Kalkulyatorla yoxlayın`, go: 2 });
  if (a.ownFundsPercent != null && a.ownFundsPercent < 20) warn.push({ t: `Öz vəsaiti ${a.ownFundsPercent}%: 20%-dən az olanda kredit almaq çətinləşir`, go: 2 });
  const bp = (a.businessPlan || '').length;
  if (bp >= 200 && bp < 1200) tip.push({ t: `Biznes plan ${bp} simvoldur: 1200+ simvolda bu meyar üzrə tam bal`, go: 2 });
  if (!(a.technologies || []).length) tip.push({ t: 'Müasir texnologiya seçilməyib (damcı suvarma, dolu toru və s. əlavə bal verir)', go: 2 });
  if (a.experienceYears == null) tip.push({ t: 'Sahədə təcrübə ili göstərilməyib', go: 2 });
  const have = new Set(a.documents.map((d) => d.type));
  S.ref.documentTypes.filter((t) => !t.required && !have.has(t.code)).forEach((t) => tip.push({ t: `İstəyə bağlı sənəd: ${t.label} (qiymətləndirmədə üstünlük)`, go: 3 }));
  const item = (x, cls, ic) => `<li class="${cls}">${icon(ic, 'i-sm')}<span>${esc(x.t)}</span>${x.go != null ? `<button type="button" class="btn btn-ghost btn-sm" data-goto="${x.go}">Düzəlt</button>` : ''}</li>`;
  const ok = !must.length;
  return `<div class="copilot ${ok ? 'ok' : ''}">
    <div class="copilot-h"><span class="copilot-ico">${icon('bot')}</span><div><b>Müraciət köməkçisi</b><div class="small muted">${ok ? 'Məcburi tələblər tamamdır.' : `${must.length} məsələ göndərməyə mane olur.`} ${warn.length ? `${warn.length} ziddiyyət yoxlanmalıdır.` : ''} Qərarı komissiya verir: köməkçi müraciəti təsdiq və ya rədd etmir.</div></div></div>
    <ul class="copilot-list">${must.map((x) => item(x, 'must', 'circle-x')).join('')}${warn.map((x) => item(x, 'warn', 'triangle-alert')).join('')}${tip.map((x) => item(x, 'tip', 'lightbulb')).join('')}${!must.length && !warn.length && !tip.length ? item({ t: 'Hər şey qaydasındadır: müraciət göndərilə bilər', go: null }, 'good', 'circle-check') : ''}</ul>
  </div>`;
}

// ---------------------------------------------------------------- step 4: review & submit
function step4(body) {
  const a = S.app;
  const missing = a.missingRequirements || [];
  body.innerHTML = `<div class="card card-pad">
    <h2 style="font-size:22px">${icon('clipboard-check')} Yoxlayın və göndərin</h2>
    <div class="kv" style="margin:14px 0">
      <div>Müraciətçi</div><div>${esc(a.applicantType === 'LEGAL' ? a.companyName || '-' : a.applicantName)} <span class="badge">${esc(a.applicantTypeLabel || '')}</span></div>
      <div>${a.applicantType === 'LEGAL' ? 'VÖEN' : a.applicantType === 'FOREIGN' ? 'Pasport' : 'FİN'}</div><div class="mono">${esc((a.applicantType === 'LEGAL' ? a.voen : a.applicantType === 'FOREIGN' ? `${a.passportNo || '-'} · ${a.citizenship || ''}` : a.fin) || '-')}</div>
      <div>Əlaqə</div><div>${esc(a.contactPhone || '-')} · ${esc(a.contactEmail || '-')}</div>
      <div>Fəaliyyət</div><div>${esc(a.activityLabel || '-')}</div>
      <div>İnvestisiya</div><div><b>${fmt.money(a.investmentAmount)}</b> · öz vəsaiti ${a.ownFundsPercent ?? '-'}%</div>
      <div>İş yerləri / müddət</div><div>${a.jobsPlanned ?? '-'} iş yeri · ${a.implementationMonths ?? '-'} ay</div>
      <div>Texnologiyalar</div><div>${(a.technologies || []).map((t) => `<span class="badge badge-green" style="margin:2px">${esc(t.label)}</span>`).join('') || '-'}</div>
      <div>Sənədlər</div><div>${a.documents.length} fayl</div>
    </div>
    ${copilot(a, missing)}
    <label class="check" style="margin:18px 0"><input type="checkbox" id="consent" ${a.consentGiven ? 'checked' : ''}><span>Təqdim etdiyim məlumatların doğruluğunu təsdiq edirəm və "Fərdi məlumatlar haqqında" Qanuna uyğun olaraq onların müsabiqə komissiyası və Kənd Təsərrüfatı Nazirliyinin EKTİS sistemi tərəfindən emalına razıyam.</span></label>
    <div class="row between"><button class="btn" id="back">${icon('arrow-left', 'i-sm')}Geri</button>
      <button class="btn btn-primary btn-lg" id="submit" data-loading="Göndərilir...">${icon('send')}Müraciəti göndər</button></div>
  </div>`;
  $('#back').addEventListener('click', () => { S.step -= 1; render(); });
  body.querySelectorAll('[data-goto]').forEach((b) => b.addEventListener('click', () => { S.step = Number(b.dataset.goto); render(); }));
  $('#consent').addEventListener('change', async (e) => {
    try { S.app = await api(`/api/v1/applications/${S.app.id}`, { method: 'PUT', body: requestFrom({ consentGiven: e.target.checked }) }); render(); } catch (err) { errorToast(err); }
  });
  $('#submit').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    try {
      S.app = await api(`/api/v1/applications/${S.app.id}/submit`, { method: 'POST' });
      success();
    } catch (err) { errorToast(err); }
  }));
}

function success() {
  const a = S.app;
  $('#title').textContent = 'Müraciətiniz qəbul edildi';
  $('#subtitle').textContent = 'Qəbzi yadda saxlayın və ya çap edin. Statusun hər dəyişməsi barədə bildiriş alacaqsınız.';
  root.innerHTML = `<div style="max-width:820px;margin:0 auto">
    <div class="no-print" style="text-align:center;margin-bottom:20px"><div class="success-burst">${icon('circle-check')}</div><h2>Təbriklər! Müraciət uğurla göndərildi</h2>
      <p class="muted">Avtomatik ilkin bal: <b>${a.autoScore}/100</b>${a.ektisFarmerStatus ? ` · EKTİS fermer reyestri: <b>${a.ektisFarmerStatus === 'REGISTERED' ? 'qeydiyyatdadır' : a.ektisFarmerStatus === 'NOT_REGISTERED' ? 'qeydiyyat tapılmadı' : a.ektisFarmerStatus === 'NOT_APPLICABLE' ? 'xarici vətəndaş, qalibiyyətdən sonra' : 'yoxlama gözləyir'}</b>` : ''}</p></div>
    ${receipt(a)}
    <div class="row-wrap no-print" style="justify-content:center;margin-top:20px">
      <button class="btn btn-primary" id="print">${icon('printer')}Qəbzi çap et</button>
      <a class="btn" href="/muraciet-detal.html?id=${a.id}">${icon('eye')}Müraciətə bax</a>
      <a class="btn" href="/kabinet.html">${icon('briefcase')}Kabinetim</a>
    </div></div>`;
  $('#print').addEventListener('click', () => window.print());
  window.scrollTo({ top: 0 });
}

init().catch((e) => { root.innerHTML = `<div class="alert alert-crit">${icon('circle-x')}<div>${esc(e.message)}</div></div>`; });
