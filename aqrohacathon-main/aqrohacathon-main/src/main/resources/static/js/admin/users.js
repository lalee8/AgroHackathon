import { api, $, on, esc, icon, fmt, modal, toast, errorToast, withLoading, showFieldErrors } from '../core.js';

const ROLE_BADGE = { ADMIN: 'badge-green', EXPERT: 'badge-info', INVESTOR: '' };

export async function render(el) {
  const list = await api('/api/v1/admin/users');
  el.innerHTML = `<div class="admin-title"><div><h1>İstifadəçilər və rollar</h1><p class="muted" style="margin:0">Rol əsaslı giriş: investor yalnız öz müraciətlərini, ekspert maskalanmış şəxsi məlumatları, admin isə bütün idarəetmə funksiyalarını görür.</p></div>
    <button class="btn btn-primary" id="add">${icon('user-plus')}Əməkdaş əlavə et</button></div>
    <div class="card"><div class="table-wrap"><table class="table"><thead><tr><th>Ad</th><th>E-poçt</th><th>Rol</th><th>Vəziyyət</th><th>Son giriş</th><th>Yaradılıb</th><th></th></tr></thead><tbody>
    ${list.map((x) => `<tr><td><b>${esc(x.user.fullName)}</b></td><td class="small">${esc(x.user.email)}</td><td><span class="badge ${ROLE_BADGE[x.user.role]}">${esc(x.user.roleLabel)}</span></td>
      <td>${x.enabled ? (x.locked ? `<span class="badge badge-warn">${icon('lock')}Müvəqqəti bloklanıb</span>` : `<span class="badge badge-good">${icon('circle-check')}Aktiv</span>`) : `<span class="badge badge-crit">${icon('circle-x')}Deaktiv</span>`}</td>
      <td class="small muted">${x.lastLoginAt ? fmt.rel(x.lastLoginAt) : '-'}</td><td class="small muted">${fmt.date(x.user.createdAt)}</td>
      <td class="r"><button class="btn btn-sm ${x.enabled ? 'btn-danger' : 'btn-success'}" data-toggle="${x.user.id}">${x.enabled ? 'Deaktiv et' : 'Aktiv et'}</button></td></tr>`).join('')}
    </tbody></table></div></div>`;
  on(el, 'click', '[data-toggle]', async (e, b) => {
    try { await api(`/api/v1/admin/users/${b.dataset.toggle}/toggle`, { method: 'POST' }); toast('Yeniləndi'); render(el); } catch (err) { errorToast(err); }
  });
  $('#add').addEventListener('click', () => {
    const m = modal({
      title: 'Yeni əməkdaş hesabı',
      body: `<form id="sf" novalidate>
        <div class="field"><label class="req">Ad və soyad</label><input class="input" name="fullName"></div>
        <div class="field"><label class="req">E-poçt</label><input class="input" name="email" type="email"></div>
        <div class="field"><label class="req">Müvəqqəti şifrə</label><input class="input" name="password" type="text" value="Temp${Math.floor(1000 + Math.random() * 9000)}!x"></div>
        <div class="field"><label class="req">Rol</label><select class="input" name="role"><option value="EXPERT">Komissiya eksperti</option><option value="ADMIN">Nazirlik operatoru (admin)</option></select></div>
      </form>`,
      foot: '<button class="btn" data-close>Ləğv et</button><button class="btn btn-primary" id="save" data-loading="Yaradılır...">Yarat</button>',
    });
    m.el.querySelector('#save').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
      const f = m.el.querySelector('#sf');
      const body = Object.fromEntries(new FormData(f));
      try { await api('/api/v1/admin/users', { method: 'POST', body }); toast('Hesab yaradıldı'); m.close(); render(el); } catch (err) { if (!showFieldErrors(f, err)) errorToast(err); }
    }));
  });
}
