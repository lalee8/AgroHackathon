// AI investisiya məsləhətçisi: söhbət komponenti (səhifə və üzən panel üçün ortaq)
import { esc, icon, toast, session, prefs } from './core.js';

export const SUGGESTIONS = [
  '500 min manatım var, aşağı riskli nə tövsiyə edirsən?',
  'Əkinə yararsız şoran torpaqda nə etmək olar?',
  'Ayda 1 milyon manat qazanmaq üçün günəş stansiyası nə qədər olmalıdır?',
  'Bonitet balı nədir, pul baxımından nə deməkdir?',
  'Sahibkaram, alma bağı ideyam var. İnvestoru necə inandırım?',
  'Emal və soyuducu anbara investisiya etmək istəyirəm, alıcı varmı?',
  'I am a foreign investor with $2M. What is the safest option in Karabakh?',
];

/** Sadə və təhlükəsiz markdown: əvvəlcə escape, sonra **qalın**, _kursiv_, [mətn](/daxili-link), "- " siyahı. */
export function md(text) {
  const lines = esc(text).split('\n');
  let html = '', list = false;
  const inline = (s) => s
    .replace(/(\d)(il)(?=[a-zəüöığçş]|\s|[.,)]|$)/g, '$1 $2')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/(^|\s)[_*](\S(?:.*?\S)?)[_*](?=\s|$|[.,])/g, '$1<i>$2</i>')
    .replace(/\[([^\]]+)\]\((\/[^)\s]*)\)/g, '<a href="$2">$1</a>');
  for (const raw of lines) {
    const l = raw.trimEnd();
    if (/^\s*[-•]\s+/.test(l)) {
      if (!list) { html += '<ul>'; list = true; }
      html += `<li>${inline(l.replace(/^\s*[-•]\s+/, ''))}</li>`;
      continue;
    }
    if (list) { html += '</ul>'; list = false; }
    if (l.trim()) html += `<p>${inline(l)}</p>`;
  }
  if (list) html += '</ul>';
  return html;
}

function cardHtml(c) {
  const ic = c.type === 'plot' ? 'map-pin' : c.type === 'project' ? 'handshake' : 'hand-coins';
  const tag = c.type === 'plot' ? 'Torpaq' : c.type === 'project' ? 'Layihə' : 'Alıcı';
  return `<a class="adv-card" href="${esc(c.url)}"><div class="ac-top"><span class="ac-ico">${icon(ic, 'i-sm')}</span><span class="ac-tag">${tag}</span></div><b>${esc(c.title)}</b><div class="small muted">${esc(c.subtitle)}</div><div class="small">${esc(c.body || '')}</div></a>`;
}

/**
 * Söhbəti mount edir. root: konteyner element. opts.compact: üzən panel üçün.
 */
