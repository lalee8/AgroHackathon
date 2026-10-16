package az.aqroinvest.seed;

import az.aqroinvest.application.*;
import az.aqroinvest.competition.Competition;
import az.aqroinvest.competition.CompetitionRepository;
import az.aqroinvest.competition.CompetitionStatus;
import az.aqroinvest.competition.Criterion;
import az.aqroinvest.config.AppProperties;
import az.aqroinvest.document.DocumentStorageService;
import az.aqroinvest.integration.ektis.IntegrationEventPublisher;
import az.aqroinvest.notification.Notification;
import az.aqroinvest.notification.NotificationRepository;
import az.aqroinvest.plot.*;
import az.aqroinvest.user.Role;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static az.aqroinvest.plot.PlotCategory.*;

/**
 * Nümayiş üçün real görünüşlü məlumatlar: 11 azad edilmiş rayonda 30 sahə, 5 müsabiqə
 * (müxtəlif statuslarda), investorlar, ekspertlər və bütün statuslarda müraciətlər.
 * Koordinatlar və göstəricilər nümayiş məqsədli təxmini dəyərlərdir.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {
    private final AppProperties props;
    private final UserRepository users;
    private final PlotRepository plots;
    private final CompetitionRepository competitions;
    private final ApplicationRepository applications;
    private final EvaluationRepository evaluations;
    private final NotificationRepository notifications;
    private final DocumentStorageService storage;
    private final ScoringService scoring;
    private final IntegrationEventPublisher events;
    private final ApplicationService applicationService;
    private final PasswordEncoder encoder;
    private final ObjectMapper mapper;

    private final Instant now = Instant.now();
    private final Random rnd = new Random(2026);

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed().enabled()) return;
        if (plots.count() > 0) {
            ensureExtraPlots();
            return;
        }
        log.info("Demo məlumatları yüklənir...");
        Map<String, User> u = seedUsers();
        Map<String, Plot> p = seedPlots();
        Map<String, Competition> c = seedCompetitions(u, p);
        seedApplications(u, p, c);
        ensureExtraPlots();
        log.info("Demo məlumatları hazırdır: {} istifadəçi, {} sahə, {} müsabiqə, {} müraciət",
                users.count(), plots.count(), competitions.count(), applications.count());
    }

    // ------------------------------------------------------------------ users

    private Map<String, User> seedUsers() {
        Map<String, User> m = new LinkedHashMap<>();
        String adminPw = encoder.encode("Admin123!");
        String expertPw = encoder.encode("Ekspert123!");
        String investorPw = encoder.encode("Investor123!");
        m.put("admin", user("admin@aqroinvest.az", "Rəşad Məmmədov", Role.ADMIN, adminPw, "+994 12 555 10 01", null));
        m.put("nazirlik", user("nazirlik@aqroinvest.az", "Leyla Həsənova", Role.ADMIN, adminPw, "+994 12 555 10 02", null));
        m.put("ekspert", user("ekspert@aqroinvest.az", "Dr. Elçin Quliyev", Role.EXPERT, expertPw, "+994 12 555 10 03", null));
        m.put("ekspert2", user("ekspert2@aqroinvest.az", "Nərmin Əliyeva", Role.EXPERT, expertPw, "+994 12 555 10 04", null));
        m.put("investor", user("investor@aqroinvest.az", "Kamran Əhmədov", Role.INVESTOR, investorPw, "+994 50 321 45 67", "5ZK8M2P"));
        String[][] inv = {
                {"aqro.qarabag@example.az", "Tural Babayev", "+994 55 410 22 11", "3HN7QW1"},
                {"araz.vadisi@example.az", "Səbinə Rzayeva", "+994 50 777 18 90", "6TR2LA9"},
                {"zengilan.baglari@example.az", "Elvin Nağıyev", "+994 70 222 35 46", "2MK9XC4"},
                {"serq.bal@example.az", "Aygün Kərimova", "+994 51 600 44 12", "7PL3DN8"},
                {"kur.aqrotex@example.az", "Fərid İsmayılov", "+994 55 908 71 23", "4QW6ER2"},
                {"yasil.vadi@example.az", "Günel Abbasova", "+994 77 315 62 09", "9ZX4CV7"},
                {"fuzuli.taxil@example.az", "Orxan Səfərov", "+994 50 144 89 30", "1AS5DF3"},
                {"qarabag.vineyards@example.az", "Mehmet Kaya", "+90 532 418 27 63", null},
                {"dag.ferma@example.az", "Ramin Cəfərov", "+994 70 581 90 27", "5BN8MQ1"},
        };
        for (int i = 0; i < inv.length; i++) {
            m.put("inv" + i, user(inv[i][0], inv[i][1], Role.INVESTOR, investorPw, inv[i][2], inv[i][3]));
        }
        // Müraciət edənlərin hüquqi statusu: fiziki şəxs (o cümlədən yerli sakin), hüquqi şəxs, xarici vətəndaş
        local(m.get("investor"), District.AGDAM, null);
        company(m.get("inv0"), "Qarabağ Aqro MMC", "MMC", "1500234567", "Direktor");
        company(m.get("inv1"), "Araz Vadisi Fermer Təsərrüfatı MMC", "MMC", "1306789045", "Direktor");
        company(m.get("inv2"), "Zəngilan Bağları MMC", "MMC", "1607890156", "Təsisçi, direktor");
        local(m.get("inv3"), District.LACIN, null);
        company(m.get("inv4"), "Kür Aqrotex ASC", "ASC", "1205678934", "İdarə Heyətinin sədri");
        company(m.get("inv5"), "Yaşıl Vadi MMC", "MMC", "1403456782", "Direktor");
        company(m.get("inv6"), "Füzuli Taxıl MMC", "MMC", "1904567813", "Direktor");
        foreign(m.get("inv7"), "Türkiyə", "U18427639", "Kaya Bağcılık A.Ş.");
        m.get("inv7").setVoen("1708451236"); // Azərbaycandakı filialın VÖEN-i
        users.save(m.get("inv7"));
        local(m.get("inv8"), District.KELBECER, null);
        return m;
    }

    private void local(User u, District home, String voen) {
        u.setLegalType(az.aqroinvest.user.LegalType.INDIVIDUAL);
        u.setLocalResident(true);
        u.setHomeDistrict(home);
        u.setVoen(voen);
        users.save(u);
    }

    private void company(User u, String name, String form, String voen, String position) {
        u.setLegalType(az.aqroinvest.user.LegalType.COMPANY);
        u.setCompanyName(name);
        u.setCompanyForm(form);
        u.setVoen(voen);
        u.setRepPosition(position);
        users.save(u);
    }

    private void foreign(User u, String citizenship, String passport, String companyName) {
        u.setLegalType(az.aqroinvest.user.LegalType.FOREIGN);
        u.setCitizenship(citizenship);
        u.setPassportNo(passport);
        u.setCompanyName(companyName);
        u.setPrefLang("en");
        users.save(u);
    }

    private User user(String email, String name, Role role, String hash, String phone, String fin) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setRole(role);
        if (role == Role.INVESTOR) u.setUserType(az.aqroinvest.user.UserType.INVESTOR);
        u.setPasswordHash(hash);
        u.setPhone(phone);
        u.setFin(fin);
        u.setCreatedAt(now.minus(60 + rnd.nextInt(60), ChronoUnit.DAYS));
        return users.save(u);
    }

    // ------------------------------------------------------------------ plots

    private record P(String code, String name, District d, String village, PlotCategory cat, double area,
                     double lat, double lng, String soil, int quality, String water, boolean irrig, double road,
                     boolean elec, boolean gas, int alt, double slope, DeminingStatus dem, int lease, double rent,
                     long minInv, int jobs, String crops, String desc) {}

    private Map<String, Plot> seedPlots() {
        DeminingStatus C = DeminingStatus.CLEARED, S = DeminingStatus.SURVEYED;
        List<P> list = List.of(
                new P("AGD-001", "Ağdam şimal taxılçılıq massivi", District.AGDAM, "Sarıçoban", EKINCILIK, 420, 40.0599, 46.9459,
                        "Boz-qəhvəyi", 78, "Xaçınçay su anbarı kanalı", true, 2.5, true, true, 311, 1.5, C, 25, 35, 1_200_000, 25,
                        "Buğda|Arpa|Qarğıdalı|Günəbaxan",
                        "Düzənlik ərazidə yerləşən iri taxılçılıq massivi. Xaçınçay su anbarından çəkilmiş suvarma kanalı bərpa edilib, sahəyə asfalt yol və elektrik xətti çəkilib. Ağdam Sənaye Parkına yaxınlıq məhsulun emalı və logistikası üçün üstünlük yaradır."),
                new P("AGD-002", "Papravənd üzüm bağları zonası", District.AGDAM, "Papravənd", UZUMCULUK, 85, 39.9783, 46.8822,
                        "Şabalıdı", 82, "Qarqarçay + artezian quyular", true, 1.8, true, false, 425, 2.7, C, 40, 45, 1_000_000, 40,
                        "Mədrəsə|Bayanşirə|Rkasiteli|Kişmiş",
                        "Tarixən Ağdamın məşhur üzümçülük zonası. Cənub yamaclı, yaxşı drenajlı torpaqlar texniki üzüm sortları üçün idealdır."),
                new P("AGD-003", "Xıdırlı süd-əmtəə ferması sahəsi", District.AGDAM, "Xıdırlı", HEYVANDARLIQ, 160, 40.081, 46.9132,
                        "Boz-qəhvəyi", 70, "Artezian quyu", false, 3.2, true, true, 354, 2.4, C, 30, 25, 1_800_000, 35,
                        "Yonca|Silos qarğıdalı|Çoxillik otlar",
                        "Müasir süd-əmtəə ferması və yem bazası üçün nəzərdə tutulub. Sahədə 2 artezian quyusu qazılıb, qaz və elektrik xəttinə qoşulma mümkündür."),
                new P("AGD-004", "Kəngərli nar və alma bağı", District.AGDAM, "Kəngərli", BAGCILIQ, 60, 39.9543, 46.9132,
                        "Şabalıdı", 80, "Xaçınçay kanalı", true, 4.0, true, false, 407, 3.0, C, 40, 40, 850_000, 30,
                        "Nar|Alma|Armud|Heyva",
                        "İntensiv tipli meyvə bağı üçün əlverişli sahə. Damcı suvarma sisteminin qurulması tövsiyə olunur."),
                new P("AGD-005", "Ağdam sənaye parkı yanında istixana sahəsi", District.AGDAM, "Ağdam şəhəri", ISTIXANA, 25, 39.9666, 46.9595,
                        "Boz-qəhvəyi", 72, "Şəhər su xətti + artezian", true, 0.5, true, true, 341, 2.0, C, 30, 110, 4_000_000, 90,
                        "Pomidor|Xiyar|Bibər|Çiyələk",
                        "Ağdam Sənaye Parkına bitişik ərazi. Qaz, elektrik, su və dəmir yolu logistikasına birbaşa çıxış - ixrac yönümlü istixana kompleksi üçün ideal."),
                new P("FZL-001", "Füzuli şərq düzənliyi taxılçılıq massivi", District.FUZULI, "Qacar", EKINCILIK, 520, 39.58, 47.265,
                        "Boz-qəhvəyi", 76, "Köndələnçay su anbarı", true, 3.0, true, true, 311, 1.9, C, 25, 32, 1_500_000, 30,
                        "Buğda|Arpa|Noxud|Qarğıdalı",
                        "Regionun ən böyük taxılçılıq massivlərindən biri. Köndələnçay su anbarı bərpa edildikdən sonra suvarma tam təmin olunub. Füzuli Beynəlxalq Hava Limanına 7 km."),
                new P("FZL-002", "Dövlətyarlı istixana kompleksi sahəsi", District.FUZULI, "Dövlətyarlı", ISTIXANA, 18, 39.592, 47.238,
                        "Çəmən-boz", 72, "Artezian + yağış suyu toplama", true, 0.8, true, true, 340, 2.6, C, 30, 120, 3_500_000, 80,
                        "Pomidor|Xiyar|Bibər|Badımcan",
                        "Füzuli-Horadiz magistral yolunun və Füzuli hava limanının yaxınlığında düz ərazi. Qaz xətti istixana isitməsi üçün hazırdır."),
                new P("FZL-003", "Qacar üzümçülük zonası", District.FUZULI, "Mərdinli", UZUMCULUK, 110, 39.6703, 47.0098,
                        "Şabalıdı", 79, "Köndələnçay kanalı", true, 2.2, true, false, 572, 4.8, C, 40, 42, 1_200_000, 45,
                        "Mədrəsə|Saperavi|Şirvanşahı|Ağ şanı",
                        "Dağətəyi zonada yerləşən, günəşli və küləkdən qorunan yamaclar. Şərab və süfrə üzümü üçün yüksək potensial."),
                new P("FZL-004", "Merdinli texniki bitkilər massivi", District.FUZULI, "Böyük Bəhmənli", EKINCILIK, 300, 39.545, 47.28,
                        "Boz", 74, "Araz çayından nasos stansiyası", true, 2.8, true, false, 286, 1.6, C, 25, 30, 950_000, 22,
                        "Pambıq|Günəbaxan|Soya|Şəkər çuğunduru",
                        "Texniki bitkilər və növbəli əkin üçün geniş, düz sahə. Pambıq emalı müəssisəsinin yaxınlıqda tikilməsi planlaşdırılır."),
                new P("CBR-001", "Cəbrayıl Araz vadisi taxıl sahəsi", District.CEBRAYIL, "Soltanlı", EKINCILIK, 380, 39.256, 47.03,
                        "Boz-qəhvəyi", 73, "Araz çayı", true, 3.5, true, false, 253, 3.0, C, 25, 28, 1_100_000, 22,
                        "Buğda|Arpa|Qarğıdalı",
                        "Araz çayı boyunca münbit allüvial torpaqlar. Horadiz-Ağbənd dəmir yolu xəttinə yaxınlıq daşınma xərclərini azaldır."),
                new P("CBR-002", "Dağtumas bağçılıq sahəsi", District.CEBRAYIL, "Dağtumas", BAGCILIQ, 95, 39.431, 47.045,
                        "Şabalıdı", 77, "Artezian + damcı suvarma hovuzu", true, 2.0, true, false, 636, 5.4, C, 40, 38, 1_100_000, 40,
                        "Nar|Xurma|Badam|Ərik",
                        "Cəbrayıl \"Dost\" aqroparkı yaxınlığında yerləşən sahə. Subtropik meyvələr üçün əlverişli mikroiqlim."),
                new P("CBR-003", "Horovlu qoyunçuluq kompleksi sahəsi", District.CEBRAYIL, "Horovlu", HEYVANDARLIQ, 240, 39.4557, 47.0739,
                        "Dağ-qəhvəyi", 60, "Artezian quyu", false, 5.0, true, false, 596, 7.7, C, 30, 15, 700_000, 18,
                        "Çoxillik otlar|Yonca|Arpa",
                        "Otlaq və yem sahəsi olan qoyunçuluq kompleksi üçün ərazi. Damazlıq qoyunçuluq üçün dövlət subsidiyası mövcuddur."),
                new P("CBR-004", "Şıxlar üzüm bağları", District.CEBRAYIL, "Şıxlar", UZUMCULUK, 70, 39.296, 47.105,
                        "Şabalıdı", 75, "Araz çayından suvarma xətti", true, 2.6, true, false, 196, 3.5, C, 40, 40, 800_000, 30,
                        "Kişmiş|Ağ şanı|Mədrəsə",
                        "Süfrə üzümü və kişmiş istehsalı üçün tövsiyə olunan sahə."),
                new P("ZNG-001", "Zəngilan subtropik meyvə bağı", District.ZENGILAN, "Ağalı", BAGCILIQ, 75, 39.112, 46.7121,
                        "Qəhvəyi dağ-meşə", 84, "Bəsitçay + Oxçuçay", true, 1.5, true, true, 536, 4.2, C, 45, 45, 1_200_000, 45,
                        "Nar|Əncir|Xurma|Zeytun",
                        "\"Ağıllı kənd\" Ağalı yaxınlığında yerləşir. Zəngilanın subtropik iqlimi nar, əncir və zeytun üçün unikaldır. Zəngilan Beynəlxalq Hava Limanına 10 km."),
                new P("ZNG-002", "Mincivan istixana sahəsi", District.ZENGILAN, "Mincivan", ISTIXANA, 12, 39.0277, 46.7361,
                        "Allüvial", 76, "Bərgüşad çayı + artezian", true, 0.5, true, true, 313, 1.2, C, 30, 115, 2_800_000, 60,
                        "Pomidor|Xiyar|Göyərti|Çiyələk",
                        "Mincivan dəmir yolu stansiyasının yanında. İxrac yönümlü istixana üçün logistika üstünlüyü."),
                new P("ZNG-003", "Bartaz arıçılıq sahəsi", District.ZENGILAN, "Bartaz", ARICILIQ, 15, 39.141, 46.5899,
                        "Dağ-meşə", 65, "Bulaqlar", false, 4.5, false, false, 681, 15.5, C, 20, 10, 90_000, 6,
                        "Akasiya|Cökə|Kəklikotu|Yonca",
                        "Zəngin bal bitkiləri olan dağ meşəsi kənarında. Ekoloji təmiz \"Zəngilan balı\" brendi üçün potensial."),
                new P("QBD-001", "Həkəri vadisi bağçılıq sahəsi", District.QUBADLI, "Xanlıq", BAGCILIQ, 50, 39.3083, 46.6301,
                        "Allüvial-çəmən", 75, "Həkəri çayı", true, 2.4, true, false, 438, 3.4, C, 40, 35, 650_000, 25,
                        "Alma|Armud|Gilas|Qoz",
                        "Həkəri çayı vadisində münbit sahə. Soyuducu anbar tikintisi üçün yer ayrılıb."),
                new P("QBD-002", "Qubadlı dağətəyi heyvandarlıq sahəsi", District.QUBADLI, "Əyin", HEYVANDARLIQ, 180, 39.3883, 46.541,
                        "Dağ-qəhvəyi", 62, "Bulaqlar + su hovuzu", false, 6.0, true, false, 551, 11.4, S, 30, 12, 600_000, 15,
                        "Çoxillik otlar|Yonca",
                        "Mal-qara və qoyunçuluq üçün otlaq sahəsi. Texniki sorğu aparılıb, risk aşkarlanmayıb."),
                new P("QBD-003", "Qubadlı arıçılıq təsərrüfatı", District.QUBADLI, "Muradxanlı", ARICILIQ, 10, 39.3373, 46.497,
                        "Dağ-meşə", 66, "Bulaq", false, 3.8, true, false, 1185, 25.0, C, 20, 10, 70_000, 5,
                        "Akasiya|Şabalıd|Kəklikotu",
                        "Meşə massivi ilə həmsərhəd, 300+ arı ailəsi üçün yer."),
                new P("LCN-001", "Laçın yay otlaqları", District.LACIN, "Qorçu", HEYVANDARLIQ, 600, 39.6767, 46.4898,
                        "Dağ-çəmən", 62, "Həkəri çayının qolları, bulaqlar", false, 7.0, false, false, 1050, 16.9, S, 20, 8, 900_000, 20,
                        "Alp çəmənlikləri|Çoxillik otlar",
                        "Geniş alp və subalp çəmənlikləri - mövsümi (yaylaq) heyvandarlıq üçün. Yeni Laçın-Qorçu yolu ilə əlaqə."),
                new P("LCN-002", "Zabux arıçılıq və dərman bitkiləri sahəsi", District.LACIN, "Zabux", ARICILIQ, 20, 39.6019, 46.5943,
                        "Dağ-çəmən", 68, "Zabux çayı", false, 1.2, true, false, 1136, 15.0, C, 25, 10, 120_000, 8,
                        "Kəklikotu|Dağ nanəsi|Cökə|Yonca",
                        "\"Ağıllı kənd\" Zabux yaxınlığında. Arıçılıq və dərman bitkiləri becərilməsi üçün ekoloji təmiz ərazi."),
                new P("KLB-001", "Kəlbəcər yaylaq otlaqları", District.KELBECER, "İstisu", HEYVANDARLIQ, 850, 40.0583, 45.951,
                        "Dağ-çəmən", 64, "Tərtərçay qolları, bulaqlar", false, 9.0, false, false, 2671, 13.1, S, 15, 6, 900_000, 20,
                        "Alp çəmənlikləri",
                        "Kəlbəcərin məşhur yaylaqları - yay aylarında xırda və iribuynuzlu heyvandarlıq üçün. İstisu mineral bulaqları yaxınlığında."),
                new P("KLB-002", "Tərtərçay vadisi arıçılıq sahəsi", District.KELBECER, "Ağdaban", ARICILIQ, 25, 40.1447, 46.102,
                        "Dağ-meşə", 70, "Tərtərçay", false, 5.0, true, false, 1621, 30.0, C, 20, 8, 110_000, 6,
                        "Kəklikotu|Akasiya|Dağ çiçəkləri",
                        "Yüksək dağlıq zonada ekoloji təmiz bal istehsalı üçün."),
                new P("XCL-001", "Xocalı meyvə bağları sahəsi", District.XOCALI, "Ballıca", BAGCILIQ, 65, 39.9377, 46.7498,
                        "Qəhvəyi dağ-meşə", 78, "Qarqarçay", true, 2.0, true, true, 696, 4.1, C, 40, 38, 800_000, 30,
                        "Alma|Armud|Gavalı|Qoz",
                        "Dağətəyi meyvəçilik zonası. Xocalı hava limanına yaxın - təzə meyvə ixracı üçün imkan."),
                new P("XCL-002", "Xocalı aeroport yanı istixana sahəsi", District.XOCALI, "Xocalı", ISTIXANA, 10, 39.9209, 46.826,
                        "Boz-qəhvəyi", 70, "Artezian", true, 0.7, true, true, 509, 3.2, C, 30, 120, 2_500_000, 55,
                        "Pomidor|Xiyar|Çiyələk|Gül",
                        "Xocalı hava limanına yaxın düz ərazi - tez xarab olan məhsulların aviaixracı üçün."),
                new P("XCV-001", "Hadrut üzüm və şərab zonası", District.XOCAVEND, "Hadrut", UZUMCULUK, 90, 39.4968, 47.1044,
                        "Qəhvəyi dağ-meşə", 81, "Quruçay + artezian", true, 2.8, true, false, 517, 5.5, C, 45, 40, 1_100_000, 40,
                        "Saperavi|Mədrəsə|Xındoğnu|Rkasiteli",
                        "Xındoğnu kimi yerli sortların vətəni. Şərabçılıq zavodu ilə birlikdə layihələr üstünlük təşkil edir."),
                new P("XCV-002", "Tuğ tut və bağçılıq sahəsi", District.XOCAVEND, "Tuğ", BAGCILIQ, 40, 39.5909, 47.0912,
                        "Qəhvəyi", 74, "Quruçay", true, 3.0, true, false, 512, 3.8, C, 40, 32, 500_000, 20,
                        "Tut|Nar|Qoz|Fındıq",
                        "Tut bağları və ipəkçiliyin bərpası üçün sahə - ənənəvi sənətin dirçəldilməsi."),
                new P("TRT-001", "Suqovuşan taxıl və bağ massivi", District.TERTER, "Suqovuşan", EKINCILIK, 210, 40.179, 46.5,
                        "Boz-qəhvəyi", 75, "Sərsəng su anbarı / Tərtərçay", true, 1.6, true, true, 1337, 11.9, C, 25, 30, 700_000, 18,
                        "Buğda|Arpa|Qarğıdalı|Kartof",
                        "Sərsəng su anbarından suvarma - region üçün etibarlı su mənbəyi."),
                new P("TRT-002", "Talış bağçılıq sahəsi", District.TERTER, "Talış", BAGCILIQ, 55, 40.199, 46.584,
                        "Qəhvəyi dağ-meşə", 73, "Tərtərçay", true, 2.5, true, false, 910, 16.3, C, 40, 34, 600_000, 22,
                        "Alma|Fındıq|Qoz|Albalı",
                        "Fındıq və qoz plantasiyaları üçün tövsiyə olunan dağətəyi sahə."),
                new P("SSA-001", "Şuşa dağ arıçılığı sahəsi", District.SUSA, "Daşaltı", ARICILIQ, 12, 39.7373, 46.7802,
                        "Dağ-meşə", 69, "Daşaltı çayı", false, 2.0, true, false, 1271, 19.1, C, 25, 12, 100_000, 6,
                        "Kəklikotu|Cökə|Xarıbülbül bölgəsi florası",
                        "Mədəniyyət paytaxtı Şuşanın yaxınlığında ekoturizmlə birləşdirilə bilən arıçılıq sahəsi."));

        Map<String, Plot> m = new LinkedHashMap<>();
        for (P d : list) m.put(d.code(), plots.save(toPlot(d, LandDesignation.defaultFor(d.cat()), false)));
        return m;
    }

    /**
     * Torpaq təyinatlarını nümayiş etdirən əlavə sahələr: əkinə yararsız yamac (günəş enerjisi), su anbarı sahili
     * (aqroturizm) və sənaye zonası (emal, soyuducu anbar). Mövcud bazaya da əlavə olunur.
     */
    private void ensureExtraPlots() {
        DeminingStatus C = DeminingStatus.CLEARED;
        record X(P p, LandDesignation des, boolean scenic) {}
        List<X> extra = List.of(
                new X(new P("FZL-006", "Horadiz eroziyalı yamacları - günəş və otlaq zonası", District.FUZULI, "Horadiz", HEYVANDARLIQ, 140, 39.470, 47.280,
                        "Eroziyaya uğramış, qismən şoranlaşmış boz torpaq", 24, "Yoxdur (Araz kanalı ~6 km)", false, 2.4, true, false, 269, 2.9, C, 30, 8, 400_000, 6,
                        "Ekstensiv qoyunçuluq|Panellər altında otarma",
                        "Əkin üçün yararsız, eroziyaya uğramış və qismən şoranlaşmış yamaclar. Hazırda yalnız mövsümi otlaq kimi istifadə oluna bilər. "
                                + "Düzənlik relyef, yüksək günəş radiasiyası, Horadiz-Ağbənd yoluna və elektrik xəttinə yaxınlıq sahəni günəş elektrik stansiyası üçün güclü namizəd edir. "
                                + "Panellərin altında qoyun otarmaq (aqrovoltaika) otlağı da qoruyur."),
                        LandDesignation.YARARSIZ, false),
                new X(new P("CBR-006", "Xudafərin su anbarı sahili - bağ və aqroturizm sahəsi", District.CEBRAYIL, "Xudafərin", BAGCILIQ, 18, 39.1772, 46.8885,
                        "Şabalıdı, daşlı", 58, "Xudafərin su anbarı (nasos stansiyası)", true, 0.6, true, false, 321, 8.5, C, 40, 30, 900_000, 18,
                        "Nar|Əncir|Zeytun|Üzüm",
                        "Xudafərin su anbarının şimal sahilində, Horadiz-Zəngilan avtomobil yolunun yanında mənzərəli sahə. Tarixi Xudafərin körpülərinə bir neçə kilometr məsafədədir. "
                                + "Kiçik meyvə bağı ilə yanaşı qonaq evi, yerli mətbəx və bağ turları üçün əlverişlidir."),
                        LandDesignation.COXILLIK, true),
                new X(new P("AGD-006", "Ağdam şərq sənaye zonası - aqro-emal və soyuducu anbar sahəsi", District.AGDAM, "Ağdam şəhəri", ISTIXANA, 12, 40.004, 46.975,
                        "Tikinti üçün hazırlanmış meydança", 50, "Şəhər su xətti", true, 0.3, true, true, 313, 3.2, C, 49, 120, 1_500_000, 25,
                        "Soyuducu anbar|Çeşidləmə və qablaşdırma|Hidroponik istixana",
                        "Bərpa olunan Ağdam şəhərinin şərqində sənaye təyinatlı sahə. Elektrik, qaz və su xətləri yanındadır. "
                                + "Ətrafdakı taxıl, meyvə və üzüm sahələrinin məhsulu üçün soyuducu anbar, çeşidləmə-qablaşdırma xətti və ya torpaqsız istixana üçün nəzərdə tutulub."),
                        LandDesignation.SENAYE, false));
        int added = 0;
        for (X x : extra) {
            if (plots.findByCode(x.p().code()).isPresent()) continue;
            plots.save(toPlot(x.p(), x.des(), x.scenic()));
            added++;
        }
        if (added > 0) log.info("Torpaq təyinatı nümunələri əlavə edildi: {} sahə", added);
    }

    private Plot toPlot(P d, LandDesignation designation, boolean scenic) {
        {
            Plot p = new Plot();
            p.setDesignation(designation);
            p.setScenic(scenic);
            p.setCode(d.code());
            p.setName(d.name());
            p.setDistrict(d.d());
            p.setVillage(d.village());
            p.setCategory(d.cat());
            p.setAreaHa(BigDecimal.valueOf(d.area()));
            p.setLatitude(d.lat());
            p.setLongitude(d.lng());
            p.setPolygon(GeoUtil.polygonFor(d.lat(), d.lng(), p.getAreaHa(), d.code().hashCode()));
            p.setSoilType(d.soil());
            p.setSoilQuality(d.quality());
            p.setWaterSource(d.water());
            p.setIrrigation(d.irrig());
            p.setRoadDistanceKm(BigDecimal.valueOf(d.road()));
            p.setElectricity(d.elec());
            p.setGas(d.gas());
            p.setAltitudeM(d.alt());
            p.setSlopePercent(BigDecimal.valueOf(d.slope()));
            p.setDeminingStatus(d.dem());
            p.setDeminingCertificate(d.dem() == DeminingStatus.CLEARED
                    ? "ANAMA-" + (2023 + Math.abs(d.code().hashCode()) % 3) + "-" + (10000 + Math.abs(d.code().hashCode()) % 89999) : null);
            p.setUsageType(UsageType.LEASE);
            p.setLeaseYears(49); // Qaydalar 4.16: ilkin 5 il, monitorinqdən sonra 44 ilədək uzadılır
            p.setAnnualRentPerHa(BigDecimal.valueOf(d.rent()));
            p.setMinInvestment(BigDecimal.valueOf(d.minInv()));
            p.setMinJobs(d.jobs());
            p.setRecommendedCrops(new ArrayList<>(List.of(d.crops().split("\\|"))));
            p.setDescription(d.desc());
            p.setTerms(String.join("\n",
                    "İcarə müddəti: " + d.lease() + " il (uzadılma hüququ ilə).",
                    "İllik icarə haqqı: " + (int) d.rent() + " AZN/ha; ilk 2 il üçün 50% güzəşt.",
                    "Minimum investisiya öhdəliyi: " + String.format(Locale.ROOT, "%,d", d.minInv()).replace(',', ' ') + " AZN, 36 ay ərzində.",
                    "Ən azı " + d.jobs() + " daimi iş yeri; yerli sakinlərin və məcburi köçkünlərin işə cəlbinə üstünlük.",
                    "Torpaqdan yalnız təyinatı üzrə istifadə; subicarə qadağandır.",
                    "Müasir suvarma və aqrotexnologiyaların tətbiqi tövsiyə olunur."));
            p.setStatus(PlotStatus.AVAILABLE);
            return p;
        }
    }

    // ------------------------------------------------------------------ competitions

    private Map<String, Competition> seedCompetitions(Map<String, User> u, Map<String, Plot> p) {
        Map<String, Competition> m = new LinkedHashMap<>();
        int y = Year.now().getValue();

        m.put("closed", competition("MSB-" + (y - 1) + "-04", "Ağdam və Xocavənd üzümçülük pilot müsabiqəsi",
                "Üzümçülük və şərabçılığın bərpası üzrə ilk pilot müsabiqə. Müsabiqə yekunlaşıb, qaliblər müəyyən edilib.",
                now.minus(220, ChronoUnit.DAYS), now.minus(160, ChronoUnit.DAYS), CompetitionStatus.CLOSED,
                u.get("admin"), u.get("nazirlik"), List.of(p.get("AGD-002"), p.get("XCV-001"))));

        m.put("main", competition("MSB-" + y + "-01", "Qarabağ aqrar investisiya müsabiqəsi - " + y + " (I mərhələ)",
                "Qarabağ iqtisadi rayonunda kənd təsərrüfatı təyinatlı torpaq sahələrinin uzunmüddətli icarəyə verilməsi üzrə açıq müsabiqə. "
                        + "Məqsəd - taxılçılıq, üzümçülük, bağçılıq və istixana təsərrüfatlarına özəl investisiyaların cəlb edilməsi, "
                        + "yeni iş yerlərinin yaradılması və müasir aqrotexnologiyaların tətbiqidir.",
                now.minus(20, ChronoUnit.DAYS), now.plus(25, ChronoUnit.DAYS), CompetitionStatus.PUBLISHED,
                u.get("admin"), u.get("nazirlik"),
                List.of(p.get("AGD-001"), p.get("AGD-003"), p.get("AGD-004"), p.get("AGD-005"), p.get("FZL-001"),
                        p.get("FZL-002"), p.get("FZL-003"), p.get("FZL-004"), p.get("CBR-001"), p.get("CBR-002"),
                        p.get("CBR-004"), p.get("XCL-001"), p.get("XCL-002"), p.get("XCV-002"))));

        m.put("east", competition("MSB-" + y + "-02", "Şərqi Zəngəzur: bağçılıq, istixana və arıçılıq müsabiqəsi",
                "Zəngilan, Qubadlı, Laçın və Kəlbəcər rayonlarında subtropik meyvəçilik, istixana təsərrüfatı və ekoloji təmiz "
                        + "arıçılığın inkişafı üçün sahələr. \"Yaşıl enerji zonası\" konsepsiyasına uyğun layihələrə əlavə bal verilir.",
                now.minus(6, ChronoUnit.DAYS), now.plus(40, ChronoUnit.DAYS), CompetitionStatus.PUBLISHED,
                u.get("nazirlik"), u.get("admin"),
                List.of(p.get("ZNG-001"), p.get("ZNG-002"), p.get("ZNG-003"), p.get("QBD-001"), p.get("QBD-003"),
                        p.get("LCN-002"), p.get("KLB-002"), p.get("SSA-001"), p.get("CBR-003"))));

        Competition pending = competition("MSB-" + y + "-03", "Dağ otlaqlarında heyvandarlıq - pilot müsabiqə",
                "Laçın, Kəlbəcər və Qubadlının yay otlaqlarında mövsümi heyvandarlığın bərpası. Təsdiq gözləyir.",
                now.plus(3, ChronoUnit.DAYS), now.plus(60, ChronoUnit.DAYS), CompetitionStatus.PENDING_APPROVAL,
                u.get("admin"), null, List.of(p.get("LCN-001"), p.get("KLB-001"), p.get("QBD-002")));
        m.put("pending", pending);

        m.put("draft", competition("MSB-" + y + "-05", "Tərtər su anbarı ətrafı əkinçilik və bağçılıq müsabiqəsi",
                "Sərsəng su anbarının suvarma zonasında yerləşən sahələr üzrə müsabiqə layihəsi.",
                now.plus(15, ChronoUnit.DAYS), now.plus(75, ChronoUnit.DAYS), CompetitionStatus.DRAFT,
                u.get("nazirlik"), null, List.of(p.get("TRT-001"), p.get("TRT-002"))));

        for (String k : List.of("main", "east")) {
            m.get(k).getPlots().forEach(pl -> pl.setStatus(PlotStatus.IN_COMPETITION));
        }
        return m;
    }

    private Competition competition(String code, String title, String desc, Instant start, Instant deadline,
                                    CompetitionStatus status, User creator, User approver, List<Plot> ps) {
        Competition c = new Competition();
        c.setCode(code);
        c.setTitle(title);
        c.setDescription(desc);
        c.setStartAt(start);
        c.setDeadlineAt(deadline);
        c.setStatus(status);
        c.setCreatedBy(creator);
        if (approver != null) {
            c.setApprovedBy(approver);
            c.setApprovedAt(start.minus(2, ChronoUnit.DAYS));
        }
        try {
            c.setCriteria(mapper.writeValueAsString(Criterion.defaults()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        c.setPlots(new LinkedHashSet<>(ps));
        return competitions.save(c);
    }

    // ------------------------------------------------------------------ applications

    private record A(String user, String comp, String plot, ApplicantType type, String company, String voen,
                     long investment, int ownFunds, int jobs, int months, int exp, List<String> tech, boolean idp,
                     long revenue, ApplicationStatus status, Decision decision, int daysAgo, String note) {}

    private void seedApplications(Map<String, User> u, Map<String, Plot> p, Map<String, Competition> c) {
        ApplicantType L = ApplicantType.LEGAL, I = ApplicantType.INDIVIDUAL, F = ApplicantType.FOREIGN;
        ApplicationStatus SUB = ApplicationStatus.SUBMITTED, REV = ApplicationStatus.UNDER_REVIEW,
                INFO = ApplicationStatus.INFO_REQUESTED, DEC = ApplicationStatus.DECIDED, WD = ApplicationStatus.WITHDRAWN;
        List<A> list = List.of(
                // Bağlanmış 2025 müsabiqəsi - qərarlar verilib
                new A("inv7", "closed", "AGD-002", F, "Kaya Bağcılık A.Ş.", null, 2_400_000, 55, 62, 30, 12,
                        List.of("DRIP_IRRIGATION", "PROCESSING", "SOLAR", "COLD_STORAGE"), false, 1_350_000, DEC, Decision.APPROVED, 175,
                        "Komissiyanın yekdil qərarı ilə qalib elan edildi."),
                new A("inv0", "closed", "AGD-002", L, "Qarabağ Aqro MMC", "1500234567", 1_150_000, 35, 42, 36, 6,
                        List.of("DRIP_IRRIGATION"), false, 600_000, DEC, Decision.REJECTED, 178,
                        "Yekun bal qalib təklifdən aşağıdır. Növbəti müsabiqələrdə iştirak tövsiyə olunur."),
                new A("investor", "closed", "XCV-001", I, null, null, 1_650_000, 60, 48, 24, 9,
                        List.of("DRIP_IRRIGATION", "ORGANIC", "PROCESSING", "IOT_SENSORS"), true, 980_000, DEC, Decision.APPROVED, 172,
                        "Qalib elan edildi. Torpaq icarəsi müqaviləsinin imzalanması üçün 10 iş günü ərzində Nazirliyə müraciət edin."),
                new A("inv5", "closed", "XCV-001", L, "Yaşıl Vadi MMC", "1403456782", 1_200_000, 30, 41, 36, 3,
                        List.of(), false, 0, DEC, Decision.REJECTED, 170, "Biznes plan kifayət qədər əsaslandırılmayıb."),

                // Əsas müsabiqə (aktiv)
                new A("inv6", "main", "FZL-001", L, "Füzuli Taxıl MMC", "1904567813", 2_100_000, 50, 38, 18, 15,
                        List.of("PRECISION_AG", "DRONES", "SOLAR"), true, 1_100_000, REV, null, 18, null),
                new A("investor", "main", "FZL-001", I, null, null, 1_650_000, 45, 32, 24, 9,
                        List.of("DRIP_IRRIGATION", "PRECISION_AG", "IOT_SENSORS"), true, 820_000, SUB, null, 3, null),
                new A("inv4", "main", "AGD-001", L, "Kür Aqrotex ASC", "1205678934", 1_900_000, 65, 34, 20, 20,
                        List.of("PRECISION_AG", "IOT_SENSORS", "DRONES", "WATER_RECYCLING"), false, 1_050_000, REV, null, 15, null),
                new A("inv0", "main", "AGD-001", L, "Qarabağ Aqro MMC", "1500234567", 1_300_000, 40, 26, 24, 6,
                        List.of("DRIP_IRRIGATION"), true, 640_000, SUB, null, 9, null),
                new A("inv5", "main", "AGD-005", L, "Yaşıl Vadi MMC", "1403456782", 5_200_000, 45, 120, 18, 7,
                        List.of("CLIMATE_CONTROL", "SOLAR", "WATER_RECYCLING", "COLD_STORAGE"), false, 3_400_000, REV, null, 14, null),
                new A("inv1", "main", "FZL-002", L, "Araz Vadisi Fermer Təsərrüfatı MMC", "1306789045", 3_900_000, 35, 85, 24, 5,
                        List.of("CLIMATE_CONTROL", "DRIP_IRRIGATION"), true, 2_300_000, INFO, null, 12,
                        "Bank arayışını və istixana layihəsinin texniki sxemini əlavə edin."),
                new A("inv8", "main", "AGD-003", I, null, null, 2_000_000, 50, 40, 30, 14,
                        List.of("SOLAR", "COLD_STORAGE", "PROCESSING"), true, 1_250_000, SUB, null, 6, null),
                new A("inv2", "main", "CBR-002", L, "Zəngilan Bağları MMC", "1607890156", 1_400_000, 55, 46, 30, 8,
                        List.of("DRIP_IRRIGATION", "ORGANIC", "COLD_STORAGE"), true, 760_000, REV, null, 11, null),
                new A("inv7", "main", "FZL-003", F, "Kaya Bağcılık A.Ş.", null, 1_600_000, 60, 55, 24, 12,
                        List.of("DRIP_IRRIGATION", "PROCESSING", "SOLAR"), false, 900_000, SUB, null, 4, null),
                new A("inv4", "main", "CBR-001", L, "Kür Aqrotex ASC", "1205678934", 1_250_000, 60, 24, 18, 20,
                        List.of("PRECISION_AG"), false, 640_000, SUB, null, 2, null),
                new A("inv6", "main", "FZL-004", L, "Füzuli Taxıl MMC", "1904567813", 1_000_000, 40, 22, 24, 15,
                        List.of(), false, 500_000, WD, null, 16, null),
                new A("inv0", "main", "XCL-002", L, "Qarabağ Aqro MMC", "1500234567", 2_700_000, 30, 58, 20, 6,
                        List.of("CLIMATE_CONTROL", "SOLAR"), true, 1_600_000, SUB, null, 1, null),

                // Şərqi Zəngəzur müsabiqəsi (aktiv)
                new A("inv2", "east", "ZNG-001", L, "Zəngilan Bağları MMC", "1607890156", 1_800_000, 55, 60, 30, 8,
                        List.of("DRIP_IRRIGATION", "ORGANIC", "IOT_SENSORS", "COLD_STORAGE"), true, 1_000_000, REV, null, 5, null),
                new A("investor", "east", "ZNG-001", I, null, null, 1_350_000, 40, 47, 36, 9,
                        List.of("DRIP_IRRIGATION", "SOLAR"), true, 700_000, INFO, null, 4,
                        "Öz vəsaitinizin mənbəyini təsdiq edən bank arayışını yükləyin və iş yerlərinin bölgüsünü dəqiqləşdirin."),
                new A("inv3", "east", "ZNG-003", I, null, null, 140_000, 70, 8, 12, 11,
                        List.of("ORGANIC"), true, 95_000, SUB, null, 3, null),
                new A("inv3", "east", "LCN-002", I, null, null, 160_000, 65, 9, 12, 11,
                        List.of("ORGANIC", "SOLAR"), true, 110_000, SUB, null, 2, null),
                new A("inv8", "east", "CBR-003", I, null, null, 820_000, 50, 20, 24, 14,
                        List.of("SOLAR", "WATER_RECYCLING"), true, 420_000, SUB, null, 1, null));

        // Göndərilmə tarixinə görə sıralayaraq ardıcıl nömrələr veririk
        List<A> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingInt(A::daysAgo).reversed());
        Map<String, Application> byKey = new HashMap<>();
        for (A d : sorted) {
            Application a = buildApplication(d, u, p, c);
            byKey.put(d.user() + d.plot(), a);
        }

        // Demo investorun yarımçıq qaralaması
        Application draft = new Application();
        User inv = u.get("investor");
        draft.setApplicant(inv);
        draft.setCompetition(c.get("main"));
        draft.setPlot(p.get("FZL-002"));
        draft.setApplicantType(ApplicantType.INDIVIDUAL);
        draft.setFin(inv.getFin());
        draft.setContactEmail(inv.getEmail());
        draft.setContactPhone(inv.getPhone());
        draft.setActivityType(ISTIXANA);
        draft.setInvestmentAmount(BigDecimal.valueOf(3_600_000));
        draft.setJobsPlanned(82);
        draft.setTrackingCode(ApplicationService.randomCode(8));
        draft.setStatus(ApplicationStatus.DRAFT);
        draft.setCreatedAt(now.minus(1, ChronoUnit.DAYS));
        applications.save(draft);
        history(draft, null, ApplicationStatus.DRAFT, "Qaralama yaradıldı", inv.getFullName(), now.minus(1, ChronoUnit.DAYS));

        // Ekspert qiymətləndirmələri
        evaluate(byKey.get("inv6FZL-001"), u.get("ekspert"), Map.of("investment", 9, "jobs", 8, "businessPlan", 9, "innovation", 8, "ownFunds", 8, "documents", 9, "social", 10), "Güclü təcrübə, texnoloji yanaşma nümunəvidir.");
        evaluate(byKey.get("inv6FZL-001"), u.get("ekspert2"), Map.of("investment", 8, "jobs", 8, "businessPlan", 8, "innovation", 9, "ownFunds", 7, "documents", 9, "social", 10), "Tövsiyə olunur.");
        evaluate(byKey.get("inv4AGD-001"), u.get("ekspert"), Map.of("investment", 8, "jobs", 7, "businessPlan", 9, "innovation", 10, "ownFunds", 9, "documents", 8, "social", 4), "Dəqiq əkinçilik texnologiyaları baxımından ən güclü təklif.");
        evaluate(byKey.get("inv5AGD-005"), u.get("ekspert2"), Map.of("investment", 10, "jobs", 10, "businessPlan", 7, "innovation", 9, "ownFunds", 7, "documents", 7, "social", 4), "İxrac planı ətraflı izah edilməlidir.");
        evaluate(byKey.get("inv2ZNG-001"), u.get("ekspert"), Map.of("investment", 8, "jobs", 9, "businessPlan", 8, "innovation", 9, "ownFunds", 8, "documents", 8, "social", 10), "Subtropik bağçılıq üçün uyğun layihə.");
        evaluate(byKey.get("inv7AGD-002"), u.get("ekspert"), Map.of("investment", 10, "jobs", 9, "businessPlan", 9, "innovation", 9, "ownFunds", 8, "documents", 10, "social", 4), "Qalib təklif.");
        evaluate(byKey.get("investorXCV-001"), u.get("ekspert2"), Map.of("investment", 9, "jobs", 9, "businessPlan", 8, "innovation", 9, "ownFunds", 9, "documents", 9, "social", 10), "Yerli sortların bərpası baxımından dəyərli layihə.");

        // Qaliblərin sahələri
        p.get("AGD-002").setStatus(PlotStatus.AWARDED);
        p.get("XCV-001").setStatus(PlotStatus.AWARDED);

        // Demo investor üçün bildirişlər
        Application info = byKey.get("investorZNG-001");
        notification(inv.getId(), "STATUS", "Əlavə məlumat tələb olunur",
                info.getAppNumber() + ": Öz vəsaitinizin mənbəyini təsdiq edən bank arayışını yükləyin.",
                "/muraciet-detal.html?id=" + info.getId(), false, 2);
        Application sub = byKey.get("investorFZL-001");
        notification(inv.getId(), "STATUS", "Müraciətiniz qəbul edildi",
                "Müraciət nömrəsi: " + sub.getAppNumber() + ". İzləmə kodu: " + sub.getTrackingCode(),
                "/muraciet-detal.html?id=" + sub.getId(), false, 3);
        notification(inv.getId(), "COMPETITION", "Yeni müsabiqə elan edildi: Şərqi Zəngəzur",
                "9 sahə, bağçılıq, istixana və arıçılıq", "/musabiqeler.html", true, 6);
        Application won = byKey.get("investorXCV-001");
        notification(inv.getId(), "STATUS", "Təbriklər! Müraciətiniz təsdiqləndi",
                won.getAppNumber() + ": Hadrut üzüm və şərab zonası üzrə qalib elan edildiniz.",
                "/muraciet-detal.html?id=" + won.getId(), true, 160);
        notification(u.get("nazirlik").getId(), "APPROVAL", "Müsabiqə təsdiq gözləyir",
                "Dağ otlaqlarında heyvandarlıq - pilot müsabiqə", "/admin.html#competitions", false, 1);
    }

    private Application buildApplication(A d, Map<String, User> u, Map<String, Plot> p, Map<String, Competition> c) {
        User applicant = u.get(d.user());
        Plot plot = p.get(d.plot());
        Instant submitted = now.minus(d.daysAgo(), ChronoUnit.DAYS).minus(rnd.nextInt(8), ChronoUnit.HOURS);
        Application a = new Application();
        a.setApplicant(applicant);
        a.setCompetition(c.get(d.comp()));
        a.setPlot(plot);
        a.setApplicantType(d.type());
        a.setCompanyName(d.company());
        // Qaydalar 2.4.6: fiziki şəxs də vergi ödəyicisi kimi qeydiyyatda olmalıdır (fərdi sahibkar VÖEN-i)
        a.setVoen(d.voen() != null ? d.voen() : applicant.getVoen() != null ? applicant.getVoen()
                : String.format(Locale.ROOT, "%010d", Math.floorMod(applicant.getEmail().hashCode(), 1_000_000_000L) + 1_000_000_000L).substring(0, 10));
        a.setFin(applicant.getFin());
        a.setCitizenship(d.type() == ApplicantType.FOREIGN ? applicant.getCitizenship() : null);
        a.setPassportNo(d.type() == ApplicantType.FOREIGN ? applicant.getPassportNo() : null);
        a.setContactPhone(applicant.getPhone());
        a.setContactEmail(applicant.getEmail());
        a.setLegalAddress(d.type() == ApplicantType.LEGAL ? "Bakı şəhəri, Nəsimi rayonu, " + (10 + rnd.nextInt(150)) + " saylı bina"
                : d.type() == ApplicantType.FOREIGN ? "İzmir, Türkiyə" : "Bakı şəhəri, Yasamal rayonu");
        a.setActivityType(plot.getCategory());
        a.setInvestmentAmount(BigDecimal.valueOf(d.investment()));
        a.setOwnFundsPercent(d.ownFunds());
        a.setJobsPlanned(d.jobs());
        a.setImplementationMonths(d.months());
        a.setExperienceYears(d.exp());
        a.setTechnologies(new ArrayList<>(d.tech()));
        a.setIdpStatus(d.idp());
        a.setExpectedAnnualRevenue(d.revenue() > 0 ? BigDecimal.valueOf(d.revenue()) : null);
        a.setBusinessPlan(businessPlan(d, plot));
        a.setConsentGiven(true);
        a.setTrackingCode(ApplicationService.randomCode(8));
        a.setCreatedAt(submitted.minus(2, ChronoUnit.DAYS));
        a.setStatus(ApplicationStatus.DRAFT);
        applications.save(a);

        // Sənədlər
        String who = d.company() != null ? d.company() : applicant.getFullName();
        addDoc(a, DocumentType.BUSINESS_PLAN, "biznes-plan-" + plot.getCode().toLowerCase() + ".pdf", "Biznes plan - " + who,
                List.of("Sahe: " + plot.getCode() + " - " + plot.getName(), "Investisiya: " + d.investment() + " AZN",
                        "Is yerleri: " + d.jobs(), "Icra muddeti: " + d.months() + " ay"), submitted);
        addDoc(a, DocumentType.IDENTITY, d.type() == ApplicantType.LEGAL ? "nizamname.pdf" : d.type() == ApplicantType.FOREIGN ? "pasport-notarial-tercume.pdf" : "sexsiyyet-vesiqesi.pdf",
                d.type() == ApplicantType.LEGAL ? "Nizamname - " + who : d.type() == ApplicantType.FOREIGN ? "Pasport (notarial tercume ile) - " + applicant.getFullName()
                        : "Sexsiyyet vesiqesi (surət)", List.of(who), submitted);
        // Qaydalar 2.4.6: VÖEN və vergi borcu arayışı, maliyyə hesabatı və bank dövriyyəsi, təcrübə, iştirak haqqı
        addDoc(a, DocumentType.TAX_CERTIFICATE, "vergi-arayisi.pdf", "VOEN ve vergi borcunun olmamasi haqqinda arayis",
                List.of("VOEN: " + a.getVoen(), "Vergi borcu yoxdur, riskli vergi odeyicisi deyil"), submitted);
        addDoc(a, DocumentType.BANK_REFERENCE, "maliyye-hesabati-bank-dovriyyesi.pdf", "Maliyye hesabati ve bank dovriyyesi",
                List.of("Son 12 ayin bank dovriyyesi", "Oz vesaiti: " + d.ownFunds() + "%"), submitted);
        addDoc(a, DocumentType.EXPERIENCE_PROOF, "tecrube.pdf", "Tecrube haqqinda arayis", List.of(d.exp() + " il fealiyyet"), submitted);
        addDoc(a, DocumentType.FEE_RECEIPT, "istirak-haqqi-qebz.pdf", "Istirak haqqi: 50 AZN", List.of("Kend Teserrufati Nazirliyinin hesabina", "Mebleg: 50 AZN"), submitted);

        // Göndərilmə
        a.setAppNumber(String.format("AQR-%d-%06d", Year.from(java.time.LocalDate.ofInstant(submitted, java.time.ZoneOffset.UTC)).getValue(),
                applications.nextApplicationNumber()));
        a.setSubmittedAt(submitted);
        a.setStatus(ApplicationStatus.SUBMITTED);
        scoring.apply(a);
        String taxId = d.voen() != null ? d.voen() : applicant.getFin() != null ? applicant.getFin() : applicant.getPassportNo();
        a.setEktisFarmerStatus(Math.abs(java.util.Objects.toString(taxId, applicant.getEmail()).hashCode()) % 10 < 6 ? "REGISTERED" : "NOT_REGISTERED");
        history(a, ApplicationStatus.DRAFT, ApplicationStatus.SUBMITTED,
                "Müraciət elektron qaydada qəbul edildi. Nömrə: " + a.getAppNumber(), applicant.getFullName(), submitted);
        events.publish("APPLICATION_SUBMITTED", "Application", a.getId(), applicationService.exchangePayload(a));

        String admin = "Rəşad Məmmədov (Nazirlik operatoru)";
        Instant t = submitted;
        if (d.status() == ApplicationStatus.UNDER_REVIEW || d.status() == ApplicationStatus.INFO_REQUESTED
                || d.status() == ApplicationStatus.DECIDED) {
            t = t.plus(1, ChronoUnit.DAYS).plus(rnd.nextInt(5), ChronoUnit.HOURS);
            if (t.isAfter(now)) t = now.minus(1, ChronoUnit.HOURS);
            history(a, ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW, "İlkin yoxlama uğurla tamamlandı. Komissiyaya göndərildi.", admin, t);
            a.setStatus(ApplicationStatus.UNDER_REVIEW);
        }
        if (d.status() == ApplicationStatus.INFO_REQUESTED) {
            t = t.plus(1, ChronoUnit.DAYS);
            if (t.isAfter(now)) t = now.minus(30, ChronoUnit.MINUTES);
            history(a, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.INFO_REQUESTED, d.note(), admin, t);
            a.setStatus(ApplicationStatus.INFO_REQUESTED);
        }
        if (d.status() == ApplicationStatus.DECIDED) {
            t = t.plus(20 + rnd.nextInt(10), ChronoUnit.DAYS);
            history(a, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.DECIDED, d.decision().label() + ". " + d.note(),
                    "Leyla Həsənova (Nazirlik operatoru)", t);
            a.setStatus(ApplicationStatus.DECIDED);
            a.setDecision(d.decision());
            a.setDecisionNote(d.note());
            a.setDecidedAt(t);
            a.setEktisReference("EKTIS-" + (Year.now().getValue() - 1) + "-" + (500 + rnd.nextInt(400)));
        }
        if (d.status() == ApplicationStatus.WITHDRAWN) {
            t = t.plus(2, ChronoUnit.DAYS);
            history(a, ApplicationStatus.SUBMITTED, ApplicationStatus.WITHDRAWN, "Müraciət investor tərəfindən geri çağırıldı",
                    applicant.getFullName(), t);
            a.setStatus(ApplicationStatus.WITHDRAWN);
        }
        return a;
    }

    private void addDoc(Application a, DocumentType type, String name, String title, List<String> lines, Instant when) {
        DocumentStorageService.StoredFile f = storage.store(SamplePdf.create(title, lines), name);
        ApplicationDocument d = new ApplicationDocument();
        d.setApplication(a);
        d.setDocType(type);
        d.setOriginalName(f.originalName());
        d.setStoredName(f.storedName());
        d.setContentType(f.contentType());
        d.setSizeBytes(f.size());
        d.setSha256(f.sha256());
        d.setUploadedBy(a.getApplicant());
        d.setUploadedAt(when.minus(3, ChronoUnit.HOURS));
        a.getDocuments().add(d);
    }

    private void history(Application a, ApplicationStatus from, ApplicationStatus to, String comment, String actor, Instant at) {
        ApplicationStatusHistory h = new ApplicationStatusHistory();
        h.setApplication(a);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setComment(comment);
        h.setActorName(actor);
        h.setCreatedAt(at);
        a.getHistory().add(h);
    }

    private void evaluate(Application a, User expert, Map<String, Integer> scores, String comment) {
        Evaluation e = new Evaluation();
        e.setApplication(a);
        e.setExpert(expert);
        try {
            e.setScores(mapper.writeValueAsString(new TreeMap<>(scores)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        e.setTotal(scoring.expertTotal(scores, Criterion.defaults()));
        e.setComment(comment);
        e.setCreatedAt(now.minus(1 + rnd.nextInt(3), ChronoUnit.DAYS));
        evaluations.save(e);
    }

    private void notification(Long userId, String type, String title, String msg, String link, boolean read, int daysAgo) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(msg);
        n.setLink(link);
        n.setRead(read);
        n.setCreatedAt(now.minus(daysAgo, ChronoUnit.DAYS));
        notifications.save(n);
    }

    private static String businessPlan(A d, Plot plot) {
        String who = d.company() != null ? d.company() : "Fərdi sahibkar";
        String product = plot.getCategory().product().toLowerCase(Locale.ROOT);
        return who + " " + plot.getDistrict().label() + " rayonunun " + plot.getVillage() + " ərazisində yerləşən "
                + plot.getAreaHa().stripTrailingZeros().toPlainString() + " ha sahədə müasir " + plot.getCategory().label().toLowerCase(Locale.ROOT)
                + " təsərrüfatı yaratmağı planlaşdırır. Layihə çərçivəsində " + product + " istehsalı, "
                + (d.tech().isEmpty() ? "ənənəvi aqrotexniki üsullar" : "müasir aqrotexnologiyalar (" + d.tech().size() + " texnoloji həll)")
                + " tətbiq olunacaq. Ümumi investisiya " + String.format(Locale.ROOT, "%,d", d.investment()).replace(',', ' ')
                + " AZN təşkil edir, bunun " + d.ownFunds() + "%-i öz vəsaiti, qalan hissəsi isə Aqrar Kredit və İnkişaf Agentliyinin "
                + "güzəştli kreditləri hesabına maliyyələşdiriləcək. Layihənin icrası " + d.months() + " ay ərzində mərhələlərlə "
                + "həyata keçiriləcək: 1) torpağın hazırlanması və suvarma infrastrukturu; 2) əkin/tikinti işləri; 3) istehsalın başlanması "
                + "və satış kanallarının qurulması. " + d.jobs() + " daimi iş yeri yaradılacaq, işçilərin əksəriyyəti region sakinləri "
                + "və keçmiş məcburi köçkünlər arasından seçiləcək. Məhsulun daxili bazarda və ixrac yönümlü satışı nəzərdə tutulur.";
    }
}
