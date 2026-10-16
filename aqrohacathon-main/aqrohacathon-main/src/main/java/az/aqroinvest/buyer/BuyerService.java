package az.aqroinvest.buyer;

import az.aqroinvest.application.Application;
import az.aqroinvest.application.ApplicationRepository;
import az.aqroinvest.audit.AuditService;
import az.aqroinvest.buyer.BuyerDtos.*;
import az.aqroinvest.buyer.BuyerEnums.*;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.market.Project;
import az.aqroinvest.market.ProjectRepository;
import az.aqroinvest.notification.NotificationService;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotCategory;
import az.aqroinvest.plot.PlotRepository;
import az.aqroinvest.security.AuthPrincipal;
import az.aqroinvest.security.CurrentUser;
import az.aqroinvest.user.Role;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.*;

/**
 * Alıcı tələbləri və niyyət məktubları. Fermerin ən böyük qorxusu "məhsulu kimə satacağam?" sualıdır: platforma
 * yoxlanılmış alıcı tələblərini sahələrlə məhsul, məsafə və həcm üzrə uyğunlaşdırır, niyyət məktubu isə müraciətə əlavə olunur.
 */
@Service
@RequiredArgsConstructor
public class BuyerService {
    private final BuyerDemandRepository demands;
    private final LetterOfIntentRepository letters;
    private final PlotRepository plots;
    private final ApplicationRepository applications;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser currentUser;

    // ------------------------------------------------------------------ ictimai

