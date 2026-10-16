// Kateqoriyaya görə avtomatik yaradılan landşaft illüstrasiyaları (SVG) və loqo.
let uid = 0;

function rng(seed) {
  let s = 0;
  for (const ch of String(seed)) s = (s * 31 + ch.charCodeAt(0)) >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

const SKIES = [
  ['#9fd3f2', '#e8f6ff'],
  ['#f6c98b', '#fdecd2'],
  ['#a7d8e8', '#eef8f1'],
  ['#b8c7f0', '#f3f0ff'],
];

function base(id, r, sky, hillA, hillB) {
  const sx = 300 + r() * 70, sy = 38 + r() * 18;
  const m1 = 90 + r() * 20, m2 = 70 + r() * 25;
  return `
  <defs>
    <linearGradient id="sky${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${sky[0]}"/><stop offset="1" stop-color="${sky[1]}"/></linearGradient>
    <linearGradient id="hill${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${hillA}"/><stop offset="1" stop-color="${hillB}"/></linearGradient>
    <radialGradient id="sun${id}"><stop offset="0" stop-color="#fff7d6"/><stop offset=".5" stop-color="#ffe08a"/><stop offset="1" stop-color="#ffe08a" stop-opacity="0"/></radialGradient>
  </defs>
  <rect width="400" height="200" fill="url(#sky${id})"/>
  <circle cx="${sx}" cy="${sy}" r="46" fill="url(#sun${id})"/>
  <circle cx="${sx}" cy="${sy}" r="15" fill="#fff3c4"/>
  <path d="M0 ${m1} L40 ${m1 - 30} L80 ${m1 - 8} L130 ${m1 - 46} L175 ${m1 - 12} L220 ${m1 - 38} L270 ${m1 - 4} L320 ${m1 - 34} L360 ${m1 - 10} L400 ${m1 - 26} L400 200 L0 200Z" fill="#8fa9b8" opacity=".55"/>
  <path d="M0 ${m1 + 8} L60 ${m2 + 10} L110 ${m1 + 2} L170 ${m2 + 4} L230 ${m1 + 6} L300 ${m2} L360 ${m1 + 4} L400 ${m2 + 12} L400 200 L0 200Z" fill="#6f8f86" opacity=".6"/>
  <path d="M0 118 C80 96 160 112 230 104 C300 96 350 108 400 100 L400 200 L0 200Z" fill="url(#hill${id})"/>`;
}

function fields(r) {
  let s = '<path d="M0 132 C120 118 260 126 400 116 L400 200 L0 200Z" fill="#e7b93c"/>';
  for (let i = 0; i < 14; i++) {
    const x = -60 + i * 36;
    s += `<path d="M${x + 60} 200 L${200 + (x - 140) * 0.35} 128" stroke="#c99a1e" stroke-width="3" opacity=".55"/>`;
  }
  for (let i = 0; i < 26; i++) {
    const x = r() * 400, y = 150 + r() * 45, h = 10 + r() * 10;
    s += `<path d="M${x} ${y} l0 -${h}" stroke="#a87b10" stroke-width="1.6"/><ellipse cx="${x}" cy="${y - h}" rx="2.4" ry="5" fill="#b8860b"/>`;
  }
  return s;
}

function pasture(r) {
  let s = '<path d="M0 130 C120 120 260 128 400 118 L400 200 L0 200Z" fill="#7cc36b"/>';
  s += `<g transform="translate(${40 + r() * 30} 104)"><rect x="0" y="12" width="56" height="34" fill="#b3462f"/><path d="M-6 14 L28 -6 L62 14Z" fill="#7d2c1d"/><rect x="21" y="26" width="14" height="20" fill="#f4e3c4"/></g>`;
  const cows = 5 + Math.floor(r() * 3);
  for (let i = 0; i < cows; i++) {
    const x = 140 + r() * 240, y = 148 + r() * 38, k = 0.8 + r() * 0.5;
    s += `<g transform="translate(${x} ${y}) scale(${k})"><ellipse cx="0" cy="0" rx="14" ry="8" fill="#fff"/><circle cx="-5" cy="-2" r="3" fill="#2b2b2b"/><circle cx="5" cy="2" r="3.5" fill="#2b2b2b"/><ellipse cx="15" cy="-4" rx="5" ry="4" fill="#fff"/><rect x="-10" y="6" width="2.4" height="7" fill="#444"/><rect x="8" y="6" width="2.4" height="7" fill="#444"/></g>`;
  }
  return s;
}

function orchard(r, fruit) {
  let s = '<path d="M0 128 C120 118 260 126 400 116 L400 200 L0 200Z" fill="#88c46a"/>';
  for (let row = 0; row < 3; row++) {
    const y = 140 + row * 22, sc = 0.75 + row * 0.25;
    for (let i = 0; i < 9 - row; i++) {
      const x = 20 + i * (400 / (9 - row)) + r() * 12;
      s += `<g transform="translate(${x} ${y}) scale(${sc})"><rect x="-2" y="0" width="4" height="14" fill="#7a4a24"/><circle cx="0" cy="-6" r="13" fill="#3f8f3a"/><circle cx="-5" cy="-9" r="8" fill="#4fa548"/>`;
      for (let f = 0; f < 4; f++) s += `<circle cx="${-8 + r() * 16}" cy="${-14 + r() * 14}" r="2.3" fill="${fruit}"/>`;
      s += '</g>';
    }
  }
  return s;
}

function vineyard(r) {
  let s = '<path d="M0 124 C120 112 260 122 400 110 L400 200 L0 200Z" fill="#9cc66b"/>';
  for (let i = 0; i < 9; i++) {
    const y = 128 + i * 9 + i * i * 0.6;
    s += `<path d="M0 ${y} C130 ${y - 8} 270 ${y + 4} 400 ${y - 6}" stroke="#4d7c2f" stroke-width="${3 + i * 0.7}" fill="none" stroke-linecap="round"/>`;
    for (let g = 0; g < 7; g++) {
      const x = r() * 400;
      s += `<circle cx="${x}" cy="${y + 3}" r="${1.6 + i * 0.25}" fill="#6a2c8a"/>`;
    }
  }
  return s;
}

function greenhouse(r) {
  let s = '<path d="M0 128 C120 120 260 126 400 118 L400 200 L0 200Z" fill="#8cc779"/>';
  for (let row = 0; row < 2; row++) {
    const y = row === 0 ? 150 : 180, sc = row === 0 ? 0.8 : 1.1;
    for (let i = 0; i < 4; i++) {
      const x = 10 + i * 100 + (row ? -30 : 10) + r() * 10;
      s += `<g transform="translate(${x} ${y}) scale(${sc})"><path d="M0 0 L0 -18 C0 -44 80 -44 80 -18 L80 0Z" fill="#e9f7fb" opacity=".88" stroke="#b9dbe6" stroke-width="1.5"/>`;
      for (let k = 1; k < 5; k++) s += `<path d="M${k * 16} 0 L${k * 16} -${18 + Math.sin((k / 5) * Math.PI) * 18}" stroke="#bcdce6" stroke-width="1"/>`;
      s += '<path d="M6 -4 h68" stroke="#4aa34a" stroke-width="5" stroke-dasharray="4 3" opacity=".7"/></g>';
    }
  }
  return s;
}

function apiary(r) {
  let s = '<path d="M0 128 C120 118 260 126 400 116 L400 200 L0 200Z" fill="#94cf74"/>';
  const colors = ['#f2d16b', '#e88f6a', '#f7f0d4', '#a9d2ef'];
  for (let i = 0; i < 70; i++) {
    s += `<circle cx="${r() * 400}" cy="${135 + r() * 62}" r="${1.6 + r() * 1.8}" fill="${colors[Math.floor(r() * colors.length)]}"/>`;
  }
  for (let i = 0; i < 5; i++) {
    const x = 50 + i * 70 + r() * 10, y = 150 + (i % 2) * 14;
    s += `<g transform="translate(${x} ${y})"><rect x="0" y="0" width="34" height="11" fill="#f0b429"/><rect x="0" y="11" width="34" height="11" fill="#e6a117"/><rect x="0" y="22" width="34" height="11" fill="#f0b429"/><rect x="-3" y="-4" width="40" height="5" rx="1" fill="#8a5a12"/><rect x="13" y="28" width="8" height="3" fill="#5a3a0a"/></g>`;
  }
  for (let i = 0; i < 9; i++) {
    const x = 60 + r() * 300, y = 90 + r() * 50;
    s += `<g transform="translate(${x} ${y})"><ellipse cx="0" cy="0" rx="3.2" ry="2.2" fill="#2b2b2b"/><ellipse cx="0" cy="0" rx="1.4" ry="2.2" fill="#f2c230"/><ellipse cx="-1" cy="-2.6" rx="2" ry="1.3" fill="#fff" opacity=".8"/></g>`;
  }
  return s;
}

export function plotArt(category, seed = '') {
  const id = `a${++uid}`;
  const r = rng(category + seed);
  const sky = SKIES[Math.floor(r() * SKIES.length)];
  let body;
  switch (category) {
    case 'EKINCILIK': body = base(id, r, sky, '#d9b44a', '#c49a2c') + fields(r); break;
    case 'HEYVANDARLIQ': body = base(id, r, sky, '#86c66f', '#5ea34b') + pasture(r); break;
    case 'BAGCILIQ': body = base(id, r, sky, '#80c06a', '#5f9f4c') + orchard(r, '#d6334f'); break;
    case 'UZUMCULUK': body = base(id, r, sky, '#9ccb73', '#77a856') + vineyard(r); break;
    case 'ISTIXANA': body = base(id, r, sky, '#86c66f', '#64a650') + greenhouse(r); break;
    case 'ARICILIQ': body = base(id, r, sky, '#8fcd70', '#6aac52') + apiary(r); break;
    default: body = base(id, r, sky, '#86c66f', '#5ea34b');
  }
  return `<svg viewBox="0 0 400 200" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg" role="img" aria-hidden="true">${body}</svg>`;
}

export function heroArt() {
  const id = `h${++uid}`;
  const r = rng('hero-qarabag');
  let s = `<svg viewBox="0 0 560 420" xmlns="http://www.w3.org/2000/svg" class="art" role="img" aria-label="Qarabağ landşaftı">
  <defs>
    <linearGradient id="hs${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffd9a0"/><stop offset=".55" stop-color="#ffeccc"/><stop offset="1" stop-color="#eaf6e6"/></linearGradient>
    <radialGradient id="hsun${id}"><stop offset="0" stop-color="#fff8dc"/><stop offset=".45" stop-color="#ffd36b"/><stop offset="1" stop-color="#ffd36b" stop-opacity="0"/></radialGradient>
    <linearGradient id="hf${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#e9bf45"/><stop offset="1" stop-color="#c99a24"/></linearGradient>
    <linearGradient id="hg${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5fb35a"/><stop offset="1" stop-color="#2f7d3a"/></linearGradient>
  </defs>
  <rect width="560" height="420" fill="url(#hs${id})"/>
  <circle cx="410" cy="120" r="120" fill="url(#hsun${id})"/>
  <circle cx="410" cy="120" r="34" fill="#fff4cf"/>
  <path d="M0 210 L60 150 L110 190 L170 110 L230 175 L290 125 L350 180 L420 130 L480 170 L560 120 L560 420 L0 420Z" fill="#9cb2b8" opacity=".6"/>
  <path d="M170 110 L185 128 L178 126 L170 140 L162 126 L155 128Z" fill="#fff" opacity=".85"/>
  <path d="M290 125 L302 140 L296 139 L290 150 L284 139 L278 140Z" fill="#fff" opacity=".85"/>
  <path d="M0 240 L80 190 L150 228 L230 180 L310 226 L390 186 L470 224 L560 190 L560 420 L0 420Z" fill="#6f9585" opacity=".7"/>
  <path d="M0 265 C110 235 230 262 330 248 C430 234 500 252 560 240 L560 420 L0 420Z" fill="url(#hg${id})"/>
  <path d="M0 300 C140 280 300 300 560 280 L560 420 L0 420Z" fill="url(#hf${id})"/>`;
  for (let i = 0; i < 18; i++) {
    const x = -100 + i * 44;
    s += `<path d="M${x + 100} 420 L${280 + (x - 250) * 0.25} 292" stroke="#b78d1d" stroke-width="3" opacity=".5"/>`;
  }
  for (let i = 0; i < 9; i++) {
    const x = 30 + i * 58 + r() * 10, y = 262 + r() * 8;
    s += `<g transform="translate(${x} ${y})"><rect x="-2" y="0" width="4" height="12" fill="#6b4220"/><circle cx="0" cy="-6" r="11" fill="#2f7d3a"/><circle cx="-4" cy="-9" r="6" fill="#4fa548"/><circle cx="4" cy="-4" r="2" fill="#d6334f"/><circle cx="-5" cy="-2" r="2" fill="#d6334f"/></g>`;
  }
  s += `<g transform="translate(70 318)"><rect x="0" y="0" width="70" height="40" fill="#e9f7fb" opacity=".9" rx="2"/><path d="M0 0 C0 -26 70 -26 70 0Z" fill="#e9f7fb" opacity=".92"/></g>
  <g transform="translate(470 300)"><rect x="0" y="0" width="16" height="44" fill="#f2f2f2"/><circle cx="8" cy="0" r="4" fill="#f2f2f2"/><g transform="translate(8 0)"><path d="M0 0 L0 -38 L4 -2Z" fill="#fff"/><path d="M0 0 L34 16 L2 4Z" fill="#fff"/><path d="M0 0 L-32 20 L-2 4Z" fill="#fff"/></g></g>
  <g transform="translate(395 330)"><rect x="0" y="0" width="54" height="24" rx="3" fill="#264d8c"/><g stroke="#8db3ea" stroke-width="1"><path d="M0 8 h54 M0 16 h54 M18 0 v24 M36 0 v24"/></g><rect x="25" y="24" width="4" height="12" fill="#555"/></g>
  </svg>`;
  return s;
}

export const LOGO = `<img class="brand-mark" src="/img/logo.png" alt="" width="38" height="38">`;
