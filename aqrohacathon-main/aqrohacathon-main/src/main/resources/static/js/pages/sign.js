// Sahədə quraşdırılacaq QR kodlu çap lövhəsi (A4).
import { api, $, esc, fmt, CAT, params, demining, countdown, leaseText } from '../core.js';
import { LOGO } from '../art.js';
import { qrSvg } from '../receipt.js';

const id = params().get('id');
$('#print').addEventListener('click', () => window.print());
$('#back').href = `/sahe.html?id=${id}`;

api(`/api/v1/plots/${id}`).then((p) => {
  document.title = `${p.code} lövhəsi - Əkinçi 2.0`;
  const url = `${location.origin}/sahe.html?id=${p.id}`;
  const comp = p.competition;
  $('#sign').innerHTML = `
    <div class="sign-top">
      <div class="brand">${LOGO}<span class="brand-text">Əkinçi 2.0<small>Aqrar investisiya müsabiqələri portalı</small></span></div>
      <div style="margin-top:10mm" class="kicker">İnvestisiya üçün torpaq sahəsi</div>
      <h1>${esc(p.name)}</h1>
      <div style="font-size:13pt;color:#dbe9df">${esc(p.districtLabel)} rayonu${p.village ? ', ' + esc(p.village) : ''} · ${esc(CAT[p.category].label)}</div>
    </div>
    <div class="sign-mid">
      <div>
        <div style="font-size:11pt;font-weight:700;color:#5a6860;letter-spacing:.1em">SAHƏNİN KODU</div>
        <div class="sign-code">${esc(p.code)}</div>
        <div class="sign-facts">
          <div><span>Sahə</span><b>${fmt.ha(p.areaHa)}</b></div>
          <div><span>İcarə müddəti</span><b>${leaseText(p)}</b></div>
          <div><span>Minimum investisiya</span><b>${fmt.azn(p.minInvestment)}</b></div>
          <div><span>${comp && comp.open ? 'Son müraciət tarixi' : 'Status'}</span><b>${comp && comp.open ? fmt.date(comp.deadlineAt) : esc(p.statusLabel)}</b></div>
        </div>
        <div class="sign-demining">${p.deminingStatus === 'CLEARED' ? '✓ Minalardan tam təmizlənib' : esc(p.deminingLabel)}${p.deminingCertificate ? ` · ${esc(p.deminingCertificate)}` : ''}</div>
        <p style="margin-top:6mm;font-size:11pt;color:#2f3b33">${esc((p.description || '').slice(0, 260))}</p>
      </div>
      <div class="sign-qr">${qrSvg(url)}<b>Skan edin və müraciət edin</b><small>${esc(url)}</small></div>
    </div>
    <div class="sign-bottom"><div>Koordinatlar: <b>${p.latitude.toFixed(5)}, ${p.longitude.toFixed(5)}</b></div><div>Status və şərtlər portalda real vaxtda yenilənir</div></div>`;
}).catch((e) => { $('#sign').innerHTML = `<div class="empty">${esc(e.message)}</div>`; });
