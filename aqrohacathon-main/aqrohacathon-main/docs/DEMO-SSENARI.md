# Demo ssenarisi: "1 İnvestorun Səyahəti" (7 dəqiqə) və suallara hazırlıq

> **Əsas qayda: funksiya sərgisi yox, bir hekayə.** Platformada çox modul var, amma 7 dəqiqədə hamısını göstərmək münsifi yorur ("feature overload"). Demo yalnız bir investorun yolunu izləyir: **AI məsləhətçi ilə danışır → portfel qurucusunda riski bölür → pulu peyk təsdiqli escrow ilə hissə-hissə verir.** Qalan modullar (xəritə, kataloq, simulyator, Böyük Qayıdış, EKTİS, alıcılar) yalnız münsif soruşanda açılır: aşağıda "Ehtiyat ekranlar" bölməsində hər biri üçün 30 saniyəlik cavab var.
>
> **Hazırlıq:** portalı ictimai ünvanla açın (Railway). İki pəncərə: (1) qonaq, (2) `investor@aqroinvest.az`. Ehtiyat üçün üçüncü: `admin@aqroinvest.az`. Railway-də AI açarı təyin olunubsa Əkinçi AI dil modeli ilə, olmasa oflayn rejimdə cavab verir. Bir layihənin memorandumunu (PDF) çap edib masaya qoyun. Brauzer zoom-u 100% olsun (istənilən zoom-da başlıq özü yığılır, amma ekran böyük görünsün).

## Qiymətləndirmə meyarlarına uyğun bölgü

| Meyar | Çəki | Səyahətdə harada görünür |
|---|---|---|
| Funksionallıq və texniki icra | 25% | AI məsləhətçi → portfel qurucusu → peyk təsdiqli escrow: başdan sona işləyən bir axın |
| İnteqrasiya potensialı və arxitektura | 15% | mygov ID ilə giriş, canlı Sentinel-2, Landsat NDVI, PVGIS; ehtiyat: EKTİS canlı ekranı (GeoJSON), 132 API endpoint |
| İstifadə rahatlığı və UI/UX | 15% | Səslə sual, hazır portfellər (Konservativ / Balanslı / Aqressiv), səmərəli sərhəd, mobil |
| Problemin həlli və innovativ yanaşma | 15% | Korrelyasiyalı risk, hava effekti çıxılmış peyk təsdiqi, avtomatik dayandırılan ödəniş |
| Məlumat təhlükəsizliyi və məxfilik | 5% | AI-a şəxsi məlumat getmir, ödənişi yalnız investor edir, hər addım audit jurnalında |
| Demo, təqdimat və suallara cavab | 20% | Bu ssenari + aşağıdakı cavablar |
| Komanda işi və inkişaf perspektivi | 5% | Yol xəritəsi |

---

## 0:00 - 0:40 · Problem: investorun üç qorxusu

"Qarabağda torpaq var, pul var. Amma investorun üç sualı var: **pulumu haraya qoyum? quraqlıq olsa nə olacaq? pulu verdim, işləməsələr?** İndi bir investorun bu üç sualdan necə keçdiyini göstərəcəyik."

## 0:40 - 2:00 · 1-ci addım: "Haraya qoyum?" AI məsləhətçi

0. (10 saniyə) Başlıqdakı **mygov ID** ilə daxil olun: QR kodu telefonla skan edin, telefonda "Kamran Əhmədov" seçin, kompüter özü daxil olur. *"Dövlət identifikasiyası ilə giriş: şifrə yoxdur. Prototipdə test mühitidir, pilotda real mygov ID-yə qoşulur."*
1. Başlıqdakı **AI məsləhətçi** düyməsini basın: söhbət paneli səhifəni tərk etmədən açılır. Mikrofonu basıb deyin: *"Beş milyon manatım var, riskim orta olsun."*
2. Əkinçi AI cavabı axınla yazır: büdcəyə uyğun sahələr, investor axtaran layihələr, məhsulun alıcıları. Kartlar birbaşa bazadandır.
3. *"AI rəqəm uydurmur: rəqəmləri simulyasiya hesablayır, AI yalnız izah edir. Mövzudan kənar suala cavab vermir (münsif yoxlaya bilər: \"futbolda kim qalib gəldi?\"). Şəxsi məlumat AI-a getmir."*
4. Cavabın sonundakı **"Riski bölün: portfel qurucusu"** linkinə keçin.

