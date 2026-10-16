# Quraşdırma və işə salma təlimatı

## Tələblər

| Variant | Lazım olan |
|---|---|
| A. Lokal (ən sadə) | **Java 21** (JDK). Maven lazım deyil, layihədə Maven Wrapper var |
| B. Docker | Docker Desktop / Docker Engine + Compose v2 |
| C. Hazır JAR | Java 21 |

Port: **8080**. Brauzer: Chrome, Edge, Firefox, Safari (son versiyalar).

## A. Lokal işə salma (H2, sıfır quraşdırma)

```bash
git clone https://github.com/AdamMammadov/aqrohacathon.git
cd aqrohacathon
./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run
```

İlk işə salmada asılılıqlar yüklənir (1-3 dəqiqə). Sonra tətbiq bunları avtomatik edir:
- `./data/aqroinvest.mv.db` H2 bazasını yaradır (PostgreSQL rejimində);
- Flyway miqrasiyasını icra edir;
- demo məlumatlarını yükləyir: 14 istifadəçi, 30 sahə, 5 müsabiqə, 22 müraciət və PDF sənədlər.

Açın: **http://localhost:8080**

> Bazanı sıfırlamaq üçün tətbiqi dayandırıb `data/` qovluğunu silin.

## B. Docker Compose (PostgreSQL 16)

```bash
cp .env.example .env      # istəyə görə sirləri dəyişin
docker compose up --build
```

İki konteyner qalxır:
- `db` - PostgreSQL 16, `pgdata` volume-u ilə;
- `app` - portal, root olmayan istifadəçi ilə işləyir, `uploads` volume-u ilə.

Dayandırmaq: `docker compose down`. Məlumatlarla birlikdə silmək: `docker compose down -v`.

## C. JAR faylı

```bash
./mvnw -DskipTests package
java -jar target/aqroinvest.jar
# PostgreSQL ilə:
SPRING_PROFILES_ACTIVE=postgres DB_URL=jdbc:postgresql://localhost:5432/aqroinvest \
DB_USER=aqroinvest DB_PASSWORD=aqroinvest java -jar target/aqroinvest.jar
```

## Testlər

```bash
./mvnw test
```
38 test işləyir: inteqrasiya testləri MockMvc və in-memory H2 ilə, üstəgəl unit testlər. Testlərdə peyk şəkillərinin internetdən yüklənməsi, Telegram və LLM API söndürülüb (AI oflayn rejimdə yoxlanılır, LLM axını isə lokal saxta serverlə test olunur). Portfelin fonda ilkin hesablanması testlərdə söndürülüb.

## Konfiqurasiya (mühit dəyişənləri)

| Dəyişən | Default | Təsvir |
|---|---|---|
| `PORT` | `8080` | HTTP portu |
| `SPRING_PROFILES_ACTIVE` | - | `postgres` - PostgreSQL profili |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | H2 fayl bazası | Verilənlər bazası bağlantısı |
| `JWT_SECRET` | demo sirr | JWT imza sirri (**≥ 32 simvol**, prod-da mütləq dəyişin) |
| `JWT_TTL_MINUTES` | `480` | Token-in ömrü |
| `UPLOAD_DIR` | `./data/uploads` | Sənədlərin saxlandığı qovluq |
| `FOUR_EYES` | `true` | Müsabiqə təsdiqində "dörd göz" prinsipi |
| `SEED_DEMO` | `true` | Boş bazada demo məlumatlarını yüklə |
| `EKTIS_BASE_URL` | `http://localhost:${PORT}/mock/ektis` | EKTİS API ünvanı (default: daxili simulyator) |
| `EKTIS_API_KEY` | `ektis-demo-api-key-2026` | Sistemlərarası API açarı (hər iki istiqamət) |
| `EKTIS_HMAC_SECRET` | demo sirr | Hadisə imzası üçün paylaşılan sirr |
| `EKTIS_MOCK_FAILURE_RATE` | `0.15` | Simulyatorun "nasazlıq" ehtimalı (retry demosu üçün) |
| `APP_BASE_URL` | `http://localhost:8080` | Portalın ictimai ünvanı (Telegram mesajlarındakı linklər üçün) |
| `TELEGRAM_BOT_TOKEN` | boş | @BotFather-dən alınan bot tokeni. Boşdursa Telegram kanalı söndürülür |
| `TELEGRAM_API_URL` | `https://api.telegram.org` | Telegram Bot API ünvanı (test üçün dəyişdirilə bilər) |
| `AI_PROVIDER` | `auto` | `auto`: `GEMINI_API_KEY` varsa Gemini, `ANTHROPIC_API_KEY` varsa Claude, heç biri yoxdursa oflayn. Həmçinin `gemini`, `claude`, `offline` |
| `GEMINI_API_KEY` | boş | Google AI Studio açarı (AI məsləhətçi, portfel izahı, memorandum) |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Əsas Gemini modeli |
| `GEMINI_FALLBACK_MODEL` | `gemini-3.5-flash-lite` | Əsas model yüklü (503) və ya əlçatmaz olanda |
| `GEMINI_THINKING` | `low` | Düşünmə səviyyəsi (`low` sürətli cavab, `high`, `none`) |
| `ANTHROPIC_API_KEY` | boş | Claude API açarı (alternativ provayder) |
| `AI_MODEL` | `claude-opus-5-5` | Claude modeli |
| `AI_EFFORT` | `low` | Cavabın dərinliyi (`low`, `medium`, `high`): söhbət üçün `low` sürətlidir |
| `AI_FALLBACKS` | `true` | Model sorğunu imtina ilə bitirərsə server tərəfində tövsiyə olunan modelə yönləndirmə |
| `AI_RPM` | `20` | AI məsləhətçiyə IP üzrə dəqiqədə sorğu limiti |
| `IMAGERY_ENABLED` | `true` | Keşdə olmayan peyk şəkillərini internetdən yüklə. `false` olduqda yalnız layihədəki hazır şəkillər istifadə olunur |
| `IMAGERY_CACHE` | `./data/imagery` | Yüklənmiş peyk şəkilləri və NDVI seriyalarının keşi |

