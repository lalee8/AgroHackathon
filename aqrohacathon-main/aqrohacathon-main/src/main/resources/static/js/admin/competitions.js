import { api, $, $$, on, esc, icon, fmt, session, competitionBadge, catBadge, modal, toast, errorToast, withLoading, showFieldErrors, confirmDialog, promptDialog } from '../core.js';

export async function render(el, rest, ctx) {
  const [list, plots, ref] = await Promise.all([api('/api/v1/admin/competitions'), api('/api/v1/plots'), api('/api/v1/reference')]);
  const me = session.user();
  el.innerHTML = `<div class="admin-title"><div><h1>Müsabiqələr</h1><p class="muted" style="margin:0">Həyat dövrü: Layihə → Təsdiq gözləyir → Elan edilib → Bağlanıb. <b>"Dörd göz" prinsipi:</b> müsabiqəni yaradan admin onu özü təsdiqləyə bilməz.</p></div>
    <button class="btn btn-primary" id="add">${icon('plus')}Yeni müsabiqə</button></div>
    <div class="stack">${list.map((c) => {
      const own = c.createdBy === me.fullName;
      const actions = [];
      if (c.status === 'DRAFT') actions.push(`<button class="btn btn-sm" data-edit="${c.id}">${icon('pencil', 'i-sm')}Redaktə</button>`, `<button class="btn btn-sm btn-primary" data-act="submit" data-id="${c.id}">${icon('send', 'i-sm')}Təsdiqə göndər</button>`);
      if (c.status === 'PENDING_APPROVAL') actions.push(`<button class="btn btn-sm btn-success" data-act="approve" data-id="${c.id}" ${own ? 'title="Öz yaratdığınız müsabiqəni təsdiqləyə bilməzsiniz"' : ''}>${icon('circle-check', 'i-sm')}Təsdiqlə və dərc et</button>`, `<button class="btn btn-sm btn-danger" data-act="reject" data-id="${c.id}">${icon('arrow-left', 'i-sm')}Düzəlişə qaytar</button>`);
      if (c.status === 'PUBLISHED') actions.push(`<button class="btn btn-sm" data-act="close" data-id="${c.id}">${icon('lock', 'i-sm')}Bağla</button>`);
      actions.push(`<a class="btn btn-sm btn-ghost" href="#ranking/${c.id}">${icon('trophy', 'i-sm')}Reytinq</a>`);
      return `<div class="card"><div class="card-body">
        <div class="row between" style="flex-wrap:wrap;gap:12px"><div><div class="row-wrap"><span class="mono small muted"><b>${esc(c.code)}</b></span>${competitionBadge(c.status)}${c.open ? `<span class="badge badge-good">${icon('circle-check')}Müraciət açıqdır</span>` : ''}</div>
          <h3 style="margin:6px 0 4px">${esc(c.title)}</h3>
          <div class="small muted">${fmt.date(c.startAt)} - ${fmt.date(c.deadlineAt)} · ${c.plotCount} sahə · ${fmt.ha(c.totalAreaHa)} · ${c.applicationCount} müraciət</div></div>
          <div class="row-wrap">${actions.join('')}</div></div>
        ${c.status === 'PENDING_APPROVAL' && own ? `<div class="alert alert-warn" style="margin-top:12px">${icon('triangle-alert')}<div>Bu müsabiqəni siz yaratmısınız. Təsdiq üçün digər admin (məs. <b>nazirlik@aqroinvest.az</b>) daxil olmalıdır.</div></div>` : ''}
        ${c.reviewNote && c.status === 'DRAFT' ? `<div class="alert alert-serious" style="margin-top:12px">${icon('triangle-alert')}<div><b>Təsdiqləyicinin qeydi:</b> ${esc(c.reviewNote)}</div></div>` : ''}
        <div class="row-wrap" style="margin-top:12px">${[...new Set(c.plots.map((p) => p.category))].map((k) => catBadge(k)).join('')}</div>
        <div class="small muted" style="margin-top:10px">${icon('user', 'i-sm')} Yaradıb: <b>${esc(c.createdBy || '-')}</b>${c.approvedBy ? ` · ${icon('clipboard-check', 'i-sm')} Təsdiqləyib: <b>${esc(c.approvedBy)}</b> (${fmt.date(c.approvedAt)})` : ''}</div>
      </div></div>`;
    }).join('')}</div>`;

  $('#add').addEventListener('click', () => openForm(el, null, plots, ref, ctx));
  on(el, 'click', '[data-edit]', (e, b) => openForm(el, list.find((c) => c.id === Number(b.dataset.edit)), plots, ref, ctx));
  on(el, 'click', '[data-act]', async (e, b) => {
    const act = b.dataset.act;
    const id = b.dataset.id;
    let body;
    if (act === 'reject') {
      const note = await promptDialog('Düzəliş üçün geri qaytar', 'Geri qaytarma səbəbi', { ok: 'Geri qaytar', placeholder: 'Məs. son müraciət tarixini uzadın...' });
      if (!note) return;
      body = { note };
    }
    if (act === 'close' && !(await confirmDialog('Müsabiqə bağlansın? Yeni müraciət qəbulu dayandırılacaq.', { ok: 'Bağla', danger: true }))) return;
    if (act === 'approve') body = { note: 'Təsdiqləndi' };
    await withLoading(b, async () => {
      try {
        await api(`/api/v1/admin/competitions/${id}/${act}`, { method: 'POST', body });
        toast({ submit: 'Təsdiqə göndərildi', approve: 'Müsabiqə dərc edildi - investorlara bildiriş göndərildi', reject: 'Düzəliş üçün qaytarıldı', close: 'Müsabiqə bağlandı' }[act]);
        ctx.refreshSide();
        render(el, [], ctx);
      } catch (err) { errorToast(err); }
    });
  });
}