    @Transactional(readOnly = true)
    public List<DemandView> verified(PlotCategory category) {
        return demands.findByStatusFull(DemandStatus.VERIFIED).stream()
                .filter(d -> category == null || d.getCategory() == category).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public DemandView one(Long id) {
        BuyerDemand d = demands.findFull(id).orElseThrow(() -> ApiException.notFound("Tələb"));
        if (d.getStatus() != DemandStatus.VERIFIED) {
            AuthPrincipal me = currentUser.optional().orElseThrow(() -> ApiException.notFound("Tələb"));
            if (!me.isStaff() && !d.getBuyer().getId().equals(me.id())) throw ApiException.notFound("Tələb");
        }
        return view(d);
    }

    /** Sahənin məhsuluna uyğun təsdiqlənmiş tələblər: məhsul kateqoriyası, məsafə (alıcının radiusu) və həcm. */
    @Transactional(readOnly = true)
    public List<DemandMatch> forPlot(Long plotId) {
        Plot p = plots.findById(plotId).orElseThrow(() -> ApiException.notFound("Sahə"));
        return match(p, p.getCategory());
    }

    public List<DemandMatch> match(Plot p, PlotCategory category) {
        double production = expectedProduction(p, category);
        List<DemandMatch> out = new ArrayList<>();
        for (BuyerDemand d : demands.findByStatusFull(DemandStatus.VERIFIED)) {
            if (d.getCategory() != category) continue;
            double km = haversine(p.getLatitude(), p.getLongitude(), d.getLatitude(), d.getLongitude());
            if (km > d.getRadiusKm()) continue;
            int coverage = (int) Math.min(999, Math.round(production / d.getVolumeTons().doubleValue() * 100));
            int score = (int) Math.round(Math.max(0, 100 - km / d.getRadiusKm() * 40) * 0.6 + Math.min(100, coverage) * 0.4);
            out.add(new DemandMatch(view(d), Math.round(km * 10) / 10.0, Math.round(production), coverage, score));
        }
        out.sort(Comparator.comparingInt((DemandMatch m) -> -m.score()));
        return out;
    }

    // ------------------------------------------------------------------ investor / sahibkar

    @Transactional
    public LetterView requestLetter(Long demandId, LetterRequest r) {
        AuthPrincipal me = currentUser.require();
        if (me.role() != Role.INVESTOR) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Niyyət məktubunu yalnız investor / sahibkar istəyə bilər");
        BuyerDemand d = demands.findFull(demandId).orElseThrow(() -> ApiException.notFound("Tələb"));
        if (d.getStatus() != DemandStatus.VERIFIED) throw ApiException.badRequest("NOT_VERIFIED", "Tələb hələ təsdiqlənməyib");
        LetterOfIntent l = new LetterOfIntent();
        l.setDemand(d);
        l.setRequester(users.getReferenceById(me.id()));
        if (r.applicationId() != null) {
            Application a = applications.findById(r.applicationId()).orElseThrow(() -> ApiException.notFound("Müraciət"));
            if (!a.getApplicant().getId().equals(me.id())) throw ApiException.notFound("Müraciət");
            l.setApplication(a);
            l.setPlot(a.getPlot());
        }
        if (r.projectId() != null) {
            Project p = projects.findById(r.projectId()).orElseThrow(() -> ApiException.notFound("Layihə"));
            if (!p.getOwner().getId().equals(me.id())) throw ApiException.notFound("Layihə");
            l.setProject(p);
            if (l.getPlot() == null) l.setPlot(p.getPlot());
        }
        if (l.getPlot() == null && r.plotId() != null) l.setPlot(plots.findById(r.plotId()).orElseThrow(() -> ApiException.notFound("Sahə")));
        l.setVolumeTons(r.volumeTons().min(d.getVolumeTons()));
        l.setMessage(r.message());
        letters.save(l);
        notifications.notify(d.getBuyer().getId(), "LOI_REQUESTED", "Niyyət məktubu sorğusu: " + d.getProduct().label(),
                me.fullName() + " " + fmt(l.getVolumeTons()) + " t " + d.getProduct().label().toLowerCase(Locale.ROOT) + " üçün niyyət məktubu istəyir"
                        + (l.getPlot() == null ? "." : " (" + l.getPlot().getCode() + ")."), "/alicilar.html#letters");
        audit.log("LOI_REQUESTED", "BuyerDemand", d.getId(), fmt(l.getVolumeTons()) + " t");
        return view(l);
    }

    @Transactional(readOnly = true)
    public List<LetterView> myLetters(Long applicationId, Long projectId) {
        AuthPrincipal me = currentUser.require();
        if (applicationId != null) {
            Application a = applications.findById(applicationId).orElseThrow(() -> ApiException.notFound("Müraciət"));
            if (!me.isStaff() && !a.getApplicant().getId().equals(me.id())) throw ApiException.notFound("Müraciət");
            return letters.findByApplication(applicationId).stream().map(this::view).toList();
        }
        if (projectId != null) return letters.findByProject(projectId).stream().filter(l -> me.isStaff() || l.getRequester().getId().equals(me.id())).map(this::view).toList();
        return letters.findByRequester(me.id()).stream().map(this::view).toList();
    }

    // ------------------------------------------------------------------ alıcı

    @Transactional(readOnly = true)
    public List<DemandView> mine() {
        return demands.findByBuyer(requireBuyer().id()).stream().map(this::view).toList();
    }

    @Transactional
    public DemandView create(DemandRequest r) {
        AuthPrincipal me = requireBuyer();
        BuyerDemand d = new BuyerDemand();
        d.setBuyer(users.getReferenceById(me.id()));
        d.setCode(nextCode());
        apply(d, r);
        demands.save(d);
        audit.log("DEMAND_CREATED", "BuyerDemand", d.getId(), d.getProduct() + " " + fmt(d.getVolumeTons()) + " t");
        return view(d);
    }

    @Transactional
    public DemandView update(Long id, DemandRequest r) {
        AuthPrincipal me = requireBuyer();
        BuyerDemand d = demands.findFull(id).orElseThrow(() -> ApiException.notFound("Tələb"));
        if (!d.getBuyer().getId().equals(me.id())) throw ApiException.notFound("Tələb");
        apply(d, r);
        if (d.getStatus() == DemandStatus.VERIFIED || d.getStatus() == DemandStatus.REJECTED) d.setStatus(DemandStatus.PENDING);
        return view(d);
    }

    @Transactional(readOnly = true)
    public List<LetterView> incoming() {
        return letters.findByBuyer(requireBuyer().id()).stream().map(this::view).toList();
    }

    @Transactional
    public LetterView decide(Long letterId, boolean sign, SignRequest r) {
        AuthPrincipal me = requireBuyer();
        LetterOfIntent l = letters.findFull(letterId).orElseThrow(() -> ApiException.notFound("Niyyət məktubu"));
        if (!l.getDemand().getBuyer().getId().equals(me.id())) throw ApiException.notFound("Niyyət məktubu");
        if (l.getStatus() != LoiStatus.REQUESTED) throw ApiException.conflict("ALREADY_DECIDED", "Bu sorğuya artıq cavab verilib");
        l.setStatus(sign ? LoiStatus.SIGNED : LoiStatus.DECLINED);
        l.setBuyerNote(r == null ? null : r.note());
        if (sign) {
            BuyerDemand d = l.getDemand();
            BigDecimal price = r != null && r.price() != null ? r.price() : d.getPriceMin();
            l.setPrice(price);
            l.setSignedAt(Instant.now());
        }
        notifications.notify(l.getRequester().getId(), sign ? "LOI_SIGNED" : "LOI_DECLINED",
                sign ? "Niyyət məktubu imzalandı: " + l.getDemand().getCompany() : "Alıcı niyyət məktubunu imzalamadı",
                sign ? fmt(l.getVolumeTons()) + " t " + l.getDemand().getProduct().label().toLowerCase(Locale.ROOT) + ", " + fmt(l.getPrice()) + " ₼/t, "
                        + l.getDemand().getContractYears() + " il. Məktub müraciətinizə əlavə olundu." : (l.getBuyerNote() == null ? l.getDemand().getCompany() : l.getBuyerNote()),
                l.getApplication() != null ? "/muraciet-detal.html?id=" + l.getApplication().getId() : "/alicilar.html#mine");
        audit.log(sign ? "LOI_SIGNED" : "LOI_DECLINED", "LetterOfIntent", l.getId(), l.getDemand().getCode());
        return view(l);
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public List<DemandView> all() {
        return demands.findAllFull().stream().map(this::view).toList();
    }

    @Transactional
    public DemandView verify(Long id, boolean ok) {
        AuthPrincipal me = currentUser.require();
        BuyerDemand d = demands.findFull(id).orElseThrow(() -> ApiException.notFound("Tələb"));
        d.setStatus(ok ? DemandStatus.VERIFIED : DemandStatus.REJECTED);
        if (ok) {
            d.setVerifiedBy(users.getReferenceById(me.id()));
            d.setVerifiedAt(Instant.now());
        }
        notifications.notify(d.getBuyer().getId(), ok ? "DEMAND_VERIFIED" : "DEMAND_REJECTED",
                ok ? "Tələbiniz təsdiqləndi: " + d.getProduct().label() : "Tələbiniz təsdiqlənmədi",
                ok ? "Tələb fermer və investorlara \"təsdiqlənmiş tələb\" kimi göstərilir." : "Ətraflı məlumat üçün Nazirliklə əlaqə saxlayın.", "/alicilar.html#mine");
        audit.log(ok ? "DEMAND_VERIFIED" : "DEMAND_REJECTED", "BuyerDemand", d.getId(), d.getCode());
        return view(d);
    }

    // ------------------------------------------------------------------ köməkçilər

    private AuthPrincipal requireBuyer() {
        AuthPrincipal me = currentUser.require();
        if (me.role() != Role.BUYER) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bu bölmə alıcı hesabları üçündür");
        return me;
    }

    private void apply(BuyerDemand d, DemandRequest r) {
        d.setCompany(r.company().trim());
        d.setBuyerType(r.buyerType());
        d.setProduct(r.product());
        d.setCategory(r.product().category());
        d.setVolumeTons(r.volumeTons());
        d.setPriceMin(r.priceMin());
        d.setPriceMax(r.priceMax());
        d.setContractYears(r.contractYears());
        d.setDistrict(r.district());
        d.setLatitude(r.district().lat());
        d.setLongitude(r.district().lng());
        d.setRadiusKm(r.radiusKm());
        d.setQuality(r.quality());
        d.setSeason(r.season());
    }

    DemandView view(BuyerDemand d) {
        List<LetterOfIntent> signed = d.getId() == null ? List.of() : letters.findByBuyer(d.getBuyer().getId()).stream()
                .filter(l -> l.getDemand().getId().equals(d.getId()) && l.getStatus() == LoiStatus.SIGNED).toList();
        BigDecimal contracted = signed.stream().map(LetterOfIntent::getVolumeTons).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DemandView(d.getId(), d.getCode(), d.getCompany(), d.getBuyerType(), d.getBuyerType().label(), d.getProduct(),
                d.getProduct().label(), d.getCategory(), d.getCategory().label(), d.getVolumeTons(), d.getPriceMin(), d.getPriceMax(),
                d.getContractYears(), d.getDistrict(), d.getDistrict().label(), d.getLatitude(), d.getLongitude(), d.getRadiusKm(),
                d.getQuality(), d.getSeason(), d.getStatus(), d.getStatus().label(), d.getVerifiedAt(), d.getCreatedAt(),
                signed.size(), contracted);
    }

    LetterView view(LetterOfIntent l) {
        BuyerDemand d = l.getDemand();
        User req = l.getRequester();
        return new LetterView(l.getId(), d.getId(), d.getCode(), d.getCompany(), d.getProduct().label(), req.getFullName(),
                l.getPlot() == null ? null : l.getPlot().getId(), l.getPlot() == null ? null : l.getPlot().getCode(),
                l.getApplication() == null ? null : l.getApplication().getId(), l.getApplication() == null ? null : l.getApplication().getAppNumber(),
                l.getProject() == null ? null : l.getProject().getId(), l.getProject() == null ? null : l.getProject().getCode(),
                l.getVolumeTons(), l.getPrice(), l.getStatus(), l.getStatus().label(), l.getMessage(), l.getBuyerNote(), l.getSignedAt(), l.getCreatedAt());
    }

    /** Sahənin yetkin ildə gözlənilən məhsulu (ton). */
    static double expectedProduction(Plot p, PlotCategory c) {
        double soil = p.getSoilQuality() == null ? 60 : p.getSoilQuality();
        double factor = (0.7 + 0.3 * soil / 100.0) * (p.isIrrigation() ? 1.1 : 0.8);
        double area = p.getAreaHa().doubleValue() * (c == PlotCategory.ISTIXANA ? 0.15 : 1);
        return c.yieldTonPerHa() * factor * area;
    }

    static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371, dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private String nextCode() {
        Set<String> used = new HashSet<>();
        demands.findAll().forEach(x -> used.add(x.getCode()));
        long n = used.size() + 1;
        String code;
        do { code = String.format(Locale.ROOT, "TLB-%d-%03d", Year.now().getValue(), n++); } while (used.contains(code));
        return code;
    }

    private static String fmt(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }
}
