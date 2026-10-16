# EKTİS inteqrasiyası: qısa bələdçi

Tam sənəd: [docs/EKTIS-INTEQRASIYA.md](docs/EKTIS-INTEQRASIYA.md). Bu fayl münsiflər üçün qısa xülasədir.

- **İstiqamət 1 (push):** portal hər hadisəni (müraciət göndərildi, status dəyişdi, qərar verildi) EKTİS-ə imzalı JSON kimi göndərir: `POST {EKTIS_BASE_URL}/v1/events`, `X-Idempotency-Key`, `X-Signature: HMAC-SHA256`.
- **İstiqamət 2 (pull):** EKTİS portaldan sahələri GeoJSON, müraciətləri isə `updatedSince` ilə çəkir: `/api/v1/integration/**` (API açarı ilə).
- **Etibarlılıq:** transactional outbox, 6 təkrar cəhd (5-80 san), sonra `DEAD` və admin panelində "Yenidən göndər".
- **Hackathon demosunda:** real EKTİS-ə giriş verilməyib, ona görə portal öz **EKTİS simulyatoruna** qoşulur (`/mock/ektis/**`). Real inteqrasiya olduğu iddia edilmir: adapter hazırdır, 3 mühit dəyişəni ilə real ünvana keçir (`EKTIS_BASE_URL`, `EKTIS_API_KEY`, `EKTIS_HMAC_SECRET`).

## Müraciət hadisəsinin nümunəsi

```json
{
  "eventId": "8b1d5c0e-4f7a-4c55-9a6e-1e1f0f0c2a11",
  "eventType": "APPLICATION_SUBMITTED",
  "schemaVersion": "1.0",
  "source": "aqroinvest-qarabag",
  "occurredAt": "2026-10-14T11:29:41.512Z",
  "aggregate": { "type": "Application", "id": "23" },
  "data": {
    "applicationNumber": "AQR-2026-000022",
    "status": "SUBMITTED",
    "competitionCode": "MSB-2026-01",
    "plotCode": "AGD-004",
    "district": "AGDAM",
    "applicantType": "LEGAL",
    "applicantName": "Test Aqro MMC",
    "taxId": "1234567890",
    "applicantUserId": 2,
    "activityType": "BAGCILIQ",
    "investmentAmount": 1200000,
    "jobsPlanned": 42,
    "autoScore": 75,
    "plotAreaHa": 60.0,
    "plotGeometry": {
      "type": "Polygon",
      "coordinates": [[[46.919129, 39.955559], [46.917351, 39.95756], [46.912933, 39.9587],
                       [46.909052, 39.956985], [46.906779, 39.953989], [46.919129, 39.955559]]]
    },
    "submittedAt": "2026-10-14T11:29:41.488Z"
  }
}
```

## Canlı nümayiş

Ekranı ikiyə bölün: solda investor portalda müraciəti göndərir, sağda admin panelində **İnteqrasiya → EKTİS canlı ekranı** açıqdır. Hadisə saniyəsində simulyatora düşür, `202 Accepted` cavabı və EKTİS istinad nömrəsi görünür; status dəyişəndə investorun kabineti real vaxtda yenilənir.