## Faydalı ünvanlar

| Ünvan | Təyinat |
|---|---|
| `/` | Portal |
| `/admin.html` | İdarəetmə paneli (admin və ya ekspert ilə daxil olun) |
| `/admin.html#monitoring` | Peykdən nəzarət |
| `/ektis-canli.html` | EKTİS canlı ekranı (admin ilə). Demo zamanı ikinci ekranda açıq saxlayın |
| `/lovhe.html?id=14` | Sahə üçün çap olunan QR lövhə |
| `/simulyator.html` | Gələcək simulyatoru (Monte-Carlo, 10-25 il) |
| `/layiheler.html` | Sahibkar ↔ investor bazarı |
| `/alicilar.html` | Alıcılar və təsdiqlənmiş tələblər |
| `/meslehetci.html` | AI investisiya məsləhətçisi |
| `/swagger-ui.html` | İnteraktiv API sənədi |
| `/v3/api-docs` | OpenAPI spesifikasiyası (JSON) |
| `/api/v1/plots/geojson` | Sahələr GeoJSON formatında |
| `/actuator/health` | Sağlamlıq yoxlaması |

## Demo hesablar

| Rol | E-poçt | Şifrə |
|---|---|---|
| İnvestor | `investor@aqroinvest.az` | `Investor123!` |
| Admin (operator) | `admin@aqroinvest.az` | `Admin123!` |
| Admin (təsdiqləyici) | `nazirlik@aqroinvest.az` | `Admin123!` |
| Ekspert | `ekspert@aqroinvest.az` | `Ekspert123!` |
| Ekspert 2 | `ekspert2@aqroinvest.az` | `Ekspert123!` |
| Alıcı (Qarabağ Meyvə Emalı) | `alici@aqroinvest.az` | `Alici123!` |

Digər investorlar (`aqro.qarabag@example.az` və s.) da `Investor123!` şifrəsi ilə daxil ola bilər.

## Railway-də yerləşdirmə

Layihə Railway-də birbaşa GitHub repozitoriyasından qurulur (Dockerfile). `main` budağına hər push yeni versiyanı yerləşdirir. Railway-də **Variables** bölməsində bunları təyin edin:

| Dəyişən | Niyə |
|---|---|
| `APP_BASE_URL` | Railway ünvanı (məs. `https://aqrohacathon-production.up.railway.app`): Telegram mesajlarındakı linklər üçün |
| `JWT_SECRET` | Ən azı 32 simvol təsadüfi sətir |
| `GEMINI_API_KEY` | AI məsləhətçi, portfel izahı və memorandum üçün (Gemini). Açar yalnız Railway Variables-da saxlanılır, repozitoriyaya yazılmır |
| `ANTHROPIC_API_KEY` | Claude ilə işləmək üçün (alternativ, istəyə bağlı) |
| `TELEGRAM_BOT_TOKEN` | Telegram bildirişləri üçün (istəyə bağlı) |

H2 bazası konteynerin içindədir: hər yeni yerləşdirmədə demo məlumatları yenidən yüklənir. Daimi məlumat üçün Railway-də PostgreSQL əlavə edib `SPRING_PROFILES_ACTIVE=postgres` və `DB_URL`, `DB_USER`, `DB_PASSWORD` təyin edin. Railway ictimai HTTPS ünvan verdiyi üçün QR kodlar münsifin telefonunda birbaşa açılır.

## Əkinçi AI-ı qoşmaq