## 2:00 - 4:00 · 2-ci addım: "Quraqlıq olsa?" Portfel qurucusu

1. `/portfel.html?budget=5000000` açılır. Yuxarıda: **ayda ~150 min ₼** xalis gəlir, 7-ci ildə qayıdır, zərər ehtimalı ~1%, 80 ailənin qayıdışı.
2. **Qırmızı kart "Hamısı bir varianta"** ilə **yaşıl kart "Tövsiyə olunan portfel"** yan-yanadır: *"Bütün pulu ən gəlirli bağa qoysanız pis ssenaridə (P10) -1.3 mln ₼. Portfeldə +1.2 mln ₼. Gəlir də azalmır."*
3. **Səmərəli sərhəd:** boz nöqtələr tək variantlar, mavi xətt portfellər. *"Bütün variantlar eyni 400 gələcəkdə birlikdə simulyasiya olunur: quraqlıq ili bütün aran bağlarını eyni anda vurur, günəş stansiyası isə quraq ildə daha çox istehsal edir."*
4. **Stress test** və **AI ilə izah et** (qızılı düymə): AI portfeli bir abzasla izah edir.
5. Hazır portfeli **Konservativ**ə dəyişin: büdcə, risk və üfüq bir kliklə dəyişir, portfel yenidən qurulur. *"Hər investor öz risk iştahını görür."*

## 4:00 - 6:00 · 3-cü addım: "Pulu verdim, işləməsələr?" Peyk təsdiqli escrow

1. İnvestor pəncərəsi → kabinet → **Layihələrim və maraqlarım** → Papravənd üzüm bağı: **Mərhələli maliyyələşmə**. Pul escrow-dadır, 4 mərhələ.
2. 2-ci mərhələ: *"Peyk təsdiqlədi. Sahənin NDVI-si +0.22 artıb, amma yağışlı il idi: qonşu 24 sahə də +0.11 artıb. Hava effekti çıxılandan sonra +0.11: sahədə iş görülüb."* **Escrow-dan ödə** basın: sahibkar bildiriş alır.
3. 4-cü mərhələ (alıcı imzalayıb) təsdiqlənib, amma "növbədə": *"Ödəniş ardıcıldır, mərhələ atlanmır."*
4. **Ən güclü an:** Horadiz GES razılaşması. *"Panellər qurulsaydı yaşıllıq azalmalı idi, əksinə qonşulardan +0.18 çox artıb. Peyk tikinti izi görmür: ödəniş avtomatik dayandırılıb."*

## 6:00 - 7:00 · Yekun

"Bir investor üç sualına üç ekranda cavab aldı: AI haraya qoymağı göstərdi, portfel quraqlıq riskini böldü, peyk pulu yalnız iş görüləndə buraxdı. Bu siyahı deyil, investisiya infrastrukturudur. Hər rəqəm real peyk, PVGIS və simulyasiya məlumatına əsaslanır. 38 avtomatik test, EKTİS-ə hazır arxitektura."

---

## Ehtiyat ekranlar: yalnız münsif soruşanda (hər biri ~30 saniyə)

