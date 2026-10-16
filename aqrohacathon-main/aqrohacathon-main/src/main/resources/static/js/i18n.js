// Çoxdillilik (AZ / EN / RU). Səhifələr Azərbaycan dilində qurulur, seçilmiş dildə isə mətn qovşaqları və atributlar
// lüğətə görə tərcümə olunur. Rəqəmlər açar sözlərdən çıxarılır ({0}, {1} ...), ona görə "24 gün 23 saat qalıb" kimi
// dinamik mətnlər də bir lüğət sətri ilə tərcümə olunur. Yeni əlavə olunan məzmun (API cavabları, modallar) MutationObserver
// ilə izlənir. Lüğətdə olmayan mətn olduğu kimi qalır və window.__i18nMissing-də toplanır.

const ATTRS = ['placeholder', 'title', 'aria-label', 'alt', 'data-loading'];
const SKIP = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'CODE', 'PRE', 'TEXTAREA', 'svg', 'SVG', 'use']);
const NUM = /\d[\d   ]*(?:[.,]\d+)?|\d/g;
const HAS_LETTER = /[A-Za-zƏəĞğİıÖöŞşÜüÇçА-Яа-я]/;

let dict = null;
let lang = 'az';
const missing = new Set();
const done = new WeakMap();

export function normalize(text) {
  const nums = [];
  const key = text.replace(/\s+/g, ' ').trim().replace(NUM, (m) => {
    const t = m.trim();
    if (!t) return m;
    nums.push(t);
    // Rəqəmdən sonrakı boşluq açarda qalır: "3 iş yeri" -> "{0} iş yeri"
    return `{${nums.length - 1}}${m.length > m.trimEnd().length ? ' ' : ''}`;
  });
  return { key, nums };
}

// Artıq tərcümə olunmuş mətnlər (atribut və qrafik yenilənəndə yenidən gəlir): təkrar axtarılmır
const produced = new Set();
// Birləşmiş sətirlər ("AGD-001 · Kəngərli nar bağı (60 ha)") hissə-hissə tərcümə olunur
const SEP = /( · | ↔ | - | – |: | \(|\))/;

/**
 * Lüğət açarları manatla və "min / mln" vahidi ilə yazılıb. Seçilmiş dil və valyutada məbləğ "300K $" və ya "300 тыс. €"
 * kimi görünür: axtarış üçün açar standart formaya gətirilir, tərcümədə isə seçilmiş valyuta simvolu bərpa olunur.
 */
function canonical(key) {
  let sym = null;
  const k = key
    .replace(/\{(\d+)\}(?:K| тыс\.)(?= [₼$€])/g, '{$1} min')
    .replace(/\{(\d+)\}(?:M| млн)(?= [₼$€])/g, '{$1} mln')
    .replace(/(\{\d+\}(?: min| mln)?) ([$€])/g, (m, a, s) => { sym = s; return `${a} ₼`; });
  return { k, sym };
}

function lookup(text) {
  const { key, nums } = normalize(text);
  if (!key) return { out: null, key };
  let hit = dict[key];
  let sym = null;
  if (hit === undefined) {
    const c = canonical(key);
    if (c.k !== key) { hit = dict[c.k]; sym = c.sym; }
  }
  if (hit === undefined) return { out: null, key };
  let out = hit.replace(/\{(\d+)\}/g, (m, i) => (nums[Number(i)] !== undefined ? nums[Number(i)] : m));
  if (sym) out = out.replace(/₼/g, sym);
  return { out, key };
}

/** Bir sətri tərcümə edir; lüğətdə yoxdursa null. */
export function tr(text) {
  if (!dict || !text || !HAS_LETTER.test(text)) return null;
  const core = text.trim();
  if (produced.has(core)) return null;
  let { out, key } = lookup(core);
  if (out === null && SEP.test(core)) {
    let any = false;
    const parts = core.split(SEP).map((p, i) => {
      if (i % 2 === 1 || !HAS_LETTER.test(p)) return p;
      const r = lookup(p.trim());
      if (r.out === null) return p;
      any = true;
      return p.replace(p.trim(), r.out);
    });
    if (any) out = parts.join('');
  }
  if (out === null) {
    if (key && key.length < 400) missing.add(key);
    return null;
  }
  produced.add(out.trim());
  const lead = text.match(/^\s*/)[0], tail = text.match(/\s*$/)[0];
  return lead + out + tail;
}

