// Əkinçi 2.0 - ortaq frontend nüvəsi: API müştərisi, sessiya, formatlar, UI komponentləri, layout.
import { LOGO } from './art.js';

const TOKEN_KEY = 'aqroinvest.token';
const USER_KEY = 'aqroinvest.user';

// ------------------------------------------------------------------ DOM helpers
export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
export const params = () => new URLSearchParams(location.search);

// Peyk görüntüsü yüklənməsə sxematik təsvir göstərilir (CSP inline onerror-a icazə vermir: tutma mərhələsində dinləyici)
document.addEventListener('error', (e) => {
  const t = e.target;
  if (t instanceof HTMLImageElement && t.classList.contains('sat-img')) t.closest('.sat')?.classList.add('sat-fail');
}, true);

export function on(root, event, selector, handler) {
  root.addEventListener(event, (e) => {
    const t = e.target.closest(selector);
    if (t && root.contains(t)) handler(e, t);
  });
}

export function esc(v) {
  if (v === null || v === undefined) return '';
  return String(v).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

export function icon(name, cls = '') {
  return `<svg class="i ${cls}" aria-hidden="true"><use href="/img/icons.svg#i-${name}"></use></svg>`;
}

// ------------------------------------------------------------------ session
export const session = {
  token: () => { try { return localStorage.getItem(TOKEN_KEY); } catch { return null; } },
  user: () => { try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); } catch { return null; } },
  set(token, user) {
    try {
      localStorage.setItem(TOKEN_KEY, token);
      localStorage.setItem(USER_KEY, JSON.stringify(user));
      // Profildəki ünsiyyət dili (məs. xarici investor üçün ingilis): istifadəçi dili özü seçməyibsə tətbiq olunur
      if (user?.prefLang && ['en', 'ru'].includes(user.prefLang) && !localStorage.getItem('aqro.lang')) localStorage.setItem('aqro.lang', user.prefLang);
    } catch { /* ignore */ }
  },
  updateUser(user) { try { localStorage.setItem(USER_KEY, JSON.stringify(user)); } catch { /* ignore */ } },
  clear() { try { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); } catch { /* ignore */ } },
  loggedIn() { return !!this.token(); },
  role() { return this.user()?.role; },
  isStaff() { return ['ADMIN', 'EXPERT'].includes(this.role()); },
};

export function homeFor(role) {
  return role === 'ADMIN' || role === 'EXPERT' ? '/admin.html' : role === 'BUYER' ? '/alicilar.html#mine' : '/kabinet.html';
}

export function requireAuth(roles) {
  if (!session.loggedIn()) {
    location.href = '/giris.html?next=' + encodeURIComponent(location.pathname + location.search + location.hash);
    return false;
  }
  if (roles && !roles.includes(session.role())) {
    location.href = homeFor(session.role());
    return false;
  }
  return true;
}

// ------------------------------------------------------------------ API
export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `Xəta (${status})`);
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = body?.fieldErrors || {};
  }
}

export async function api(path, { method = 'GET', body, form, raw = false, quiet401 = false } = {}) {
  const headers = { Accept: 'application/json' };
  const token = session.token();
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (form) payload = form;
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  let res;
  try {
    res = await fetch(path, { method, headers, body: payload });
  } catch {
    throw new ApiError(0, { message: 'Serverlə əlaqə yoxdur. İnternet bağlantısını yoxlayın.' });
  }
  if (raw) {
    if (!res.ok) throw new ApiError(res.status, await res.json().catch(() => ({})));
    return res;
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && token && !quiet401) {
      session.clear();
      toast('Sessiyanın müddəti bitib. Yenidən daxil olun.', 'err');
      setTimeout(() => { location.href = '/giris.html?next=' + encodeURIComponent(location.pathname + location.search); }, 900);
    }
    throw new ApiError(res.status, data);
  }
  return data;
}

export async function downloadFile(url, fallbackName = 'fayl') {
  const res = await api(url, { raw: true });
  const blob = await res.blob();
  const cd = res.headers.get('Content-Disposition') || '';
  let name = fallbackName;
  const m = /filename\*=UTF-8''([^;]+)/i.exec(cd) || /filename="?([^";]+)"?/i.exec(cd);
  if (m) name = decodeURIComponent(m[1]);
  const href = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = href;
  if (blob.type === 'application/pdf' || blob.type.startsWith('image/')) a.target = '_blank';
  else a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(href), 60_000);
}

// ------------------------------------------------------------------ formatters
const nf = new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 });
const nf1 = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 });
const group = (s) => s.replace(/,/g, '\u00a0');
const MONTHS = {
  az: ['yan', 'fev', 'mar', 'apr', 'may', 'iyn', 'iyl', 'avq', 'sen', 'okt', 'noy', 'dek'],
  en: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  ru: ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'],
};

// ------------------------------------------------------------------ dil və valyuta seçimi
const store = {
  get(k, d) { try { return localStorage.getItem(k) || d; } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch { /* yox */ } },
};
export const LANGS = { az: 'Azərbaycan', en: 'English', ru: 'Русский' };
export const CURRENCIES = { AZN: '₼', USD: '$', EUR: '€' };
export const prefs = {
  get lang() { const l = store.get('aqro.lang', 'az'); return LANGS[l] ? l : 'az'; },
  set lang(v) { store.set('aqro.lang', v); },
  get cur() { const c = store.get('aqro.cur', 'AZN'); return CURRENCIES[c] ? c : 'AZN'; },
  set cur(v) { store.set('aqro.cur', v); },
};
// Mərkəzi Bankın məzənnəsi (1 vahid xarici valyuta = X manat); gündə bir dəfə yenilənir
const RATE_FALLBACK = { USD: 1.7, EUR: 1.9067 };
let rateInfo = (() => { try { return JSON.parse(store.get('aqro.rates', 'null')); } catch { return null; } })();
export function rates() { return rateInfo || { date: null, rates: RATE_FALLBACK, source: 'Mərkəzi Bank' }; }
(function refreshRates() {
  const today = new Date().toISOString().slice(0, 10);
  if (rateInfo && rateInfo.fetched === today) return;
  fetch('/api/v1/reference/rates').then((r) => (r.ok ? r.json() : null)).then((j) => {
    if (!j?.rates?.USD) return;
    rateInfo = { ...j, fetched: today };
    store.set('aqro.rates', JSON.stringify(rateInfo));
  }).catch(() => {});
})();
/** Manat məbləğini seçilmiş valyutaya çevirir: [dəyər, simvol]. */
function inCur(v) {
  const c = prefs.cur;
  if (c === 'AZN') return [v, '₼'];
  const r = rates().rates[c] || RATE_FALLBACK[c];
  return [v / r, CURRENCIES[c]];
}

