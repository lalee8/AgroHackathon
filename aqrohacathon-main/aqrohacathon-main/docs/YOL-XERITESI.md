# Texniki yol xəritəsi

Texniki tapşırıqda hakatondan sonra **4-8 həftəlik** inkişaf planı nəzərdə tutulur. Aşağıda həmin plan, sonra isə pilotdan sonrakı uzunmüddətli istiqamətlər verilib.

## Hazırkı vəziyyət (hakaton, v1.0)

✅ İşlək prototip: 5 funksional modulun hamısı (xəritə, kataloq, elektron müraciət, izləmə, admin paneli) işləyir
✅ EKTİS inteqrasiya qatı: outbox, imza, idempotentlik, retry, sistemlərarası API, simulyator
✅ Təhlükəsizlik: rollar, JWT, BCrypt, audit, maskalama, fayl yoxlaması, CSP
✅ Torpağın zaman maşını: 1990-2026 real peyk şəkilləri, avtomatik yenilənən son Sentinel-2 kadrı və NDVI tarixçəsi, icarə sahələrinə peykdən nəzarət
✅ Canlı yeniləmələr (SSE), EKTİS canlı ekranı, Telegram bildirişləri, sahə üçün QR lövhə
✅ Torpağın ən yaxşı istifadəsi (9 ssenari), torpaq təyinatı, PVGIS günəş məlumatı, Monte-Carlo simulyatoru və hədəf kalkulyatoru
✅ Sahibkar ↔ investor bazarı: reallıq yoxlaması, investor dili, uyğunluq faizi, razılıqla açılan əlaqə
✅ Alıcılar: Nazirliyin təsdiqlədiyi tələblər, niyyət məktubları, sahə ilə uyğunlaşdırma
✅ Əkinçi AI məsləhətçi (platforma məlumatına əsaslanır, mövzu sərhədi, oflayn rejim, səslə və istənilən dildə sual)
✅ Portfel qurucusu: korrelyasiyalı Monte-Carlo, CVaR optimallaşdırması, səmərəli sərhəd, stress testlər
✅ Peyk təsdiqli mərhələli maliyyələşmə (escrow): hava effekti çıxılmış NDVI ilə avtomatik təsdiq
✅ AI investisiya memorandumu (bir səhifəlik PDF) və Böyük Qayıdış paneli (rayonlar üzrə iş yerləri, ailələr, hazırlıq indeksi)
✅ Sənədləşdirmə, 38 avtomatik test, Docker, PostgreSQL, Railway

## Mərhələ 1 - Texniki audit və tələblərin dəqiqləşdirilməsi (1-2-ci həftə)

| İş | Nəticə |
|---|---|
| Qalib prototipin Nazirliyin İT departamenti ilə texniki auditi | Audit hesabatı, düzəliş siyahısı |
| Biznes prosesin (müsabiqə qaydaları, komissiyanın iş qaydası) mütəxəssislərlə dəqiqləşdirilməsi | Təsdiqlənmiş BPMN prosesi, meyarlar və çəkilər |
| EKTİS komandası ilə interfeys razılaşması (API spesifikasiyası, klassifikatorlar, autentifikasiya növü) | İnterfeys razılaşma sənədi (ICD) |
| Fərdi məlumatların emalı üzrə hüquqi rəy | Məxfilik siyasəti, razılıq mətnləri |

## Mərhələ 2 - Real məlumatlar və inteqrasiya (3-4-cü həftə)

- **EKTİS adapteri:** real autentifikasiya (OAuth2 client credentials və ya mTLS), `EktisCodeMapper` klassifikator uyğunlaşdırması, staging mühitində test.
- **Torpaq məlumatları:** sahələrin Dövlət Torpaq Kadastrı və ya CBS-dən idxalı (GeoJSON/Shapefile), real poliqonlar, kadastr nömrəsi sahəsi.
- **ANAMA məlumatları:** minatəmizləmə sertifikatlarının avtomatik yoxlanması (API və ya müntəzəm idxal).
- **ASAN Login:** vətəndaş və sahibkarlar üçün dövlət identifikasiyası. OAuth2 Resource Server hazırdır. FİN və VÖEN avtomatik doldurulur.
- **VÖEN yoxlaması:** Vergi Xidməti reyestri ilə hüquqi şəxs məlumatlarının avtomatik doldurulması.

