package az.aqroinvest.market;

import az.aqroinvest.audit.AuditService;
import az.aqroinvest.buyer.BuyerEnums.LoiStatus;
import az.aqroinvest.buyer.LetterOfIntent;
import az.aqroinvest.buyer.LetterOfIntentRepository;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.economics.UseCase;
import az.aqroinvest.market.MarketEnums.InterestStatus;
import az.aqroinvest.notification.NotificationService;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import az.aqroinvest.satellite.SatelliteDtos.NdviPoint;
import az.aqroinvest.satellite.SatelliteService;
import az.aqroinvest.security.AuthPrincipal;
import az.aqroinvest.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Peyk təsdiqli mərhələli maliyyələşmə. İnvestor razılaşdığı məbləği bir dəfəyə vermir: pul escrow hesabında saxlanılır və
 * hissə-hissə, hər mərhələ təsdiqləndikdə sahibkara köçürülür. Mərhələlərin bir qismini Landsat NDVI seriyası avtomatik
 * yoxlayır. Hava effektini çıxarmaq üçün "fərqlərin fərqi" üsulu: sahənin dəyişməsi eyni zonadakı (aran və ya dağlıq) digər
 * sahələrin median dəyişməsi ilə müqayisə olunur. Yağışlı il bütün sahələri yaşıllaşdırır, işlənən sahə isə onlardan fərqlənir.
 */
@Service
@RequiredArgsConstructor
public class TrancheService {
    static final double CHANGE_MIN = 0.05, GROWTH_NDVI = 0.40;
    static final int HIGHLAND_M = 900;

    private final ProjectInterestRepository interests;
    private final ProjectRepository projects;
    private final TrancheReleaseRepository releases;
    private final LetterOfIntentRepository letters;
    private final PlotRepository plots;
    private final SatelliteService satellite;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser currentUser;

    /** Peyk sübutu: sahənin və nəzarət qrupunun dəyişməsi, havadan təmizlənmiş fərq. */
    public record Evidence(int season, int baselineFrom, int baselineTo, double plotNdvi, double plotBaseline, double plotChange,
                           double controlChange, double excess, double threshold, int controls, String zone, String rule) {}

    public record Milestone(int seq, String key, String title, String description, double sharePct, long amount, String method,
                            String methodLabel, String icon, Integer season, String status, String statusLabel, String note,
                            Evidence evidence, Instant releasedAt, boolean canRelease) {}

    public record Plan(Long interestId, Long projectId, String projectCode, String projectTitle, String plotCode, String investorName,
                       String ownerName, long amount, long released, long releasable, long locked, Instant acceptedAt,
                       List<Milestone> milestones, boolean viewerIsInvestor, String method) {}

    record Step(String key, String title, String description, double share, String method, String icon) {}

    static final String METHOD = "Pul escrow hesabında saxlanılır və hər mərhələ təsdiqləndikdə hissə-hissə köçürülür. "
            + "Peyk mərhələləri Landsat 8/9 NDVI məlumatı ilə avtomatik yoxlanılır: sahənin dəyişməsi eyni zonadakı digər sahələrin "
            + "median dəyişməsi ilə müqayisə olunur (fərqlərin fərqi), beləliklə yağışlı və ya quraq ilin təsiri çıxarılır.";

    // ------------------------------------------------------------------ şablon

    static boolean vegetation(UseCase u) {
        return u == UseCase.EKINCILIK || u == UseCase.BAGCILIQ || u == UseCase.UZUMCULUK;
    }

    static boolean construction(UseCase u) {
        return u == UseCase.GUNES || u == UseCase.SOYUDUCU || u == UseCase.ISTIXANA || u == UseCase.AQROTURIZM;
    }

