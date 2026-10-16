// Müraciət qəbzi (QR kodu ilə) - çap üçün uyğun.
import { esc, fmt } from './core.js';

export function qrSvg(text) {
  if (!window.qrcode) return '';
  const qr = window.qrcode(0, 'M');
  qr.addData(text);
  qr.make();
  return qr.createSvgTag({ cellSize: 4, margin: 0, scalable: true });
}

export function receipt(a) {
  const url = `${location.origin}/izle.html?number=${encodeURIComponent(a.appNumber)}&code=${encodeURIComponent(a.trackingCode || '')}`;
  return `<div class="receipt">
    <div class="row between" style="align-items:flex-start;gap:20px;flex-wrap:wrap">
      <div>
        <div class="small muted" style="font-weight:700;letter-spacing:.08em">ELEKTRON MÜRACİƏT QƏBZİ</div>
        <div class="big-number mono" style="margin:6px 0">${esc(a.appNumber)}</div>
        <div class="small">İzləmə kodu: <b class="mono" style="font-size:16px;letter-spacing:.1em">${esc(a.trackingCode || '-')}</b></div>
      </div>
      <div class="qr" title="Statusu yoxlamaq üçün skan edin">${qrSvg(url)}</div>
    </div>
    <hr>
    <div class="kv" style="font-size:13.5px">
      <div>Müraciətçi</div><div>${esc(a.companyName || a.applicantName)}</div>
      <div>Sahə</div><div>${esc(a.plot.code)} - ${esc(a.plot.name)} (${esc(a.plot.districtLabel)}, ${fmt.ha(a.plot.areaHa)})</div>
      <div>Müsabiqə</div><div>${esc(a.competition.code)} - ${esc(a.competition.title)}</div>
      <div>İnvestisiya / iş yerləri</div><div>${fmt.azn(a.investmentAmount)} · ${a.jobsPlanned ?? '-'} iş yeri</div>
      <div>Göndərilmə tarixi</div><div>${fmt.dateTime(a.submittedAt)}</div>
      <div>Status</div><div>${esc(a.statusLabel)}</div>
      <div>Sənədlər</div><div>${a.documents.length} fayl (SHA-256 ilə qeydə alınıb)</div>
    </div>
    <p class="small muted" style="margin:14px 0 0">Statusu girişsiz yoxlamaq üçün QR kodu skan edin və ya <b>${esc(location.host)}/izle.html</b> ünvanında nömrə və izləmə kodunu daxil edin.</p>
  </div>`;
}
