# REST API

- **Baza ünvanı:** `/api/v1`. Versiya yolda göstərilir və geriyə uyğunluğu pozan dəyişikliklər `v2` altında çıxır.
- **Format:** JSON (UTF-8). Tarixlər ISO-8601 (UTC), pul vahidi AZN, koordinatlar WGS84 / EPSG:4326.
- **Canlı sənəd:** `http://localhost:8080/swagger-ui.html`. Statik spesifikasiya: [`openapi.json`](openapi.json) (OpenAPI 3.1).
- **Endpoint sayı:** 146.

## Autentifikasiya

| Kanal | Mexanizm | Kim üçün |
|---|---|---|
| İctimai | - | Sahələr, müsabiqələr, tövsiyə, ROI, klassifikatorlar, status yoxlaması |
| İstifadəçi | `Authorization: Bearer <JWT>` (HS256, 8 saat) | İnvestor, ekspert, admin |
| Sistemlərarası | `X-API-Key: <açar>` | EKTİS → `/api/v1/integration/**` |

JWT `POST /api/v1/auth/login` ilə alınır. Token-də `sub` (e-poçt), `uid`, `name` və `roles` claim-ləri var. Avtorizasiya iki səviyyədə aparılır:
- **URL səviyyəsi:** `/api/v1/admin/**` → ADMIN, `/api/v1/review/**` → ADMIN və ya EXPERT;
- **obyekt səviyyəsi:** investor yalnız öz müraciətini görür, başqasınınkına müraciət etdikdə `404` qaytarılır.

```bash
# Giriş
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"investor@aqroinvest.az","password":"Investor123!"}' | jq -r .token)

# Mənim müraciətlərim
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/applications/my
```

## Xəta formatı

Bütün xətalar eyni strukturdadır. Başqa sistemlər `code` sahəsinə görə qərar verə bilər.

```json
{
  "timestamp": "2026-10-14T11:20:00Z",
  "status": 409,
  "code": "INVALID_TRANSITION",
  "message": "'Qəbul edilib' statusundan 'Qərar verilib' statusuna keçid mümkün deyil",
  "path": "/api/v1/admin/applications/12/status",
  "fieldErrors": { "investmentAmount": "Məbləğ mənfi ola bilməz" }
}
```

| HTTP | Əsas kodlar |
|---|---|
| 400 | `VALIDATION_FAILED`, `INCOMPLETE_APPLICATION`, `COMMENT_REQUIRED`, `DECISION_REQUIRED`, `CRITERIA_WEIGHT`, `PLOT_NOT_IN_COMPETITION` |
| 401 | `UNAUTHORIZED`, `BAD_CREDENTIALS`, `INVALID_API_KEY` |
| 403 | `FORBIDDEN` (rol və ya "dörd göz" qaydası), `ACCOUNT_DISABLED` |
| 404 | `NOT_FOUND` (o cümlədən başqasının müraciəti - IDOR qorunması) |
| 409 | `INVALID_TRANSITION`, `DUPLICATE_APPLICATION`, `PLOT_ALREADY_AWARDED`, `COMPETITION_CLOSED`, `CONCURRENT_UPDATE`, `EMAIL_TAKEN` |
| 413 / 415 | `FILE_TOO_LARGE`, `UNSUPPORTED_TYPE` |
| 423 | `ACCOUNT_LOCKED` |
| 429 | `TOO_MANY_ATTEMPTS` |

## Tipik ssenari: elektron müraciət

