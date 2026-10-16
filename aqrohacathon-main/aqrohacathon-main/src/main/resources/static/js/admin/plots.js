import { api, $, on, esc, icon, fmt, CAT, DISTRICTS, modal, toast, errorToast, withLoading, showFieldErrors, confirmDialog, plotStatusBadge, catBadge } from '../core.js';
import { createMap } from '../basemap.js';

export async function render(el) {
  const list = await api('/api/v1/plots');
  el.innerHTML = `<div class="admin-title"><div><h1>Təsərrüfat sahələri</h1><p class="muted" style="margin:0">Sahələrin əlavə edilməsi və redaktəsi. Koordinatı xəritəyə klikləməklə seçin - sərhəd poliqonu avtomatik qurulur.</p></div>
    <button class="btn btn-primary" id="add">${icon('plus')}Yeni sahə</button></div>
    <div class="card"><div class="table-wrap"><table class="table"><thead><tr><th>Kod</th><th>Ad</th><th>Rayon</th><th>Təyinat</th><th class="r">Sahə</th><th class="r">Min. investisiya</th><th class="r">Müraciət</th><th>Status</th><th></th></tr></thead><tbody>
    ${list.map((p) => `<tr><td class="mono"><b>${esc(p.code)}</b></td><td><a href="/sahe.html?id=${p.id}" target="_blank" rel="noopener">${esc(p.name)}</a></td><td>${esc(p.districtLabel)}</td><td>${catBadge(p.category)}</td>
      <td class="r num">${fmt.ha(p.areaHa)}</td><td class="r num">${fmt.compactMoney(p.minInvestment)}</td><td class="r num">${p.applicationCount}</td><td>${plotStatusBadge(p.status)}</td>
      <td class="r nowrap"><a class="btn btn-sm" href="/lovhe.html?id=${p.id}" target="_blank" rel="noopener" title="QR lövhə">${icon('qr-code', 'i-sm')}</a> <button class="btn btn-sm" data-edit="${p.id}">${icon('pencil', 'i-sm')}</button> <button class="btn btn-sm btn-danger" data-del="${p.id}" data-code="${esc(p.code)}">${icon('trash-2', 'i-sm')}</button></td></tr>`).join('')}
    </tbody></table></div></div>`;
  $('#add').addEventListener('click', () => openForm(el, null));
  on(el, 'click', '[data-edit]', (e, b) => openForm(el, list.find((p) => p.id === Number(b.dataset.edit))));
  on(el, 'click', '[data-del]', async (e, b) => {
    if (!(await confirmDialog(`${b.dataset.code} sahəsi silinsin?`, { ok: 'Sil', danger: true }))) return;
    try { await api(`/api/v1/admin/plots/${b.dataset.del}`, { method: 'DELETE' }); toast('Sahə silindi'); render(el); } catch (err) { errorToast(err); }
  });
}

