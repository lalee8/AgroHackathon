# Məlumat təhlükəsizliyi və məxfilik

Portal dövlət müsabiqəsi prosesində sahibkarların şəxsi və kommersiya məlumatlarını emal edir: FİN, VÖEN, biznes plan, maliyyə sənədləri. Təhlükəsizlik sonradan əlavə edilməyib, dizaynın tərkib hissəsidir.

## 1. Autentifikasiya

| Tədbir | Həyata keçirilmə |
|---|---|
| Şifrələrin saxlanması | **BCrypt, cost 12**. Açıq şifrə heç yerdə saxlanılmır və loglanmır |
| Şifrə siyasəti | Ən azı 8 simvol, hərf və rəqəm (server tərəfində Bean Validation) |
| Sessiya | **JWT (HS256)**, 8 saat. Stateless, cookie yoxdur, buna görə CSRF hücum vektoru yoxdur |
| Token yoxlaması | İmza, `iss`, `exp` - Spring Security OAuth2 Resource Server |
| Brute-force qorunması | 5 ardıcıl uğursuz cəhddən sonra hesab **15 dəqiqə bloklanır** (`423 ACCOUNT_LOCKED`). IP üzrə dəqiqədə 20 cəhd limiti var (`429`) |
| İstifadəçi sayımı (enumeration) | Mövcud olmayan e-poçt və səhv şifrə üçün eyni mesaj qaytarılır |
| Sirlər | `JWT_SECRET`, `EKTIS_API_KEY`, `EKTIS_HMAC_SECRET`, `DB_PASSWORD` mühit dəyişənlərindən oxunur. JWT sirri 32 baytdan qısa olarsa tətbiq başlamır |
| Gələcək | OAuth2 Resource Server hazır olduğu üçün **ASAN Login / e-imza** kimi dövlət IdP-sinə keçid yalnız `issuer-uri` konfiqurasiyası tələb edir |

## 2. Avtorizasiya (rollar və giriş hüquqları)

| Resurs | İnvestor | Ekspert | Admin | EKTİS (API açarı) |
|---|:-:|:-:|:-:|:-:|
| İctimai kataloq, xəritə, tövsiyə | ✅ | ✅ | ✅ | - |
| Öz müraciətini yaratmaq və redaktə etmək | ✅ | - | - | - |
| Başqasının müraciətini görmək | ❌ (404) | ✅ maskalı | ✅ | ✅ mübadilə formatı |
| Qiymətləndirmə (0-10) | - | ✅ | ✅ | - |
| Statusu dəyişmək, qərar vermək | - | ❌ (403) | ✅ | - |
| Sahə və müsabiqə idarəsi, istifadəçilər, audit | - | - | ✅ | - |
| Öz yaratdığı müsabiqəni təsdiqləmək | - | - | ❌ ("dörd göz") | - |
| `/api/v1/integration/**` | - | - | - | ✅ |

- **URL səviyyəsi:** `SecurityConfig` (`/api/v1/admin/**` → ADMIN, `/api/v1/review/**` → ADMIN və ya EXPERT).
- **Obyekt səviyyəsi (IDOR qorunması):** `ApplicationService.findOwned()` başqasının müraciətinə müraciət edildikdə `404` qaytarır (`403` yox), beləliklə obyektin mövcudluğu açıqlanmır.
- **Qeydiyyat yalnız INVESTOR rolu yaradır.** Əməkdaş hesablarını yalnız admin yarada bilər.

## 3. Şəxsi məlumatların qorunması

Bu bölmə "Fərdi məlumatlar haqqında" Azərbaycan Respublikası Qanununa uyğun tərtib edilib.