function toLocal(iso) {
  const d = iso ? new Date(iso) : new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function openForm(el, c, plots, ref, ctx) {
  const selected = new Set((c?.plots || []).map((p) => p.id));
  const criteria = c?.criteria || ref.defaultCriteria;
  const avail = plots.filter((p) => p.status !== 'AWARDED');
  const m = modal({
    title: c ? `Müsabiqəni redaktə et - ${c.code}` : 'Yeni müsabiqə',
    wide: true,
    body: `<form id="cf" novalidate>
      <div class="field"><label class="req">Başlıq</label><input class="input" name="title" value="${esc(c?.title || '')}"></div>
      <div class="field"><label>Təsvir</label><textarea class="input" name="description" rows="3">${esc(c?.description || '')}</textarea></div>
      <div class="form-grid">
        <div class="field"><label class="req">Başlama</label><input class="input" type="datetime-local" name="startAt" value="${toLocal(c?.startAt)}"></div>
        <div class="field"><label class="req">Son müraciət tarixi</label><input class="input" type="datetime-local" name="deadlineAt" value="${toLocal(c?.deadlineAt || new Date(Date.now() + 30 * 864e5).toISOString())}"></div>
      </div>
      <div class="field"><label class="req">Sahələr (<span id="selCnt">${selected.size}</span> seçilib)</label>
        <div style="max-height:220px;overflow:auto;border:1px solid var(--line);border-radius:12px;padding:8px">${avail.map((p) => `<label class="check" style="padding:6px 8px;border-radius:8px"><input type="checkbox" name="plot" value="${p.id}" ${selected.has(p.id) ? 'checked' : ''}><span><b class="mono">${esc(p.code)}</b> ${esc(p.name)} <span class="muted small">· ${esc(p.districtLabel)} · ${fmt.ha(p.areaHa)} · ${esc(p.statusLabel)}</span></span></label>`).join('')}</div></div>
      <div class="field"><label class="req">Qiymətləndirmə meyarları və çəkilər (cəmi: <b id="wsum"></b>/100)</label>
        <div class="stack" style="gap:6px" id="crit">${criteria.map((k) => `<div class="row" style="gap:10px"><span style="flex:1" class="small">${esc(k.label)}</span><div class="input-group" style="width:120px"><input class="input num" type="number" min="0" max="100" data-key="${k.key}" data-label="${esc(k.label)}" value="${k.weight}"><span class="addon">%</span></div></div>`).join('')}</div></div>
    </form>`,
    foot: '<button class="btn" data-close>Ləğv et</button><button class="btn btn-primary" id="save" data-loading="Yadda saxlanılır...">Layihə kimi saxla</button>',
  });
  const f = m.el.querySelector('#cf');
  const sum = () => {
    const s = $$('#crit input', f).reduce((a, i) => a + Number(i.value || 0), 0);
    const el2 = m.el.querySelector('#wsum');
    el2.textContent = s;
    el2.style.color = s === 100 ? 'var(--st-good-ink)' : 'var(--st-crit-ink)';
  };
  sum();
  f.addEventListener('input', sum);
  f.addEventListener('change', () => { m.el.querySelector('#selCnt').textContent = $$('[name=plot]:checked', f).length; });
  m.el.querySelector('#save').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    const body = {
      title: f.title.value, description: f.description.value,
      startAt: new Date(f.startAt.value).toISOString(), deadlineAt: new Date(f.deadlineAt.value).toISOString(),
      plotIds: $$('[name=plot]:checked', f).map((x) => Number(x.value)),
      criteria: $$('#crit input', f).map((i) => ({ key: i.dataset.key, label: i.dataset.label, weight: Number(i.value || 0) })),
    };
    try {
      await api(c ? `/api/v1/admin/competitions/${c.id}` : '/api/v1/admin/competitions', { method: c ? 'PUT' : 'POST', body });
      toast('Müsabiqə layihəsi yadda saxlanıldı');
      m.close();
      render(el, [], ctx);
    } catch (err) { if (!showFieldErrors(f, err)) errorToast(err); }
  }));
}