/** JS kodundan istifadə üçün: mətn tərcüməsi, yoxdursa özü. */
export function t(text) {
  return tr(text) ?? text;
}

function translateNode(node) {
  if (node.nodeType === Node.TEXT_NODE) {
    if (node.parentElement?.closest('[data-no-tr]')) return;
    const v = node.nodeValue;
    if (done.get(node) === v) return;
    const out = tr(v);
    if (out !== null && out !== v) node.nodeValue = out;
    done.set(node, node.nodeValue);
    return;
  }
  if (node.nodeType !== Node.ELEMENT_NODE || SKIP.has(node.nodeName) || node.closest?.('[data-no-tr]')) return;
  for (const a of ATTRS) {
    if (!node.hasAttribute(a)) continue;
    const v = node.getAttribute(a);
    const out = tr(v);
    if (out !== null && out !== v) node.setAttribute(a, out);
  }
  if ((node.tagName === 'INPUT' && (node.type === 'button' || node.type === 'submit')) && node.value) {
    const out = tr(node.value);
    if (out) node.value = out;
  }
  for (const c of node.childNodes) translateNode(c);
}

function observe() {
  let queue = [];
  let scheduled = false;
  const flush = () => {
    scheduled = false;
    const q = queue;
    queue = [];
    for (const n of q) if (n.isConnected) translateNode(n);
  };
  new MutationObserver((muts) => {
    for (const m of muts) {
      if (m.type === 'characterData') queue.push(m.target);
      else if (m.type === 'attributes') queue.push(m.target);
      else m.addedNodes.forEach((n) => queue.push(n));
    }
    if (!scheduled) { scheduled = true; requestAnimationFrame(flush); }
  }).observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ATTRS });
}

/** Chart.js: oxlar, legenda və tooltip mətnləri canvas-da çəkildiyi üçün ayrıca tərcümə olunur. */
function patchCharts() {
  const C = window.Chart;
  if (!C || C.__i18n) return;
  C.__i18n = true;
  const fix = (v) => (typeof v === 'string' ? t(v) : Array.isArray(v) ? v.map(fix) : v);
  C.register({
    id: 'i18n',
    beforeUpdate(chart) {
      const d = chart.data;
      if (d.labels) d.labels = d.labels.map(fix);
      (d.datasets || []).forEach((ds) => { if (ds.label) ds.label = fix(ds.label); });
      const sc = chart.options?.scales || {};
      Object.values(sc).forEach((s) => { if (s?.title?.text) s.title.text = fix(s.title.text); });
      const pl = chart.options?.plugins;
      if (pl?.title?.text) pl.title.text = fix(pl.title.text);
    },
  });
}

export async function startI18n(l) {
  lang = l;
  document.documentElement.lang = l;
  try {
    const res = await fetch(`/i18n/${l}.json`);
    dict = res.ok ? await res.json() : {};
  } catch {
    dict = {};
  }
  if (!Object.keys(dict).length) document.documentElement.classList.remove('i18n-wait');
  window.__i18nMissing = missing;
  window.__t = t;
  if (document.title) document.title = t(document.title);
  translateNode(document.body);
  document.documentElement.classList.remove('i18n-wait');
  observe();
  patchCharts();
  window.addEventListener('load', patchCharts);
  // Başlıq dəyişəndə (səhifə adı) yenidən tərcümə
  const titleEl = document.querySelector('title');
  if (titleEl) new MutationObserver(() => { const v = t(document.title); if (v !== document.title) document.title = v; }).observe(titleEl, { childList: true });
}

export function currentLang() {
  return lang;
}