1. [aistudio.google.com](https://aistudio.google.com) saytında API açarı yaradın.
2. Railway-də **Variables** bölməsinə `GEMINI_API_KEY` əlavə edin (lokal işə salanda: `GEMINI_API_KEY=... ./mvnw spring-boot:run`). Tətbiq avtomatik yenidən başlayır.
3. `/meslehetci.html` səhifəsinin altında "Əkinçi AI" yazısı görünəcək (açar yoxdursa "oflayn rejim"). Eyni açar portfel qurucusunun "AI ilə izah et" düyməsini və layihələrin investisiya memorandumunu da işə salır.
4. Model yüklü (503) və ya limiti bitmiş (429) olanda sorğu avtomatik `GEMINI_FALLBACK_MODEL`-ə keçir; limiti bitmiş model 20 dəqiqə ərzində yenidən sınanmır. Açar olmasa və ya AI cavab verməsə məsləhətçi "Oflayn rejim"də işləməyə davam edir: cavabı platforma məlumatından qaydalarla qurur.

> **Pulsuz tarif barədə:** Google AI Studio-nun pulsuz açarında bəzi modellər üçün gündəlik limit çox kiçikdir (məsələn `gemini-3.8-flash` üçün gündə 20 sorğu). Demo günü bu limit tez bitə bilər; onda cavablar ehtiyat model (`gemini-3.5-flash-lite`) ilə gəlir. Daha sabit iş üçün Google Cloud-da billing aktivləşdirin və ya Railway-də `GEMINI_MODEL=gemini-3.5-flash-lite` təyin edin.

Claude istifadə etmək üçün `ANTHROPIC_API_KEY` təyin edin (`AI_PROVIDER=claude` ilə məcburi seçmək olar).

## Peyk şəkilləri

33 sahənin hamısı üçün şəkillər (Landsat 1990, 2000, 2010, Sentinel-2 2018 və 2025 mozaikaları, son Sentinel-2 çəkilişi) və NDVI seriyaları layihəyə daxil edilib (`src/main/resources/imagery`, ~8 MB). Demo internetsiz də işləyir. Admin yeni sahə əlavə etdikdə və ya koordinatı dəyişdikdə şəkillər ilk baxışda internetdən yüklənir və `IMAGERY_CACHE` qovluğunda saxlanılır. Mənbələr:

- Landsat GLS və Landsat 8/9: `landsat2.arcgis.com` (Esri Landsat ImageServer, USGS/NASA məlumatı);
- Sentinel-2 cloudless: `tiles.maps.eox.at` (EOX IT Services, Copernicus məlumatı);
- Son Sentinel-2 çəkilişi: `sentinel.arcgis.com` (Esri Sentinel-2 Views, Copernicus L2A, AWS açıq məlumatı, açar tələb etmir). Server hər `IMAGERY_LIVE_REFRESH_HOURS` saatdan bir (standart 12) son 14 aydakı ən yeni buludsuz çəkilişi yoxlayır, yenisi varsa şəkli və NDVI dəyərini yeniləyir. İnternet yoxdursa son saxlanılan kadr göstərilir.

## Telegram botunu qoşmaq (2 dəqiqə)

1. Telegram-da **@BotFather** botuna `/newbot` yazın, ad və istifadəçi adı verin (məs. `AqroInvestQarabagBot`).
2. Verilən tokeni mühit dəyişəninə yazın və tətbiqi yenidən başladın:
   ```bash
   TELEGRAM_BOT_TOKEN=123456:ABC... ./mvnw spring-boot:run
   ```
3. İnvestor kabinetində **Bildirişlər** → **Telegram-ı qoş** düyməsini basın, QR kodu telefonla skan edin və Telegram-da **Start** toxunun.

Bot webhook yox, long polling ilə işləyir: server internetə açıq olmasa da (noutbuk, NAT arxasında) mesajlar gəlir. Yalnız Telegram API-yə çıxış lazımdır.

## Telefonla QR skan etmək (demo üçün)

Qəbz və sahə lövhəsindəki QR kodlar portalın ünvanını göstərir. `localhost` münsifin telefonunda açılmır. Demo zamanı portalı ictimai ünvanla açın, məsələn:

```bash
cloudflared tunnel --url http://localhost:8080      # və ya: ngrok http 8080
```

Verilən `https://...` ünvanını brauzerdə açın və `APP_BASE_URL` olaraq təyin edin. Bundan sonra yaradılan QR kodlar telefonda birbaşa açılacaq.

## Problemlərin həlli

| Simptom | Həll |
|---|---|
| `Port 8080 was already in use` | `PORT=8081 ./mvnw spring-boot:run` |
| Xəritə altlığı görünmür | Xəritə plitələri (Esri, yazısız altlıqlar) internet tələb edir. Qalan funksionallıq internetsiz işləyir |
| `JWT_SECRET ən azı 32 bayt olmalıdır` | Daha uzun sirr təyin edin |
| Docker-də `app` bazaya qoşulmur | `db` konteynerinin `healthy` olmasını gözləyin: `docker compose ps` |
| Hesab bloklandı (5 səhv şifrə) | 15 dəqiqə gözləyin və ya admin paneldə "Deaktiv et" → "Aktiv et" edin |
| Kabinetdə "bot hələ qoşulmayıb" yazılıb | `TELEGRAM_BOT_TOKEN` təyin edilməyib. Yuxarıdakı "Telegram botunu qoşmaq" bölməsinə baxın |
| Canlı yeniləmə işləmir (Nginx arxasında) | SSE üçün proksidə `proxy_buffering off;` təyin edin. Tətbiq `X-Accel-Buffering: no` başlığını özü göndərir |
