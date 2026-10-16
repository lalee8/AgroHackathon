// Bələdçi tur: qonağı salamlayır, platformanın əsas bölmələrini səhifə-səhifə gəzir və hər birini qısa izah edir.
// Vəziyyət sessionStorage-da saxlanılır: "Növbəti" başqa səhifəyə aparanda tur orada davam edir.
import { esc, icon } from './core.js';

const KEY = 'aqro.tour';

const STEPS = [
  { page: '/index.html', center: true, title: 'Salam, xoş gəlmisiniz!', text: 'Mən Əkinçi 2.0 bələdçisiyəm. 2 dəqiqədə platformanı göstərim: torpağı haradan tapmaq, bu torpaqda nə etmək olar, riski necə ölçmək, investoru və alıcını necə tapmaq. Hər addımda "Növbəti" düyməsini basın.' },
  { page: '/index.html', sel: '#heroStats', title: 'Platforma rəqəmlərdə', text: 'Azad edilmiş rayonlarda müsabiqəyə çıxarılan sahələr, ümumi ərazi və aktiv müsabiqələr. Rəqəmlər bazadan canlı gəlir.' },
  { page: '/xerite.html', sel: '#map', title: 'Aqrar investisiya xəritəsi', text: 'Bütün sahələr xəritədədir. Altlıqda kənar yer adı yoxdur, adlar Azərbaycan dilində öz qatımızdandır. Sahələr qruplaşır, yaxınlaşdıqca ayrılır.' },
  { page: '/xerite.html', sel: '#catChips', title: 'Filtrlər', text: 'Təyinata, rayona, ölçüyə, suvarma və elektrikə görə süzün. Filtrlər linkdə saxlanılır: linki göndərsəniz eyni nəticə açılır.' },
  { page: '/kataloq.html', sel: '.plot-grid .plot-card', title: 'Kataloq: real peyk görüntüsü', text: 'Hər kartda sahənin real Sentinel-2 görüntüsü və sarı sərhədi var. Müraciətin son tarixi, infrastruktur və minimum investisiya bir baxışda görünür.' },
  { page: '/sahe.html?id=4', sel: '#tm', title: 'Torpağın zaman maşını', text: 'Peyk arxivi 1990-dan bu günə qədər avtomatik oynayır: işğaldan əvvəl, işğal dövrü, azadlıqdan sonra. Son kadr ən son Sentinel-2 çəkilişidir, server onu özü yeniləyir.' },
  { page: '/sahe.html?id=4', sel: '#potential', title: 'Bu torpaqda nə etmək olar?', text: 'Sistem torpağın təyinatına, relyefinə, suyuna və bazara görə 9 istiqaməti qiymətləndirir: bağ, taxıl, istixana, günəş stansiyası və s. Hər birinin uyğunluq balı və səbəbi yazılıb.' },
  { page: '/simulyator.html?plot=4', sel: '#controls', title: 'Gələcək simulyatoru', text: 'Hazır ssenarini seçin (Baza, Bədbin, Nikbin) və "Gələcəyi yaşa" basın: 2000 mümkün gələcək hesablanır, birini il-il canlı görürsünüz. Nəticə: pis, orta və yaxşı ssenari.' },
  { page: '/portfel.html', sel: '#controls', title: 'Portfel qurucusu', text: 'Büdcənizi riskə görə bir neçə sahə və istiqamət arasında bölür. Quraqlıq ili bütün aran bağlarını eyni anda vurur: portfel bunu nəzərə alıb itkini azaldır.' },
  { page: '/layiheler.html', sel: '#sectors', title: 'Sahibkar və investor', text: 'Müsabiqəyə hazırlaşan və ya qalib olmuş sahibkar layihəsini rəqəmlərlə təqdim edir, platforma onu reallıq yoxlamasından keçirir, investor öz profilinə uyğun layihələri faizlə görür.' },
  { page: '/alicilar.html', sel: '#allGrid', title: 'Məhsulu kim alacaq?', text: 'Emal, ixrac və şəbəkə alıcılarının tələbləri xəritədə qəbul radiusu ilə. Niyyət məktubu layihənin riskini azaldır.' },
  { page: '/texnika.html', sel: '#eqGrid', title: 'Texnika icarəsi', text: 'Traktor, kombayn, suvarma sistemi, dron: texnikanı almaq əvəzinə icarəyə götürün. Kalkulyator almaqla icarənin gəlirliliyini yan-yana göstərir.' },
  { page: '/musabiqeler.html', sel: '.deadline-box', title: 'Müsabiqə və müraciət', text: 'Canlı geri sayım, şəffaf meyarlar və "Müraciət et" düyməsi. Elektron müraciət addım-addım doldurulur, sonda nömrə və QR-li qəbz verilir.' },
  { page: '/regionlar.html', sel: '.kpis', title: 'Böyük Qayıdış paneli', text: 'Dövlət üçün: bu torpaqlar işləsə nə qədər investisiya, iş yeri və neçə ailənin qayıdışı. Rayonlar hazırlıq indeksinə görə sıralanır.' },
  { page: '/regionlar.html', sel: '.site-header .ai-pill', title: 'Əkinçi AI', text: 'Sualınızı yazın və ya deyin: "500 min manatım var, nə tövsiyə edirsən?". AI yalnız platformanın real məlumatına əsaslanır və mövzudan kənar suallara cavab vermir.' },
  { page: '/regionlar.html', center: true, final: true, title: 'Hazırsınız!', text: 'mygov ID ilə bir kliklə daxil olun və ya qeydiyyatdan keçin: fiziki şəxs, hüquqi şəxs və ya xarici vətəndaş kimi. Turu istənilən vaxt "Alətlər" menyusundan yenidən başlada bilərsiniz.' },
];

