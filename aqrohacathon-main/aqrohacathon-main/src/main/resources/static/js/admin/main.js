// İdarəetmə paneli - hash əsaslı marşrutlaşdırma və rol əsaslı menyu.
import { renderLayout, requireAuth, session, $, esc, icon, api, onLive } from '../core.js';

renderLayout('');
document.querySelector('.site-footer')?.remove();
if (!requireAuth(['ADMIN', 'EXPERT'])) throw new Error('auth');

const role = session.role();
const MENU = [
  { grp: 'İdarəetmə' },
  { key: 'dashboard', label: 'Analitika paneli', icon: 'chart-column', roles: ['ADMIN'] },
  { key: 'applications', label: 'Müraciətlər', icon: 'inbox', roles: ['ADMIN', 'EXPERT'] },
  { key: 'ranking', label: 'Reytinq', icon: 'trophy', roles: ['ADMIN', 'EXPERT'] },
  { key: 'commission', label: 'Komissiya', icon: 'gavel', roles: ['ADMIN', 'EXPERT'] },
  { key: 'monitoring', label: 'Peyk monitorinqi', icon: 'satellite', roles: ['ADMIN'] },
  { grp: 'Kataloq' },
  { key: 'plots', label: 'Torpaq sahələri', icon: 'map', roles: ['ADMIN'] },
  { key: 'competitions', label: 'Müsabiqələr', icon: 'clipboard-check', roles: ['ADMIN'], badge: 'pendingCompetitionApprovals' },
  { grp: 'Bazar' },
  { key: 'demands', label: 'Alıcı tələbləri', icon: 'hand-coins', roles: ['ADMIN'], badge: 'pendingDemands' },
  { key: 'projects', label: 'Sahibkar layihələri', icon: 'handshake', roles: ['ADMIN'] },
  { grp: 'Sistem' },
  { key: 'integration', label: 'EKTİS inteqrasiyası', icon: 'network', roles: ['ADMIN'] },
  { key: 'audit', label: 'Audit jurnalı', icon: 'history', roles: ['ADMIN'] },
  { key: 'users', label: 'İstifadəçilər və rollar', icon: 'users', roles: ['ADMIN'] },
];

const VIEWS = {
  dashboard: () => import('./dashboard.js'),
  applications: () => import('./applications.js'),
  ranking: () => import('./ranking.js'),
  commission: () => import('./commission.js'),
  monitoring: () => import('./monitoring.js'),
  demands: () => import('./demands.js'),
  projects: () => import('./projects.js'),
  plots: () => import('./plots.js'),
  competitions: () => import('./competitions.js'),
  integration: () => import('./integration.js'),
  audit: () => import('./audit.js'),
  users: () => import('./users.js'),
};

const allowed = MENU.filter((m) => m.key && m.roles.includes(role));

async function renderSide() {
  let counts = {};
  if (role === 'ADMIN') counts = await api('/api/v1/admin/stats').catch(() => ({}));
  $('#side').innerHTML = MENU.map((m) => {
    if (m.grp) return `<div class="grp">${m.grp}</div>`;
    if (!m.roles.includes(role)) return '';
    const n = m.badge ? counts[m.badge] : 0;
    return `<a href="#${m.key}" data-key="${m.key}">${icon(m.icon)}<span>${m.label}</span>${n ? `<span class="cnt">${n}</span>` : ''}</a>`;
  }).join('') + (role === 'ADMIN' ? `<a href="/ektis-canli.html" target="_blank" rel="noopener">${icon('network')}EKTİS canlı ekranı</a>` : '') + `<div class="grp">Sənədlər</div><a href="/swagger-ui.html" target="_blank" rel="noopener">${icon('webhook')}API (OpenAPI)</a><a href="/index.html">${icon('house')}Portala qayıt</a>`;
  markActive();
}

function markActive() {
  const key = (location.hash.slice(1).split('/')[0]) || allowed[0].key;
  document.querySelectorAll('#side a[data-key]').forEach((a) => a.classList.toggle('active', a.dataset.key === key));
}

let seq = 0;
async function route() {
  const [key, ...rest] = location.hash.slice(1).split('/');
  const view = allowed.find((m) => m.key === key) ? key : allowed[0].key;
  markActive();
  const my = ++seq;
  const el = $('#view');
  el.innerHTML = '<div class="loader"></div>';
  try {
    const mod = await VIEWS[view]();
    if (my !== seq) return;
    await mod.render(el, rest, { role, refreshSide: renderSide });
  } catch (e) {
    console.error(e);
    el.innerHTML = `<div class="alert alert-crit">${icon('circle-x')}<div>${esc(e.message)}</div></div>`;
  }
  window.scrollTo({ top: 0 });
}

window.addEventListener('hashchange', route);
onLive('notification', () => {
  const h = location.hash.slice(1);
  if (h === '' || h === 'dashboard' || h === 'applications' || h === 'integration') route();
  renderSide();
});
renderSide();
route();