```bash
# 1. Qaralama yarat
curl -X POST localhost:8080/api/v1/applications -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{
  "competitionId": 2, "plotId": 4, "applicantType": "LEGAL",
  "companyName": "Test Aqro MMC", "voen": "1234567890", "contactPhone": "+994501112233",
  "contactEmail": "info@testaqro.az", "activityType": "BAGCILIQ",
  "investmentAmount": 1200000, "ownFundsPercent": 50, "jobsPlanned": 40, "implementationMonths": 24,
  "experienceYears": 6, "technologies": ["DRIP_IRRIGATION","SOLAR"],
  "businessPlan": "…ən azı 200 simvol…", "idpStatus": true, "consentGiven": true }'

# 2. Sənəd yüklə (PDF/PNG/JPG, maks. 10 MB)
curl -X POST localhost:8080/api/v1/applications/23/documents -H "Authorization: Bearer $TOKEN" \
  -F type=BUSINESS_PLAN -F file=@biznes-plan.pdf

# 3. Gözlənilən balı yoxla
curl localhost:8080/api/v1/applications/23/score-preview -H "Authorization: Bearer $TOKEN"

# 4. Göndər → nömrə + izləmə kodu
curl -X POST localhost:8080/api/v1/applications/23/submit -H "Authorization: Bearer $TOKEN"

# 5. Girişsiz status
curl "localhost:8080/api/v1/track?number=AQR-2026-000022&code=6MV9JWLG"
```

## Admin: statusun dəyişdirilməsi

```bash
curl -X POST localhost:8080/api/v1/admin/applications/23/status -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"INFO_REQUESTED","comment":"Bank arayışını əlavə edin"}'

curl -X POST localhost:8080/api/v1/admin/applications/23/status -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"DECIDED","decision":"APPROVED","comment":"Komissiyanın qərarı ilə qalib"}'
```

## Sahə filtrləri

`GET /api/v1/plots` aşağıdakı parametrləri qəbul edir:

| Parametr | Təsvir |
|---|---|
| `category` | Təyinat (təkrarlana bilər) |
| `district` | Rayon (təkrarlana bilər) |
| `minArea`, `maxArea` | Sahənin ölçü aralığı (ha) |
| `maxRent` | Maksimum icarə haqqı |
| `irrigation`, `electricity` | Infrastruktur tələbləri |
| `status` | Sahənin statusu |
| `q` | Ad, kod, kənd və təsvir üzrə axtarış |
| `openOnly` | Yalnız müraciəti açıq sahələr |
| `sort` | `area_desc`, `area_asc`, `rent_asc`, `investment_asc`, `quality`, `infra`, `deadline` |

## Real vaxt kanalı (SSE)

Brauzerin `EventSource` obyekti başlıq göndərə bilmir. Buna görə JWT URL-də ötürülmür: əvvəlcə 60 saniyəlik birdəfəlik bilet alınır, sonra axın həmin biletlə açılır.

```bash
TICKET=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/live/ticket | jq -r .ticket)
curl -N "localhost:8080/api/v1/live/stream?ticket=$TICKET"
# event: notification
# data: {"id":41,"type":"STATUS_CHANGED","title":"Müraciətinizə baxılır","link":"/muraciet-detal.html?id=16"}
```

| Hadisə | Kimə | Nə vaxt |
|---|---|---|
| `notification` | müraciət sahibi | hər yeni bildiriş (status, sorğu, qərar) |
| `ektis` | adminlər | EKTİS hadisəni qəbul etdi (imza yoxlaması ilə) |
| `ektis-rejected` | adminlər | EKTİS hadisəni rədd etdi (503 və ya yanlış imza), outbox yenidən cəhd edəcək |

Hadisələr tranzaksiya **commit olunduqdan sonra** göndərilir, buna görə ekranda heç vaxt geri qaytarılmış dəyişiklik görünmür. 25 saniyədə bir heartbeat bağlantını proksilərdə açıq saxlayır.

## Monte-Carlo simulyasiyası

```bash
curl -s -X POST localhost:8080/api/v1/simulate -H 'Content-Type: application/json' \
  -d '{"plotId":31,"useCase":"GUNES","scale":10,"offtakeShare":0,"runs":2000}'
```

Cavabda: `spec` (fərziyyələr və mənbələri), `result.summary` (P10/P50/P90 xalis bugünkü dəyər, geri qayıtma ehtimalı, IRR), `result.fan` (hər il üçün yığılmış nəticənin P10-P90 paylanması), `result.typical/bad/good` (il-il yaşanan ssenarilər və hadisələr), `result.drivers` (risk amilləri), alıcı müqaviləsi verilibsə `result.withoutOfftake` (müqaviləsiz müqayisə). Eyni sorğu həmişə eyni nəticəni verir (sabit toxum).