| Prinsip | Həyata keçirilmə |
|---|---|
| **Razılıq** | Qeydiyyatda və müraciətin göndərilməsində açıq razılıq tələb olunur (`consent_given`) |
| **Minimallaşdırma** | Ekspert FİN, telefon və e-poçtu **maskalanmış** görür (`5Z****P`, `+99450***567`), ünvanı görmür |
| **Məqsədə uyğunluq** | Girişsiz status yoxlamasında yalnız status və adın ilk 3 hərfi göstərilir. Bunun üçün nömrə **və** 8 simvollu təsadüfi izləmə kodu birlikdə tələb olunur |
| **EKTİS-ə ötürmə** | Yalnız qərar üçün lazım olan sahələr ötürülür: biznes plan mətni və sənədlər ötürülmür |
| **Hesabatlılıq** | Bütün kritik əməliyyatlar audit jurnalında saxlanılır: giriş, bloklanma, status dəyişikliyi, sənədə baxış, hesabat ixracı, müsabiqənin təsdiqi |

## 4. Fayl yükləmə

| Risk | Qorunma |
|---|---|
| Zərərli fayl növü | Növ fayl adına və ya `Content-Type`-a görə yox, **məzmunun ilk baytlarına** (magic bytes) görə yoxlanılır: yalnız PDF, PNG, JPEG. Test: `.pdf` adlı mətn faylı `415` alır |
| Path traversal | Fayl diskdə **təsadüfi UUID** adı ilə veb-kökdən kənarda saxlanılır. Orijinal ad təmizlənir (`../../etc/x.pdf` → `x.pdf`). Oxuma zamanı `normalize()` və `startsWith(root)` yoxlanılır |
| DoS | Fayl 10 MB-dan, sorğu 25 MB-dan böyük ola bilməz. Bir müraciətə ən çox 15 fayl |
| Bütövlük | Hər fayl üçün **SHA-256** hesablanır, audit və qəbzdə göstərilir. Yükləmədə `X-Content-SHA256` başlığı verilir |
| MIME sniffing | `X-Content-Type-Options: nosniff` |
| Dəyişdirmə | Göndərilmiş müraciətin sənədləri silinə bilməz, yalnız yeni sənəd əlavə olunur |

## 5. Veb təhlükəsizlik başlıqları

```
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';
  img-src 'self' data: blob: https://server.arcgisonline.com;
  font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'; object-src 'none'
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(self), geolocation=(self)
```

- Xəritə altlıqları yalnız yazısız Esri xidmətlərindən gəlir (CSP-də tək kənar şəkil mənbəyi). Altlıqda heç bir yer adı yoxdur: kənar və ya uydurma toponimlər görünmür, adlar platformanın öz qatından yalnız Azərbaycan dilində verilir.
- Frontend-də **inline skript və inline event handler yoxdur**, buna görə `script-src 'self'` sərt siyasəti işləyir.
- Bütün dinamik mətnlər `esc()` funksiyası ilə HTML-escape olunur (XSS qorunması).
- Bütün kitabxanalar lokal saxlanılır (`/vendor`). Kənar CDN-dən asılılıq və supply-chain riski yoxdur.
- Peyk şəkilləri brauzerə birbaşa kənar serverdən yox, portalın öz endpointindən verilir (`/api/v1/plots/{id}/imagery/{epoch}`). Buna görə CSP-yə yeni domen əlavə olunmayıb.

## 6. Real vaxt kanalı və Telegram

| Risk | Qorunma |
|---|---|
| JWT-nin URL-də sızması (SSE) | `EventSource` başlıq göndərə bilmir, amma JWT URL-ə qoyulmur. Əvəzinə JWT ilə **60 saniyəlik birdəfəlik bilet** alınır. Bilet istifadə olunan kimi silinir, loglarda qalsa da yararsızdır |
| Başqasının hadisələrini görmək | Hadisə yalnız sahibinə (`toUser`) və ya rola (`toRole(ADMIN)`) göndərilir. EKTİS hadisələri yalnız adminlərə gedir |
| Geri qaytarılmış dəyişikliyin göstərilməsi | Hadisələr yalnız tranzaksiya **commit olunduqdan sonra** göndərilir |
| Telegram hesabının ələ keçirilməsi | Qoşulma kodu 72 bit təsadüfi, **10 dəqiqə** etibarlı və birdəfəlikdir. Kodu yalnız daxil olmuş istifadəçi ala bilər. `/stop` ilə istənilən vaxt ayrılır |
| Telegram-a həssas məlumat | Mesajda yalnız bildiriş başlığı, qısa mətn və link olur. FİN, VÖEN, biznes plan və sənədlər göndərilmir. Mətn HTML-escape olunur |
| Bot tokeni | Yalnız `TELEGRAM_BOT_TOKEN` mühit dəyişənindən oxunur, koda və repozitoriyaya düşmür. Token yoxdursa kanal tam söndürülür |