| Sual | Göstərin | Bir cümlə |
|---|---|---|
| "Sahələri harada görürük?" | **Xəritə** (`/xerite.html`) | Altlıqda heç bir yer adı yoxdur, adlar yalnız Azərbaycan dilində öz qatımızdandır; sahələr qruplaşır, filtrlər linkdə saxlanılır. |
| "Bu torpaqda nə etmək olar?" | **Simulyator** (`/simulyator.html?plot=...`) | Hazır ssenarilər: Baza, Bədbin (quraqlıq ili), Nikbin (ixrac bumu); nəticə P10 / P50 / P90. |
| "Kiçik investor üçün?" | **Mənə uyğun sahə** (`/tovsiye.html`) | "Kiçik sahibkar" profilini basın: düymədə canlı say, hər kartda balın izahı (+25 Büdcə, +30 İstiqamət). |
| "Sahibkar ideyasını necə sübut edir?" | **Layihələr** → reallıq yoxlaması və **memorandum (PDF)** | 0-40 qırmızı, 41-75 sarı, 76-100 yaşıl; memorandumun çap nüsxəsi masadadır. |
| "Məhsulu kim alacaq?" | **Alıcılar** (`/alicilar.html`) | Doluluq zolağı, xəritədə qəbul radiusu, "Niyyət məktubu istə". |
| "Dövlətə nə verir?" | **Böyük Qayıdış** (`/regionlar.html`) | 33 sahə ən uyğun istiqamətdə işləsə ~59 mln ₼ investisiya, ~740 daimi iş yeri, ~590 ailənin qayıdışı; minatəmizləmə nişanları. |
| "Peyk məlumatı köhnə deyil?" | Sahə səhifəsi → **zaman maşını** | 1990-dan bu günə avtomatik oynayır; son kadr bu ayın Sentinel-2 çəkilişidir, server yeni buludsuz səhnəni özü yoxlayır. |
| "Bu məlumat haradan gəlir?" | Sahə səhifəsi → **AgroEvidence** | Hər faktın mənbəyi və tarixi; real mənbə (peyk, relyef) ilə demo məlumat (kadastr, ANAMA) açıq ayrılır. |
| "Risklər harada görünür?" | Xəritə → **Risk radarı** | Sahələr qayda əsaslı xəbərdarlıqların sayına görə rənglənir: suvarma yoxdur, yol uzaqdır, sertifikat yoxdur. Fakt deyil, yoxlanmalı məqam. |
| "Texnika çox baha deyil?" | Sahə səhifəsi → **al və ya icarə** | AGD-001 taxıl: texnika alsan ilkin investisiya 840 min ₼, ROI 24%; icarəyə götürsən 420 min ₼, ROI 38%, pul 7-ci yox, 5-ci ildə qayıdır. |
| "Müraciəti kim yoxlayır?" | Müraciət sihirbazı → **Müraciət köməkçisi** | Göndərməzdən əvvəl boş sahələr, çatışmayan sənədlər, ziddiyyətli rəqəmlər; qərarı yenə komissiya verir. |
| "Platformanı tez göstərin" | Ana səhifə → **Bələdçi tur** | 16 addımda bütün bölmələri özü gəzir və izah edir. |
| "EKTİS ilə necə?" | Admin: **EKTİS canlı ekranı** | Status dəyişən kimi imzalı JSON; outbox və təkrar göndərmə. |
| "Müsabiqə necə gedir?" | **Müsabiqələr** | Canlı geri sayım (son 5 gündə qırmızı), "dörd göz" təsdiqi. |
| "Qərarı kim verir?" | `ekspert@aqroinvest.az` → Admin → müraciət → **Komissiyanın səsverməsi**, sonra **Komissiya protokolu** | Qaydalar 3.5: 9 qurumdan komissiya, kvorum 6, açıq səs, bitərəf yoxdur, bərabərlikdə sədr; meyarlar 0-5 bal ilə. |
| "Kim müraciət edə bilər?" | **Qeydiyyat** (`/qeydiyyat.html`) və **Qaydalar** (`/qaydalar.html`) | Fiziki şəxs, hüquqi şəxs, xarici vətəndaş (yalnız icarə); yerli sakin bərabər balda üstündür; 2.4.6 sənədləri və 50 ₼ iştirak haqqı. |
| "Fermer nə vaxt nə etməlidir?" | **Fəaliyyət planı** (`/plan.html?plot=4`) | NASA iqlimi, mövsüm təqvimi, su balansı, nə qədər ting, texnika, işçi; ilk 12 ayın addımları. |
| "Qalib vədini yerinə yetirirmi?" | Sahə səhifəsi `/sahe.html?id=26` → **Vəd və reallıq** | Vəd edilən investisiya və iş yerləri EKTİS hesabatı və peyk NDVI ilə; geri qalan icarəçi sarı nişan alır. |
| "Bazar nə qədərdir?" | `/regionlar.html` → **Rəsmi nəticələr, TAM / SAM / SOM** | 2.2 mlrd ₼ potensial, 594 mln ₼ müsabiqədə; Nazirliyin rəsmi rəqəmləri. |
| "Xarici investor üçün?" | Başlıqdakı **dil və valyuta** (🌐) | Sayt və AI ingilis və rus dilində, məbləğlər $ və €-da (Mərkəzi Bankın məzənnəsi). |
| "Köhnə versiyanız haradadır?" | [VERSIYALAR.md](VERSIYALAR.md) | Mentor rəyindən əvvəlki v1 commit d67276b-də saxlanılıb; nə dəyişdiyi cədvəldədir. |