Hədəf kalkulyatoru: `GET /api/v1/simulate/goal?plotId=31&useCase=GUNES&monthlyNet=1000000` - "ayda 1 mln ₼ üçün" lazım olan MWp, investisiya və torpaq.

## Reallıq yoxlaması və investor dili

`POST /api/v1/projects/reality-check` layihə formasının gövdəsini qəbul edir və saxlamadan təhlil qaytarır: `credibility` (0-100), `checks` (CRIT / WARN / OK), `terms.headline` ("Siz X qoyursunuz, ..."), `terms.scenarios` (pis, orta, yaxşı ssenaridə investorun aldığı pul, neçə qat, geri qayıtma ili, IRR).

## AI məsləhətçi (axın)

```bash
curl -N -X POST localhost:8080/api/v1/advisor/stream -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"500 min manatım var, aşağı riskli nə tövsiyə edirsən?"}]}'
# event: meta   data: {"intent":"büdcə 500 min ₼ · aşağı risk","llm":true,"cards":[...]}
# event: delta  data: {"t":"**Qısa cavab:** ..."}
# event: done   data: {"mode":"ai"}
```

`mode` `ai` (Əkinçi AI dil modeli ilə), `offline` (açar yoxdur, imtina və ya xəta: eyni məlumatdan qaydalara əsaslanan cavab) və ya `guard` (sual platformanın mövzusundan kənardır, standart imtina cavabı). Sorğu limiti IP üzrə dəqiqədə 20 (`AI_RPM`). Sual istənilən dildə ola bilər: büdcə USD və EUR-dan manata çevrilir, cavab sualın dilində gəlir.

## Portfel qurucusu

```bash
curl 'localhost:8080/api/v1/portfolio?budget=5000000&risk=50&horizon=15&maxShare=35&maxSector=40'
```

Cavab: `totals` (yerləşdirilən məbləğ, NPV P10/P50/P90, zərər ehtimalı, yetkin illərdə illik və aylıq xalis gəlir, qayıdış ili, IRR), `positions` (sahə, istiqamət, miqyas, məbləğ, simulyator linki), `frontier` (λ üzrə portfellər, `efficient` və `recommended` işarələri), `singles` (bütün büdcə tək variantda), `diversification` (pis ssenari itkisinin azalması, effektiv mövqe sayı, "hamısı bir varianta" müqayisəsi, bir-birini sığortalayan cüt), `stress` (aranda quraqlıq, dağlıq zonada sərt il, qiymət düşüşü, turizm böhranı), `fan`, `flows`, `corr`, `impact`, `summary`, `assumptions`. Filtrlər: `districts=AGDAM,FUZULI`, `uses=BAGCILIQ,SOYUDUCU`.

`POST /api/v1/portfolio/explain` eyni parametrlərlə portfelin AI izahını qaytarır (`{"mode":"ai","text":"..."}`).

## Peyk təsdiqli mərhələli maliyyələşmə

```bash
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/projects/interests/2/tranches
```

Hər mərhələ: `status` (`RELEASED`, `VERIFIED`, `FLAGGED`, `WAITING`, `PENDING`), `canRelease` (əvvəlki mərhələlər ödənilibsə), `note` və peyk mərhələləri üçün `evidence`:

```json
{"season":2026,"plotBaseline":0.400,"plotNdvi":0.622,"plotChange":0.222,"controlChange":0.111,"excess":0.111,
 "threshold":0.05,"controls":24,"zone":"aran","rule":"|Fərq| ≥ 0.05: torpaq işlənib və ya əkilib"}
```

`excess = plotChange - controlChange`: sahənin dəyişməsindən eyni zonadakı sahələrin median dəyişməsi çıxılır (hava effekti). `POST .../tranches/{seq}/release` təsdiqlənmiş hissəni escrow-dan sahibkara köçürür (yalnız investor, ardıcıl).

