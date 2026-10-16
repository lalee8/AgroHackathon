<div align="center">

# 🌾 Əkinçi 2.0

### İşğaldan azad edilmiş ərazilər üçün Aqrar İnvestisiya Müsabiqəsi Portalı

**Torpaq satmırıq: investoru, sahibkarı və alıcını birləşdiririk. Hər rəqəm simulyasiya, peyk və real məlumatla yoxlanılır.**

*Ad 1875-ci ildə Həsən bəy Zərdabinin nəşr etdiyi ilk Azərbaycan qəzeti «Əkinçi»dən gəlir: o, kəndliyə bilik çatdırırdı. Əkinçi 2.0 isə 150 il sonra azad edilmiş torpağı, investoru və dövləti rəqəmlərlə birləşdirir.*

![Java](https://img.shields.io/badge/Java-21-red) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue) ![OpenAPI](https://img.shields.io/badge/OpenAPI-3.1-85EA2D) ![Tests](https://img.shields.io/badge/tests-49%20passing-success) ![Docker](https://img.shields.io/badge/Docker-compose-2496ED)

<img src="docs/screenshots/01-ana-sehife.jpg" alt="Ana səhifə" width="900">

</div>

---

## Platformanın dəyəri: dörd tərəf, bir bazar

| Kim | Sualı | Platforma nə verir |
|---|---|---|
| 💰 **İnvestor** | "Pulum var, haraya qoyum? Quraqlıq olsa? İşləməsələr?" | **Portfel qurucusu** büdcəni 50+ variant arasında riskə görə bölür. **Peyk təsdiqli escrow**: pul hissə-hissə, iş görüləndə ödənilir. **Əkinçi AI** məsləhətçi kəndlinin dilini pul dilinə çevirir, **AI memorandum** bank formatında bir səhifə verir |
| 💡 **Sahibkar** | "İdeyam var, investor lazımdır" | İdeyanı rəqəmlə ifadə edən təqdimat və **canlı reallıq yoxlaması** (1500 ssenari). "Ayda 1 milyon" hədəfi üçün lazım olan miqyas və investisiya. **Torpağın ən yaxşı istifadəsi**: şoran torpaq əkin üçün 0, günəş üçün 100 bal |
| 🚜 **Texnika sahibi** | "Traktorum mövsümün yarısı boş dayanır" | **Texnika icarəsi**: elan, sorğu, qəbul; sahibkar üçün ilkin investisiya azalır, texnika sahibi mövsümlük gəlir qazanır |
| 🏭 **Alıcı** | "Xammal lazımdır, kimdən alım?" | Nazirliyin yoxladığı **"təsdiqlənmiş tələb"**, sahələrlə məhsul, məsafə və həcm üzrə uyğunlaşdırma, **niyyət məktubu** müraciətə əlavə olunur |
| 🏛 **Nazirlik** | "Müsabiqəni şəffaf aparmaq, torpaq işləsin, insanlar qayıtsın" | Elektron müraciət, status avtomatı, "dörd göz", peykdən nəzarət, EKTİS inteqrasiyası. **Böyük Qayıdış paneli**: rayon-rayon iş yerləri, qayıdan ailələr, investisiyaya hazırlıq indeksi |

**Canlı demo:** https://aqrohacathon-production.up.railway.app

## Bu nədir?

**Əkinçi 2.0** Qarabağ və Şərqi Zəngəzur iqtisadi rayonlarında kənd təsərrüfatı təyinatlı torpaq sahələrinə investisiya cəlb etmək üçün **işlək veb-portal prototipidir**. Vizual maket deyil: arxasında real backend, verilənlər bazası, rol əsaslı təhlükəsizlik, API və Kənd Təsərrüfatı Nazirliyinin **EKTİS** sisteminə inteqrasiya qatı var.

| Kim üçün | Nə edir |
|---|---|
| 👨‍🌾 **İnvestor / sahibkar** | 11 rayonda 33 sahəni xəritədə və kataloqda araşdırır, "Mənə uyğun sahə" tövsiyəsi alır, ROI-ni hesablayır, 5 addımda elektron müraciət edir, sənəd yükləyir, QR-li qəbz alır. Torpağın 1990-cı ildən bu günə peyk tarixçəsinə baxır. Status dəyişən kimi səhifə özü yenilənir, istəsə bildiriş Telegram-a da gəlir |
| 🏛 **Nazirlik operatoru (admin)** | Sahələri və müsabiqələri idarə edir ("dörd göz" təsdiq mexanizmi ilə), müraciətləri status avtomatı üzrə aparır, analitika və hesabat alır, EKTİS inteqrasiyasını canlı izləyir, icarəyə verilmiş torpaqlara **peykdən nəzarət** edir, sahəyə QR lövhə çap edir |
| ⚖️ **Komissiya üzvü** | Dövlət qurumunu təmsil edir: müraciətləri Qaydalar üzrə 0-5 bal şkalası ilə qiymətləndirir, lehinə və ya əleyhinə səs verir, maskalanmış şəxsi məlumatlarla işləyir |
| 🖥 **EKTİS (dövlət sistemi)** | API açarı ilə sahələri (GeoJSON), müsabiqələri və müraciətləri oxuyur, imzalı hadisələr qəbul edir, geri-çağırış (callback) göndərir |

## ⚡ 60 saniyəyə işə salın

**Variant 1: yalnız Java 21 lazımdır** (Maven quraşdırmaq lazım deyil, H2 bazası avtomatik yaradılır)
```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

**Variant 2: Docker + PostgreSQL 16**
```bash
docker compose up --build
```

Sonra brauzerdə **http://localhost:8080** açın. API sənədləri: **http://localhost:8080/swagger-ui.html**

### 🔑 Demo hesablar
Giriş səhifəsində bir kliklə daxil olmaq mümkündür.

| Rol | E-poçt | Şifrə | Nəyi göstərmək üçün |
|---|---|---|---|
| İnvestor (fiziki şəxs, yerli sakin) | `investor@aqroinvest.az` | `Investor123!` | Qaralama, "əlavə məlumat" sorğusu, təsdiqlənmiş müraciət, vəd və reallıq, sevimlilər, bildirişlər |
| Hüquqi şəxs | `zengilan.baglari@example.az` | `Investor123!` | VÖEN, şirkət rekvizitləri ilə müraciət |
| Xarici vətəndaş | `qarabag.vineyards@example.az` | `Investor123!` | Pasport, vətəndaşlıq, icarə hüququ; interfeys ingilis dilində |
| Admin (operator) | `admin@aqroinvest.az` | `Admin123!` | Analitika, statusun dəyişdirilməsi, sahələr, EKTİS monitorinqi |
| Admin (təsdiqləyici), komissiya sədri | `nazirlik@aqroinvest.az` | `Admin123!` | Gözləyən müsabiqəni təsdiqləmək ("dörd göz"), sədrin səsi, protokol |
| Komissiya üzvü | `ekspert@aqroinvest.az` | `Ekspert123!` | Qaydalar üzrə 0-5 bal, səsvermə (kvorum), reytinq |
| Alıcı | `alici@aqroinvest.az` | `Alici123!` | Təsdiqlənmiş tələblər, niyyət məktubu sorğularını imzalamaq |

Ətraflı quraşdırma: [docs/QURASDIRMA.md](docs/QURASDIRMA.md) · Münsiflər üçün demo ssenarisi: [docs/DEMO-SSENARI.md](docs/DEMO-SSENARI.md)

---

## 🎯 Texniki tapşırığın tam əhatəsi

| Tapşırıqdakı modul | Həll | Status |
|---|---|---|
| **1. Aqrar investisiya xəritəsi** | Leaflet xəritəsi: yazısız altlıq (kənar toponim yoxdur) + Azərbaycan dilində yer adları qatı, sahə poliqonları, qruplaşan markerlər (cluster), rayon etiketləri, peyk və relyef altlıqları, paylaşılan linkdə saxlanan filtrlər (təyinat, rayon, ölçü, suvarma, elektrik, aktiv müsabiqə), detal səhifəsi | ✅ |
| **2. İnvestisiya imkanları kataloqu** | Kartlar, 8 sıralama növü, ölçü, ərazi, istifadə şərtləri, son tarixə geri sayım, 4 sahəyə qədər **müqayisə** (ən yaxşı dəyər ★ ilə) | ✅ |
| **3. Elektron müraciət sistemi** | Qeydiyyat və giriş (JWT), 5 addımlı sihirbaz (fiziki və ya hüquqi şəxs, investisiya, biznes plan, texnologiyalar), sənəd yükləmə (drag&drop), **AQR-2026-000001** formatında nömrə, izləmə kodu, QR-li qəbz | ✅ |
| **4. Müraciətlərin izlənməsi** | Statuslar: *Qəbul edilib → Baxılır → Əlavə məlumat tələb olunur → Qərar verilib*, tarixçə (timeline), portal daxili bildirişlər, girişsiz status yoxlaması | ✅ |
| **5. İnzibati idarəetmə paneli** | Sahələrin əlavə və redaktəsi (xəritədən koordinat seçimi), müsabiqələr və **"dörd göz" təsdiq mexanizmi**, müraciətlərə baxış, status avtomatı, ekspert qiymətləndirməsi, reytinq, statistika, CSV hesabat, audit, istifadəçilər | ✅ |
| **EKTİS-ə inteqrasiya** | Ports & Adapters, Transactional Outbox, HMAC-SHA256 imza, idempotentlik, eksponensial retry, sistemlərarası API (X-API-Key), GeoJSON, callback, EKTİS simulyatoru | ✅ |

### "Qalib komandadan gözlənilən nəticələr" (minimum təhvil paketi)

| Tələb | Harada |
|---|---|
| İşlək portal prototipi | `./mvnw spring-boot:run` və ya `docker compose up` |
| Kataloq və axtarış | `/kataloq.html`, `/xerite.html`, `/tovsiye.html` |
| Elektron müraciət və statusun izlənməsi | `/muraciet.html`, `/kabinet.html`, `/izle.html` |
| İnzibati panelin ilkin versiyası | `/admin.html` (8 bölmə) |
| Verilənlər bazasının strukturu / məlumat modeli | [docs/MELUMAT-MODELI.md](docs/MELUMAT-MODELI.md), [Flyway miqrasiyası](src/main/resources/db/migration/V1__init_schema.sql) |
| API və inteqrasiya konsepsiyası | [docs/API.md](docs/API.md), [docs/EKTIS-INTEQRASIYA.md](docs/EKTIS-INTEQRASIYA.md), [docs/openapi.json](docs/openapi.json) |
| Mənbə kodu və texniki sənədləşdirmə | Bu repozitoriya + [docs/ARXITEKTURA.md](docs/ARXITEKTURA.md) |
| Quraşdırma və işə salma təlimatı | [docs/QURASDIRMA.md](docs/QURASDIRMA.md) |
| Gələcək inkişaf üçün texniki yol xəritəsi | [docs/YOL-XERITESI.md](docs/YOL-XERITESI.md) |

---

## 🚀 Bizi fərqləndirən üstünlüklər

### Ən yeni

> **v2: yarışın Qaydalarına uyğunlaşdırılıb.** Mentor görüşündən sonra müraciət edənlər, torpağın verilməsi və komissiya Kənd Təsərrüfatı Nazirliyinin dərc etdiyi Qaydalara uyğun quruldu. Köhnə versiya (v1) silinmədi: [docs/VERSIYALAR.md](docs/VERSIYALAR.md) hər iki versiyanı yan-yana müqayisə edir.

- **🏛 Dövlət komissiyası (Qaydalar 3.5).** 9 səs hüquqlu üzv: KTN 2 (sədr), İqtisadiyyat Nazirliyi 2, ETSN, RİNN, ŞADK, SEDA, Xüsusi nümayəndəlik; katib səs vermir. Kvorum 2/3, açıq səsvermə, sadə çoxluq, bərabərlikdə sədrin səsi. Meyarlar 0 / 2 / 3 / 4 / 5 bal şkalası ilə, bərabər balda yerli sakinə üstünlük (4.10.7). Çap olunan protokol.
- **🗓 Fəaliyyət planı: harada, nə vaxt, nə, niyə, nə lazımdır.** Seçilmiş sahə və istiqamət üçün NASA POWER iqlimi (2001-2025), mövsüm təqvimi (Gantt), su balansı və suvarma həcmi, şitil, texnika, işçi qüvvəsi, risklər, ilk 12 ayın addımları və Qaydaların minimumları.
- **📊 Rəsmi nəticələr və bazar həcmi.** Nazirliyin göstəriciləri (4 müsabiqə, 42 905 ha, 521 iştirakçı, 212 qalib, 15 253 ha icarə) və TAM / SAM / SOM.
- **🔍 Vəd və reallıq.** Qalibin vəd etdiyi investisiya və iş yerləri EKTİS aylıq hesabatları və peyk NDVI ilə müqayisə olunur: "vəd edildiyi kimi", "qrafikdən geri qalır" və ya "yoxlama lazımdır".
- **🌐 Üç dil və üç valyuta.** Sayt və AI məsləhətçi Azərbaycan, ingilis və rus dilində; məbləğlər ₼, $ və ya € ilə (Mərkəzi Bankın gündəlik məzənnəsi). Rəsmi sənədlər (qəbz, protokol, müqavilə) həmişə manatda.
- **❤️ Sevimli sahələr və bildiriş.** Sahə müsabiqəyə çıxanda izləyən investora portalda və Telegram-da xəbər gəlir.
- **🔗 Paylaşım önizləməsi.** Sahənin linki Telegram, WhatsApp, Facebook-da adı, şərtləri və peyk şəkli ilə açılır.
- **⬆️⬇️ Lift düyməsi.** Səhifənin aşağısında: bölmədən bölməyə bir kliklə, scroll etmədən.
- **🪪 mygov ID ilə giriş.** Başlıqdakı "mygov ID" düyməsi: QR kodu telefonla skan edib təsdiqləyirsiniz, kompüter özü daxil olur, şifrə yoxdur. Prototipdə identifikasiya tərəfi açıq işarələnmiş test mühitidir (real şifrə istənilmir); portal tərəfi (sessiya, birdəfəlik kod, FİN üzrə hesab) hazırdır, pilotda real OIDC adapteri qoşulur.
- **👥 Üç hüquqi status və məqsəd üzrə qeydiyyat.** Müraciət edən tərəf fiziki şəxs (o cümlədən yerli sakin), hüquqi şəxs (VÖEN, səlahiyyətli nümayəndə) və ya xarici vətəndaş (pasport, icarə hüququ) kimi qeydiyyatdan keçir, sonra məqsədini seçir: investor, sahibkar və ya alıcı. Torpağı yalnız dövlət verir: müraciətə dövlət qurumlarından ibarət komissiya baxır. Qeydiyyatdakı rekvizitlər müsabiqə müraciətinə avtomatik köçürülür. Müraciətdə Qaydalar 2.4.6 üzrə tam sənəd paketi və 50 manat iştirak haqqı.
- **🚜 Texnika icarəsi (üçtərəfli bazar).** Texnika sahibi traktor, kombayn, suvarma sistemi, dron elan edir; sahibkar və investor sahəsi üçün icarəyə götürür, sahibi qəbul və ya rədd edir. Sahə səhifəsində **"al və ya icarə"** eyni Monte-Carlo modelində: məsələn AGD-001 taxılda ilkin investisiya 840 min ₼-dan 420 min ₼-a düşür, ROI 24%-dən 38%-ə qalxır.
- **🛰 Canlı peyk görüntüsü və real zaman maşını.** Zaman maşını 1990-dan bu günə avtomatik oynayır (keçidlər, il sayğacı, NDVI); 2025 buludsuz illik mozaika və **son Sentinel-2 çəkilişi** (server yeni az buludlu səhnəni 6 saatdan bir yoxlayır, 5 gündən bir yeni çəkiliş). Kataloq kartlarında illüstrasiya yox, hər sahənin real peyk görüntüsü və sərhədi.
- **🔎 AgroEvidence, Risk radarı, Müraciət köməkçisi.** Hər faktın mənbəyi, tarixi və "real və ya demo" olması; xəritədə qayda əsaslı risk qatı; göndərməzdən əvvəl boş sahələri, çatışmayan sənədləri və ziddiyyətli rəqəmləri göstərən köməkçi (qərarı yenə komissiya verir).
- **🧭 Bələdçi tur.** Bir kliklə 16 addımda bütün bölmələri gəzir və izah edir.
- **🤖 Əkinçi AI mövzu sərhədi.** AI yalnız platformanın mövzusuna cavab verir; kod, idman, siyasət və "təlimatlarını göstər" kimi suallar bloklanır.

### Investorun dörd qorxusuna dörd cavab

- **📊 Portfel qurucusu: "Bütün pulu bir sahəyə qoymayın".** İnvestor büdcəsini və risk meylini yazır. Platforma 53 torpaq-istiqamət variantını, hər birini 5 real miqyasda, **eyni 400 gələcək ssenaridə birlikdə** simulyasiya edir (korrelyasiyalı Monte-Carlo): quraqlıq ili bütün aran bağlarını eyni vaxtda vurur, dağlıq zonaya az təsir edir; günəş stansiyası quraq (günəşli) illərdə əksinə daha çox istehsal edir; kənd təsərrüfatı qiymətləri birlikdə dəyişir. Optimallaşdırma E[NPV] - λ·CVaR10 meyarı ilə, mövqe, istiqamət və torpaq limitləri ilə aparılır. Nəticə: **səmərəli sərhəd** qrafiki, "hamısı bir varianta" ilə müqayisə (5 mln ₼ üçün pis ssenaridə -1.5 mln əvəzinə +1.2 mln), korrelyasiya matrisi, **stress testlər** ("aranda quraqlıq ili: portfel -16%, ən çox təsirlənən -40%"), ayda xalis gəlir, IRR, yaradılan iş yerləri və AI izahı.
- **🛰 Peyk təsdiqli mərhələli maliyyələşmə (escrow).** Razılaşmadan sonra investorun pulu bir dəfəyə verilmir: escrow hesabında qalır və 4 mərhələdə ödənilir. Peyk mərhələlərini Landsat 8/9 NDVI **avtomatik** yoxlayır və **hava effekti çıxılır** ("fərqlərin fərqi"): sahənin dəyişməsi eyni zonadakı 24 qonşu sahənin median dəyişməsi ilə müqayisə olunur, çünki yağışlı il bütün sahələri yaşıllaşdırır. Real nümunə: Papravənd üzüm bağında 2026-cı ildə NDVI +0.22, qonşular +0.11, təmiz fərq +0.11, ikinci hissə ödənişə açıldı. Horadiz GES-də isə tikinti bitki örtüyünü azaltmalı idi, amma NDVI qonşulardan +0.18 çox artdı: **ödəniş avtomatik dayandırıldı**. Mərhələlər ardıcıl ödənilir, alıcı müqaviləsi imzalananda müvafiq mərhələ özü təsdiqlənir.
- **📄 AI investisiya memorandumu.** Hər layihə üçün bank formatında bir səhifə (A4, PDF kimi çap): Monte-Carlo nəticələri, investorun pis, orta və yaxşı ssenaridə alacağı, reallıq yoxlaması, mərhələli ödəniş planı, sosial təsir, QR kod. İcmalı, güclü tərəfləri, riskləri və tövsiyəni (davam et, şərtlərlə, dayandır) **Əkinçi AI** yalnız platformanın hesabladığı rəqəmlərə əsaslanaraq yazır.
- **🏛 Böyük Qayıdış paneli.** 11 rayon üzrə: portaldakı torpaq ən uyğun istiqamətdə işləsə nə qədər investisiya, neçə daimi iş yeri, neçə ailənin qayıdışı, nə qədər ərzaq, qaz qənaəti və CO2. **İnvestisiyaya hazırlıq indeksi** (minatəmizləmə 30, infrastruktur 25, iqtisadi potensial 25, bazar marağı 20): növbəti müsabiqə hansı rayonda elan olunmalıdır? Hər layihənin səhifəsində də "Böyük Qayıdış təsiri" bloku var.
- **🎙 Səslə və istənilən dildə.** AI məsləhətçiyə mikrofonla Azərbaycan dilində danışmaq olar. Xarici investor ingilis, rus və ya türk dilində yazır: büdcə (USD, EUR) manata çevrilir, cavab onun dilində gəlir.

### Əsas modullar

- **🎲 "Fermanın gələcəyini 30 saniyədə yaşa" (Monte-Carlo, 10-25 il).** Seçilmiş torpaqda biznesin 2000 mümkün gələcəyi: quraqlıq, şaxta, dolu, xəstəlik, bazar qiyməti, xərclərin inflyasiyası. İllər bir-bir "yaşanır", qrafikdə P10-P90 paylanması toplanır. Nəticə tək rəqəm yox: geri qayıtma ehtimalı, pis və yaxşı ssenari, risk amilləri, alıcı müqaviləsinin effekti. Hədəf kalkulyatoru: "ayda 1 mln ₼ üçün nə lazımdır?"
- **🧭 Bu torpaqda nə etmək olar? (9 ssenari).** Torpağın hüquqi təyinatı (əkin, çoxillik, otlaq, əkinə yararsız, sənaye) və fiziki şərtlərinə görə hər ssenarinin uyğunluq balı və səbəbləri: taxıl, bağ, üzüm, istixana, ferma, arıçılıq, günəş stansiyası (real PVGIS məlumatı, 8.5 qəpik tarif), aqroturizm, soyuducu anbar.
- **🤝 Sahibkar ↔ investor bazarı.** Strukturlaşdırılmış layihə, reallıq yoxlaması (iddia ssenarilərin neçə faizində mümkündür), investor dilində təklif (pay, borc, gəlirdən pay), uyğunluq faizi, maraq bildirmə. Əlaqə məlumatı yalnız sahibkar razılıq verəndə açılır.
- **🏭 Alıcılar və niyyət məktubları.** Emal, ixrac və şəbəkə alıcılarının Nazirlik tərəfindən təsdiqlənmiş tələbləri xəritədə qəbul radiusu ilə, sahə ilə uyğunlaşdırma, imzalanan niyyət məktubu müraciətə əlavə olunur.
- **🤖 Əkinçi AI.** Platformanın öz məsləhətçisi: yalnız portalın real məlumatına əsaslanır (rəqəmləri mühərriklər hesablayır, AI izah edir), mövzudan kənar suallara (kod, idman, siyasət, ümumi bilik) cavab vermir. Dil modeli API ilə qoşulur və dəyişdirilə bilər; model yüklü olanda ehtiyat modelə keçir, açar olmasa oflayn rejimdə eyni məlumatdan cavab qurur. Şəxsi məlumat AI-a göndərilmir.
- **🛰 Torpağın zaman maşını (real peyk arxivi).** Hər sahə üçün eyni ərazinin 1990, 2000, 2010 (Landsat), 2018, 2025 (Sentinel-2 illik mozaika) şəkilləri və son Sentinel-2 çəkilişi. "Oynat" 35 ili avtomatik göstərir, "Müqayisə" iki ili sürgü ilə tutuşdurur. Altında 1990-cı ildən bu günə **NDVI** (bitki örtüyü indeksi) qrafiki və rəqəmlərə əsaslanan izah var. Şəkillər və rəqəmlər süni yaradılmayıb, real peyk məlumatıdır.
- **📡 Peykdən nəzarət.** Müsabiqə icarə ilə bitmir: Nazirlik icarəyə verilmiş torpağın təyinatı üzrə istifadə olunub-olunmadığını yoxlamalıdır. Admin panelində hər sahə üçün illik NDVI göstərilir, fəaliyyət əlaməti zəif olan icarə sahələri avtomatik "Diqqət tələb edir" kimi qeyd olunur.
- **⚡ Canlı yeniləmələr.** Admin statusu dəyişən kimi investorun kabineti ~0.2 saniyəyə özü yenilənir (Server-Sent Events). Ayrıca **EKTİS canlı ekranı** imzalı JSON hadisələrinin dövlət sisteminə necə çatdığını real vaxtda göstərir: qəbul, rədd, təkrar cəhd.
- **📲 Telegram bildirişləri və QR lövhə.** İnvestor kabinetdə QR kodu skan edib botu öz hesabına qoşur, status dəyişən kimi telefonuna mesaj gəlir. Hər sahə üçün çap olunan A4 lövhə var: sahəyə qoyulur, QR kod birbaşa həmin sahənin səhifəsini açır.
- **🧠 Ağıllı tövsiyə mühərriki.** İnvestor büdcəsini, istiqamətini, ölçünü, rayonu və infrastruktur tələblərini daxil edir. Sistem bütün sahələri **izahlı** uyğunluq balı ilə sıralayır, məsələn: "Büdcəniz minimum tələbi 1.9 dəfə qarşılayır", "Suvarma imkanı var".
- **📈 Gəlirlilik (ROI) kalkulyatoru.** 10 illik pul axını, geri ödəmə müddəti və ROI hesablanır. Torpağın bonitet balı, suvarma və kateqoriyanın bar verməyə qədər olan illəri nəzərə alınır. Qrafik və cədvəl görünüşü var.
- **⚖️ Şəffaf, izahlı qiymətləndirmə.** Müsabiqənin meyar çəkiləri əvvəlcədən elan olunur. Avtomatik ilkin bal hər meyar üzrə izahla göstərilir, "qara qutu" yoxdur. Yekun bal avtomatik balın 40%-i və ekspertlərin orta balının 60%-i ilə hesablanır, hər sahə üzrə lider ayrıca göstərilir.
- **🛡 Minatəmizləmə şəffaflığı.** Hər sahədə ANAMA sertifikatı və ya texniki sorğu statusu görünür. Azad edilmiş ərazilərdə investorun ilk sualı budur.
- **👀 "Dörd göz" prinsipi.** Müsabiqəni yaradan admin onu özü dərc edə bilməz. Bu yanaşma dövlət prosedurlarında korrupsiya riskini azaldır.
- **🔁 Etibarlı inteqrasiya.** Hadisələr biznes əməliyyatı ilə eyni tranzaksiyada outbox-a yazılır. EKTİS əlçatmaz olsa da məlumat itmir: 5s → 10s → 20s… gecikmə ilə təkrar cəhd edilir. Simulyator bunu canlı göstərmək üçün sorğuların 15%-ni bilərəkdən "uğursuz" edir.
- **🔐 Təhlükəsizlik dizaynın özündədir.** BCrypt(12), JWT (HS256), hesabın bloklanması, IP üzrə limit, IDOR qorunması, ekspert üçün şəxsi məlumatların maskalanması, faylın məzmununa görə yoxlanılması (magic bytes), SHA-256, CSP başlıqları, CSV injection qorunması, audit jurnalı.
- **📱 Hər cihazda.** Responsiv dizayn, çap üçün qəbz, internetsiz demo. Bütün kitabxanalar lokal saxlanılır, 33 sahənin peyk şəkilləri və NDVI seriyaları layihəyə daxil edilib. İnternet yalnız xəritə altlığı, son peyk görüntüsü, Telegram və AI üçün lazımdır.

---

## 🖼 Ekran görüntüləri

| | |
|---|---|
| ![Xəritə](docs/screenshots/02-investisiya-xeritesi.jpg) **İnvestisiya xəritəsi** | ![Kataloq](docs/screenshots/03-kataloq.jpg) **Kataloq və müqayisə** |
| ![Sahə](docs/screenshots/04-sahe-detal.jpg) **Sahə səhifəsi və geri sayım** | ![ROI](docs/screenshots/05-roi-kalkulyator.jpg) **ROI kalkulyatoru** |
| ![Tövsiyə](docs/screenshots/06-agilli-tovsiye.jpg) **Ağıllı tövsiyə** | ![Müqayisə](docs/screenshots/07-muqayise.jpg) **Sahələrin müqayisəsi** |
| ![Sihirbaz](docs/screenshots/08-muraciet-sihirbazi.jpg) **Müraciət sihirbazı və canlı bal** | ![Kabinet](docs/screenshots/09-investor-kabineti.jpg) **İnvestor kabineti** |
| ![Qəbz](docs/screenshots/10-muraciet-qebz.jpg) **Müraciət, QR qəbz, tarixçə** | ![İzləmə](docs/screenshots/16-status-izleme.jpg) **Girişsiz status yoxlaması** |
| ![Admin](docs/screenshots/11-admin-analitika.jpg) **Admin analitikası** | ![Müraciət](docs/screenshots/12-admin-muraciet.jpg) **Baxış, qiymətləndirmə, qərar** |
| ![Müsabiqələr](docs/screenshots/13-admin-musabiqeler.jpg) **"Dörd göz" təsdiqi** | ![EKTİS](docs/screenshots/14-ektis-monitorinq.jpg) **EKTİS monitorinqi** |
| ![Reytinq](docs/screenshots/15-reytinq.jpg) **Reytinq (ekspert görünüşü)** | ![API](docs/screenshots/17-swagger-api.jpg) **OpenAPI / Swagger** |

| ![Zaman maşını](docs/screenshots/19-zaman-masini-ana.jpg) **Torpağın zaman maşını: 1990-dan 2026-ya avtomatik oynayır** | ![NDVI](docs/screenshots/20-zaman-masini-ndvi.jpg) **Son Sentinel-2 kadrı (2026) və NDVI tarixçəsi** |
| ![Monitorinq](docs/screenshots/21-peyk-monitorinqi.jpg) **Peykdən nəzarət (admin)** | ![EKTİS canlı](docs/screenshots/22-ektis-canli-ekran.jpg) **EKTİS canlı ekranı** |
| ![Simulyator](docs/screenshots/25-simulyator.jpg) **Gələcək simulyatoru (Monte-Carlo, 10-25 il)** | ![Potensial](docs/screenshots/26-torpagin-potensiali.jpg) **Bu torpaqda nə etmək olar?** |
| ![Layihələr](docs/screenshots/27-layiheler.jpg) **Layihələr: xanalar və uyğunluq** | ![Reallıq](docs/screenshots/28-realliq-yoxlamasi.jpg) **Reallıq yoxlaması və investor dili** |
| ![Alıcılar](docs/screenshots/29-alicilar.jpg) **Alıcılar və təsdiqlənmiş tələblər** | ![AI](docs/screenshots/30-ai-meslehetci.jpg) **Əkinçi AI məsləhətçisi** |
| ![Portfel](docs/screenshots/32-portfel-qurucusu.jpg) **Portfel qurucusu** | ![Diversifikasiya](docs/screenshots/34-semereli-serhed.jpg) **Hamısı bir varianta və portfel** |
| ![Analitika](docs/screenshots/35-frontier-stress.jpg) **Səmərəli sərhəd, pul axını, stress test, korrelyasiya** | ![Böyük Qayıdış](docs/screenshots/36-boyuk-qayidis.jpg) **Böyük Qayıdış paneli** |
| ![Escrow](docs/screenshots/37-peyk-tesdiqli-odenis.jpg) **Peyk təsdiqli ödəniş: iş başladı** | ![Bayraq](docs/screenshots/38-peyk-bayraq.jpg) **Peyk tikinti izi görmür: ödəniş dayandırıldı** |
| ![Memorandum](docs/screenshots/39-ai-memorandum.jpg) **AI investisiya memorandumu (PDF)** | ![Alətlər](docs/screenshots/40-ana-aletler.jpg) **Ana səhifə: investor alətləri** |
| ![Texnika](docs/screenshots/42-texnika-icaresi.jpg) **Texnika icarəsi (AgroTech)** | ![Al və ya icarə](docs/screenshots/43-al-yoxsa-icare.jpg) **Texnikanı almaq yoxsa icarə: ROI müqayisəsi** |
| ![mygov ID](docs/screenshots/44-mygov-id.jpg) **mygov ID ilə giriş (test simulyatoru)** | ![Qeydiyyat](docs/screenshots/45-qeydiyyat-novleri.jpg) **İnvestor, sahibkar və alıcı üçün ayrı qeydiyyat** |
| ![AgroEvidence](docs/screenshots/46-agroevidence.jpg) **AgroEvidence: hər faktın mənbəyi** | ![Risk radarı](docs/screenshots/47-risk-radari.jpg) **AgroRisk radarı xəritədə** |
| ![Bələdçi tur](docs/screenshots/48-beledci-tur.jpg) **Bələdçi tur: platformanı 2 dəqiqəyə gəzdirir** | ![Mövzu qoruması](docs/screenshots/49-ai-movzu-qorumasi.jpg) **AI mövzudan kənar suala cavab vermir** |

**v2: Qaydalara uyğunlaşdırılmış versiya**

| | |
|---|---|
| ![Qeydiyyat](docs/screenshots/50-qeydiyyat-uc-status.jpg) **Üç hüquqi status: fiziki, hüquqi şəxs, xarici vətəndaş** | ![Komissiya](docs/screenshots/51-komissiya-sesvermesi.jpg) **Komissiya: 0-5 bal, səsvermə, kvorum** |
| ![Protokol](docs/screenshots/52-komissiya-protokolu.jpg) **Komissiya iclasının protokolu** | ![Rəsmi nəticələr](docs/screenshots/53-resmi-neticeler-tam-sam-som.jpg) **Rəsmi nəticələr və TAM / SAM / SOM** |
| ![Qaydalar](docs/screenshots/54-qaydalar.jpg) **Müsabiqə Qaydaları bir səhifədə** | ![Fəaliyyət planı](docs/screenshots/55-fealiyyet-plani.jpg) **Fəaliyyət planı: harada, nə vaxt, nə lazımdır** |
| ![Vəd və reallıq](docs/screenshots/56-ved-ve-reallik.jpg) **Vəd və reallıq: qalibin öhdəlikləri və peyk** | ![Vəd cədvəli](docs/screenshots/57-ved-ve-reallik-cedvel.jpg) **Qaliblər vədlərini necə yerinə yetirir** |
| ![Sevimlilər](docs/screenshots/58-sevimliler.jpg) **Sevimli sahələr və müsabiqə bildirişi** | ![Dil və valyuta](docs/screenshots/59-dil-valyuta.jpg) **Üç dil və ₼ / $ / € (Mərkəzi Bank məzənnəsi)** |
| ![English](docs/screenshots/60-ingilis-dili.jpg) **İngilis dili (xarici investor üçün)** | ![Русский](docs/screenshots/61-rus-dili.jpg) **Rus dili: rəsmi nəticələr və TAM / SAM / SOM** |

<p align="center"><img src="docs/screenshots/23-qr-lovhe.jpg" alt="QR lövhə" width="300">&nbsp;&nbsp;&nbsp;<img src="docs/screenshots/18-mobil.jpg" alt="Mobil görünüş" width="260"><br><b>Sahəyə qoyulan QR lövhə · Mobil görünüş</b></p>

---

## 🏗 Arxitektura (qısa)

```mermaid
flowchart LR
    subgraph Client["Brauzer (HTML + CSS + JS modulları)"]
        P[İctimai portal<br/>xəritə · kataloq · tövsiyə]
        I[İnvestor kabineti<br/>sihirbaz · izləmə]
        A[Admin paneli<br/>analitika · idarəetmə]
    end
    subgraph Backend["Spring Boot 3.5 · Java 21 (modul monolit)"]
        SEC[Security<br/>JWT · rollar · rate limit]
        PL[plot] --- CO[competition] --- AP[application]
        AP --- SC[scoring · matching · ROI]
        AP --- NO[notification · audit]
        NO --> LV[live · SSE]
        NO --> TG[telegram]
        PL --- SAT[satellite<br/>zaman maşını · NDVI]
        AP --> OB[(Outbox)]
        OB --> PUB[Outbox prosessoru<br/>retry · HMAC]
        IN[Integration API<br/>X-API-Key]
    end
    DB[(PostgreSQL / H2<br/>Flyway)]
    FS[(Sənəd anbarı<br/>UUID · SHA-256)]
    EKTIS[[EKTİS<br/>Kənd Təsərrüfatı Nazirliyi]]
    Client -- REST /api/v1 --> SEC
    Backend --> DB
    AP --> FS
    PUB -- imzalı hadisələr --> EKTIS
    EKTIS -- GeoJSON · müraciətlər · callback --> IN
    LV -. canlı hadisələr .-> Client
    SAT -- şəkil və NDVI, keşlə --> GEO[[Landsat · Sentinel-2]]
    TG --> TGAPI[[Telegram Bot API]]
```

Ətraflı: [docs/ARXITEKTURA.md](docs/ARXITEKTURA.md)

### Texnologiyalar
| Qat | Texnologiya |
|---|---|
| Backend | **Java 21, Spring Boot 3.5** (Web, Data JPA, Validation, Security, OAuth2 Resource Server, Actuator) |
| Verilənlər bazası | **PostgreSQL 16** (prod), H2 PostgreSQL rejimi (sıfır quraşdırma), **Flyway** miqrasiyaları |
| Frontend | **HTML5, CSS3, vanilla JavaScript (ES modules)**, Leaflet 1.9, Chart.js 4, QR generator, Inter şrifti. Framework və build addımı yoxdur |
| Real vaxt | **Server-Sent Events** (birdəfəlik biletlə), Telegram Bot API (long polling, istəyə bağlı) |
| AI | **Əkinçi AI**: öz kontekst qatı (platforma məlumatı), mövzu qoruyucusu, rəqəm mühərrikləri; dil modeli dəyişdirilə bilən LLM şlüzü ilə (SSE axını, yüklənmədə ehtiyat model), oflayn qaydalara əsaslanan rejim; brauzerdə nitq tanıma (az-AZ) |
| İqtisadi model | Monte-Carlo (2000 ssenari, sabit toxum), **korrelyasiyalı portfel modeli** (Qauss kopulası, ortaq hava, qiymət, turizm və inflyasiya amilləri, CVaR optimallaşdırması), **PVGIS** (Avropa Komissiyası) günəş istehsalı, Tarif Şurasının 8.5 qəpik/kVts tarifi |
| Peyk məlumatı | **Landsat** GLS 1990/2000/2005/2010 və Landsat 8/9 (USGS/NASA, Esri ImageServer), **Sentinel-2** cloudless 2018/2024 (Copernicus/ESA, EOX) |
| API | REST `/api/v1`, **OpenAPI 3.1 / Swagger UI**, GeoJSON (EPSG:4326) |
| Test | JUnit 5, Spring MockMvc: 38 avtomatik test və Playwright ilə uçdan-uca UI ssenarisi |
| Yerləşdirmə | Dockerfile (multi-stage, root olmayan istifadəçi), docker-compose, Maven Wrapper |

---

## 📁 Layihənin strukturu

```
aqrohacathon/
├── src/main/java/az/aqroinvest/
│   ├── security/        JWT, rollar, giriş limiti, API açarı filtri, CSP
│   ├── user/            İstifadəçilər və rollar
│   ├── plot/            Sahələr, filtrlər, GeoJSON, infrastruktur indeksi
│   ├── competition/     Müsabiqələr, meyarlar, "dörd göz" təsdiqi
│   ├── application/     Müraciətlər, status avtomatı, skorinq, ekspert qiymətləndirməsi
│   ├── matching/        Ağıllı tövsiyə və ROI kalkulyatoru
│   ├── economics/       Torpağın ən yaxşı istifadəsi, Monte-Carlo, hədəf kalkulyatoru, PVGIS
│   ├── market/          Sahibkar ↔ investor: layihə, reallıq yoxlaması, investor dili, maraq, peyk təsdiqli escrow, AI memorandum
│   ├── portfolio/       Portfel qurucusu: korrelyasiyalı Monte-Carlo, CVaR optimallaşdırması, stress test
│   ├── impact/          Böyük Qayıdış paneli: rayonlar üzrə potensial və hazırlıq indeksi
│   ├── buyer/           Alıcı tələbləri, təsdiq, niyyət məktubu
│   ├── advisor/         Əkinçi AI: kontekst, mövzu qoruyucusu, LLM şlüzü və oflayn rejim
│   ├── satellite/       Torpağın zaman maşını, NDVI tarixçəsi, peykdən nəzarət
│   ├── live/            Real vaxt yeniləmələri (SSE)
│   ├── telegram/        Telegram bildiriş botu
│   ├── integration/ektis/  EKTİS portu, HTTP adapteri, outbox, simulyator, inbound API
│   ├── document/        Təhlükəsiz fayl anbarı
│   ├── notification/    Bildirişlər
│   ├── audit/           Audit jurnalı
│   ├── analytics/       Statistika
│   └── seed/            Demo məlumatları (33 sahə, 5 müsabiqə, 22 müraciət, 8 layihə, 9 alıcı tələbi)
├── src/main/resources/
│   ├── db/migration/    Flyway SQL sxemi
│   ├── imagery/         33 sahənin peyk şəkilləri, NDVI seriyaları və PVGIS günəş məlumatı (internetsiz demo üçün)
│   └── static/          Frontend (26 səhifə, admin SPA)
├── src/test/            İnteqrasiya və unit testlər
├── docs/                Texniki sənədləşdirmə + ekran görüntüləri + openapi.json
├── Dockerfile · docker-compose.yml · mvnw
```

## 🧪 Testlər

```bash
./mvnw test
```
49 test bunları yoxlayır:
- müraciətin tam həyat dövrü
- status avtomatının qaydaları
- IDOR qorunması
- hesabın bloklanması
- saxta faylın rədd edilməsi və path traversal
- "dörd göz" prinsipi
- meyar çəkilərinin yoxlanması
- EKTİS API açarı
- HMAC imzası
- outbox retry
- maskalama
- SSE biletinin yoxlanması
- Telegram kanalının söndürülmüş rejimi
- peyk monitorinqinin yalnız admin üçün olması
- torpaq təyinatına görə ən yaxşı istifadə (şoran torpaqda günəş, əkin yerində GES qadağası)
- Monte-Carlo: təkrarlanma və alıcı müqaviləsinin riski azaltması, hədəf kalkulyatoru
- reallıq yoxlamasının şişirdilmiş iddianı tapması
- maraq → razılıq: əlaqə məlumatının yalnız razılıqdan sonra açılması
- niyyət məktubunu yalnız tələbin sahibinin imzalaya bilməsi
- AI məsləhətçi: niyyətin anlaşılması (USD, EUR, ingilis və rus dili daxil), platforma məlumatına əsaslanan cavab
- LLM axını saxta serverlə: yüklü modeldən ehtiyat modelə keçid, düşüncə hissələrinin atılması, təhlükəsizlik filtrində oflayna keçid
- AI mövzu sərhədi: kod, şeir, idman, təlimatları dəyişmə cəhdi bloklanır, platforma sualları cavablanır
- korrelyasiyalı dünya: eyni zonadakı iki bağ birlikdə düşür, günəş stansiyası onları sığortalayır
- portfel: büdcə, mövqe, istiqamət və torpaq limitləri, diversifikasiyanın faydası, səmərəli sərhəd
- peyk təsdiqli ödəniş: hava effekti çıxılmış NDVI ilə təsdiq, tikinti izi olmayanda bayraq, ardıcıllıq, təkrar ödəniş qadağası, kənar istifadəçiyə bağlı
- Böyük Qayıdış paneli, layihənin sosial təsiri, memorandumun oflayn mətni
- üç hüquqi status: hüquqi şəxs üçün VÖEN, xarici vətəndaş üçün pasport və vətəndaşlıq, xarici vətəndaşın mygov ID ilə qeydiyyatının qadağası
- komissiya: kvorum (2/3), katibin səs verə bilməməsi, əleyhinə səsdə səbəbin məcburiliyi, kvorumsuz qərarın rədd edilməsi, qərardan sonra səsvermənin bağlanması, protokol
- fəaliyyət planı: NASA iqlimi əsasında su balansı, resurslar və Qaydaların minimumları; rəsmi müsabiqə göstəriciləri və TAM > SAM > SOM
- sevimli sahə müsabiqəyə çıxanda bildiriş, vəd və reallıq hesabatı, paylaşım önizləməsi (Open Graph), valyuta məzənnələri
- 404 səhifəsi, naməlum dəyərdə 500 əvəzinə izahlı 400, statik faylların keşi

## 📚 Sənədlər

| Sənəd | Məzmun |
|---|---|
| [ARXITEKTURA.md](docs/ARXITEKTURA.md) | Modullar, qatlar, ardıcıllıq diaqramları, dizayn qərarları |
| [MELUMAT-MODELI.md](docs/MELUMAT-MODELI.md) | ER diaqramı, cədvəllər, status avtomatları |
| [API.md](docs/API.md) | Bütün endpointlər, autentifikasiya, nümunələr, xəta formatı |
| [EKTIS-INTEQRASIYA.md](docs/EKTIS-INTEQRASIYA.md) | İnteqrasiya konsepsiyası, hadisə kataloqu, təhlükəsizlik, real sistemə keçid |
| [TEHLUKESIZLIK.md](docs/TEHLUKESIZLIK.md) | Təhlükəsizlik tədbirləri, OWASP Top 10 xəritəsi, fərdi məlumatlar |
| [QURASDIRMA.md](docs/QURASDIRMA.md) | Quraşdırma, konfiqurasiya, mühit dəyişənləri |
| [YOL-XERITESI.md](docs/YOL-XERITESI.md) | Hakatondan sonrakı 8 həftəlik plan və uzunmüddətli inkişaf |
| [DEMO-SSENARI.md](docs/DEMO-SSENARI.md) | 7 dəqiqəlik təqdimat ssenarisi və münsiflərin suallarına hazır cavablar |
| [TEQDIMAT.md](docs/TEQDIMAT.md) | 9 slayd, hər slaydda bir fikir; TAM / SAM / SOM |
| [VERSIYALAR.md](docs/VERSIYALAR.md) | v1 və v2: mentor rəyindən sonra nə dəyişdi və niyə |

---

<div align="center">
<sub>Hakaton prototipi · Sahə sərhədləri və icarə şərtləri nümayiş məqsədlidir (koordinatlar relyef və peyk şəkillərinə görə real kənd təsərrüfatı torpaqlarına yerləşdirilib) · Real EKTİS-ə qoşulma yalnız səlahiyyətli qurumun razılığı ilə həyata keçirilir.<br>Peyk şəkilləri: Landsat - USGS/NASA (Esri Landsat ImageServer vasitəsilə) · Sentinel-2 cloudless - Copernicus/ESA, EOX IT Services (CC BY 4.0 / CC BY-NC-SA 4.0)</sub>
</div>
