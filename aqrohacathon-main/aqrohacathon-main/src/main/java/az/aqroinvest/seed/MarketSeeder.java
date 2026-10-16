package az.aqroinvest.seed;

import az.aqroinvest.application.Application;
import az.aqroinvest.application.ApplicationRepository;
import az.aqroinvest.buyer.*;
import az.aqroinvest.buyer.BuyerEnums.*;
import az.aqroinvest.config.AppProperties;
import az.aqroinvest.market.*;
import az.aqroinvest.market.MarketEnums.*;
import az.aqroinvest.plot.District;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import az.aqroinvest.user.Role;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Bazar modulunun demo məlumatları: sahibkar layihələri, investor maraqları, alıcılar, təsdiqlənmiş tələblər və
 * niyyət məktubları. Əsas demo məlumatlarından sonra işləyir və mövcud bazaya da əlavə olunur (öz cədvəlləri boşdursa).
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class MarketSeeder implements CommandLineRunner {
    private final AppProperties props;
    private final UserRepository users;
    private final PlotRepository plots;
    private final ApplicationRepository applications;
    private final ProjectRepository projects;
    private final ProjectInterestRepository interests;
    private final InvestorProfileRepository profiles;
    private final EntrepreneurProfileRepository entrepreneurProfiles;
    private final BuyerDemandRepository demands;
    private final LetterOfIntentRepository letters;
    private final PasswordEncoder encoder;
    private final Instant now = Instant.now();

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed().enabled() || plots.count() == 0) return;
        Map<String, BuyerDemand> d = demands.count() == 0 ? seedBuyers() : null;
        Map<String, Project> p = projects.count() == 0 ? seedProjects() : null;
        if (d != null && p != null) seedLetters(d, p);
        if (d != null || p != null) log.info("Bazar demo məlumatları: {} layihə, {} alıcı tələbi", projects.count(), demands.count());
    }

    // ------------------------------------------------------------------ alıcılar

    private Map<String, BuyerDemand> seedBuyers() {
        String pw = encoder.encode("Alici123!");
        User a = buyer("alici@aqroinvest.az", "Nicat Əlizadə", "+994 50 612 33 40", pw);
        User t = buyer("araz.taxil@example.az", "Samir Qasımov", "+994 55 301 77 25", pw);
        User w = buyer("xanbagi.serab@example.az", "Leyla Novruzova", "+994 50 845 19 02", pw);
        User x = buyer("xezer.ixrac@example.az", "Emil Rüstəmov", "+994 70 455 60 81", pw);
        User b = buyer("dag.bali@example.az", "Aysel Məmmədova", "+994 51 270 48 36", pw);
        User s = buyer("qarabag.sud@example.az", "Rauf Kazımov", "+994 55 733 21 90", pw);
        Map<String, BuyerDemand> m = new LinkedHashMap<>();
        m.put("NAR", demand(a, "Qarabağ Meyvə Emalı MMC", BuyerType.EMAL, Product.NAR, 3000, 550, 680, 5, District.AGDAM, 140,
                "Kalibr 250 q və yuxarı. Şirə üçün 2-ci sort da qəbul olunur. Məhsul sahədən götürülür.", "Oktyabr-noyabr", DemandStatus.VERIFIED, 40));
        m.put("ALMA", demand(a, "Qarabağ Meyvə Emalı MMC", BuyerType.EMAL, Product.ALMA, 1500, 450, 560, 3, District.AGDAM, 140,
                "Şirə və püre üçün. Pestisid qalığı analizi tələb olunur.", "Sentyabr-oktyabr", DemandStatus.VERIFIED, 32));
        m.put("BUGDA", demand(t, "Araz Taxıl Terminalı ASC", BuyerType.EMAL, Product.BUGDA, 20000, 430, 470, 3, District.FUZULI, 160,
                "Ərzaqlıq buğda: zülal 12.5%-dən az olmamaqla, nəmlik 14%-dən çox olmamaqla. Daşınma alıcının hesabına.", "İyul-avqust", DemandStatus.VERIFIED, 55));
        m.put("ARPA", demand(t, "Araz Taxıl Terminalı ASC", BuyerType.EMAL, Product.ARPA, 6000, 360, 400, 2, District.FUZULI, 160,
                "Yemlik arpa.", "İyul", DemandStatus.PENDING, 3));
        m.put("UZUM", demand(w, "Xan Bağı Şərab Evi MMC", BuyerType.EMAL, Product.UZUM_TEXNIKI, 1200, 700, 860, 7, District.AGDAM, 150,
                "Mədrəsə, Rkasiteli, Bayanşirə sortları. Şəkərlilik 20%-dən az olmamalı. Uzunmüddətli müqavilə.", "Sentyabr", DemandStatus.VERIFIED, 28));
        m.put("POMIDOR", demand(x, "Xəzər Təzə Məhsul İxracat MMC", BuyerType.IXRAC, Product.POMIDOR, 2500, 950, 1250, 3, District.AGDAM, 200,
                "İxrac keyfiyyəti: kalibr 57-67 mm, GlobalGAP sertifikatı üstünlükdür. Qablaşdırma alıcının hesabına.", "Noyabr-may", DemandStatus.VERIFIED, 21));
        m.put("XIYAR", demand(x, "Xəzər Təzə Məhsul İxracat MMC", BuyerType.IXRAC, Product.XIYAR, 800, 700, 950, 2, District.AGDAM, 200,
                "Qısameyvəli sortlar.", "Dekabr-aprel", DemandStatus.PENDING, 2));
        m.put("BAL", demand(b, "Dağ Balı Kooperativi", BuyerType.PERAKENDE, Product.BAL, 35, 19000, 24000, 3, District.LACIN, 200,
                "Təbii dağ balı, laboratoriya analizi ilə. Qablaşdırılmamış qəbul olunur.", "Avqust-sentyabr", DemandStatus.VERIFIED, 18));
        m.put("SUD", demand(s, "Qarabağ Süd Məhsulları MMC", BuyerType.EMAL, Product.SUD, 4000, 650, 760, 5, District.AGDAM, 150,
                "Gündəlik toplama, soyuducu çənlə. Yağlılıq 3.4%-dən az olmamalı.", "İl boyu", DemandStatus.VERIFIED, 26));
        return m;
    }

    private User buyer(String email, String name, String phone, String pw) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFullName(name);
            u.setRole(Role.BUYER);
            u.setPasswordHash(pw);
            u.setPhone(phone);
            u.setCreatedAt(now.minus(70, ChronoUnit.DAYS));
            return users.save(u);
        });
    }

    private BuyerDemand demand(User buyer, String company, BuyerType type, Product product, double tons, double pMin, double pMax,
                               int years, District district, int radius, String quality, String season, DemandStatus st, int daysAgo) {
        BuyerDemand d = new BuyerDemand();
        d.setCode(String.format(Locale.ROOT, "TLB-2026-%03d", demands.count() + 1));
        d.setBuyer(buyer);
        d.setCompany(company);
        d.setBuyerType(type);
        d.setProduct(product);
        d.setCategory(product.category());
        d.setVolumeTons(BigDecimal.valueOf(tons));
        d.setPriceMin(BigDecimal.valueOf(pMin));
        d.setPriceMax(BigDecimal.valueOf(pMax));
        d.setContractYears(years);
        d.setDistrict(district);
        d.setLatitude(district.lat());
        d.setLongitude(district.lng());
        d.setRadiusKm(radius);
        d.setQuality(quality);
        d.setSeason(season);
        d.setStatus(st);
        d.setCreatedAt(now.minus(daysAgo, ChronoUnit.DAYS));
        if (st == DemandStatus.VERIFIED) {
            d.setVerifiedBy(users.findByEmailIgnoreCase("nazirlik@aqroinvest.az").orElse(null));
            d.setVerifiedAt(now.minus(Math.max(1, daysAgo - 4), ChronoUnit.DAYS));
        }
        return demands.save(d);
    }

    // ------------------------------------------------------------------ layihələr

    private record J(String key, String owner, ProjectSector sector, String plot, District district, String title, String tagline,
                     Stage stage, Double scale, long capex, long own, long ask, DealType deal, double pct, Long rev, Long profit,
                     Double yield, Double price, int jobs, boolean verified, int daysAgo, String problem, String solution,
                     String market, String team) {}

    private Map<String, Project> seedProjects() {
        List<J> list = List.of(
                new J("SOLAR", "kur.aqrotex@example.az", ProjectSector.ENERJI, "FZL-006", null,
                        "Horadiz günəş fermi: 10 MWp və panellər altında qoyunçuluq",
                        "Əkinə yararsız eroziyalı yamacda şəbəkəyə elektrik satan stansiya, otlaq da itirilmir",
                        Stage.LAND, 10.0, 12_000_000, 2_500_000, 9_500_000, DealType.EQUITY, 70, 1_150_000L, 950_000L, 1340.0, 85.0, 6, true, 240,
                        "Horadiz ətrafındakı 140 ha eroziyaya uğramış və qismən şoranlaşmış torpaq əkin üçün yararsızdır və boş qalır.",
                        "Birinci mərhələdə 10 MWp stasionar panellər quraşdırılır. Elektrik 8.5 qəpik/kVts tarifi ilə şəbəkəyə satılır. Panellərin altında 400 başlıq qoyun sürüsü otarılır: ot biçininə ehtiyac qalmır, yerli fermerə əlavə gəlir.",
                        "Qarabağ \"yaşıl enerji zonası\" elan olunub. Cəbrayılda 240 MW-lıq Şəfəq GES tikilir, şəbəkə infrastrukturu genişlənir.",
                        "Komandada 2 elektrik mühəndisi və bir GES layihəsini başa çatdırmış layihə meneceri var."),
                new J("HOTEL", "zengilan.baglari@example.az", ProjectSector.TURIZM, "CBR-006", null,
                        "Xudafərin eko-oteli və nar bağı",
                        "Su anbarının sahilində 12 otaqlı qonaq evi, yerli mətbəx və bağ turları",
                        Stage.PLAN, 12.0, 1_150_000, 250_000, 900_000, DealType.EQUITY, 45, 1_200_000L, 650_000L, null, null, 14, false, 9,
                        "Xudafərin körpülərinə gələn turistlərin qalmağa yeri yoxdur, gecələmək üçün Füzuliyə qayıdırlar.",
                        "Su anbarının sahilində 12 otaqlı eko-otel, nar bağında piknik zonası, yerli məhsullardan restoran.",
                        "Qarabağa turist axını hər il artır, Horadiz-Zəngilan yolu turistik marşrutdur.",
                        "Ailə biznesi: otelçilik təcrübəsi Qəbələdə 6 il."),
                new J("COLD", "aqro.qarabag@example.az", ProjectSector.EMAL, "AGD-006", null,
                        "Ağdam soyuducu anbarı və ixrac qablaşdırma xətti",
                        "3 000 tonluq anbar: nar və almanı mövsümdən sonra 40-60% baha satmaq imkanı",
                        Stage.LAND, 3000.0, 1_550_000, 450_000, 1_100_000, DealType.LOAN, 11, 580_000L, 300_000L, null, 150.0, 16, true, 15,
                        "Ağdam, Füzuli və Xocavənddə yeni bağlar bar verməyə başlayır, amma regionda soyuducu anbar yoxdur: fermer məhsulu yığım vaxtı ucuz satır.",
                        "3 000 ton tutumlu nəzarətli atmosferli anbar və çeşidləmə-qablaşdırma xətti. Xidmət haqqı və mövsümi qiymət fərqindən gəlir.",
                        "Regionda 1 500 ha-dan çox meyvə bağı salınır. Təsdiqlənmiş alıcı tələbləri var: nar 3 000 t, alma 1 500 t.",
                        "Logistika şirkətində 10 il təcrübə, soyuducu avadanlıq tədarükçüsü ilə ilkin razılaşma."),
                new J("AGRITECH", "yasil.vadi@example.az", ProjectSector.AQROTEXNOLOGIYA, null, District.AGDAM,
                        "AqroSensor: ağıllı suvarma üçün torpaq sensorları və mobil tətbiq",
                        "Fermer suyu və gübrəni 30% az istifadə edir, tətbiq nə vaxt suvarmağı deyir",
                        Stage.PLAN, null, 380_000, 80_000, 300_000, DealType.EQUITY, 25, 420_000L, 130_000L, null, null, 9, false, 6,
                        "Yeni salınan bağlarda suvarma \"gözəyarı\" aparılır: su itkisi böyükdür, məhsul azalır.",
                        "Ucuz torpaq nəmliyi sensorları, peyk NDVI məlumatı və hava proqnozu ilə suvarma qrafiki. Abunə modeli: 1 ha üçün ildə 60 ₼.",
                        "Qarabağda damcı suvarmalı bağlar sürətlə genişlənir. İlk 3 ildə 7 000 ha hədəf.",
                        "2 proqramçı, 1 aqronom. Prototip Ağcabədidə 40 ha-da sınaqdan keçib."),
                new J("APPLE", "investor@aqroinvest.az", ProjectSector.BAGCILIQ, "XCV-002", null,
                        "Xocavənd intensiv alma bağı (damcı suvarma)",
                        "40 ha intensiv bağ: 4-cü ildən tam məhsul",
                        Stage.PLAN, 40.0, 980_000, 240_000, 740_000, DealType.REVENUE_SHARE, 12, 1_150_000L, 620_000L, 32.0, 900.0, 16, false, 4,
                        "Regionda keyfiyyətli alma istehsalı azdır, emal müəssisələri xammalı kənardan gətirir.",
                        "M9 calaqaltı üzərində intensiv bağ, damcı suvarma, dolu toru.",
                        "Qarabağ Meyvə Emalı MMC ildə 1 500 ton alma tələbi elan edib.",
                        "Aqronom məsləhətçi ilə müqavilə, 15 illik fermer təcrübəsi."),
                new J("TOMATO", "araz.vadisi@example.az", ProjectSector.ISTIXANA, "AGD-005", null,
                        "Ağdam istixanası: ixrac üçün pomidor",
                        "3 ha isidilən çoxbölməli istixana, qışda ixrac",
                        Stage.LAND, 3.0, 1_850_000, 550_000, 1_300_000, DealType.EQUITY, 35, 560_000L, 180_000L, 170.0, 1100.0, 20, true, 12,
                        "Qış aylarında ixrac üçün keyfiyyətli pomidor çatışmır.",
                        "Çoxbölməli plyonka istixana, hidroponika, qaz ilə isitmə, damla suvarma və iqlim nəzarəti.",
                        "Xəzər Təzə Məhsul İxracat MMC ildə 2 500 t pomidor tələbi elan edib.",
                        "İstixana sahəsində 8 il təcrübə, Sabirabadda 1 ha istixana idarə edir."),
                new J("HONEY", "serq.bal@example.az", ProjectSector.ARICILIQ, "LCN-002", null,
                        "Laçın dağ balı: 300 ailəlik arıçılıq və qablaşdırma",
                        "Alp çəmənliklərinin balı: yüksək qiymət, az xərc",
                        Stage.OPERATING, 20.0, 120_000, 50_000, 70_000, DealType.LOAN, 10, 92_000L, 36_000L, null, null, 6, true, 30,
                        "Dağ balına tələb böyükdür, amma keyfiyyəti təsdiqlənmiş, qablaşdırılmış məhsul azdır.",
                        "Arı ailələrini 300-ə çatdırmaq, laboratoriya analizi və brend qablaşdırma.",
                        "Dağ Balı Kooperativi ildə 35 t bal tələbi elan edib.",
                        "Ailə təsərrüfatı, 120 arı ailəsi ilə fəaliyyətdədir."),
                new J("WINE", "qarabag.vineyards@example.az", ProjectSector.UZUMCULUK, "AGD-002", null,
                        "Papravənd üzüm bağı və mini şərab zavodu",
                        "85 ha texniki üzüm, öz şərab brendi",
                        Stage.LAND, 85.0, 1_300_000, 350_000, 950_000, DealType.EQUITY, 40, 760_000L, 280_000L, 11.0, 760.0, 30, true, 250,
                        "Ağdamın tarixi üzüm bağları bərpa olunmalıdır.",
                        "Yerli sortlarla bağ, 3-cü ildən şərab zavoduna xammal.",
                        "Xan Bağı Şərab Evi 7 illik müqavilə ilə 1 200 t üzüm tələbi elan edib.",
                        "Şərabçılıq üzrə ixtisaslı enoloq komandadadır."));
        Map<String, Project> m = new LinkedHashMap<>();
        int n = 1;
        for (J j : list) {
            Optional<User> owner = users.findByEmailIgnoreCase(j.owner());
            if (owner.isEmpty()) continue;
            Plot plot = j.plot() == null ? null : plots.findByCode(j.plot()).orElse(null);
            Project p = new Project();
            p.setCode(String.format(Locale.ROOT, "LYH-2026-%03d", n++));
            p.setOwner(owner.get());
            markEntrepreneur(owner.get(), j, plot);
            p.setPlot(plot);
            p.setSector(j.sector());
            p.setUseCase(j.sector().useCase());
            p.setDistrict(j.district() != null ? j.district() : plot != null ? plot.getDistrict() : null);
            p.setTitle(j.title());
            p.setTagline(j.tagline());
            p.setProblem(j.problem());
            p.setSolution(j.solution());
            p.setMarket(j.market());
            p.setTeam(j.team());
            p.setStage(j.stage());
            p.setScale(j.scale() == null ? null : BigDecimal.valueOf(j.scale()));
            p.setCapex(BigDecimal.valueOf(j.capex()));
            p.setOwnFunds(BigDecimal.valueOf(j.own()));
            p.setAskAmount(BigDecimal.valueOf(j.ask()));
            p.setDealType(j.deal());
            switch (j.deal()) {
                case EQUITY -> p.setEquityPercent(BigDecimal.valueOf(j.pct()));
                case LOAN -> p.setInterestPercent(BigDecimal.valueOf(j.pct()));
                default -> p.setRevenueSharePercent(BigDecimal.valueOf(j.pct()));
            }
            p.setClaimedRevenue(j.rev() == null ? null : BigDecimal.valueOf(j.rev()));
            p.setClaimedProfit(j.profit() == null ? null : BigDecimal.valueOf(j.profit()));
            p.setClaimedYield(j.yield() == null ? null : BigDecimal.valueOf(j.yield()));
            p.setClaimedPrice(j.price() == null ? null : BigDecimal.valueOf(j.price()));
            p.setJobs(j.jobs());
            p.setStatus(ProjectStatus.PUBLISHED);
            p.setVerified(j.verified());
            p.setViews(40 + j.daysAgo() * 7);
            p.setCreatedAt(now.minus(j.daysAgo(), ChronoUnit.DAYS));
            m.put(j.key(), projects.save(p));
        }
        // İnvestorun iki köhnə razılaşması (2026-cı ilin yazında): mərhələli maliyyələşmə peyk məlumatı ilə yoxlanılır
        interest(m.get("SOLAR"), "investor@aqroinvest.az", 1_000_000, "Bərpa olunan enerjiyə uzunmüddətli investisiya axtarıram. Şəbəkəyə qoşulma razılaşmasının statusunu öyrənmək istərdim.",
                InterestStatus.ACCEPTED, "Görüşə hazırıq. Şəbəkə operatoru ilə texniki şərtlər alınıb, sənədləri göstərərik.", 214);
        interest(m.get("WINE"), "investor@aqroinvest.az", 400_000, "Papravənd bağının bərpasına payla qoşuluram. Mərhələli ödəniş şərtimdir.",
                InterestStatus.ACCEPTED, "Razıyıq, escrow və peyk nəzarəti ilə işləyək.", 225);
        interest(m.get("COLD"), "investor@aqroinvest.az", 500_000, "Borc şərtləri maraqlıdır. Girov və sığorta barədə məlumat lazımdır.", InterestStatus.NEW, null, 2);
        interest(m.get("APPLE"), "fuzuli.taxil@example.az", 200_000, "Gəlirdən pay modeli mənə uyğundur. Dolu toru büdcəyə daxildirmi?", InterestStatus.NEW, null, 1);
        interest(m.get("WINE"), "aqro.qarabag@example.az", 300_000, "Şərab brendinə payla qoşulmaq istəyirəm.", InterestStatus.NEW, null, 8);
        interest(m.get("HOTEL"), "dag.ferma@example.az", 250_000, "Otel layihəsi maraqlıdır, amma gəlir rəqəmləri yüksək görünür.", InterestStatus.NEW, null, 3);
        users.findByEmailIgnoreCase("investor@aqroinvest.az").ifPresent(u -> {
            InvestorProfile pr = new InvestorProfile();
            pr.setUserId(u.getId());
            pr.setSectors(new ArrayList<>(List.of("ENERJI", "EMAL", "BAGCILIQ")));
            pr.setTicketMin(BigDecimal.valueOf(300_000));
            pr.setTicketMax(BigDecimal.valueOf(3_000_000));
            pr.setRisk(Risk.MID);
            pr.setHorizonYears(10);
            profiles.save(pr);
        });
        return m;
    }

    private void interest(Project p, String email, long amount, String msg, InterestStatus st, String reply, int daysAgo) {
        if (p == null) return;
        users.findByEmailIgnoreCase(email).ifPresent(u -> {
            ProjectInterest i = new ProjectInterest();
            i.setProject(p);
            i.setInvestor(u);
            i.setAmount(BigDecimal.valueOf(amount));
            i.setMessage(msg);
            i.setStatus(st);
            i.setReply(reply);
            i.setCreatedAt(now.minus(daysAgo + 5L, ChronoUnit.DAYS));
            if (st == InterestStatus.ACCEPTED) i.setAcceptedAt(now.minus(daysAgo, ChronoUnit.DAYS));
            interests.save(i);
        });
    }

    // ------------------------------------------------------------------ niyyət məktubları

    private void seedLetters(Map<String, BuyerDemand> d, Map<String, Project> p) {
        users.findByEmailIgnoreCase("investor@aqroinvest.az").ifPresent(inv -> {
            Optional<Application> wheat = applications.findByApplicantIdOrderByCreatedAtDesc(inv.getId()).stream()
                    .filter(a -> a.getPlot() != null && "FZL-001".equals(a.getPlot().getCode())).findFirst();
            wheat.ifPresent(a -> letter(d.get("BUGDA"), inv, a.getPlot(), a, null, 2000, 440, LoiStatus.SIGNED,
                    "Füzuli taxıl massivindən illik 2 000 t ərzaqlıq buğda təklif edirik.", "Keyfiyyət şərtlərinə əməl olunarsa 3 il ərzində alırıq.", 10));
            if (p.get("APPLE") != null) letter(d.get("ALMA"), inv, p.get("APPLE").getPlot(), null, p.get("APPLE"), 800, null, LoiStatus.REQUESTED,
                    "Xocavənd intensiv bağından 4-cü ildən illik 800 t alma.", null, 1);
        });
        if (p.get("TOMATO") != null) users.findByEmailIgnoreCase("araz.vadisi@example.az").ifPresent(u ->
                letter(d.get("POMIDOR"), u, p.get("TOMATO").getPlot(), null, p.get("TOMATO"), 400, 1050, LoiStatus.SIGNED,
                        "Qış mövsümündə 400 t ixrac pomidoru.", "GlobalGAP sertifikatı alınarsa həcmi artırmağa hazırıq.", 7));
        if (p.get("WINE") != null) users.findByEmailIgnoreCase("qarabag.vineyards@example.az").ifPresent(u ->
                letter(d.get("UZUM"), u, p.get("WINE").getPlot(), null, p.get("WINE"), 600, 760, LoiStatus.SIGNED,
                        "Papravənd bağından 600 t texniki üzüm.", "7 illik müqavilə üçün hazırıq.", 14));
    }

    private void letter(BuyerDemand d, User req, Plot plot, Application app, Project project, double tons, Integer price,
                        LoiStatus st, String msg, String note, int daysAgo) {
        if (d == null) return;
        LetterOfIntent l = new LetterOfIntent();
        l.setDemand(d);
        l.setRequester(req);
        l.setPlot(plot);
        l.setApplication(app);
        l.setProject(project);
        l.setVolumeTons(BigDecimal.valueOf(tons));
        l.setPrice(price == null ? null : BigDecimal.valueOf(price));
        l.setStatus(st);
        l.setMessage(msg);
        l.setBuyerNote(note);
        l.setCreatedAt(now.minus(daysAgo, ChronoUnit.DAYS));
        if (st == LoiStatus.SIGNED) l.setSignedAt(now.minus(Math.max(0, daysAgo - 2), ChronoUnit.DAYS));
        letters.save(l);
    }

    /** Layihə sahibi sahibkar hesabıdır (əsas demo investoru istisna: o həm investor, həm də bir layihənin sahibidir). */
    private void markEntrepreneur(User u, J j, Plot plot) {
        if ("investor@aqroinvest.az".equalsIgnoreCase(u.getEmail()) || u.getUserType() == az.aqroinvest.user.UserType.ENTREPRENEUR) return;
        u.setUserType(az.aqroinvest.user.UserType.ENTREPRENEUR);
        users.save(u);
        EntrepreneurProfile ep = new EntrepreneurProfile();
        ep.setUserId(u.getId());
        ep.setDistrict(j.district() != null ? j.district() : plot != null ? plot.getDistrict() : null);
        ep.setSector(j.sector());
        ep.setLandStatus(plot != null ? EntrepreneurProfile.LandStatus.APPLYING : EntrepreneurProfile.LandStatus.NONE);
        ep.setLandHa(j.scale() == null ? null : BigDecimal.valueOf(j.scale()));
        ep.setNeedAmount(BigDecimal.valueOf(j.ask()));
        ep.setIdea(j.tagline());
        entrepreneurProfiles.save(ep);
    }
}