    static List<Step> steps(UseCase u, boolean plot) {
        List<Step> s = new ArrayList<>();
        s.add(new Step("CONTRACT", "Müqavilə və escrow", "Razılaşma imzalanır, vəsait escrow hesabına köçürülür, ilk hissə işlərin başlanması üçün sahibkara verilir.", 25, "CONTRACT", "handshake"));
        if (plot && vegetation(u)) {
            s.add(new Step("SAT_START", "Sahədə işlər başladı", "Torpaq hazırlığı, suvarma və əkin peykdən görünən dəyişiklik yaradır: sahənin NDVI dəyişməsi qonşu sahələrdən ən azı 0.05 fərqlənməlidir.", 25, "SATELLITE", "satellite"));
            s.add(new Step("SAT_GROWTH", "Bitki örtüyü formalaşdı", "İkinci mövsümdə sahənin pik NDVI-si ən azı 0.40 olmalı və zonanın medianından aşağı olmamalıdır.", 30, "SATELLITE", "sprout"));
            s.add(new Step("SALES", "İlk satış müqaviləsi", "Platformadakı təsdiqlənmiş alıcı ilə niyyət məktubu imzalanır: məhsulun bazarı təmin olunub.", 20, "BUYER", "file-check"));
        } else if (plot && construction(u)) {
            s.add(new Step("SAT_BUILD", "Tikinti peykdən görünür", "Panellər, bina və ya istixana torpağı örtür: sahənin NDVI-si qonşu sahələrə nisbətən ən azı 0.05 azalmalıdır.", 30, "SATELLITE", "satellite"));
            s.add(new Step("EXPERT", "İstismara qəbul", "Ekspert səfəri və istismara qəbul aktı (şəbəkəyə qoşulma, avadanlığın quraşdırılması).", 25, "EXPERT", "clipboard-check"));
            s.add(new Step("SALES", "İlk gəlir", "Alıcı və ya şəbəkə operatoru ilə satış müqaviləsi imzalanır.", 20, "BUYER", "file-check"));
        } else {
            s.add(new Step("EXPERT", "Avadanlıq və ilk fəaliyyət", "Ekspert səfəri: avadanlığın alındığı və fəaliyyətin başladığı təsdiqlənir (otlaqda NDVI təbii örtüyü göstərir, meyar deyil).", 30, "EXPERT", "clipboard-check"));
            s.add(new Step("PILOT", "Pilot nəticələri", "Sahibkar pilot dövrün hesabatını yükləyir, ekspert yoxlayır.", 25, "DOCUMENT", "file-search"));
            s.add(new Step("SALES", "İlk satış müqaviləsi", "Platformadakı təsdiqlənmiş alıcı ilə niyyət məktubu imzalanır.", 20, "BUYER", "file-check"));
        }
        return s;
    }

    static String methodLabel(String m) {
        return switch (m) {
            case "CONTRACT" -> "Müqavilə";
            case "SATELLITE" -> "Peyk (avtomatik)";
            case "BUYER" -> "Alıcı müqaviləsi (avtomatik)";
            case "EXPERT" -> "Ekspert səfəri";
            default -> "Sənəd yoxlaması";
        };
    }

    /** Layihənin açıq şablonu (investordan istənilən məbləğ üzrə). */
    @Transactional(readOnly = true)
    public List<Milestone> template(Long projectId) {
        Project p = projects.findFull(projectId).orElseThrow(() -> ApiException.notFound("Layihə"));
        return template(p, p.getAskAmount().longValue());
    }

    /** Hər layihə üçün açıq şablon: investor razılaşmadan əvvəl pulun necə hissələrə bölünəcəyini görür. */
    public List<Milestone> template(Project p, long amount) {
        UseCase u = p.getUseCase() != null ? p.getUseCase() : p.getSector().useCase();
        List<Milestone> out = new ArrayList<>();
        int seq = 1;
        for (Step st : steps(u, p.getPlot() != null)) {
            out.add(new Milestone(seq++, st.key(), st.title(), st.description(), st.share(), Math.round(amount * st.share() / 100), st.method(),
                    methodLabel(st.method()), st.icon(), null, "PLANNED", "Plan", null, null, null, false));
        }
        return out;
    }

    // ------------------------------------------------------------------ canlı vəziyyət