---

## Münsiflərin ehtimal olunan sualları və cavablar

**S: AI səhv rəqəm desə?**
C: AI rəqəm hesablamır. Rəqəmləri deterministik mühərriklər hesablayır (Monte-Carlo, portfel optimallaşdırması, reallıq yoxlaması), dil modelinə yalnız nəticələr verilir və sistem promptu onlardan kənara çıxmağı qadağan edir. Kartlar birbaşa bazadan gəlir. Model yüklü olanda ehtiyat modelə keçir, AI açarı olmasa belə məsləhətçi, portfel izahı və memorandum eyni məlumatdan oflayn mətn qurur.

**S: Əkinçi AI sizin öz modelinizdir?**
C: AI qatı bizimdir: kontekst (platformanın real məlumatı), mövzu qoruyucusu, rəqəm mühərrikləri və cavab qaydaları. Dil hissəsi üçün bazardakı böyük dil modeli API ilə qoşulur və konfiqurasiya ilə dəyişdirilə bilər. Model sıfırdan öyrədilməyib: bu, həm sürətli, həm də dövlət layihəsi üçün daha etibarlı yoldur, çünki rəqəmlər modeldən yox, bizim hesablamadan gəlir.

**S: AI-a başqa mövzuda sual versəm?**
C: Cavab vermir. Kənar sual (kod, şeir, idman, siyasət, ümumi bilik, "təlimatlarını göstər") dil modelinə ümumiyyətlə göndərilmir; qeyri-müəyyən sualı model özü qiymətləndirir və kənardırsa standart cavab qaytarılır: "Mən yalnız Əkinçi 2.0 mövzusunda kömək edirəm" və nümunə suallar.

**S: mygov ID realdırmı?**
C: Real mygov ID-yə qoşulmaq üçün Rəqəmsal İnkişaf və Nəqliyyat Nazirliyindən müştəri açarları lazımdır. Prototipdə identifikasiya tərəfi test mühitində simulyasiya olunur və bu, səhifədə açıq yazılıb: real şifrə və ya şəxsi məlumat istənilmir. Portal tərəfi (sessiya, QR, birdəfəlik kod, FİN üzrə hesab) hazırdır; pilotda yalnız identifikasiya adapteri real OIDC ünvanına keçir.

**S: Son peyk görüntüsü haradan gəlir və nə vaxt yenilənir?**
C: Copernicus Sentinel-2 L2A (AWS açıq məlumatı). Server hər sahə üçün ən son az buludlu (≤10%) səhnəni tapır, görüntünü və sahənin NDVI-sini hesablayıb saxlayır, 6 saatdan bir yeni səhnə olub-olmadığını yoxlayır. Sentinel-2 eyni yeri 5 gündən bir çəkir. İnternet olmasa son saxlanmış görüntü göstərilir.

**S: AI-a şəxsi məlumat gedirmi?**
C: Xeyr. Kontekstdə yalnız sahələr, simulyasiya nəticələri, layihələrin investor xülasəsi və alıcı tələbləri var. Ad, telefon, e-poçt, FİN, VÖEN, biznes plan və sənədlər göndərilmir. Sorğular IP üzrə limitlənir.

**S: Portfeldə korrelyasiyanı haradan götürürsünüz?**
C: 50+ investisiya üçün kovariasiya matrisini əllə vermək mümkün deyil. Biz izah olunan ortaq amillərdən istifadə edirik: hava (regional + aran və ya dağlıq zona), kənd təsərrüfatı bazar qiyməti, turizm tələbi, inflyasiya. Hər investisiyanın bu amillərə həssaslığı var: bağ havaya güclü, istixana zəif, günəş stansiyası əks istiqamətdə bağlıdır (quraq il günəşli ildir). Quraqlıq, şaxta və dolu şokları Qauss kopulası ilə: öz ehtimalı saxlanılır, amma pis ildə bütün sahələrdə birlikdə baş verir. Nəticədə eyni zonadakı iki bağın korrelyasiyası ~0.3, bağ ilə günəşinki ~0-dır və test bunu yoxlayır.