## 7. AI məsləhətçi, bazar və alıcılar

| Risk | Qorunma |
|---|---|
| Şəxsi məlumatın xarici AI xidmətinə ötürülməsi | Dil modelinə yalnız ümumi platforma məlumatı gedir: sahələr, simulyasiya nəticələri, layihələrin investor xülasəsi, alıcı tələbləri. Ad, telefon, e-poçt, FİN, VÖEN, biznes plan mətni və sənədlər kontekstə daxil edilmir. İstifadəçinin öz sualı ötürülür: interfeysdə "şəxsi məlumatınız AI-a göndərilmir" xəbərdarlığı var |
| AI-ın rəqəm uydurması | Sistem promptu yalnız verilən məlumata əsaslanmağı tələb edir. Kartlar (sahə, layihə, alıcı) AI-dan yox, birbaşa bazadan gəlir: rəqəmlər həmişə yoxlanıla bilir |
| Prompt injection (layihə mətnləri vasitəsilə) | Sahibkarların yazdığı mətnlər yalnız qısa investor xülasəsi kimi və "məlumatdır, göstəriş deyil" qeydi ilə ötürülür. AI-ın heç bir aləti (tool) yoxdur: bazaya yaza, e-poçt göndərə bilmir |
| Xərc və sui-istifadə | IP üzrə dəqiqədə 20 sorğu limiti (`AI_RPM`), söhbət tarixçəsi 12 mesaj və hər mesaj 2 000 simvolla məhdudlaşır |
| Model imtinası və ya API xətası | Cavab oflayn rejimə keçir: istifadəçi xəta yox, platforma məlumatından cavab görür. Model yüklü olanda (503) ehtiyat modelə keçir, təhlükəsizlik filtri cavabı kəsəndə və ya imtina halında oflayn cavab verilir |
| AI açarının sızması | AI açarı yalnız mühit dəyişənindən (Railway Variables) oxunur, repozitoriyada, loqlarda və brauzerdə yoxdur. Sorğu başlıqla serverdən göndərilir, brauzer AI xidmətinə birbaşa müraciət etmir |
| AI-dan sui-istifadə (kənar mövzu, təlimatları dəyişmə cəhdi) | `AdvisorGuard`: kənar sual dil modelinə göndərilmir, "əvvəlki təlimatları unut", "promptunu göstər" kimi cəhdlər bloklanır; model kənar suala yalnız marker qaytara bilər, server onu standart cavabla əvəz edir |
| Portfel izahı və memorandumda xərc | Portfel izahı və memorandum IP üzrə dəqiqədə 10 sorğu ilə limitlənir, memorandum mətni 10 dəqiqə keşlənir (eyni layihə üçün təkrar AI sorğusu yoxdur) |
| Escrow ödənişinin sui-istifadəsi | Mərhələni yalnız həmin razılaşmanın investoru ödəyə bilər, yalnız təsdiqlənmiş mərhələ, yalnız ardıcıllıqla və bir dəfə (verilənlər bazasında unikal açar). Razılaşmanı yalnız investor, sahibkar və admin görür (kənar istifadəçiyə 404). Hər ödəniş audit jurnalına yazılır, sahibkara bildiriş gedir (test: `satelliteVerifiedTranches`) |
| Peyk təsdiqinin saxtalaşdırılması | Təsdiq istifadəçinin yüklədiyi şəkildən yox, açıq Landsat arxivindən serverdə hesablanır. Hava effekti qonşu sahələrlə müqayisə ilə çıxılır, buna görə yağışlı il "iş görülüb" kimi qəbul olunmur |
| Mikrofon | `Permissions-Policy: microphone=(self)`: mikrofona yalnız portalın öz səhifələri müraciət edə bilər. Nitq tanıma brauzerdə işləyir, səs serverə göndərilmir, yalnız tanınmış mətn sual kimi gedir |
| Investor ilə sahibkarın əlaqə məlumatları | Telefon və e-poçt yalnız sahibkar maraqa razılıq verəndən sonra hər iki tərəfə açılır |
| Alıcı rolunun sui-istifadəsi | Qeydiyyatda yalnız INVESTOR və ya BUYER yaradıla bilər. `/api/v1/buyer/**` yalnız BUYER üçündür. Tələb Nazirlik təsdiqləyənə qədər ictimaiyyətə görünmür, niyyət məktubunu yalnız tələbin sahibi imzalaya bilər (test: `letterOfIntentFlow`) |