## Bütün endpointlər

### Torpaq sahələri

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/plots` | Sahələrin siyahısı (filtr və sıralama ilə) |
| `GET` | `/api/v1/plots/compare` | Seçilmiş sahələrin müqayisəsi (maks. 4) |
| `GET` | `/api/v1/plots/geojson` | Bütün sahələr GeoJSON formatında (CBS/GIS sistemləri üçün) |
| `GET` | `/api/v1/plots/{id}` | Sahə haqqında ətraflı məlumat |
| `GET` | `/api/v1/plots/{id}/plan` | Fəaliyyət planı: istiqamət, bitki, mövsüm təqvimi, NASA iqlimi, su balansı, resurslar, risklər, ilk 12 ay (`use`, `item`) |
| `POST` | `/api/v1/plots/{id}/watch` | Sahəni sevimlilərə əlavə et (müsabiqəyə çıxanda bildiriş) |
| `DELETE` | `/api/v1/plots/{id}/watch` | Sahəni sevimlilərdən çıxar |
| `GET` | `/api/v1/me/watches` | Mənim sevimli sahələrim |
| `GET` | `/p/{id}` | Paylaşım səhifəsi: Open Graph önizləməsi (ad, şərtlər, peyk şəkli), sonra sahə səhifəsinə yönləndirmə |

### Peyk arxivi və monitorinq

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/admin/monitoring` | Peykdən nəzarət: icarəyə verilmiş və müsabiqədəki sahələr |
| `GET` | `/api/v1/plots/{id}/imagery` | Sahənin peyk arxivi: epoxalar, çərçivə, poliqon |
| `GET` | `/api/v1/plots/{id}/imagery/{epoch}` | Epoxa üzrə peyk şəkli (JPEG) |
| `GET` | `/api/v1/plots/{id}/ndvi` | Sahə üzrə NDVI illik seriyası (1990-dan bu günə) və izahı |