    @Transactional(readOnly = true)
    public Plan plan(Long interestId) {
        AuthPrincipal me = currentUser.require();
        ProjectInterest i = interests.findFull(interestId).orElseThrow(() -> ApiException.notFound("Razılaşma"));
        boolean investor = i.getInvestor().getId().equals(me.id());
        if (!investor && !i.getProject().getOwner().getId().equals(me.id()) && !me.isAdmin()) throw ApiException.notFound("Razılaşma");
        if (i.getStatus() != InterestStatus.ACCEPTED) throw ApiException.badRequest("NOT_ACCEPTED", "Mərhələli maliyyələşmə sahibkar razılaşmanı qəbul etdikdən sonra başlayır");
        return evaluate(i, investor);
    }

    Plan evaluate(ProjectInterest i, boolean viewerIsInvestor) {
        Project p = i.getProject();
        UseCase u = p.getUseCase() != null ? p.getUseCase() : p.getSector().useCase();
        long amount = i.getAmount().longValue();
        Instant accepted = i.getAcceptedAt() != null ? i.getAcceptedAt() : i.getUpdatedAt();
        ZonedDateTime acc = accepted.atZone(ZoneOffset.UTC);
        // Yazdan əvvəl imzalanıbsa işlər həmin mövsümdə görünür, sonra imzalanıbsa növbəti mövsümdə
        int season = acc.getMonthValue() <= 5 ? acc.getYear() : acc.getYear() + 1;
        Map<Integer, TrancheRelease> rel = new HashMap<>();
        releases.findByInterestIdOrderBySeq(i.getId()).forEach(r -> rel.put(r.getSeq(), r));
        boolean signed = letters.findByProject(p.getId()).stream().anyMatch(l -> l.getStatus() == LoiStatus.SIGNED);

        List<Milestone> out = new ArrayList<>();
        int seq = 1;
        long released = 0, releasable = 0;
        boolean prevReleased = true;
        for (Step st : steps(u, p.getPlot() != null)) {
            long part = Math.round(amount * st.share() / 100);
            String status, label, note = null;
            Evidence ev = null;
            Integer seas = null;
            switch (st.key()) {
                case "CONTRACT" -> { status = "VERIFIED"; label = "Təsdiqləndi"; note = "Razılaşma " + acc.toLocalDate() + " tarixində qəbul edilib"; }
                case "SAT_START", "SAT_BUILD" -> {
                    seas = season;
                    ev = change(p.getPlot(), season, st.key().equals("SAT_BUILD"));
                    if (ev == null) { status = "WAITING"; label = season + " mövsümü gözlənilir"; note = "Landsat mövsümün pik yaşıllığını sentyabrda yekunlaşdırır, nəticə oktyabrda avtomatik yoxlanılır"; }
                    else if (st.key().equals("SAT_START") ? Math.abs(ev.excess()) >= CHANGE_MIN : ev.excess() <= -CHANGE_MIN) {
                        status = "VERIFIED"; label = "Peyk təsdiqlədi";
                        note = String.format(Locale.ROOT, "%d mövsümündə sahənin NDVI-si %+.2f dəyişib, qonşu sahələr %+.2f: hava effekti çıxılandan sonra fərq %+.2f",
                                season, ev.plotChange(), ev.controlChange(), ev.excess());
                    } else {
                        status = "FLAGGED"; label = "Peyk dəyişiklik görmür";
                        note = st.key().equals("SAT_BUILD") && ev.excess() > 0
                                ? String.format(Locale.ROOT, "Sahədə bitki örtüyü qonşulardan %+.2f çox artıb: tikinti izi görünmür. Ödəniş dayandırılıb, sahibkardan izahat və foto hesabat tələb olunur", ev.excess())
                                : String.format(Locale.ROOT, "Hava effekti çıxılandan sonra fərq cəmi %+.2f (tələb ±%.2f). Ödəniş dayandırılıb, yerində yoxlama tövsiyə olunur", ev.excess(), CHANGE_MIN);
                    }
                }
                case "SAT_GROWTH" -> {
                    seas = season + 1;
                    ev = level(p.getPlot(), season + 1, season);
                    if (ev == null) { status = "WAITING"; label = (season + 1) + " mövsümü gözlənilir"; note = "İkinci vegetasiya mövsümünün peyk məlumatı mövsüm bitəndən sonra yoxlanılır"; }
                    else if (ev.plotNdvi() >= GROWTH_NDVI && ev.excess() >= 0) { status = "VERIFIED"; label = "Peyk təsdiqlədi"; note = String.format(Locale.ROOT, "Pik NDVI %.2f, zonanın medianından %+.2f yüksək", ev.plotNdvi(), ev.excess()); }
                    else { status = "FLAGGED"; label = "Bitki örtüyü zəifdir"; note = String.format(Locale.ROOT, "Pik NDVI %.2f, zonanın medianına nisbətən %+.2f", ev.plotNdvi(), ev.excess()); }
                }
                case "SALES" -> {
                    if (signed) { status = "VERIFIED"; label = "Alıcı imzaladı"; note = "Platformada təsdiqlənmiş alıcı ilə niyyət məktubu imzalanıb"; }
                    else { status = "PENDING"; label = "Gözlənilir"; note = "Alıcılar bölməsində niyyət məktubu imzalananda avtomatik təsdiqlənir"; }
                }
                default -> { status = "PENDING"; label = "Ekspert gözlənilir"; note = "Sahibkar hazır olduqda ekspert səfəri təyin olunur"; }
            }
            TrancheRelease r = rel.get(seq);
            if (r == null && seq == 1) status = "RELEASED";
            if (r != null) status = "RELEASED";
            // Mərhələlər ardıcıl ödənilir: əvvəlki hissə ödənilməyibsə (və ya dayandırılıbsa) təsdiqlənmiş mərhələ növbədə qalır
            boolean can = status.equals("VERIFIED") && prevReleased;
            if (status.equals("RELEASED")) { label = "Ödənildi"; released += part; }
            else if (can) releasable += part;
            else if (status.equals("VERIFIED")) label = label + " · növbədə";
            out.add(new Milestone(seq, st.key(), st.title(), st.description(), st.share(), part, st.method(), methodLabel(st.method()), st.icon(),
                    seas, status, label, note, ev, r != null ? r.getReleasedAt() : seq == 1 ? accepted : null, can));
            prevReleased = prevReleased && status.equals("RELEASED");
            seq++;
        }
        return new Plan(i.getId(), p.getId(), p.getCode(), p.getTitle(), p.getPlot() == null ? null : p.getPlot().getCode(),
                i.getInvestor().getFullName(), p.getOwner().getFullName(), amount, released, releasable, amount - released - releasable,
                accepted, out, viewerIsInvestor, METHOD);
    }