const state = {
  get() { try { return JSON.parse(sessionStorage.getItem(KEY) || 'null'); } catch { return null; } },
  set(v) { try { sessionStorage.setItem(KEY, JSON.stringify(v)); } catch { /* yox */ } },
  clear() { try { sessionStorage.removeItem(KEY); } catch { /* yox */ } },
};

const samePage = (step) => {
  const u = new URL(step.page, location.origin);
  const cur = location.pathname === '/' ? '/index.html' : location.pathname;
  if (u.pathname !== cur) return false;
  const want = u.searchParams.get('id') || u.searchParams.get('plot');
  if (!want) return true;
  const p = new URLSearchParams(location.search);
  return (p.get('id') || p.get('plot')) === want;
};

let ui = null;

function build() {
  ui = document.createElement('div');
  ui.className = 'tour';
  ui.innerHTML = `<div class="tour-shade"></div><div class="tour-ring" hidden></div>
    <div class="tour-pop" role="dialog" aria-modal="false" aria-live="polite">
      <div class="tour-head"><span class="tour-badge">${icon('navigation', 'i-sm')} Bələdçi</span><span class="tour-n"></span><button class="icon-btn tour-x" type="button" aria-label="Turu bitir">${icon('x', 'i-sm')}</button></div>
      <h3 class="tour-t"></h3><p class="tour-p"></p>
      <div class="tour-bar"><i></i></div>
      <div class="tour-btns"><button class="btn btn-ghost btn-sm tour-prev" type="button">${icon('arrow-left', 'i-sm')}Geri</button><span></span><button class="btn btn-primary btn-sm tour-next" type="button">Növbəti${icon('arrow-right', 'i-sm')}</button></div>
    </div>`;
  document.body.appendChild(ui);
  ui.querySelector('.tour-x').addEventListener('click', end);
  ui.querySelector('.tour-prev').addEventListener('click', () => go(state.get().i - 1));
  ui.querySelector('.tour-next').addEventListener('click', () => go(state.get().i + 1));
  document.addEventListener('keydown', (e) => {
    if (!state.get()) return;
    if (e.key === 'Escape') end();
    if (e.key === 'ArrowRight') go(state.get().i + 1);
    if (e.key === 'ArrowLeft') go(state.get().i - 1);
  });
  window.addEventListener('resize', place);
  window.addEventListener('scroll', place, { passive: true });
}

let target = null;