### Torpağın potensialı və simulyator

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/plots/{id}/potential` | Bu torpaqda nə etmək olar? Bütün istifadə ssenariləri uyğunluq və gəlirlilik üzrə |
| `POST` | `/api/v1/simulate` | Monte-Carlo simulyasiyası: biznesin minlərlə mümkün gələcəyi (P10/P50/P90, geri qayıtma ehtimalı, risk amilləri) |
| `GET` | `/api/v1/simulate/goal` | Hədəf kalkulyatoru: "ayda X ₼ qazanmaq üçün" nə qədər miqyas, investisiya və torpaq lazımdır |
| `GET` | `/api/v1/simulate/use-cases` | Simulyasiya ssenariləri və istinad göstəriciləri |

### Layihələr: sahibkar ↔ investor

| Metod | Yol | Təsvir |
|---|---|---|
| `POST` | `/api/v1/admin/projects/{id}/verify` | Layihəni yoxlanılmış kimi işarələ (admin) |
| `GET` | `/api/v1/plots/{id}/projects` | Bu sahədə investor axtaran layihələr |
| `GET` | `/api/v1/projects` | Dərc edilmiş layihələr (profil verilərsə uyğunluq balı ilə) |
| `POST` | `/api/v1/projects` | Yeni layihə (sahibkar) |
| `GET` | `/api/v1/projects/interests/mine` | Göndərdiyim maraqlar (qəbul olunanlarda sahibkarın əlaqə məlumatı) |
| `POST` | `/api/v1/projects/interests/{id}/respond` | Maraqa cavab: görüşə razılıq (əlaqə açılır) və ya imtina (sahibkar) |
| `GET` | `/api/v1/projects/mine` | Mənim layihələrim və gələn investor maraqları |
| `GET` | `/api/v1/projects/profile` | İnvestor profili (istiqamətlər, məbləğ, risk, müddət) |
| `PUT` | `/api/v1/projects/profile` | İnvestor profilini yadda saxla |
| `POST` | `/api/v1/projects/reality-check` | Reallıq yoxlaması (formada canlı): iddialar istinad göstəriciləri və Monte-Carlo ilə müqayisə olunur |
| `GET` | `/api/v1/projects/sectors` | İstiqamətlər (xanalar): hər birində layihə sayı və ümumi tələb |
| `GET` | `/api/v1/projects/{id}` | Layihə: təqdimat, reallıq yoxlaması, investor dilində təklif |
| `PUT` | `/api/v1/projects/{id}` | Layihəni redaktə et (sahibkar) |
| `POST` | `/api/v1/projects/{id}/interest` | Layihəyə maraq bildir (investor) |
| `POST` | `/api/v1/projects/{id}/status` | Layihənin statusu: PUBLISHED, FUNDED, CLOSED (sahibkar) |
| `GET` | `/api/v1/projects/{id}/tranches` | Mərhələli maliyyələşmə şablonu: istənilən məbləğ hansı mərhələlərdə və necə təsdiqlənərək ödənilir |
| `POST` | `/api/v1/projects/{id}/memo` | İnvestisiya memorandumunun AI icmalı (Əkinçi AI, açar yoxdursa qaydalara əsaslanan mətn) |
| `GET` | `/api/v1/projects/interests/{id}/tranches` | Razılaşmanın mərhələləri: peyk (NDVI, hava effekti çıxılmaqla), alıcı müqaviləsi, ekspert (investor, sahibkar, admin) |
| `POST` | `/api/v1/projects/interests/{id}/tranches/{seq}/release` | Təsdiqlənmiş mərhələnin ödənilməsi: escrow-dan sahibkara (investor) |

### Portfel qurucusu və Böyük Qayıdış paneli

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/portfolio` | Büdcə və risk meylinə görə optimal portfel, səmərəli sərhəd, stress testlər və sosial təsir |
| `POST` | `/api/v1/portfolio/explain` | Portfelin AI ilə investor dilində izahı |
| `GET` | `/api/v1/impact/regions` | Rayonlar üzrə potensial investisiya, iş yerləri, qayıdan ailələr, investor marağı və hazırlıq indeksi |
| `GET` | `/api/v1/impact/official` | Keçirilmiş müsabiqələrin rəsmi göstəriciləri (Nazirlik) və bazar həcmi: TAM / SAM / SOM |
| `GET` | `/api/v1/impact/delivery` | Vəd və reallıq: icarəyə verilmiş bütün sahələr üzrə öhdəliklərin icrası |
| `GET` | `/api/v1/impact/delivery/{plotId}` | Vəd və reallıq: qalib müraciətin öhdəlikləri, aylıq icra (EKTİS hesabatı) və peyk NDVI yoxlaması |

### Alıcılar və niyyət məktubları

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/admin/demands` | Admin: bütün tələblər |
| `POST` | `/api/v1/admin/demands/{id}/verify` | Admin: tələbi təsdiqlə və ya rədd et |
| `GET` | `/api/v1/buyer/demands` | Alıcı: mənim tələblərim |
| `POST` | `/api/v1/buyer/demands` | Alıcı: yeni tələb (Nazirliyin yoxlamasına göndərilir) |
| `PUT` | `/api/v1/buyer/demands/{id}` | Alıcı: tələbi redaktə et (yenidən yoxlamaya gedir) |
| `GET` | `/api/v1/buyer/letters` | Alıcı: gələn niyyət məktubu sorğuları |
| `POST` | `/api/v1/buyer/letters/{id}/decline` | Alıcı: niyyət məktubundan imtina |
| `POST` | `/api/v1/buyer/letters/{id}/sign` | Alıcı: niyyət məktubunu imzala |
| `GET` | `/api/v1/demands` | Təsdiqlənmiş alıcı tələbləri |
| `GET` | `/api/v1/demands/{id}` | Tələb |
| `POST` | `/api/v1/demands/{id}/letters` | Alıcıdan niyyət məktubu istə (investor / sahibkar) |
| `GET` | `/api/v1/letters` | Niyyət məktublarım (və ya müraciətə / layihəyə əlavə olunanlar) |
| `GET` | `/api/v1/plots/{id}/demands` | Sahənin məhsuluna uyğun alıcılar (məhsul, məsafə, həcm) |

### AI investisiya məsləhətçisi

| Metod | Yol | Təsvir |
|---|---|---|
| `POST` | `/api/v1/advisor/chat` | Söhbət (tam cavab) |
| `GET` | `/api/v1/advisor/status` | Məsləhətçinin rejimi: `ai` və ya `offline` |
| `POST` | `/api/v1/advisor/stream` | Söhbət (axın): meta (kartlar), delta (mətn hissələri), done |

### Müsabiqələr

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/competitions` | Elan edilmiş və bağlanmış müsabiqələr |
| `GET` | `/api/v1/competitions/{id}` | Müsabiqə haqqında ətraflı məlumat |
| `GET` | `/api/v1/competitions/commission` | Komissiyada təmsil olunan dövlət qurumları (Qaydalar 3.5) |