export const fmt = {
  num: (n) => (n === null || n === undefined || n === '' ? '-' : nf.format(Number(n)).replace(/ |,/g, ' ')),
  num1: (n) => (n === null || n === undefined ? '-' : nf1.format(Number(n)).replace(/,/g, ' ')),
  money: (n) => {
    if (n === null || n === undefined || n === '') return '-';
    const [v, sym] = inCur(Number(n));
    return `${fmt.num(v)} ${sym}`;
  },
  compactMoney(n) {
    if (n === null || n === undefined) return '-';
    const [v, sym] = inCur(Number(n));
    // Qrafik oxları canvas-da çəkilir və lüğətdən keçmir: vahid seçilmiş dildə yazılır
    const [M, K] = { en: ['M', 'K'], ru: [' млн', ' тыс.'] }[prefs.lang] || [' mln', ' min'];
    if (Math.abs(v) >= 1e6) return `${fmt.num1(v / 1e6)}${M} ${sym}`;
    if (Math.abs(v) >= 1e3) return `${fmt.num1(v / 1e3)}${K} ${sym}`;
    return `${fmt.num(v)} ${sym}`;
  },
  /** Həmişə manatda (rəsmi sənədlər: protokol, qəbz, müqavilə). */
  azn: (n) => (n === null || n === undefined || n === '' ? '-' : `${fmt.num(n)} ₼`),
  ha: (n) => (n === null || n === undefined ? '-' : `${fmt.num1(n)} ha`),
  date(iso) {
    if (!iso) return '-';
    const d = new Date(iso);
    return `${String(d.getDate()).padStart(2, '0')} ${(MONTHS[prefs.lang] || MONTHS.az)[d.getMonth()]} ${d.getFullYear()}`;
  },
  dateTime(iso) {
    if (!iso) return '-';
    const d = new Date(iso);
    return `${fmt.date(iso)}, ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  },
  rel(iso) {
    if (!iso) return '';
    const diff = (Date.now() - new Date(iso).getTime()) / 1000;
    if (diff < 60) return 'indicə';
    if (diff < 3600) return `${Math.floor(diff / 60)} dəq əvvəl`;
    if (diff < 86400) return `${Math.floor(diff / 3600)} saat əvvəl`;
    if (diff < 86400 * 30) return `${Math.floor(diff / 86400)} gün əvvəl`;
    return fmt.date(iso);
  },
  bytes(n) {
    if (n < 1024) return `${n} B`;
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
    return `${(n / 1024 / 1024).toFixed(1)} MB`;
  },
};

/** Məbləğ sahəsi: yazdıqca minliklər boşluqla ayrılır (100 000); moneyValue yalnız rəqəmləri oxuyur. */
export const moneyValue = (el) => { const d = String(el.value).replace(/\D/g, ''); return d ? Number(d) : null; };
export function bindMoneyInput(el, onChange) {
  el.addEventListener('input', () => {
    const fromEnd = el.value.length - (el.selectionStart ?? el.value.length);
    const d = el.value.replace(/\D/g, '').replace(/^0+(?=\d)/, '').slice(0, 12);
    el.value = d ? fmt.num(Number(d)) : '';
    const pos = Math.max(0, el.value.length - fromEnd);
    try { el.setSelectionRange(pos, pos); } catch { /* fokusda deyil */ }
    onChange?.(moneyValue(el));
  });
}

export function countdown(iso) {
  const ms = new Date(iso).getTime() - Date.now();
  if (ms <= 0) return { over: true, d: 0, h: 0, m: 0, s: 0, text: 'Müddət bitib' };
  const d = Math.floor(ms / 86400000), h = Math.floor((ms % 86400000) / 3600000);
  const m = Math.floor((ms % 3600000) / 60000), s = Math.floor((ms % 60000) / 1000);
  return { over: false, d, h, m, s, text: d > 0 ? `${d} gün ${h} saat qalıb` : `${h} saat ${m} dəq qalıb`, urgent: d < 5 };
}

// ------------------------------------------------------------------ domain meta
export const CAT = {
  EKINCILIK: { label: 'Əkinçilik', icon: 'wheat', color: 'var(--cat-EKINCILIK)', hex: '#eda100' },
  HEYVANDARLIQ: { label: 'Heyvandarlıq', icon: 'milk', color: 'var(--cat-HEYVANDARLIQ)', hex: '#2a78d6' },
  BAGCILIQ: { label: 'Bağçılıq', icon: 'cherry', color: 'var(--cat-BAGCILIQ)', hex: '#e87ba4' },
  UZUMCULUK: { label: 'Üzümçülük', icon: 'grape', color: 'var(--cat-UZUMCULUK)', hex: '#6250d6' },
  ISTIXANA: { label: 'İstixana', icon: 'warehouse', color: 'var(--cat-ISTIXANA)', hex: '#1baf7a' },
  ARICILIQ: { label: 'Arıçılıq', icon: 'hexagon', color: 'var(--cat-ARICILIQ)', hex: '#eb6834' },
};

/** İcarə müddəti Qaydalara görə: ilkin 5 il, monitorinqdən sonra 49 ilədək uzadılır (4.16). */
export function leaseText(p, short = false) {
  const max = p?.leaseYears || 49;
  return short ? `5 + ${Math.max(0, max - 5)} il` : `ilk 5 il, ${max} ilədək uzadılır`;
}

export const DISTRICTS = {
  AGDAM: 'Ağdam', FUZULI: 'Füzuli', XOCAVEND: 'Xocavənd', XOCALI: 'Xocalı', SUSA: 'Şuşa', TERTER: 'Tərtər',
  CEBRAYIL: 'Cəbrayıl', ZENGILAN: 'Zəngilan', QUBADLI: 'Qubadlı', LACIN: 'Laçın', KELBECER: 'Kəlbəcər',
};

const STATUS = {
  DRAFT: ['', 'pencil', 'Qaralama'],
  SUBMITTED: ['badge-info', 'inbox', 'Qəbul edilib'],
  UNDER_REVIEW: ['badge-warn', 'hourglass', 'Baxılır'],
  INFO_REQUESTED: ['badge-serious', 'triangle-alert', 'Əlavə məlumat tələb olunur'],
  DECIDED: ['badge-good', 'award', 'Qərar verilib'],
  WITHDRAWN: ['', 'circle-x', 'Geri çağırılıb'],
};

export function statusBadge(status, decision) {
  let [cls, ic, label] = STATUS[status] || ['', 'circle-dot', status];
  if (status === 'DECIDED' && decision === 'APPROVED') { cls = 'badge-good'; ic = 'award'; label = 'Təsdiqləndi'; }
  if (status === 'DECIDED' && decision === 'REJECTED') { cls = 'badge-crit'; ic = 'circle-x'; label = 'İmtina edildi'; }
  return `<span class="badge ${cls}">${icon(ic)}${esc(label)}</span>`;
}

export function competitionBadge(status) {
  const m = {
    DRAFT: ['', 'pencil', 'Layihə'],
    PENDING_APPROVAL: ['badge-warn', 'hourglass', 'Təsdiq gözləyir'],
    PUBLISHED: ['badge-good', 'circle-check', 'Elan edilib'],
    CLOSED: ['', 'lock', 'Bağlanıb'],
  }[status] || ['', 'circle-dot', status];
  return `<span class="badge ${m[0]}">${icon(m[1])}${m[2]}</span>`;
}

export function plotStatusBadge(status, dark = false) {
  const m = {
    AVAILABLE: [dark ? 'badge-dark' : 'badge-outline', 'circle-dot', 'Müsabiqəyə hazır'],
    IN_COMPETITION: ['badge-green', 'circle-check', 'Müsabiqədə'],
    AWARDED: [dark ? 'badge-dark' : '', 'lock', 'İcarəyə verilib'],
  }[status] || ['', 'circle-dot', status];
  return `<span class="badge ${m[0]}">${icon(m[1])}${m[2]}</span>`;
}

export function catBadge(cat, dark = false) {
  const c = CAT[cat];
  if (!c) return '';
  return `<span class="badge ${dark ? 'badge-dark' : 'badge-outline'}"><span class="cat-dot" style="background:${c.color}"></span>${icon(c.icon)}${c.label}</span>`;
}

export function catSymbol(cat, size = 42) {
  const c = CAT[cat] || { color: 'var(--green-600)', icon: 'leaf' };
  return `<span class="sym" style="background:${c.color};width:${size}px;height:${size}px;border-radius:${Math.round(size / 3.4)}px;display:grid;place-items:center;color:#fff;flex:none">${icon(c.icon)}</span>`;
}

export function demining(status) {
  if (status === 'CLEARED') return `<span class="badge badge-good">${icon('shield-check')}Minalardan təmizlənib</span>`;
  if (status === 'SURVEYED') return `<span class="badge badge-info">${icon('shield')}Texniki sorğu: risk yoxdur</span>`;
  return `<span class="badge badge-warn">${icon('triangle-alert')}Təmizləmə davam edir</span>`;
}

export function ring(score, size = '') {
  const v = Math.max(0, Math.min(100, Number(score) || 0));
  const c = v >= 75 ? 'var(--green-600)' : v >= 55 ? '#d99a00' : 'var(--st-crit)';
  return `<div class="ring ${size}" style="--p:${v};--c:${c}" title="${v}/100"><b>${score ?? '-'}</b></div>`;
}

// ------------------------------------------------------------------ UI
let toastRoot;
export function toast(message, type = 'ok') {
  if (!toastRoot) {
    toastRoot = document.createElement('div');
    toastRoot.className = 'toasts';
    toastRoot.setAttribute('role', 'status');
    document.body.appendChild(toastRoot);
  }
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.innerHTML = `${icon(type === 'err' ? 'circle-x' : 'circle-check')}<div>${esc(message)}</div>`;
  toastRoot.appendChild(el);
  setTimeout(() => { el.style.opacity = '0'; el.style.transition = 'opacity .3s'; }, 4200);
  setTimeout(() => el.remove(), 4600);
}

export function errorToast(e) {
  toast(e?.message || 'Gözlənilməz xəta', 'err');
}

export function modal({ title, body, foot = '', wide = false, onClose } = {}) {
  const bd = document.createElement('div');
  bd.className = 'modal-backdrop open';
  bd.innerHTML = `<div class="modal ${wide ? 'wide' : ''}" role="dialog" aria-modal="true" aria-label="${esc(title)}">
      <div class="modal-head"><h3>${title}</h3><button class="icon-btn" data-close aria-label="Bağla">${icon('x')}</button></div>
      <div class="modal-body">${body}</div>
      ${foot ? `<div class="modal-foot">${foot}</div>` : ''}
    </div>`;
  document.body.appendChild(bd);
  document.body.style.overflow = 'hidden';
  const close = () => {
    bd.remove();
    document.body.style.overflow = '';
    document.removeEventListener('keydown', onKey);
    onClose?.();
  };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  bd.addEventListener('click', (e) => { if (e.target === bd || e.target.closest('[data-close]')) close(); });
  return { el: bd.querySelector('.modal'), close };
}

export function confirmDialog(message, { ok = 'Təsdiqlə', danger = false } = {}) {
  return new Promise((resolve) => {
    let done = false;
    const m = modal({
      title: 'Təsdiq',
      body: `<p style="margin:0">${esc(message)}</p>`,
      foot: `<button class="btn" data-close>Ləğv et</button><button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-ok>${esc(ok)}</button>`,
      onClose: () => { if (!done) resolve(false); },
    });
    m.el.querySelector('[data-ok]').addEventListener('click', () => { done = true; m.close(); resolve(true); });
  });
}

export function promptDialog(title, label, { ok = 'Göndər', placeholder = '' } = {}) {
  return new Promise((resolve) => {
    let done = false;
    const m = modal({
      title,
      body: `<div class="field" style="margin:0"><label class="req">${esc(label)}</label><textarea class="input" rows="4" data-input placeholder="${esc(placeholder)}"></textarea></div>`,
      foot: `<button class="btn" data-close>Ləğv et</button><button class="btn btn-primary" data-ok>${esc(ok)}</button>`,
      onClose: () => { if (!done) resolve(null); },
    });
    const input = m.el.querySelector('[data-input]');
    input.focus();
    m.el.querySelector('[data-ok]').addEventListener('click', () => {
      if (!input.value.trim()) { input.focus(); return; }
      done = true; m.close(); resolve(input.value.trim());
    });
  });
}

export async function withLoading(btn, fn) {
  if (!btn) return fn();
  const html = btn.innerHTML;
  btn.disabled = true;
  btn.innerHTML = `<span class="spin"></span>${btn.dataset.loading || 'Gözləyin...'}`;
  try {
    return await fn();
  } finally {
    btn.disabled = false;
    btn.innerHTML = html;
  }
}

export function showFieldErrors(form, err) {
  $$('.field.invalid', form).forEach((f) => f.classList.remove('invalid'));
  const fe = err?.fieldErrors || {};
  let first;
  Object.entries(fe).forEach(([name, msg]) => {
    const input = form.querySelector(`[name="${name}"]`);
    const field = input?.closest('.field');
    if (field) {
      field.classList.add('invalid');
      let e = field.querySelector('.err');
      if (!e) { e = document.createElement('div'); e.className = 'err'; field.appendChild(e); }
      e.textContent = msg;
      first = first || input;
    }
  });
  first?.focus();
  return Object.keys(fe).length > 0;
}

export function formObject(form) {
  const o = {};
  new FormData(form).forEach((v, k) => { o[k] = typeof v === 'string' ? v.trim() : v; });
  return o;
}

export function setTabs(root, onChange) {
  on(root, 'click', '.tabs button', (e, b) => {
    const t = b.dataset.tab;
    $$('.tabs button', root).forEach((x) => x.classList.toggle('active', x === b));
    $$('.tab-panel', root).forEach((p) => p.classList.toggle('active', p.dataset.panel === t));
    onChange?.(t);
  });
}

// ------------------------------------------------------------------ compare (sessionStorage)
const CMP_KEY = 'aqroinvest.compare';
export const compareStore = {
  get() { try { return JSON.parse(sessionStorage.getItem(CMP_KEY) || '[]'); } catch { return []; } },
  set(list) { try { sessionStorage.setItem(CMP_KEY, JSON.stringify(list.slice(0, 4))); } catch { /* ignore */ } },
  toggle(item) {
    const list = this.get();
    const i = list.findIndex((x) => x.id === item.id);
    if (i >= 0) list.splice(i, 1);
    else {
      if (list.length >= 4) { toast('Ən çox 4 sahəni müqayisə etmək olar', 'err'); return list; }
      list.push(item);
    }
    this.set(list);
    return list;
  },
  has(id) { return this.get().some((x) => x.id === id); },
};

export function renderCompareTray() {
  let tray = $('.compare-tray');
  if (!tray) {
    tray = document.createElement('div');
    tray.className = 'compare-tray no-print';
    document.body.appendChild(tray);
    on(tray, 'click', '[data-clear]', () => { compareStore.set([]); renderCompareTray(); document.dispatchEvent(new CustomEvent('compare-changed')); });
  }
  const list = compareStore.get();
  tray.innerHTML = `${icon('git-compare')}<div class="items">${list.map((x) => `<span>${esc(x.code)}</span>`).join('')}</div>
    <a class="btn btn-gold btn-sm" href="/muqayise.html?ids=${list.map((x) => x.id).join(',')}">Müqayisə et (${list.length})</a>
    <button class="btn btn-sm btn-light" data-clear>${icon('x', 'i-sm')}</button>`;
  tray.classList.toggle('show', list.length > 0);
}

// ------------------------------------------------------------------ layout
/** Masaüstündə "Alətlər" menyusu (mobil menyuda eyni səhifələr ayrıca keçidlərdir). */
const TOOLS = [
  { href: '/portfel.html', key: 'portfolio', icon: 'chart-pie', label: 'Portfel qurucusu', desc: 'Büdcəni riskə görə 50+ variant arasında böl', fresh: true },
  { href: '/regionlar.html', key: 'regions', icon: 'landmark', label: 'Böyük Qayıdış paneli', desc: 'Rayonlar üzrə potensial, iş yerləri, hazırlıq', fresh: true },
  { href: '/simulyator.html', key: 'sim', icon: 'activity', label: 'Gələcək simulyatoru', desc: 'Təsərrüfatın 10-25 ilini il-il yaşa' },
  { href: '/tovsiye.html', key: 'match', icon: 'sparkles', label: 'Mənə uyğun sahə', desc: 'Profilinizə görə sahə tövsiyəsi' },
  { href: '/muqayise.html', key: 'compare', icon: 'git-compare', label: 'Sahələri müqayisə et', desc: '4 sahəyə qədər yan-yana' },
  { href: '/texnika.html', key: 'machinery', icon: 'tractor', label: 'Texnika icarəsi', desc: 'Traktor, kombayn, suvarma: al və ya icarəyə götür', fresh: true },
  { href: '/plan.html', key: 'plan', icon: 'calendar-range', label: 'Fəaliyyət planı', desc: 'Harada, nə vaxt, nə və niyə: iqlim, təqvim, resurs', fresh: true },
  { href: '/qaydalar.html', key: 'rules', icon: 'scroll-text', label: 'Müsabiqə qaydaları', desc: 'Rəsmi Qaydalar: kim, necə, hansı meyarla', fresh: true },
  { href: '#tur', key: 'tour', icon: 'navigation', label: 'Bələdçi tur', desc: 'Platformanı 2 dəqiqədə tanıyın', tour: true },
];

const NAV = [
  { href: '/index.html', key: 'home', icon: 'house', label: 'Ana səhifə', mobile: true },
  { href: '/xerite.html', key: 'map', icon: 'map', label: 'Xəritə' },
  { href: '/kataloq.html', key: 'catalog', icon: 'list', label: 'Kataloq' },
  { href: '/layiheler.html', key: 'projects', icon: 'handshake', label: 'Layihələr' },
  { href: '/alicilar.html', key: 'buyers', icon: 'hand-coins', label: 'Alıcılar' },
  { href: '/musabiqeler.html', key: 'competitions', icon: 'trophy', label: 'Müsabiqələr' },
  { href: '/tovsiye.html', key: 'match', icon: 'sparkles', label: 'Mənə uyğun sahə', mobile: true },
  { href: '/simulyator.html', key: 'sim', icon: 'activity', label: 'Gələcək simulyatoru', mobile: true },
  { href: '/portfel.html', key: 'portfolio', icon: 'chart-pie', label: 'Portfel qurucusu', mobile: true },
  { href: '/regionlar.html', key: 'regions', icon: 'landmark', label: 'Böyük Qayıdış paneli', mobile: true },
  { href: '/plan.html', key: 'plan', icon: 'calendar-range', label: 'Fəaliyyət planı', mobile: true },
  { href: '/texnika.html', key: 'machinery', icon: 'tractor', label: 'Texnika icarəsi', mobile: true },
  { href: '/meslehetci.html', key: 'advisor', icon: 'bot', label: 'AI məsləhətçi', mobile: true },
  { href: '/izle.html', key: 'track', icon: 'search', label: 'Status yoxla', mobile: true },
];

// "Dr.", "Prof." kimi titullar ad sayılmır
const plainName = (name = '') => name.replace(/^(?:(?:Dr|Prof|Akad)\.\s*)+/i, '');
function initials(name = '') {
  return plainName(name).split(/\s+/).filter(Boolean).slice(0, 2).map((s) => s[0]).join('').toUpperCase();
}

export function renderLayout(active) {
  const user = session.user();
  const header = document.createElement('header');
  header.className = 'site-header';
  header.innerHTML = `<div class="container">
      <a class="brand" href="/index.html" aria-label="Əkinçi 2.0 - ana səhifə">${LOGO}<span class="brand-text">Əkinçi 2.0<small>Aqrar investisiya müsabiqələri portalı</small></span></a>
      <nav class="nav" id="mainNav" aria-label="Əsas menyu">
        ${NAV.map((n) => `<a href="${n.href}" class="${n.key === active ? 'active' : ''}${n.mobile ? ' nav-mobile' : ''}">${icon(n.icon)}<span>${n.label}</span></a>`).join('')}
        ${user ? '' : `<a href="/mygov-id.html" class="nav-mobile">${icon('fingerprint')}<span>mygov ID ilə daxil ol</span></a>`}
        <a href="#tur" class="nav-mobile" data-tour>${icon('navigation')}<span>Bələdçi tur</span></a>
        <div class="dropdown nav-tools" id="toolsDd">
          <button type="button" class="nav-tools-btn${TOOLS.some((t) => t.key === active) ? ' active' : ''}" id="toolsBtn" aria-haspopup="true">${icon('sliders-horizontal')}<span>Alətlər</span>${icon('chevron-down', 'i-sm')}</button>
          <div class="dropdown-panel dropdown-menu tools-menu">${TOOLS.map((t) => `<a href="${t.href}" class="${t.key === active ? 'active' : ''}"${t.tour ? ' data-tour' : ''}><span class="tm-ico">${icon(t.icon)}</span><span><b>${t.label}${t.fresh ? ' <em class="tm-new">yeni</em>' : ''}</b><small>${t.desc}</small></span></a>`).join('')}</div>
        </div>
      </nav>
      <div class="header-actions">
        <div class="dropdown pref-dd" id="prefDd">
          <button type="button" class="pref-btn" id="prefBtn" aria-haspopup="true" aria-label="Dil və valyuta">${icon('globe', 'i-sm')}<span class="pref-lang">${prefs.lang.toUpperCase()}</span><span class="pref-cur">${CURRENCIES[prefs.cur]}</span></button>
          <div class="dropdown-panel pref-panel">
            <div class="pref-grp"><span class="pref-h">Dil · Language · Язык</span>${Object.entries(LANGS).map(([k, v]) => `<button type="button" data-lang="${k}" class="${prefs.lang === k ? 'active' : ''}"><b>${k.toUpperCase()}</b>${v}</button>`).join('')}</div>
            <div class="pref-grp"><span class="pref-h">Valyuta</span>${Object.entries(CURRENCIES).map(([k, v]) => `<button type="button" data-cur="${k}" class="${prefs.cur === k ? 'active' : ''}"><b>${v}</b>${k}</button>`).join('')}</div>
            <p class="pref-note" id="prefNote"></p>
          </div>
        </div>
        <a class="btn ai-pill${active === 'advisor' ? ' active' : ''}" href="/meslehetci.html" aria-label="AI investisiya məsləhətçisi">${icon('bot')}<span class="ai-lbl">AI məsləhətçi</span></a>
        ${user ? `
          <div class="dropdown" id="notifDd">
            <button class="icon-btn" id="notifBtn" aria-label="Bildirişlər">${icon('bell')}<span class="dot hidden" id="notifCount"></span><span class="live-dot" title="Real vaxt bağlantısı aktivdir"></span></button>
            <div class="dropdown-panel"><div class="dropdown-head"><b>Bildirişlər</b><button class="btn btn-ghost btn-sm" id="readAll">Hamısını oxu</button></div><div class="dropdown-list" id="notifList"><div class="loader"></div></div></div>
          </div>
          <div class="dropdown" id="userDd">
            <button class="user-chip" id="userBtn"><span class="avatar">${esc(initials(user.fullName))}</span><span class="uname">${esc(plainName(user.fullName).split(' ')[0])}</span>${icon('chevron-down', 'i-sm')}</button>
            <div class="dropdown-panel dropdown-menu" style="width:260px">
              <div class="dropdown-head"><div><b>${esc(user.fullName)}</b><div class="small muted">${esc(user.roleLabel || user.role)}</div></div></div>
              ${user.role === 'INVESTOR' ? `<a href="/kabinet.html">${icon('briefcase')}Kabinetim</a><a href="/kabinet.html#projects">${icon('handshake')}Layihələrim və maraqlarım</a><a href="/layihe-yeni.html">${icon('plus')}Layihə təqdim et</a><a href="/kabinet.html#profile">${icon('user')}Profil</a>`
                : user.role === 'BUYER' ? `<a href="/alicilar.html#mine">${icon('hand-coins')}Tələblərim</a><a href="/alicilar.html#letters">${icon('file-check')}Niyyət məktubları</a>`
                : `<a href="/admin.html">${icon('chart-column')}İdarəetmə paneli</a>`}
              <a href="/swagger-ui.html" target="_blank" rel="noopener">${icon('webhook')}API sənədləri</a>
              <button id="logoutBtn">${icon('log-out')}Çıxış</button>
            </div>
          </div>` : `
          <a class="btn mygov-btn hdr-mygov" href="/mygov-id.html" aria-label="mygov ID ilə daxil ol"><span class="mygov-word">mygov<i></i>ID</span></a>
          <a class="btn btn-ghost" href="/giris.html" aria-label="Daxil ol">${icon('log-in')}<span class="hide-sm">Daxil ol</span></a>
          <a class="btn btn-primary" href="/qeydiyyat.html" aria-label="Qeydiyyat">${icon('user-plus')}<span class="hide-sm">Qeydiyyat</span></a>`}
        <button class="icon-btn menu-toggle" id="menuToggle" aria-label="Menyu">${icon('menu')}</button>
      </div>
    </div>`;
  document.body.prepend(header);

  const footer = document.createElement('footer');
  footer.className = 'site-footer';
  footer.innerHTML = `<div class="container">
      <div class="footer-grid">
        <div>
          <a class="brand" href="/index.html" style="color:#fff">${LOGO}<span class="brand-text" style="color:#fff">Əkinçi 2.0<small style="color:#9fb8a8">Aqrar investisiya müsabiqələri portalı</small></span></a>
          <p style="margin-top:14px;font-size:14px;max-width:360px">İşğaldan azad edilmiş ərazilərdə kənd təsərrüfatı təyinatlı torpaqlara investisiyaların cəlbi üçün vahid rəqəmsal platforma. Kənd Təsərrüfatı Nazirliyinin EKTİS sisteminə inteqrasiyaya hazırdır.</p>
        </div>
        <div><h4>Portal</h4><a href="/xerite.html">İnvestisiya xəritəsi</a><a href="/kataloq.html">Sahələr kataloqu</a><a href="/layiheler.html">Layihələr: sahibkar və investor</a><a href="/alicilar.html">Alıcılar</a><a href="/simulyator.html">Gələcək simulyatoru</a><a href="/portfel.html">Portfel qurucusu</a><a href="/regionlar.html">Böyük Qayıdış paneli</a><a href="/qaydalar.html">Müsabiqə qaydaları</a><a href="/texnika.html">Texnika icarəsi</a><a href="/meslehetci.html">AI məsləhətçi</a><a href="/musabiqeler.html">Müsabiqələr</a></div>
        <div><h4>İnvestorlar üçün</h4><a href="/qeydiyyat.html">Qeydiyyat</a><a href="/kabinet.html">Şəxsi kabinet</a><a href="/izle.html">Müraciətin statusu</a><a href="/muqayise.html">Sahələrin müqayisəsi</a></div>
        <div><h4>Texniki</h4><a href="/swagger-ui.html" target="_blank" rel="noopener">REST API (OpenAPI)</a><a href="/api/v1/plots/geojson" target="_blank" rel="noopener">GeoJSON məlumatları</a><a href="/api/v1/reference" target="_blank" rel="noopener">Klassifikatorlar</a><a href="/actuator/health" target="_blank" rel="noopener">Sistem vəziyyəti</a></div>
      </div>
      <div class="footer-bottom"><span>© ${new Date().getFullYear()} Əkinçi 2.0 · Hakaton prototipi · Technocare</span><span>Göstərilən sahə məlumatları nümayiş məqsədlidir</span></div>
    </div>`;
  document.body.appendChild(footer);

  $('#menuToggle')?.addEventListener('click', () => $('#mainNav').classList.toggle('open'));
  $('#prefBtn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    const d = $('#prefDd'); const o = d.classList.contains('open');
    $$('.dropdown.open').forEach((x) => x.classList.remove('open'));
    d.classList.toggle('open', !o);
    const r = rates();
    $('#prefNote').textContent = `Mərkəzi Bankın məzənnəsi${r.date ? ` (${r.date})` : ''}: 1 $ = ${r.rates.USD} ₼, 1 € = ${r.rates.EUR} ₼. Rəsmi sənədlər manatda qalır.`;
  });
  $$('#prefDd [data-lang]').forEach((b) => b.addEventListener('click', () => { prefs.lang = b.dataset.lang; location.reload(); }));
  $$('#prefDd [data-cur]').forEach((b) => b.addEventListener('click', () => { prefs.cur = b.dataset.cur; location.reload(); }));
  if (prefs.lang !== 'az') import('./i18n.js').then((m) => m.startI18n(prefs.lang)).catch(() => {});
  const closeAll = () => $$('.dropdown.open').forEach((d) => d.classList.remove('open'));
  document.addEventListener('click', (e) => { if (!e.target.closest('.dropdown')) closeAll(); });
  $('#toolsBtn')?.addEventListener('click', (e) => { e.stopPropagation(); const d = $('#toolsDd'); const o = d.classList.contains('open'); closeAll(); d.classList.toggle('open', !o); });
  $('#userBtn')?.addEventListener('click', () => { const d = $('#userDd'); const o = d.classList.contains('open'); closeAll(); d.classList.toggle('open', !o); });
  $('#logoutBtn')?.addEventListener('click', () => { session.clear(); location.href = '/index.html'; });
  if (user) {
    $('#notifBtn').addEventListener('click', () => { const d = $('#notifDd'); const o = d.classList.contains('open'); closeAll(); d.classList.toggle('open', !o); if (!o) loadNotifications(); });
    on($('#notifList'), 'click', '[data-nid]', (e, a) => { api(`/api/v1/notifications/${a.dataset.nid}/read`, { method: 'POST' }).catch(() => {}); });
    $('#readAll').addEventListener('click', async () => { await api('/api/v1/notifications/read-all', { method: 'POST' }).catch(() => {}); loadNotifications(); });
    loadNotifications(true);
    startLive();
  }
  renderCompareTray();
  mountLift();
  syncFavs();
  if (active !== 'advisor') {
    // Başlıqdakı AI düyməsi səhifəni tərk etmədən söhbət panelini açır (Ctrl/Cmd ilə tam səhifə)
    header.querySelector('.ai-pill')?.addEventListener('click', (e) => {
      if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
      e.preventDefault();
      toggleAdvisorPanel('top');
    });
  }
  fitHeader(header);
  // Bələdçi tur: istənilən [data-tour] elementi turu başladır; tur davam edirsə bu səhifədə davam etdirilir
  document.addEventListener('click', (e) => {
    const t = e.target.closest('[data-tour]');
    if (!t) return;
    e.preventDefault();
    import('./tour.js').then((m) => m.startTour());
  });
  try { if (sessionStorage.getItem('aqro.tour')) import('./tour.js').then((m) => m.resumeTour()); } catch { /* yox */ }
}

/**
 * Başlığı real ölçüyə görə yığır: əvvəl ikonlar və alt yazı gizlənir, sonra AI düyməsinin yazısı, sonda menyu
 * "hamburger"ə keçir. Brauzer zoom-u və ya pəncərə eni nə olursa olsun elementlər üst-üstə düşmür.
 */
function fitHeader(header) {
  const box = header.querySelector('.container'), nav = header.querySelector('.nav'), actions = header.querySelector('.header-actions');
  const overflow = () => {
    if (box.scrollWidth > box.clientWidth + 1) return true;
    if (header.classList.contains('hdr-3')) return false;
    const last = nav.lastElementChild;
    return nav.scrollWidth > nav.clientWidth + 1 || (last && last.getBoundingClientRect().right > actions.getBoundingClientRect().left - 6);
  };
  const run = () => {
    header.classList.add('hdr-fit');
    header.classList.remove('hdr-1', 'hdr-2', 'hdr-3', 'hdr-4');
    for (const lvl of ['hdr-1', 'hdr-2', 'hdr-3', 'hdr-4']) {
      if (!overflow()) break;
      header.classList.add(lvl);
    }
    if (!header.classList.contains('hdr-3')) nav.classList.remove('open');
  };
  let raf = 0;
  const schedule = () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(run); };
  run();
  window.addEventListener('resize', schedule);
  document.fonts?.ready.then(schedule);
}

// ------------------------------------------------------------------ AI məsləhətçi (üzən düymə)
let advisorPanel = null;
async function toggleAdvisorPanel(where) {
  if (advisorPanel) {
    const open = !advisorPanel.classList.contains('open') || !advisorPanel.classList.contains(where);
    advisorPanel.classList.toggle('top', where === 'top');
    advisorPanel.classList.toggle('open', open);
    return;
  }
  advisorPanel = document.createElement('div');
  advisorPanel.className = `ai-panel open no-print ${where === 'top' ? 'top' : ''}`;
  advisorPanel.innerHTML = `<div class="ai-panel-head"><b>${icon('bot', 'i-sm')} AI investisiya məsləhətçisi</b><div class="row" style="gap:4px"><a class="icon-btn" href="/meslehetci.html" title="Tam ekran">${icon('external-link', 'i-sm')}</a><button class="icon-btn" data-close aria-label="Bağla">${icon('x', 'i-sm')}</button></div></div><div class="ai-panel-body"></div>`;
  document.body.appendChild(advisorPanel);
  advisorPanel.querySelector('[data-close]').addEventListener('click', () => advisorPanel.classList.remove('open'));
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') advisorPanel?.classList.remove('open'); });
  const { mountAdvisor } = await import('./advisor.js');
  mountAdvisor(advisorPanel.querySelector('.ai-panel-body'), { compact: true });
}

// ------------------------------------------------------------------ live (SSE)
let liveSrc = null;
const liveHandlers = {};
export function onLive(type, fn) { (liveHandlers[type] ||= []).push(fn); }

export async function startLive({ silent = false } = {}) {
  if (!session.loggedIn() || liveSrc) return;
  try {
    const { ticket } = await api('/api/v1/live/ticket', { method: 'POST', quiet401: true });
    liveSrc = new EventSource('/api/v1/live/stream?ticket=' + encodeURIComponent(ticket));
    liveSrc.addEventListener('hello', () => document.body.classList.add('live-on'));
    ['notification', 'ektis', 'ektis-rejected'].forEach((type) => liveSrc.addEventListener(type, (e) => {
      let d;
      try { d = JSON.parse(e.data); } catch { d = e.data; }
      if (type === 'notification' && !silent) {
        toast(`${d.title}${d.message ? ' - ' + d.message : ''}`);
        loadNotifications(true);
      }
      (liveHandlers[type] || []).forEach((fn) => { try { fn(d); } catch (err) { console.error(err); } });
    }));
    liveSrc.onerror = () => {
      document.body.classList.remove('live-on');
      liveSrc.close();
      liveSrc = null;
      setTimeout(() => startLive({ silent }), 4000);
    };
  } catch { /* ignore */ }
}

const NOTIF_ICON = { STATUS: 'file-check', COMPETITION: 'trophy', APPROVAL: 'clipboard-check', APPLICATION: 'inbox', EKTIS: 'network' };

async function loadNotifications(countOnly = false) {
  try {
    const data = await api('/api/v1/notifications', { quiet401: true });
    const c = $('#notifCount');
    if (c) { c.textContent = data.unread > 9 ? '9+' : data.unread; c.classList.toggle('hidden', !data.unread); }
    if (countOnly) return;
    const list = $('#notifList');
    if (!data.items.length) { list.innerHTML = `<div class="empty">${icon('bell')}<div>Bildiriş yoxdur</div></div>`; return; }
    list.innerHTML = data.items.map((n) => `<a class="dropdown-item ${n.read ? '' : 'unread'}" href="${esc(n.link || '#')}" data-nid="${n.id}">
        <span style="color:var(--green-700)">${icon(NOTIF_ICON[n.type] || 'bell')}</span>
        <div><div class="t">${esc(n.title)}</div><div class="m">${esc(n.message || '')}</div><div class="m">${fmt.rel(n.createdAt)}</div></div></a>`).join('');
  } catch { /* ignore */ }
}

export function emptyState(text, ic = 'inbox') {
  return `<div class="empty">${icon(ic)}<div>${esc(text)}</div></div>`;
}

// ------------------------------------------------------------------ lift: bölmədən bölməyə keçid
/**
 * Səhifənin aşağısında ortada yuxarı və aşağı oxlar: hər klik növbəti (və ya əvvəlki) karta və bölməyə keçirir.
 * Uzun səhifəni sürüşdürmək istəməyənlər üçün. Bölmələr hər klikdə yenidən hesablanır (məzmun sonradan yüklənə bilər).
 */
function liftBlocks() {
  const main = document.querySelector('main');
  if (!main) return [];
  const sel = 'section, .card, article, .kpis, .plot-grid > *, .flow-steps, .sides, .tm-stage, .gantt, .rule';
  const vh = innerHeight;
  const all = [...main.querySelectorAll(sel)].filter((el) => {
    if (el.closest('.dropdown-panel, .modal, .tour, .ai-panel')) return false;
    const r = el.getBoundingClientRect();
    return r.height >= 90 && r.width >= 200 && getComputedStyle(el).visibility !== 'hidden';
  });
  // İçində başqa namizəd olan böyük konteynerlər əvəzinə daxili kartlar götürülür (ekrandan hündür olmayanlar)
  const leaf = all.filter((el) => {
    const r = el.getBoundingClientRect();
    if (r.height <= vh * 0.9) return true;
    return !all.some((o) => o !== el && el.contains(o));
  });
  const tops = [];
  for (const el of leaf) {
    const top = Math.round(el.getBoundingClientRect().top + scrollY);
    if (!tops.some((t) => Math.abs(t - top) < 48)) tops.push(top);
  }
  return tops.sort((a, b) => a - b);
}

function mountLift() {
  const page = location.pathname;
  if (/\/(xerite|giris|qeydiyyat|mygov-id|memo|protokol|lovhe|ektis-canli|admin|404)\.html$/.test(page)) return;
  const el = document.createElement('div');
  el.className = 'lift no-print';
  el.innerHTML = `<button type="button" data-dir="-1" aria-label="Əvvəlki bölmə" title="Əvvəlki bölmə">${icon('chevron-up')}</button><span class="lift-sep"></span><button type="button" data-dir="1" aria-label="Növbəti bölmə" title="Növbəti bölmə">${icon('chevron-down')}</button>`;
  document.body.appendChild(el);
  const headerH = () => (document.querySelector('.site-header')?.getBoundingClientRect().height || 64) + 12;
  const go = (dir) => {
    const tops = liftBlocks();
    const y = scrollY + headerH();
    let target;
    if (dir > 0) target = tops.find((t) => t > y + 8);
    else target = [...tops].reverse().find((t) => t < y - 8);
    if (target === undefined) target = dir > 0 ? document.documentElement.scrollHeight : 0;
    window.scrollTo({ top: Math.max(0, target - headerH()), behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  };
  el.addEventListener('click', (e) => { const b = e.target.closest('[data-dir]'); if (b) go(Number(b.dataset.dir)); });
  const sync = () => {
    const max = document.documentElement.scrollHeight - innerHeight;
    el.classList.toggle('show', max > innerHeight * 0.5);
    el.querySelector('[data-dir="-1"]').disabled = scrollY < 20;
    el.querySelector('[data-dir="1"]').disabled = scrollY > max - 20;
    el.classList.toggle('raised', !!document.querySelector('.compare-tray.show, #compareTray.show'));
  };
  let raf = 0;
  const schedule = () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(sync); };
  addEventListener('scroll', schedule, { passive: true });
  addEventListener('resize', schedule);
  new ResizeObserver(schedule).observe(document.body);
  sync();
}

// ------------------------------------------------------------------ sevimli sahələr
/** Sevimlilər brauzerdə saxlanılır; daxil olmuş istifadəçidə serverlə sinxronlaşır və müsabiqəyə çıxanda bildiriş gəlir. */
export const favs = {
  list() { try { return JSON.parse(localStorage.getItem('aqro.favs') || '[]').map(Number); } catch { return []; } },
  save(l) { try { localStorage.setItem('aqro.favs', JSON.stringify([...new Set(l)])); } catch { /* yox */ } },
  has(id) { return favs.list().includes(Number(id)); },
};

export function favButton(id, big = false) {
  const on = favs.has(id);
  return `<button type="button" class="fav-btn${big ? ' big' : ''}${on ? ' on' : ''}" data-fav="${id}" aria-pressed="${on}" title="${on ? 'Sevimlilərdədir: müsabiqəyə çıxanda xəbər veriləcək' : 'Sevimlilərə əlavə et: müsabiqəyə çıxanda xəbər ver'}">${icon('heart', big ? '' : 'i-sm')}${big ? `<span>${on ? 'Sevimlilərdə' : 'Sevimlilərə əlavə et'}</span>` : ''}</button>`;
}

document.addEventListener('click', async (e) => {
  const b = e.target.closest('[data-fav]');
  if (!b) return;
  e.preventDefault();
  const id = Number(b.dataset.fav);
  const on = !favs.has(id);
  favs.save(on ? [...favs.list(), id] : favs.list().filter((x) => x !== id));
  document.querySelectorAll(`[data-fav="${id}"]`).forEach((x) => {
    x.classList.toggle('on', on);
    x.setAttribute('aria-pressed', String(on));
    const sp = x.querySelector('span');
    if (sp) sp.textContent = on ? 'Sevimlilərdə' : 'Sevimlilərə əlavə et';
  });
  if (session.loggedIn()) {
    api(`/api/v1/plots/${id}/watch`, { method: on ? 'POST' : 'DELETE' }).catch(() => {});
    toast(on ? 'Sevimlilərə əlavə edildi: sahə müsabiqəyə çıxanda bildiriş alacaqsınız' : 'Sevimlilərdən çıxarıldı');
  } else {
    toast(on ? 'Sevimlilərə əlavə edildi. Müsabiqə bildirişi üçün daxil olun' : 'Sevimlilərdən çıxarıldı');
  }
});

/** Daxil olandan sonra brauzerdəki sevimliləri serverə köçürür (sessiyada bir dəfə). */
export async function syncFavs() {
  if (!session.loggedIn() || session.role() !== 'INVESTOR') return;
  try { if (sessionStorage.getItem('aqro.favsSynced')) return; sessionStorage.setItem('aqro.favsSynced', '1'); } catch { /* yox */ }
  try {
    const server = (await api('/api/v1/me/watches')).map((w) => w.plotId);
    const local = favs.list();
    for (const id of local.filter((x) => !server.includes(x))) await api(`/api/v1/plots/${id}/watch`, { method: 'POST' }).catch(() => {});
    favs.save([...server, ...local]);
  } catch { /* yox */ }
}