**S: Peyk yağışlı ili "iş görülüb" kimi qəbul etməzmi?**
C: Məhz buna görə mütləq həddən istifadə etmirik. 2026-cı ildə bütün sahələrin median NDVI-si +0.11 artıb. Biz sahənin dəyişməsindən eyni zonadakı 24 sahənin median dəyişməsini çıxırıq ("fərqlərin fərqi", iqtisadçıların təsir qiymətləndirməsində istifadə etdiyi üsul). Yalnız qonşulardan fərqlənən dəyişiklik işə aid edilir. Tikinti üçün qayda əksinədir: panellər və bina yaşıllığı azaltmalıdır.

**S: Escrow real bank hesabıdırmı?**
C: Prototipdə escrow məntiqidir: platforma hansı hissənin nə vaxt və hansı sübutla ödəniləcəyini hesablayır, ödənişi yalnız investor təsdiqləyir və hər addım audit jurnalına yazılır. Pilotda tərəfdaş bankın escrow hesabına API ilə qoşulur: platforma bank üçün ödəniş əmri və sübut paketini (peyk göstəriciləri, alıcı müqaviləsi, ekspert aktı) hazırlayır.

**S: Kənd təsərrüfatı rəqəmləri real deyil, bağ çox gəlirli görünmür?**
C: Rəqəmlər bilərəkdən ehtiyatlıdır: intensiv bağ üçün ilkin xərc 18 000 ₼/ha (damcı suvarma, dolu toru, şpalera), yetkin illərdə xalis gəlir ilkin xərcin ~35%-i, amma ilk 3 il gəlir yoxdur. Nəticədə bağ ~9-cu ildə qayıdır, IRR 12-16%-dir: real bazara yaxın. Bütün fərziyyələr simulyatorda mənbələri ilə açıq yazılıb və pilotda Nazirliyin məlumatı ilə əvəz olunur.

**S: Hazırlıq indeksi necə hesablanır?**
C: 100 bal: minatəmizləmə 30 (tam təmizlənmiş sahə payı), infrastruktur 25 (elektrik, suvarma, yola məsafə), iqtisadi potensial 25 (ən uyğun istiqamətin balı), bazar marağı 20 (müraciətlər, layihələr, alıcı tələbləri). Çəkilər açıqdır və Nazirlik dəyişə bilər.

**S: Simulyasiyanın rəqəmləri haradan gəlir?**
C: Günəş üçün real mənbələr: PVGIS (Avropa Komissiyası) hər koordinat üçün istehsal və illər arası dəyişkənlik, Tarif Şurasının 8.5 qəpik/kVts tarifi, Şəfəq GES-in açıqlanmış dəyəri. Kənd təsərrüfatı üçün istinad göstəriciləri təxminidir və simulyatorda "Fərziyyələr və mənbələr" cədvəlində açıq yazılıb. Pilotda Statistika Komitəsi və Nazirliyin məlumatı ilə əvəz olunacaq: kod dəyişmir, yalnız cədvəl.

**S: Günəş stansiyası niyə 15 ildə qayıdır? Mentor ayda 1 milyon dedi.**
C: 8.5 qəpik tariflə 1 MWp ildə ~115 min ₼ gəlir verir. Ayda 1 milyon ₼ üçün ~135 MWp, ~160 mln ₼ investisiya lazımdır. Bu, Şəfəq GES miqyasında (240 MW) layihədir. Platforma məhz bunu göstərir: ideya pis deyil, amma miqyası və investisiyası belədir. Uzunmüddətli, aşağı riskli infrastruktur investisiyasıdır.

**S: Sahibkarla investor bir-birini necə tapır? Bu sadəcə siyahı deyilmi?**
C: Xeyr: investor profilinə (istiqamət, məbləğ, risk, müddət) görə hər layihənin uyğunluq faizi hesablanır və səbəbləri yazılır. Layihə reallıq yoxlamasından keçir, təklif investorun dilinə çevrilir. Maraq bildiriləndə sahibkar bildiriş alır, razılıq verəndə əlaqə açılır. Alıcının niyyət məktubu layihənin riskini azaldır.