function place() {
  if (!ui) return;
  const pop = ui.querySelector('.tour-pop'), ring = ui.querySelector('.tour-ring');
  if (!target) {
    ring.hidden = true;
    ui.classList.add('centered');
    pop.style.left = ''; pop.style.top = '';
    return;
  }
  ui.classList.remove('centered');
  const r = target.getBoundingClientRect(), pad = 8;
  ring.hidden = false;
  Object.assign(ring.style, { left: `${r.left - pad}px`, top: `${r.top - pad}px`, width: `${r.width + pad * 2}px`, height: `${r.height + pad * 2}px` });
  const pw = pop.offsetWidth, ph = pop.offsetHeight, vw = innerWidth, vh = innerHeight;
  let top = r.bottom + 14;
  if (top + ph > vh - 10) top = r.top - ph - 14;
  if (top < 10) top = Math.max(10, Math.min(vh - ph - 10, r.top + 10));
  const left = Math.max(10, Math.min(vw - pw - 10, r.left + r.width / 2 - pw / 2));
  pop.style.left = `${left}px`;
  pop.style.top = `${top}px`;
}

async function waitFor(sel, ms = 7000) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) {
    const el = document.querySelector(sel);
    if (el && el.getBoundingClientRect().height > 0) return el;
    await new Promise((r) => setTimeout(r, 150));
  }
  return null;
}

async function show(i) {
  const step = STEPS[i];
  if (!ui) build();
  ui.hidden = false;
  ui.querySelector('.tour-n').textContent = `${i + 1} / ${STEPS.length}`;
  ui.querySelector('.tour-t').textContent = step.title;
  ui.querySelector('.tour-p').textContent = step.text;
  ui.querySelector('.tour-bar i').style.width = `${((i + 1) / STEPS.length) * 100}%`;
  ui.querySelector('.tour-prev').disabled = i === 0;
  const next = ui.querySelector('.tour-next');
  next.innerHTML = step.final ? `${icon('check', 'i-sm')}Bitir` : i === 0 ? `Başla${icon('arrow-right', 'i-sm')}` : `Növbəti${icon('arrow-right', 'i-sm')}`;
  const extra = ui.querySelector('.tour-btns span');
  extra.innerHTML = step.final ? `<a class="btn btn-sm mygov-btn" href="/mygov-id.html"><span class="mygov-word">mygov<i></i>ID</span></a> <a class="btn btn-sm" href="/qeydiyyat.html">Qeydiyyat</a>` : '';
  target = null;
  place();
  if (!step.center && step.sel) {
    const el = await waitFor(step.sel);
    if (state.get()?.i !== i) return;
    if (el) {
      target = el;
      const block = () => (el.getBoundingClientRect().height > innerHeight * 0.6 ? 'start' : 'center');
      el.scrollIntoView({ behavior: 'smooth', block: block() });
      // Səhifənin yuxarısındakı bölmələr gec yüklənib hədəfi itələyə bilər: ilk saniyələrdə hədəfi görünən saxla
      const t0 = Date.now();
      const follow = () => {
        if (target !== el || state.get()?.i !== i) return;
        const r = el.getBoundingClientRect();
        if (Date.now() - t0 < 3500 && Date.now() - t0 > 700 && (r.top > innerHeight * 0.7 || r.bottom < 80)) el.scrollIntoView({ block: block() });
        place();
        requestAnimationFrame(follow);
      };
      requestAnimationFrame(follow);
    }
  }
  place();
}

function go(i) {
  if (i < 0) return;
  if (i >= STEPS.length) { end(); return; }
  state.set({ i });
  const step = STEPS[i];
  if (!samePage(step)) {
    ui?.classList.add('leaving');
    location.href = step.page;
    return;
  }
  show(i);
}

function end() {
  state.clear();
  target = null;
  if (ui) ui.hidden = true;
}

/** Turu başladır (istənilən səhifədən). */
export function startTour() {
  go(0);
}

/** Səhifə açılanda: tur davam edirsə, həmin addımı göstər. */
export function resumeTour() {
  const s = state.get();
  if (!s) return;
  const step = STEPS[s.i];
  if (!step) { state.clear(); return; }
  if (!samePage(step)) return;
  show(s.i);
}

export const tourSteps = STEPS.map((s) => esc(s.title));