### Ağıllı tövsiyə və ROI

| Metod | Yol | Təsvir |
|---|---|---|
| `POST` | `/api/v1/recommendations` | Profilə uyğun sahələrin izahlı sıralanması |
| `POST` | `/api/v1/recommendations/summary` | Profilə uyğun (60+ bal) sahələrin sayı: formadakı canlı sayğac üçün |
| `GET` | `/api/v1/plots/{id}/evidence` | AgroEvidence: hər faktın mənbəyi, real və ya demo olması, tarixi, çatışmayan məlumatlar |
| `GET` | `/api/v1/plots/{id}/machinery` | Sahə üçün lazım olan texnika, yaxındakı elanlar və "al və ya icarə" müqayisəsi |
| `GET` | `/api/v1/equipment` | Texnika elanları (`type`, `district`, `category` süzgəcləri) |
| `GET` | `/api/v1/equipment/types` | Texnika növləri |
| `POST` | `/api/v1/equipment` | Texnikanı elan etmək (giriş tələb olunur) |
| `GET` | `/api/v1/equipment/mine` | Mənim elanlarım, mənə gələn və mənim göndərdiyim sorğular |
| `POST` | `/api/v1/equipment/{id}/requests` | İcarə sorğusu (sahə, başlama tarixi, miqdar) |
| `POST` | `/api/v1/equipment/requests/{id}/decide` | Sorğunu qəbul və ya rədd etmək (yalnız texnika sahibi) |
| `POST` | `/api/v1/equipment/requests/{id}/cancel` | Öz sorğunu ləğv etmək |
| `POST` | `/api/v1/auth/mygov/start` | mygov ID sessiyası (state) |
| `GET` | `/api/v1/auth/mygov/status` | Təsdiq gözlənilir və ya birdəfəlik kod |
| `POST` | `/api/v1/auth/mygov/exchange` | Birdəfəlik kodu portal tokeninə dəyişmək |
| `GET` | `/api/v1/mock/mygov/citizens` | Test mühiti: test vətəndaşları (FİN maskalanıb) |
| `POST` | `/api/v1/mock/mygov/approve` | Test mühiti: vətəndaşın təsdiqi (real axında mygov tətbiqi edir) |
| `PUT` | `/api/v1/auth/me/type` | Hesab növü: investor və ya sahibkar (növə aid sahələrlə) |
| `GET` | `/api/v1/roi/{plotId}` | Sahə üzrə təxmini gəlirlilik (10 illik proqnoz) |

### Status yoxlaması

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/track` | Müraciətin statusunu yoxla |

### Soraq kitabçaları

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/reference` | Bütün klassifikatorlar (kod → ad) |
| `GET` | `/api/v1/reference/rates` | Valyuta məzənnələri (Mərkəzi Bankın gündəlik bülleteni; əlçatmaz olsa son məlum məzənnə) |