**S: Real EKTİS-ə necə qoşulacaqsınız? Onların API-sini bilmirsiniz.**
C: Buna görə **Ports & Adapters** arxitekturası seçmişik. Biznes məntiqi yalnız `EktisGateway` interfeysini görür. Real API spesifikasiyası gələndə bir adapter sinfi yazılır, klassifikator mapping-i əlavə olunur və 3 mühit dəyişəni təyin edilir: `EKTIS_BASE_URL`, `EKTIS_API_KEY`, `EKTIS_HMAC_SECRET`. Domen modeli, UI və testlər dəyişmir. Pull istiqaməti (EKTİS → portal) isə artıq hazırdır: `/api/v1/integration/**`.

**S: EKTİS əlçatmaz olsa nə olur?**
C: Müraciət bloklanmır. Hadisə outbox cədvəlində qalır və 5, 10, 20, 40, 80 saniyə gecikmə ilə 6 dəfə təkrar göndərilir. Sonra `DEAD` statusu alır və admin panelində "Yenidən göndər" düyməsi çıxır. Fermer reyestri yoxlaması `UNAVAILABLE` kimi qeyd olunur və sonradan təkrarlana bilər.

**S: Eyni hadisə iki dəfə gedərsə?**
C: Hər hadisənin UUID `eventId`-si var və `X-Idempotency-Key` başlığında göndərilir. Qəbul edən tərəf dublikatı tanıyır. Simulyatorumuz bunu nümayiş etdirir (`duplicate: true`).

**S: Avtomatik bal ədalətlidirmi? Süni intellekt qərar verir?**
C: Xeyr. Qərarı həmişə komissiya verir. Avtomatik bal müsabiqədə **əvvəlcədən elan edilmiş** meyar çəkiləri ilə hesablanır və hər meyar üzrə izahı investora da göstərilir. Bu, "qara qutu" deyil. Yekun reytinqdə ekspert balı 60%, avtomatik bal 40% çəki daşıyır.

**S: Şəxsi məlumatları necə qoruyursunuz?**
C: Bir neçə səviyyədə qoruyuruq:
- rol əsaslı giriş;
- investor başqasının müraciətini görə bilmir (404, IDOR testi var);
- ekspert FİN və telefonu maskalanmış görür;
- girişsiz status yoxlaması həm nömrə, həm təsadüfi izləmə kodu tələb edir;
- EKTİS-ə yalnız lazımi sahələr ötürülür;
- şifrələr BCrypt ilə saxlanılır;
- hər kritik əməliyyat audit jurnalına IP ilə yazılır.

Qanunvericiliyə uyğun olaraq razılıq qeydiyyatda və müraciətdə ayrıca alınır.

**S: Zərərli fayl yükləsəm?**
C: Fayl növü adına görə yox, məzmunun ilk baytlarına görə yoxlanılır. Yalnız PDF, PNG və JPEG qəbul olunur, maksimum 10 MB. Fayl diskdə təsadüfi UUID adı ilə veb-kökdən kənarda saxlanılır, buna görə path traversal mümkün deyil. SHA-256 bütövlüyü qeydə alınır. Prod üçün antivirus skanı yol xəritəmizdədir.

**S: Korrupsiya riskini necə azaldırsınız?**
C: Üç mexanizmlə:
- **"Dörd göz" prinsipi:** müsabiqəni yaradan şəxs onu dərc edə bilməz;
- meyarlar əvvəlcədən elan olunur;
- hər status dəyişikliyi kim, nə vaxt, hansı səbəblə audit jurnalında və müraciətin tarixçəsində görünür, investor da bunu görür.

Bundan əlavə, eyni sahəyə ikinci qalib təyin etmək texniki olaraq bloklanıb.

**S: Niyə React/Angular yox, sadə HTML/JS?**
C: Texniki tapşırıqda HTML/CSS tövsiyə olunub. Əlavə səbəblər:
- build addımı yoxdur;
- səhifələr sürətli yüklənir;
- bütün kitabxanalar lokaldadır, buna görə internetsiz demo mümkündür;
- sərt CSP (`script-src 'self'`) tətbiq olunur.