function openForm(el, p) {
  const v = (k, d = '') => esc(p?.[k] ?? d);
  const m = modal({
    title: p ? `Sahəni redaktə et - ${p.code}` : 'Yeni təsərrüfat sahəsi',
    wide: true,
    body: `<form id="pf" novalidate><div class="form-grid">
      <div class="field"><label class="req">Kod</label><input class="input mono" name="code" value="${v('code')}" placeholder="məs. AGD-010"></div>
      <div class="field"><label class="req">Ad</label><input class="input" name="name" value="${v('name')}"></div>
      <div class="field"><label class="req">Rayon</label><select class="input" name="district">${Object.entries(DISTRICTS).map(([k, l]) => `<option value="${k}" ${p?.district === k ? 'selected' : ''}>${l}</option>`).join('')}</select></div>
      <div class="field"><label>Kənd</label><input class="input" name="village" value="${v('village')}"></div>
      <div class="field"><label class="req">Təyinat</label><select class="input" name="category">${Object.entries(CAT).map(([k, c]) => `<option value="${k}" ${p?.category === k ? 'selected' : ''}>${c.label}</option>`).join('')}</select></div>
      <div class="field"><label class="req">Sahə (ha)</label><input class="input" type="number" step="0.1" name="areaHa" value="${v('areaHa')}"></div>
      <div class="field full"><label class="req">Mərkəz koordinatı - xəritəyə klikləyin</label><div id="pickMap" style="height:240px;border-radius:12px;border:1px solid var(--line)"></div>
        <div class="row" style="margin-top:8px"><input class="input" name="latitude" type="number" step="0.000001" value="${v('latitude', '39.8')}" placeholder="enlik"><input class="input" name="longitude" type="number" step="0.000001" value="${v('longitude', '46.85')}" placeholder="uzunluq"></div></div>
      <div class="field"><label>Torpağın tipi</label><input class="input" name="soilType" value="${v('soilType')}"></div>
      <div class="field"><label>Bonitet (0-100)</label><input class="input" type="number" name="soilQuality" value="${v('soilQuality')}"></div>
      <div class="field"><label>Su mənbəyi</label><input class="input" name="waterSource" value="${v('waterSource')}"></div>
      <div class="field"><label>Yola məsafə (km)</label><input class="input" type="number" step="0.1" name="roadDistanceKm" value="${v('roadDistanceKm')}"></div>
      <div class="field"><label>Hündürlük (m)</label><input class="input" type="number" name="altitudeM" value="${v('altitudeM')}"></div>
      <div class="field"><label>Meyillik (%)</label><input class="input" type="number" step="0.1" name="slopePercent" value="${v('slopePercent')}"></div>
      <div class="field full row-wrap" style="gap:18px"><label class="check"><input type="checkbox" name="irrigation" ${p?.irrigation ? 'checked' : ''}>Suvarma</label><label class="check"><input type="checkbox" name="electricity" ${p?.electricity ? 'checked' : ''}>Elektrik</label><label class="check"><input type="checkbox" name="gas" ${p?.gas ? 'checked' : ''}>Qaz</label></div>
      <div class="field"><label class="req">Minatəmizləmə</label><select class="input" name="deminingStatus">${[['CLEARED', 'Tam təmizlənib'], ['SURVEYED', 'Texniki sorğu: risk yoxdur'], ['IN_PROGRESS', 'Davam edir']].map(([k, l]) => `<option value="${k}" ${p?.deminingStatus === k ? 'selected' : ''}>${l}</option>`).join('')}</select></div>
      <div class="field"><label>ANAMA sertifikatı</label><input class="input mono" name="deminingCertificate" value="${v('deminingCertificate')}"></div>
      <div class="field"><label class="req">İcarə müddəti (il)</label><input class="input" type="number" name="leaseYears" value="${v('leaseYears', 25)}"></div>
      <div class="field"><label class="req">İcarə haqqı (₼/ha/il)</label><input class="input" type="number" name="annualRentPerHa" value="${v('annualRentPerHa', 30)}"></div>
      <div class="field"><label class="req">Min. investisiya (₼)</label><input class="input" type="number" name="minInvestment" value="${v('minInvestment', 500000)}"></div>
      <div class="field"><label class="req">Min. iş yeri</label><input class="input" type="number" name="minJobs" value="${v('minJobs', 10)}"></div>
      <div class="field full"><label>Tövsiyə olunan bitkilər (vergüllə)</label><input class="input" name="crops" value="${esc((p?.recommendedCrops || []).join(', '))}"></div>
      <div class="field full"><label>Təsvir</label><textarea class="input" name="description" rows="3">${v('description')}</textarea></div>
      <div class="field full"><label>İstifadə şərtləri (hər sətir ayrıca bənd)</label><textarea class="input" name="terms" rows="4">${v('terms')}</textarea></div>
    </div></form>`,
    foot: '<button class="btn" data-close>Ləğv et</button><button class="btn btn-primary" id="save" data-loading="Yadda saxlanılır...">Yadda saxla</button>',
  });
  const f = m.el.querySelector('#pf');
  setTimeout(() => {
    const L = window.L;
    const lat = Number(f.latitude.value) || 39.8, lng = Number(f.longitude.value) || 46.85;
    const { map } = createMap(m.el.querySelector('#pickMap'), { layersControl: true, center: [lat, lng], zoom: p ? 12 : 8 });
    const mk = L.marker([lat, lng]).addTo(map);
    map.on('click', (e) => { mk.setLatLng(e.latlng); f.latitude.value = e.latlng.lat.toFixed(6); f.longitude.value = e.latlng.lng.toFixed(6); });
  }, 50);
  m.el.querySelector('#save').addEventListener('click', (e) => withLoading(e.currentTarget, async () => {
    const d = Object.fromEntries(new FormData(f));
    const n = (x) => (x === '' || x == null ? null : Number(x));
    const body = {
      code: d.code, name: d.name, district: d.district, village: d.village, category: d.category, areaHa: n(d.areaHa),
      latitude: n(d.latitude), longitude: n(d.longitude), polygon: p && Number(p.latitude) === n(d.latitude) && Number(p.longitude) === n(d.longitude) ? JSON.stringify(p.polygon) : null,
      soilType: d.soilType, soilQuality: n(d.soilQuality), waterSource: d.waterSource, irrigation: !!f.irrigation.checked,
      roadDistanceKm: n(d.roadDistanceKm), electricity: !!f.electricity.checked, gas: !!f.gas.checked, altitudeM: n(d.altitudeM),
      slopePercent: n(d.slopePercent), deminingStatus: d.deminingStatus, deminingCertificate: d.deminingCertificate, usageType: 'LEASE',
      leaseYears: n(d.leaseYears), annualRentPerHa: n(d.annualRentPerHa), minInvestment: n(d.minInvestment), minJobs: n(d.minJobs),
      recommendedCrops: d.crops.split(',').map((x) => x.trim()).filter(Boolean), description: d.description, terms: d.terms, status: p?.status || 'AVAILABLE',
    };
    try {
      await api(p ? `/api/v1/admin/plots/${p.id}` : '/api/v1/admin/plots', { method: p ? 'PUT' : 'POST', body });
      toast(p ? 'Sahə yeniləndi' : 'Sahə əlavə edildi - EKTİS-ə hadisə göndərildi');
      m.close();
      render(el);
    } catch (err) { if (!showFieldErrors(f, err)) errorToast(err); }
  }));
}