### Statistika və hesabatlar

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/admin/stats` | İdarəetmə paneli üçün analitika |
| `GET` | `/api/v1/stats/public` | İctimai göstəricilər (ana səhifə) |

### Autentifikasiya

| Metod | Yol | Təsvir |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Giriş - JWT token qaytarır |
| `GET` | `/api/v1/auth/me` | Cari istifadəçinin profili |
| `PUT` | `/api/v1/auth/me` | Profilin yenilənməsi |
| `POST` | `/api/v1/auth/register` | İnvestor qeydiyyatı |

### Müraciətlər (investor)

| Metod | Yol | Təsvir |
|---|---|---|
| `POST` | `/api/v1/applications` | Qaralama müraciət yarat |
| `GET` | `/api/v1/applications/my` | Mənim müraciətlərim |
| `GET` | `/api/v1/applications/{id}` | Müraciət detalları (sahibi və ya əməkdaş) |
| `PUT` | `/api/v1/applications/{id}` | Qaralamanı yenilə |
| `POST` | `/api/v1/applications/{id}/documents` | Sənəd yüklə (PDF/PNG/JPG, maks. 10 MB) |
| `GET` | `/api/v1/applications/{id}/documents/{docId}` | Sənədi yüklə (download) |
| `DELETE` | `/api/v1/applications/{id}/documents/{docId}` | Qaralamadan sənədi sil |
| `POST` | `/api/v1/applications/{id}/respond` | Əlavə məlumat sorğusuna cavab ver |
| `GET` | `/api/v1/applications/{id}/score-preview` | Gözlənilən avtomatik balın canlı proqnozu |
| `POST` | `/api/v1/applications/{id}/submit` | Müraciəti göndər - nömrə yaradılır, EKTİS-ə ötürülür |
| `POST` | `/api/v1/applications/{id}/withdraw` | Müraciəti geri çağır (qaralama silinir) |

### Bildirişlər

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/notifications` | Son 30 bildiriş və oxunmamışların sayı |
| `POST` | `/api/v1/notifications/read-all` | Hamısını oxunmuş et |
| `POST` | `/api/v1/notifications/{id}/read` | Bildirişi oxunmuş et |

### Real vaxt yeniləmələri (SSE)

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/live/stream` | Server-Sent Events axını (bildirişlər, statuslar, EKTİS hadisələri) |
| `POST` | `/api/v1/live/ticket` | SSE bağlantısı üçün 60 saniyəlik birdəfəlik bilet |

### Telegram bildirişləri

| Metod | Yol | Təsvir |
|---|---|---|
| `POST` | `/api/v1/telegram/link` | Botu hesaba qoşmaq üçün birdəfəlik deep-link (10 dəqiqə) |
| `GET` | `/api/v1/telegram/status` | Telegram kanalının vəziyyəti |
| `POST` | `/api/v1/telegram/test` | Test mesajı göndər |
| `POST` | `/api/v1/telegram/unlink` | Telegram-ı ayır |

### Baxış və qiymətləndirmə (admin, ekspert)

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/review/applications` | Daxil olan müraciətlər (filtrlə) |
| `GET` | `/api/v1/review/applications/{id}` | Müraciətin tam görünüşü (ekspert üçün şəxsi məlumatlar maskalanır) |
| `POST` | `/api/v1/review/applications/{id}/evaluations` | Komissiya üzvünün meyarlar üzrə qiymətləndirməsi (Qaydalar 4.10: 0 / 2 / 3 / 4 / 5 bal) |
| `GET` | `/api/v1/review/applications/{id}/votes` | Müraciət üzrə komissiya səsləri, kvorum və nəticə |
| `POST` | `/api/v1/review/applications/{id}/votes` | Komissiya üzvünün səsi: lehinə və ya əleyhinə (səbəb məcburidir); bitərəf qalmaq olmur |
| `GET` | `/api/v1/review/commission` | Komissiyanın tərkibi: qurumlar, sədr, üzvlər, katib |
| `GET` | `/api/v1/review/competitions/{id}/protocol` | Komissiya iclasının protokolu |
| `GET` | `/api/v1/review/competitions/{competitionId}/ranking` | Müsabiqə üzrə reytinq (avtomatik 40% + ekspert 60%) |