    @Transactional
    public Plan release(Long interestId, int seq) {
        AuthPrincipal me = currentUser.require();
        ProjectInterest i = interests.findFull(interestId).orElseThrow(() -> ApiException.notFound("Razılaşma"));
        if (!i.getInvestor().getId().equals(me.id())) throw ApiException.forbidden("Ödənişi yalnız investor təsdiqləyə bilər");
        Plan plan = evaluate(i, true);
        Milestone m = plan.milestones().stream().filter(x -> x.seq() == seq).findFirst().orElseThrow(() -> ApiException.notFound("Mərhələ"));
        if (m.status().equals("RELEASED")) throw ApiException.badRequest("ALREADY_RELEASED", "Bu hissə artıq ödənilib");
        if (!m.status().equals("VERIFIED")) throw ApiException.badRequest("NOT_VERIFIED", "Mərhələ hələ təsdiqlənməyib: " + m.statusLabel());
        if (!m.canRelease()) throw ApiException.badRequest("OUT_OF_ORDER", "Mərhələlər ardıcıl ödənilir: əvvəlcə əvvəlki mərhələ ödənilməlidir");
        TrancheRelease r = new TrancheRelease();
        r.setInterestId(i.getId());
        r.setSeq(seq);
        r.setAmount(BigDecimal.valueOf(m.amount()));
        r.setEvidence(m.note());
        r.setReleasedBy(me.id());
        r.setReleasedAt(Instant.now());
        releases.save(r);
        Project p = i.getProject();
        notifications.notify(p.getOwner().getId(), "TRANCHE_RELEASED", "Mərhələ ödənildi: " + m.title(),
                money(m.amount()) + " escrow hesabından köçürüldü. Əsas: " + m.note(), "/layihe.html?id=" + p.getId());
        audit.log("TRANCHE_RELEASED", "ProjectInterest", i.getId(), p.getCode() + " #" + seq + " " + m.amount());
        return evaluate(i, true);
    }