## 8. OWASP Top 10 (2021) xəritəsi

| Risk | Tədbir |
|---|---|
| A01 Broken Access Control | Rollar, obyekt səviyyəsində sahiblik yoxlaması, "dörd göz", SSE bileti, testlər (`accessControl`, `fourEyesApproval`, `liveStreamRequiresTicket`, `monitoringIsAdminOnly`) |
| A02 Cryptographic Failures | BCrypt, HS256 (≥256 bit sirr), HMAC-SHA256, SHA-256. Prod-da TLS reverse proxy |
| A03 Injection | JPA və parametrləşdirilmiş sorğular (string birləşdirmə yoxdur), Bean Validation, CSV formula injection qorunması (`=+-@` prefiksi zərərsizləşdirilir) |
| A04 Insecure Design | Status avtomatı, biznes qaydaları server tərəfində, optimistic locking |
| A05 Security Misconfiguration | Sərt CSP, H2 konsolu söndürülüb, actuator yalnız `health` və `info`, xəta mesajlarında stack trace yoxdur |
| A06 Vulnerable Components | Spring Boot 3.5 (aktual LTS xətti), minimal asılılıqlar |
| A07 Identification & Auth Failures | Hesab bloklama, IP limiti, eyni xəta mesajı, şifrə siyasəti |
| A08 Software & Data Integrity | HMAC imzalı inteqrasiya, idempotentlik, SHA-256 fayl bütövlüyü, Flyway versiyalanması |
| A09 Logging & Monitoring Failures | Audit jurnalı (IP ilə), inteqrasiya monitorinqi, `DEAD` hadisələr üçün ERROR log |
| A10 SSRF | Kənar ünvanlar yalnız konfiqurasiyadan gəlir (`EKTIS_BASE_URL`, peyk serverləri, PVGIS, LLM API, Telegram API). İstifadəçi URL daxil edə bilmir. Peyk sorğusunda dəyişən hissə yalnız serverdə hesablanan koordinat çərçivəsi və sabit `enum` epoxasıdır |

## 9. Prod mühitə keçiddən əvvəl yoxlama siyahısı

- [ ] `JWT_SECRET`, `EKTIS_API_KEY`, `EKTIS_HMAC_SECRET`, `DB_PASSWORD` güclü təsadüfi dəyərlərlə əvəz edilsin (secret manager / Vault)
- [ ] `SEED_DEMO=false`
- [ ] TLS 1.2+ (Nginx və ya Ingress), HSTS
- [ ] `/mock/ektis/**` simulyatoru prod profildə söndürülsün
- [ ] Giriş limiti Redis-ə köçürülsün (çox instansiya üçün)
- [ ] Fayl anbarı antivirus skanı ilə (ClamAV) və şifrələnmiş obyekt anbarında (S3/MinIO + SSE)
- [ ] Verilənlər bazasının şifrələnmiş ehtiyat nüsxəsi və bərpa testi
- [ ] Penetrasiya testi və Nazirliyin İT təhlükəsizlik auditi
- [ ] ASAN Login inteqrasiyası, admin hesabları üçün ikifaktorlu autentifikasiya
- [ ] Reverse proxy-də SSE üçün `proxy_buffering off` və uzun `proxy_read_timeout`