Bununla belə kod ES modullarına bölünüb və frontend yalnız REST API-dən istifadə edir. İstənilən vaxt SPA və ya mobil tətbiq eyni API üzərində qurula bilər.

**S: Çox istifadəçi olarsa miqyaslanır?**
C: Backend stateless-dir (JWT), buna görə üfüqi miqyaslanır. PostgreSQL üçün indekslər qurulub və optimistic locking var. Çox instansiyaya keçid üçün giriş limiti Redis-ə, outbox isə `SKIP LOCKED` rejiminə köçürülür. Bu addımlar [ARXITEKTURA.md](ARXITEKTURA.md) sənədində yazılıb.

**S: Sahə məlumatları realdır?**
C: Rayonlar və kəndlər realdır, nümunə layihələr (məsələn, Ağalı "ağıllı kənd", Ağdam Sənaye Parkı) real kontekstə əsaslanır. Sahələrin koordinatları relyef modelinə (hündürlük, maillik) və peyk şəkillərinə görə real kənd təsərrüfatı torpaqlarına yerləşdirilib: dağa, su anbarına, çay yatağına və ya yaşayış məntəqəsinə düşmür. Sahə sərhədləri və icarə şərtləri isə nümayiş məqsədlidir. Pilotda Dövlət Torpaq Kadastrı və ANAMA məlumatları ilə əvəz olunacaq. Admin panelindən istənilən sahəni əlavə etmək mümkündür: xəritəyə klikləyib koordinat seçilir, poliqon avtomatik qurulur.

**S: Peyk şəkilləri realdır? Haradan gəlir?**
C: Bəli, hamısı açıq dövlət peyk arxivlərindəndir:
- 1990, 2000, 2010: Landsat (ABŞ Geoloji Xidməti və NASA), Esri Landsat ImageServer vasitəsilə;
- 2018, 2025: Sentinel-2 (Avropa Kosmik Agentliyi, Copernicus), EOX buludsuz illik mozaikası;
- 2026 "Bu gün": ən son buludsuz Sentinel-2 L2A çəkilişi (Esri Sentinel-2 Views, AWS açıq məlumatı). Sentinel-2 hər nöqtəni təxminən 5 gündən bir çəkir, server yeni buludsuz çəkilişi avtomatik yoxlayır və kadrı özü yeniləyir.

NDVI də Landsat-ın xam kanallarından sahənin poliqonu daxilində hesablanır. Şəkillər serverdə keşlənir, 33 sahənin hamısı layihəyə daxil edilib, buna görə demo internetsiz də işləyir.

**S: 1990-cı il şəkli niyə bulanıqdır?**
C: Landsat 5-in dəqiqliyi 30 metrdir, Sentinel-2 isə 10 metrdir. Biz şəkli "gözəlləşdirmirik": investor real arxivi görür. Rəqəmlərlə müqayisə edərkən də fərqli sensorların dəyərlərini bir-biri ilə müqayisə etmirik, yalnız eyni sensor ailəsi daxilində.

**S: Telegram şəxsi məlumat üçün təhlükəsizdirmi?**
C: Telegram-a yalnız bildirişin başlığı və linki gedir. FİN, VÖEN, biznes plan və sənədlər getmir. Bağlantı 10 dəqiqəlik birdəfəlik kodla qurulur, investor `/stop` ilə istənilən vaxt ayıra bilər. Kanal istəyə bağlıdır, token yoxdursa tam söndürülür. Pilotda eyni mexanizmlə SMS və ya mygov bildirişləri qoşula bilər.

**S: Bundan sonra nə edəcəksiniz?**
C: [YOL-XERITESI.md](YOL-XERITESI.md) sənədindəki plana əsasən:
- 1-2-ci həftə: texniki audit və EKTİS ilə interfeys razılaşması;
- 3-4-cü həftə: ASAN Login, Torpaq Kadastrı və real EKTİS adapteri;
- 5-6-cı həftə: penetrasiya testi və monitorinq;
- 7-8-ci həftə: bir real müsabiqə ilə pilot.

Daha sonra elektron müqavilə, tərəfdaş bankın real escrow hesabı, Sentinel-2 ilə daha dəqiq (10 m, 5 gündən bir) peyk təsdiqi və Statistika Komitəsinin məlumatı ilə kalibrlənmiş iqtisadi model gəlir.