    // ------------------------------------------------------------------ peyk qiymətləndirməsi

    /** Mövsümdə sahənin baza illərə nisbətən dəyişməsi, zonadakı digər sahələrin median dəyişməsi ilə müqayisədə. */
    Evidence change(Plot plot, int season, boolean build) {
        if (plot == null) return null;
        Map<Integer, Double> own = series(plot);
        Double now = own.get(season), base = baseline(own, season);
        if (now == null || base == null) return null;
        List<Double> ctrl = new ArrayList<>();
        int zone = zone(plot);
        for (Plot c : plots.findAll()) {
            if (c.getId().equals(plot.getId()) || zone(c) != zone) continue;
            Map<Integer, Double> s = series(c);
            Double cn = s.get(season), cb = baseline(s, season);
            if (cn != null && cb != null) ctrl.add(cn - cb);
        }
        if (ctrl.size() < 3) return null;
        double med = median(ctrl), change = now - base;
        return new Evidence(season, season - 2, season - 1, r3(now), r3(base), r3(change), r3(med), r3(change - med),
                build ? -CHANGE_MIN : CHANGE_MIN, ctrl.size(), zone == 1 ? "dağlıq" : "aran",
                build ? "Fərq ≤ -0.05: tikinti torpağı örtür" : "|Fərq| ≥ 0.05: torpaq işlənib və ya əkilib");
    }

    /** Mövsümdə sahənin NDVI səviyyəsi zonanın medianı ilə müqayisədə. */
    Evidence level(Plot plot, int season, int startSeason) {
        if (plot == null) return null;
        Map<Integer, Double> own = series(plot);
        Double now = own.get(season), base = baseline(own, startSeason);
        if (now == null) return null;
        List<Double> ctrl = new ArrayList<>();
        int zone = zone(plot);
        for (Plot c : plots.findAll()) {
            if (c.getId().equals(plot.getId()) || zone(c) != zone) continue;
            Double v = series(c).get(season);
            if (v != null) ctrl.add(v);
        }
        if (ctrl.size() < 3) return null;
        double med = median(ctrl);
        return new Evidence(season, startSeason - 2, startSeason - 1, r3(now), base == null ? 0 : r3(base), base == null ? 0 : r3(now - base),
                r3(med), r3(now - med), GROWTH_NDVI, ctrl.size(), zone == 1 ? "dağlıq" : "aran", "NDVI ≥ 0.40 və zonanın medianından aşağı deyil");
    }

    private Map<Integer, Double> series(Plot p) {
        Map<Integer, Double> m = new HashMap<>();
        for (NdviPoint x : satellite.ndvi(p).points()) m.put(x.year(), x.ndvi());
        return m;
    }

    private static Double baseline(Map<Integer, Double> s, int season) {
        Double a = s.get(season - 1), b = s.get(season - 2);
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return (a + b) / 2;
    }

    static int zone(Plot p) {
        return p.getAltitudeM() != null && p.getAltitudeM() >= HIGHLAND_M ? 1 : 0;
    }

    private static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2;
    }

    private static double r3(double d) {
        return Math.round(d * 1000) / 1000.0;
    }

    private static String money(long v) {
        return v >= 1_000_000 ? String.format(Locale.ROOT, "%.1f mln ₼", v / 1e6) : String.format(Locale.ROOT, "%,d ₼", v).replace(',', ' ');
    }
}