export function mountAdvisor(root, { compact = false } = {}) {
  const KEY = 'aqroinvest.advisor';
  let history = [];
  try { history = JSON.parse(sessionStorage.getItem(KEY) || '[]'); } catch { history = []; }
  root.innerHTML = `
    <div class="adv ${compact ? 'compact' : ''}">
      <div class="adv-log" aria-live="polite"></div>
      <div class="adv-sugs"></div>
      <form class="adv-form"><textarea class="input" rows="1" maxlength="1500" placeholder="Sualınızı yazın və ya mikrofona deyin: büdcə, istiqamət, rayon..."></textarea><button class="btn adv-mic hidden" type="button" aria-label="Səslə soruş" title="Səslə soruş (Azərbaycan dili)">${icon('mic', 'i-sm')}</button><button class="btn btn-primary" type="submit" aria-label="Göndər">${icon('send', 'i-sm')}</button></form>
      <div class="adv-foot small muted"><span class="adv-mode"></span><span>${icon('lock', 'i-sm')} Şəxsi məlumatınız AI-a göndərilmir</span></div>
    </div>`;
  const log = root.querySelector('.adv-log'), form = root.querySelector('.adv-form'), ta = form.querySelector('textarea'), sugs = root.querySelector('.adv-sugs');
  const save = () => { try { sessionStorage.setItem(KEY, JSON.stringify(history.slice(-12))); } catch { /* boş */ } };
  const scroll = () => { log.scrollTop = log.scrollHeight; };

  function bubble(role, html, cards, translate = false) {
    const el = document.createElement('div');
    el.className = `adv-msg ${role}`;
    el.innerHTML = role === 'user' ? `<div class="adv-bubble">${esc(html)}</div>` : `<span class="adv-av">${icon('bot', 'i-sm')}</span><div class="adv-body"><div class="adv-bubble">${html}</div><div class="adv-cards">${(cards || []).map(cardHtml).join('')}</div></div>`;
    // İstifadəçinin sualı və AI cavabı artıq seçilmiş dildədir: lüğətlə tərcümə olunmur (kartlar isə tərcümə olunur)
    if (!translate) el.querySelector('.adv-bubble').setAttribute('data-no-tr', '');
    log.appendChild(el);
    scroll();
    return el;
  }

  function renderHistory() {
    log.innerHTML = '';
    bubble('assistant', md(`Salam! Mən Əkinçi 2.0 platformasının AI məsləhətçisiyəm. **Büdcənizi və marağınızı yazın**, sizə uyğun torpağı, gələcək illər üzrə gəlir ssenarisini, investor axtaran layihələri və məhsulun alıcısını göstərim. Kənd təsərrüfatı terminlərini də pul dilinə çevirirəm.`), null, true);
    history.forEach((m) => bubble(m.role, m.role === 'user' ? m.content : md(m.content), m.cards));
    sugs.innerHTML = history.length ? '' : SUGGESTIONS.slice(0, compact ? 3 : 7).map((s) => `<button type="button" class="chip">${esc(s)}</button>`).join('');
  }

  async function ask(text) {
    if (!text.trim()) return;
    sugs.innerHTML = '';
    history.push({ role: 'user', content: text.trim() });
    bubble('user', text.trim());
    const el = bubble('assistant', '<span class="adv-typing"><i></i><i></i><i></i></span>');
    const bodyEl = el.querySelector('.adv-bubble'), cardsEl = el.querySelector('.adv-cards');
    form.querySelector('button').disabled = true;
    let acc = '', cards = [];
    try {
      const res = await fetch('/api/v1/advisor/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...(session.token() ? { Authorization: 'Bearer ' + session.token() } : {}) },
        body: JSON.stringify({ messages: history.map(({ role, content }) => ({ role, content })), lang: prefs.lang }),
      });
      if (!res.ok) {
        let msg = 'Məsləhətçi cavab vermədi';
        try { msg = (await res.json()).message || msg; } catch { /* boş */ }
        throw new Error(msg);
      }
      const reader = res.body.getReader();
      const dec = new TextDecoder();
      let buf = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buf += dec.decode(value, { stream: true });
        let i;
        while ((i = buf.indexOf('\n\n')) >= 0) {
          const chunk = buf.slice(0, i);
          buf = buf.slice(i + 2);
          let ev = 'message', data = '';
          chunk.split('\n').forEach((ln) => { if (ln.startsWith('event:')) ev = ln.slice(6).trim(); else if (ln.startsWith('data:')) data += ln.slice(5); });
          if (!data) continue;
          const d = JSON.parse(data);
          if (ev === 'meta') { cards = d.cards || []; setMode(d.mode); }
          if (ev === 'delta') { acc += d.t; bodyEl.innerHTML = md(acc) + '<span class="adv-caret"></span>'; scroll(); }
          if (ev === 'done') setMode(d.mode);
        }
      }
      bodyEl.innerHTML = md(acc);
      cardsEl.innerHTML = cards.slice(0, compact ? 4 : 8).map(cardHtml).join('');
      history.push({ role: 'assistant', content: acc, cards: cards.slice(0, compact ? 4 : 8) });
      save();
      scroll();
    } catch (e) {
      bodyEl.innerHTML = `<p>${esc(e.message)}</p>`;
      history.pop();
      toast(e.message, 'err');
    }
    form.querySelector('button').disabled = false;
  }

  function setMode(mode) {
    if (mode === 'guard') return;
    root.querySelector('.adv-mode').innerHTML = mode === 'offline' ? `${icon('cpu', 'i-sm')} Əkinçi AI · oflayn rejim` : `${icon('sparkles', 'i-sm')} Əkinçi AI`;
  }

  form.addEventListener('submit', (e) => { e.preventDefault(); const t = ta.value; ta.value = ''; ask(t); });
  // Səsli sual: brauzerin nitq tanıması (Chrome, Edge, Safari). Kəndli və ya investor yazmadan danışa bilir.
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  const mic = form.querySelector('.adv-mic');
  if (SR) {
    mic.classList.remove('hidden');
    let rec = null;
    mic.addEventListener('click', () => {
      if (rec) { rec.stop(); return; }
      rec = new SR();
      rec.lang = 'az-AZ';
      rec.interimResults = true;
      rec.maxAlternatives = 1;
      const before = ta.value ? ta.value.trim() + ' ' : '';
      rec.onresult = (ev) => { ta.value = before + Array.from(ev.results).map((r) => r[0].transcript).join(' '); };
      rec.onerror = (ev) => { if (ev.error === 'not-allowed') toast('Mikrofona icazə verilməyib', 'err'); };
      rec.onend = () => {
        mic.classList.remove('rec');
        rec = null;
        if (ta.value.trim()) form.requestSubmit();
      };
      mic.classList.add('rec');
      rec.start();
    });
  }
  ta.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); form.requestSubmit(); } });
  sugs.addEventListener('click', (e) => { const b = e.target.closest('.chip'); if (b) ask(b.textContent); });
  root.addEventListener('click', (e) => { if (e.target.closest('[data-reset]')) { history = []; save(); renderHistory(); } });
  fetch('/api/v1/advisor/status').then((r) => r.json()).then((s) => setMode(s.mode)).catch(() => {});
  renderHistory();
  return { ask, reset: () => { history = []; save(); renderHistory(); } };
}