## Mərhələ 3 - Təhlükəsizlik və etibarlılıq (5-6-cı həftə)

- Penetrasiya testi, OWASP ASVS Level 2 uyğunluğu.
- Sirlərin idarəsi (Vault), TLS, HSTS, WAF.
- Fayl anbarı: MinIO/S3, şifrələmə, ClamAV antivirus skanı.
- Çox instansiya: giriş limiti Redis-də, outbox prosessoru `SKIP LOCKED` ilə, sessiyasız arxitektura.
- Monitorinq: Prometheus + Grafana, mərkəzləşdirilmiş log (ELK), inteqrasiya xətaları üçün xəbərdarlıq.
- Ehtiyat nüsxə və fəlakətdən bərpa (RPO ≤ 1 saat, RTO ≤ 4 saat).
- Admin hesabları üçün 2FA.

## Mərhələ 4 - Pilot (7-8-ci həftə)

- Bir real müsabiqə ilə (məs. 5-10 sahə) məhdud pilot.
- Komissiya üzvləri və operatorlar üçün təlim, istifadəçi təlimatları.
- İstifadə analitikası və rəy toplanması. UX düzəlişləri.
- Pilot hesabatı və tam istismara keçid planı.

## Pilotdan sonra (3-12 ay)

| İstiqamət | Təsvir |
|---|---|
| **Elektron müqavilə** | Qalib müəyyən olunduqdan sonra icarə müqaviləsinin e-imza ilə portalda bağlanması |
| **İnvestisiya öhdəliklərinin monitorinqi** | İnvestisiyanın, iş yerlərinin və istehsal göstəricilərinin rüblük hesabatları. Öhdəlik pozulduqda xəbərdarlıq |
| **Peyk monitorinqi (genişləndirmə)** | Prototipdə illik Landsat NDVI var. Növbəti addım: Sentinel-2 ilə 10 m dəqiqlik, 5 gündən bir yenilənmə, mövsümi profil (səpin-yığım) və xəbərdarlığın icarəçiyə avtomatik göndərilməsi |
| **Alıcı müqaviləsi** | Prototipdə niyyət məktubu var. Növbəti addım: e-imza ilə forvard müqavilə, qiymətin birja indeksinə bağlanması |
| **Real escrow** | Prototipdə escrow məntiqi və peyk təsdiqi var. Növbəti addım: tərəfdaş bankın escrow hesabına API ilə qoşulmaq, ödəniş əmrini sübut paketi ilə banka göndərmək, Sentinel-2 ilə aylıq təsdiq |
| **Portfelin kalibrlənməsi** | Ortaq amillərin həssaslıqlarını Hidrometeorologiya xidmətinin tarixi quraqlıq məlumatı və ixrac qiymətləri seriyası ilə statistik qiymətləndirmək |
| **Real iqtisadi istinadlar** | Simulyatorun istinad göstəricilərini (məhsuldarlıq, qiymət, xərc) Statistika Komitəsi və Nazirliyin məlumatı ilə əvəz etmək, hər il avtomatik yeniləmək |
| **Bərpa olunan enerji hərracları** | Günəş ssenarisini Energetika Nazirliyinin hərrac tarifləri və şəbəkəyə qoşulma imkanları ilə dəqiqləşdirmək |
| **Subsidiya inteqrasiyası** | Qaliblərin EKTİS vasitəsilə subsidiya və güzəştli kredit (Aqrar Kredit və İnkişaf Agentliyi) proqramlarına avtomatik yönləndirilməsi |
| **Çoxdillilik** | AI məsləhətçi artıq ingilis, rus və türk dilində cavab verir. Növbəti addım: interfeysin ingilis və rus dilinə tərcüməsi |
| **Mobil tətbiq** | Mövcud REST API üzərində (PWA və ya native) |
| **Süni intellekt köməkçisi** | Biznes planın keyfiyyətinin ilkin təhlili və boşluqlar barədə tövsiyə. Qərar həmişə komissiyada qalır |
| **Açıq məlumatlar** | Müsabiqə nəticələrinin anonimləşdirilmiş şəkildə açıq məlumat portalında dərci (şəffaflıq) |
| **Digər regionlar** | Platformanın istənilən regionun dövlət torpaqları üçün konfiqurasiya edilə bilən versiyası |
