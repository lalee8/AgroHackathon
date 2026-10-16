# Versiyalar: v1 (mentor görüşündən əvvəl) və v2 (Qaydalara uyğunlaşdırılmış)

Mentor görüşündə yarışın şərtləri dəqiqləşdi: müraciət edənlər kimlərdir, torpağı kim verir, qərarı kim qəbul edir. Heç kim səhv etməyib: şərtlər sonradan aydınlaşdı. Mentorun tövsiyəsi ilə köhnə versiya silinmədi, təqdimatda hər iki versiya göstərilir.

| | Harada |
|---|---|
| **v1** (köhnə) | Commit [`d67276b`](https://github.com/AdamMammadov/aqrohacathon/tree/d67276b) · lokal: `git checkout d67276b && ./mvnw spring-boot:run` |
| **v2** (yeni) | `main` budağı · canlı: https://aqrohacathon-production.up.railway.app |

## Nə dəyişdi və niyə

| Mövzu | v1 | v2 | Əsas |
|---|---|---|---|
| **Müraciət edənlər** | İki növ: fiziki və hüquqi şəxs. Rollar: investor, sahibkar, alıcı | **Üç hüquqi status**: fiziki şəxs, hüquqi şəxs (VÖEN, səlahiyyətli nümayəndə), **xarici vətəndaş** (pasport, vətəndaşlıq). Ayrıca "yerli sakin" əlaməti. Məqsəd (investor, sahibkar, alıcı) ikinci addımda seçilir | Mentor: "üç tip istifadəçi var"; sahibkar, investor, vətəndaş, yerli sakin hamısı müraciət edəndir |
| **Torpaq** | Sahibkar profilində "öz torpağım var" seçimi | Azad edilmiş ərazilərdə **şəxsi torpaq mülkiyyəti yoxdur**: torpağı yalnız dövlət müsabiqə ilə icarəyə verir. Statuslar: "müsabiqədə qalib, torpaq icarədədir", "müsabiqəyə müraciət edir", "hələ yoxdur". Xarici vətəndaş yalnız icarə hüququ ilə (Torpaq Məcəlləsi 48.3) | Mentor: "torpağı dövlət verir" |
| **Qəbul edən tərəf** | Nazirlik operatoru və iki ekspert | **Dövlət qurumlarından ibarət komissiya** (Qaydalar 3.5): KTN 2 üzv (sədr KTN-dən), İqtisadiyyat Nazirliyi 2, ETSN, RİNN, ŞADK, SEDA, Xüsusi nümayəndəlik 1-ər. Katib səs vermir. Kvorum 2/3, açıq səsvermə, sadə çoxluq, bitərəf qalmaq olmur, bərabərlikdə sədrin səsi həlledicidir. Çap olunan **protokol** | Mentor: "qəbul edən tərəf dövlət qurumlarından ibarət komissiyadır" |
| **Qiymətləndirmə** | 0-10 sürgü, öz meyarlarımız | Qaydalar 4.10 üzrə rəsmi meyarlar, **0 / 2 / 3 / 4 / 5 bal şkalası** (uyğunluq faizinə görə), bərabər balda **yerli sakinə üstünlük** (4.10.7) | Kənd Təsərrüfatı Nazirliyinin dərc etdiyi Qaydalar |
| **Sənədlər** | Biznes plan və şəxsiyyət məcburi, qalanı könüllü | Qaydalar 2.4.6 tam paketi məcburi: investisiya layihəsi, şəxsiyyət və ya reyestrdən çıxarış, **VÖEN və vergi borcu arayışı**, maliyyə hesabatı və 12 aylıq bank dövriyyəsi, təcrübə, **50 manat iştirak haqqı** | Qaydalar 2.4.6 |
| **İcarə müddəti** | Sahədən asılı 25-40 il | **İlk 5 il, 49 ilədək uzadılır**; subicarə qadağandır; öhdəliklər üzrə EKTİS-də aylıq hesabat | Qaydalar 4.16 |
| **Minimum sahə** | Yox idi | 5 hektardan az olmayan sahə | Qaydalar |
| **Statistika** | Portalın demo rəqəmləri | Nazirliyin rəsmi göstəriciləri: 4 müsabiqə, 9 rayon, **42 905 ha** təklif, **521** iştirakçı, **212** qalib, **15 253 ha** icarəyə verilib. TAM / SAM / SOM bazar həcmi | Mentorun verdiyi təqdimat materialı |

## v2-də yeni bölmələr

- **Fəaliyyət planı** (`/plan.html`): investor və fermer üçün "hansı ərazidə, nə vaxt, nə etmək olar, niyə, nə lazımdır". NASA POWER iqlimi (2001-2025), mövsüm təqvimi (Gantt), su balansı, şitil, texnika, işçi qüvvəsi, risklər, ilk 12 ay, Qaydaların minimumları.
- **Qaydalar səhifəsi** (`/qaydalar.html`): kim müraciət edə bilər, sənədlər, meyarlar, komissiya, müddətlər, icarə şərtləri.
- **Rəsmi nəticələr və TAM / SAM / SOM** (`/regionlar.html`).
- **Vəd və reallıq**: qalibin vəd etdiyi investisiya və iş yerləri ilə EKTİS aylıq hesabatları və peyk NDVI-nin müqayisəsi.
- **Sevimli sahələr**: sahə müsabiqəyə çıxanda bildiriş (portal, Telegram).
- **Üç dil** (Azərbaycan, ingilis, rus) sayt və AI məsləhətçi üçün; **valyuta** seçimi (₼ / $ / €, Mərkəzi Bankın gündəlik məzənnəsi).
- **Paylaşım önizləməsi**: Telegram, WhatsApp, Facebook-da sahənin adı, şərtləri və peyk şəkli (`/p/{id}`).
- **Lift düyməsi**: səhifənin aşağısında, bölmədən bölməyə yuxarı və aşağı keçid.
- Keyfiyyət düzəlişləri: dizaynlı 404 səhifəsi, səhv sorğuya 500 əvəzinə izahlı 400, statik faylların keşi, simulyator mətni ("10 il" yazılıb 15 hesablanırdı, indi "Gələcək simulyatoru").

## Təqdimat üçün bir slayd: "Əvvəl və indi"

| Əvvəl (v1) | İndi (v2) |
|---|---|
| 2 növ müraciət edən | 3 hüquqi status + yerli sakin |
| Operator qərar verir | 9 qurumdan komissiya, kvorum, protokol |
| Öz meyarlarımız | Qaydalar 4.10, 0-5 bal |
| Demo rəqəmlər | Nazirliyin rəsmi statistikası |
| "Nə əkim?" sualı açıq qalırdı | Fəaliyyət planı: harada, nə vaxt, nə lazımdır |