### Admin: Müraciətlər

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/admin/applications/export.csv` | Müraciətləri CSV formatında ixrac et (Excel uyğun) |
| `POST` | `/api/v1/admin/applications/{id}/status` | Statusu dəyiş (sonlu avtomat qaydaları ilə) |

### Admin: Torpaq sahələri

| Metod | Yol | Təsvir |
|---|---|---|
| `POST` | `/api/v1/admin/plots` | Yeni sahə əlavə et |
| `PUT` | `/api/v1/admin/plots/{id}` | Sahəni redaktə et |
| `DELETE` | `/api/v1/admin/plots/{id}` | Sahəni sil (müraciəti olmayan) |

### Admin: Müsabiqələr

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/admin/competitions` | Bütün müsabiqələr (bütün statuslar) |
| `POST` | `/api/v1/admin/competitions` | Yeni müsabiqə (Layihə statusunda) |
| `PUT` | `/api/v1/admin/competitions/{id}` | Layihə statusunda müsabiqəni redaktə et |
| `POST` | `/api/v1/admin/competitions/{id}/approve` | Təsdiqlə və dərc et (yaradan şəxs təsdiqləyə bilməz) |
| `POST` | `/api/v1/admin/competitions/{id}/close` | Müsabiqəni bağla |
| `POST` | `/api/v1/admin/competitions/{id}/reject` | Düzəliş üçün geri qaytar |
| `POST` | `/api/v1/admin/competitions/{id}/submit` | Təsdiqə göndər |

### Admin: İstifadəçilər və rollar

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/admin/users` | İstifadəçilər, rollar, vəziyyət |
| `POST` | `/api/v1/admin/users` | Əməkdaş hesabı yarat (ekspert / admin) |
| `POST` | `/api/v1/admin/users/{id}/toggle` | Hesabı aktiv/deaktiv et |

### Admin: Audit jurnalı

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/admin/audit` | Audit jurnalı (səhifələnmiş) |

### Admin: EKTİS inteqrasiya monitorinqi

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/admin/integration/ektis-inbox` | EKTİS tərəfində qəbul edilmiş hadisələr (simulyator jurnalı) |
| `GET` | `/api/v1/admin/integration/events` | Outbox hadisələri |
| `POST` | `/api/v1/admin/integration/events/{id}/retry` | Uğursuz (DEAD/FAILED) hadisəni yenidən növbəyə qoy |
| `GET` | `/api/v1/admin/integration/farmer/{taxId}` | VÖEN/FİN üzrə EKTİS fermer reyestrində yoxlama |
| `POST` | `/api/v1/admin/integration/flush` | Gözləyən hadisələri dərhal göndər |
| `GET` | `/api/v1/admin/integration/health` | İnteqrasiya vəziyyəti |

### EKTİS inteqrasiya API (sistemlərarası)

| Metod | Yol | Təsvir |
|---|---|---|
| `GET` | `/api/v1/integration/applications` | Müraciətlər (status və dəyişmə tarixinə görə filtr - artımlı sinxronizasiya) |
| `GET` | `/api/v1/integration/applications/{number}` | Müraciət nömrə üzrə |
| `POST` | `/api/v1/integration/callbacks` | EKTİS-dən geri-çağırış (məs. fermer qeydiyyatı tamamlandı) |
| `GET` | `/api/v1/integration/competitions` | Müsabiqələr |
| `GET` | `/api/v1/integration/plots` | Sahələr (GeoJSON FeatureCollection) |

### EKTİS simulyatoru (mock)

| Metod | Yol | Təsvir |
|---|---|---|
| `POST` | `/mock/ektis/v1/events` | Portal hadisələrinin qəbulu (imza + idempotentlik yoxlaması ilə) |
| `GET` | `/mock/ektis/v1/farmers/{taxId}` | Fermer reyestrində VÖEN/FİN üzrə axtarış |
