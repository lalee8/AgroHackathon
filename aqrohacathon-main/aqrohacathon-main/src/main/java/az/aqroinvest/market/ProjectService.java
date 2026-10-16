package az.aqroinvest.market;

import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.market.MarketEnums.*;
import az.aqroinvest.market.ProjectDtos.*;
import az.aqroinvest.notification.NotificationService;
import az.aqroinvest.plot.Plot;
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
import java.time.Year;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sahibkar ↔ investor bazarı. Sahibkar layihəsini strukturlaşdırılmış formada (rəqəmlərlə) təqdim edir, platforma
 * iddiaları yoxlayır və təklifi investor dilinə çevirir; investor öz profilinə uyğun layihələri görür və maraq bildirir.
 * Əlaqə məlumatları yalnız sahibkar razılıq verdikdən sonra hər iki tərəfə açılır.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projects;
    private final ProjectInterestRepository interests;
    private final InvestorProfileRepository profiles;
    private final PlotRepository plots;
    private final UserRepository users;
    private final ProjectAnalysisService analysis;
    private final NotificationService notifications;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final Map<String, ProjectAnalysisService.Analysis> memo = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ oxuma

    @Transactional(readOnly = true)
    public List<ProjectCard> list(ProjectSector sector, DealType deal, ProfileView filter, String sort) {
        ProfileView profile = filter;
        if (profile == null) profile = currentUser.optional().flatMap(u -> profiles.findById(u.id())).map(ProjectService::view).orElse(null);
        ProfileView pf = profile;
        List<ProjectCard> out = new ArrayList<>();
        for (Project p : projects.findPublished(ProjectStatus.PUBLISHED)) {
            if (sector != null && p.getSector() != sector) continue;
            if (deal != null && p.getDealType() != deal) continue;
            out.add(card(p, pf));
        }
        Comparator<ProjectCard> c = switch (sort == null ? (pf != null ? "match" : "new") : sort) {
            case "match" -> Comparator.comparingInt((ProjectCard x) -> x.match() == null ? 0 : -x.match().score());
            case "credibility" -> Comparator.comparingInt((ProjectCard x) -> -x.credibility());
            case "ask" -> Comparator.comparing(ProjectCard::askAmount);
            default -> Comparator.comparing(ProjectCard::createdAt).reversed();
        };
        out.sort(c);
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sectors() {
        List<Project> pub = projects.findPublished(ProjectStatus.PUBLISHED);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProjectSector s : ProjectSector.values()) {
            List<Project> ps = pub.stream().filter(p -> p.getSector() == s).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", s.name());
            m.put("label", s.label());
            m.put("icon", s.icon());
            m.put("projects", ps.size());
            m.put("totalAsk", ps.stream().map(Project::getAskAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            m.put("jobs", ps.stream().mapToInt(Project::getJobs).sum());
            out.add(m);
        }
        return out;
    }

    @Transactional
    public ProjectDetail detail(Long id) {
        Project p = projects.findFull(id).orElseThrow(() -> ApiException.notFound("Layihə"));
        Optional<AuthPrincipal> me = currentUser.optional();
        boolean owner = me.isPresent() && me.get().id().equals(p.getOwner().getId());
        boolean staff = me.isPresent() && me.get().isStaff();
        if (p.getStatus() == ProjectStatus.DRAFT && !owner && !staff) throw ApiException.notFound("Layihə");
        if (!owner) p.setViews(p.getViews() + 1);
        ProfileView pf = me.flatMap(u -> profiles.findById(u.id())).map(ProjectService::view).orElse(null);
        InterestView mine = null;
        Contact ownerContact = null;
        if (me.isPresent() && !owner) {
            Optional<ProjectInterest> i = interests.findByProjectIdAndInvestorId(p.getId(), me.get().id());
            if (i.isPresent()) {
                mine = interestView(i.get(), false);
                if (i.get().getStatus() == InterestStatus.ACCEPTED) ownerContact = contact(p.getOwner());
            }
        }
        List<InterestView> list = owner || staff ? interests.findByProject(p.getId()).stream().map(i -> interestView(i, owner)).toList() : List.of();
        return new ProjectDetail(card(p, pf), p.getProblem(), p.getSolution(), p.getMarket(), p.getTeam(), p.getScale(),
                p.getEquityPercent(), p.getInterestPercent(), p.getRevenueSharePercent(), p.getClaimedRevenue(),
                p.getClaimedProfit(), p.getClaimedYield(), p.getClaimedPrice(), p.getViews(), analyze(p), owner, mine, list, ownerContact);
    }

    @Transactional(readOnly = true)
    public List<ProjectCard> byPlot(Long plotId) {
        return projects.findPublishedByPlot(plotId).stream().map(p -> card(p, null)).toList();
    }

    /** Məsləhətçi üçün: layihənin investor dilində qısa xülasəsi (şəxsi məlumatsız). */
    @Transactional(readOnly = true)
    public String detailAnalysisHeadline(Long id) {
        return projects.findFull(id).map(p -> analyze(p).terms().headline()).orElse("");
    }

    public ProjectAnalysisService.Analysis preview(ProjectRequest r) {
        return analysis.analyze(input(r));
    }

    // ------------------------------------------------------------------ sahibkar

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        AuthPrincipal me = currentUser.require();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Project p : projects.findByOwner(me.id())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("project", card(p, null));
            m.put("interests", interests.findByProject(p.getId()).stream().map(i -> interestView(i, true)).toList());
            out.add(m);
        }
        return out;
    }

    @Transactional
    public ProjectDetail create(ProjectRequest r) {
        AuthPrincipal me = requireInvestor();
        Project p = new Project();
        p.setOwner(users.getReferenceById(me.id()));
        p.setCode(nextCode());
        apply(p, r);
        p.setStatus(Boolean.TRUE.equals(r.publish()) ? ProjectStatus.PUBLISHED : ProjectStatus.DRAFT);
        projects.save(p);
        audit.log("PROJECT_CREATED", "Project", p.getId(), p.getCode() + " " + p.getTitle());
        return detail(p.getId());
    }

    @Transactional
    public ProjectDetail update(Long id, ProjectRequest r) {
        Project p = owned(id);
        apply(p, r);
        if (Boolean.TRUE.equals(r.publish()) && p.getStatus() == ProjectStatus.DRAFT) p.setStatus(ProjectStatus.PUBLISHED);
        p.setVerified(false);
        audit.log("PROJECT_UPDATED", "Project", p.getId(), p.getCode());
        return detail(p.getId());
    }

    @Transactional
    public ProjectDetail setStatus(Long id, ProjectStatus status) {
        Project p = owned(id);
        p.setStatus(status);
        audit.log("PROJECT_STATUS", "Project", p.getId(), status.name());
        return detail(p.getId());
    }

    @Transactional
    public InterestView respond(Long interestId, RespondRequest r) {
        AuthPrincipal me = currentUser.require();
        ProjectInterest i = interests.findById(interestId).orElseThrow(() -> ApiException.notFound("Maraq"));
        Project p = i.getProject();
        if (!p.getOwner().getId().equals(me.id())) throw ApiException.notFound("Maraq");
        i.setStatus(r.accept() ? InterestStatus.ACCEPTED : InterestStatus.DECLINED);
        if (r.accept() && i.getAcceptedAt() == null) i.setAcceptedAt(Instant.now());
        i.setReply(r.reply());
        notifications.notify(i.getInvestor().getId(), r.accept() ? "INTEREST_ACCEPTED" : "INTEREST_DECLINED",
                r.accept() ? "Sahibkar görüşə razıdır: " + p.getTitle() : "Sahibkar təklifinizi qəbul etmədi",
                r.accept() ? "Əlaqə məlumatları açıldı. " + (r.reply() == null ? "" : r.reply()) : (r.reply() == null ? p.getTitle() : r.reply()),
                "/layihe.html?id=" + p.getId());
        audit.log(r.accept() ? "INTEREST_ACCEPTED" : "INTEREST_DECLINED", "ProjectInterest", i.getId(), p.getCode());
        return interestView(i, true);
    }

    // ------------------------------------------------------------------ investor

    @Transactional
    public InterestView interest(Long projectId, InterestRequest r) {
        AuthPrincipal me = requireInvestor();
        Project p = projects.findFull(projectId).orElseThrow(() -> ApiException.notFound("Layihə"));
        if (p.getStatus() != ProjectStatus.PUBLISHED) throw ApiException.badRequest("NOT_PUBLISHED", "Layihə maraq qəbul etmir");
        if (p.getOwner().getId().equals(me.id())) throw ApiException.badRequest("OWN_PROJECT", "Öz layihənizə maraq bildirə bilməzsiniz");
        ProjectInterest i = interests.findByProjectIdAndInvestorId(projectId, me.id()).orElseGet(ProjectInterest::new);
        boolean fresh = i.getId() == null;
        i.setProject(p);
        i.setInvestor(users.getReferenceById(me.id()));
        i.setAmount(r.amount());
        i.setMessage(r.message());
        if (fresh || i.getStatus() == InterestStatus.DECLINED) i.setStatus(InterestStatus.NEW);
        interests.save(i);
        notifications.notify(p.getOwner().getId(), "PROJECT_INTEREST", "Yeni investor marağı: " + money(r.amount()),
                me.fullName() + " \"" + p.getTitle() + "\" layihəsinə maraq bildirdi." + (r.message() == null || r.message().isBlank() ? "" : " \"" + trim(r.message(), 140) + "\""),
                "/layihe.html?id=" + p.getId() + "#interests");
        audit.log("PROJECT_INTEREST", "Project", p.getId(), money(r.amount()));
        return interestView(i, false);
    }

    @Transactional(readOnly = true)
    public List<InterestView> myInterests() {
        AuthPrincipal me = currentUser.require();
        return interests.findByInvestor(me.id()).stream().map(i -> {
            InterestView v = interestView(i, false);
            Contact c = i.getStatus() == InterestStatus.ACCEPTED ? contact(i.getProject().getOwner()) : null;
            return new InterestView(v.id(), v.projectId(), v.projectCode(), v.projectTitle(), v.investorName(), v.amount(),
                    v.message(), v.status(), v.statusLabel(), v.reply(), c, v.createdAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ProfileView profile() {
        AuthPrincipal me = currentUser.require();
        return profiles.findById(me.id()).map(ProjectService::view).orElse(new ProfileView(List.of(), null, null, Risk.MID, 10));
    }

    @Transactional
    public ProfileView saveProfile(ProfileView v) {
        AuthPrincipal me = currentUser.require();
        InvestorProfile p = profiles.findById(me.id()).orElseGet(() -> { InvestorProfile n = new InvestorProfile(); n.setUserId(me.id()); return n; });
        p.setSectors(v.sectors() == null ? new ArrayList<>() : new ArrayList<>(v.sectors().stream().filter(s -> {
            try { ProjectSector.valueOf(s); return true; } catch (IllegalArgumentException e) { return false; }
        }).toList()));
        p.setTicketMin(v.ticketMin());
        p.setTicketMax(v.ticketMax());
        p.setRisk(v.risk() == null ? Risk.MID : v.risk());
        p.setHorizonYears(v.horizonYears());
        profiles.save(p);
        return view(p);
    }

    // ------------------------------------------------------------------ admin

    @Transactional
    public ProjectDetail verify(Long id, boolean verified) {
        Project p = projects.findFull(id).orElseThrow(() -> ApiException.notFound("Layihə"));
        p.setVerified(verified);
        if (verified) notifications.notify(p.getOwner().getId(), "PROJECT_VERIFIED", "Layihəniz yoxlanıldı",
                "\"" + p.getTitle() + "\" Nazirlik tərəfindən yoxlanıldı və investorlara \"yoxlanılıb\" nişanı ilə göstərilir.", "/layihe.html?id=" + p.getId());
        audit.log(verified ? "PROJECT_VERIFIED" : "PROJECT_UNVERIFIED", "Project", p.getId(), p.getCode());
        return detail(id);
    }

    // ------------------------------------------------------------------ köməkçilər

    public ProjectAnalysisService.Analysis analyze(Project p) {
        String key = p.getId() + ":" + p.getVersion();
        return memo.computeIfAbsent(key, k -> analysis.analyze(input(p)));
    }

    ProjectCard card(Project p, ProfileView pf) {
        ProjectAnalysisService.Analysis a = analyze(p);
        String risk = a.summary().probPayback() >= 0.85 && a.credibility() >= 70 ? "LOW" : a.summary().probPayback() >= 0.55 && a.credibility() >= 45 ? "MID" : "HIGH";
        // startap və ideya mərhələsindəki layihələr regional istinadla yoxlanıla bilmir: risk ən azı "yüksək"
        if (p.getSector().useCase() == null || p.getStage() == Stage.IDEA) risk = "HIGH";
        Plot pl = p.getPlot();
        PlotRef ref = pl == null ? null : new PlotRef(pl.getId(), pl.getCode(), pl.getName(), pl.getDistrict().label(), pl.getDesignation().label());
        return new ProjectCard(p.getId(), p.getCode(), p.getTitle(), p.getTagline(), p.getSector(), p.getSector().label(),
                p.getSector().icon(), p.getStage(), p.getStage().label(), p.getDistrict(),
                p.getDistrict() == null ? (pl == null ? null : pl.getDistrict().label()) : p.getDistrict().label(), ref,
                p.getCapex(), p.getOwnFunds(), p.getAskAmount(), p.getDealType(), p.getDealType().label(), a.terms().offer(),
                p.getJobs(), p.isVerified(), p.getStatus(), p.getStatus().label(), shortName(p.getOwner().getFullName()),
                interests.countByProjectId(p.getId()), a.credibility(), a.credibilityLabel(), a.summary().probPayback(),
                a.summary().paybackP50(), risk, Risk.valueOf(risk).label(), pf == null ? null : match(p, a, risk, pf), p.getCreatedAt());
    }

    static Match match(Project p, ProjectAnalysisService.Analysis a, String risk, ProfileView pf) {
        int s = 0;
        List<String> why = new ArrayList<>();
        if (pf.sectors() == null || pf.sectors().isEmpty()) s += 20;
        else if (pf.sectors().contains(p.getSector().name())) { s += 40; why.add("Sizin istiqamətiniz: " + p.getSector().label()); }
        double ask = p.getAskAmount().doubleValue();
        double min = pf.ticketMin() == null ? 0 : pf.ticketMin().doubleValue(), max = pf.ticketMax() == null ? Double.MAX_VALUE : pf.ticketMax().doubleValue();
        if (ask >= min && ask <= max) { s += 25; why.add("Məbləğ büdcənizə uyğundur (" + money(p.getAskAmount()) + ")"); }
        else if (ask > max && max > 0) { s += 10; why.add("Məbləğ büdcənizdən böyükdür: digər investorla birgə iştirak mümkündür"); }
        Risk r = Risk.valueOf(risk);
        if (pf.risk() == null || pf.risk() == r) { s += 20; why.add("Risk səviyyəsi sizə uyğundur: " + r.label().toLowerCase(Locale.ROOT)); }
        else if (Math.abs(pf.risk().ordinal() - r.ordinal()) == 1) s += 10;
        Integer pb = a.summary().paybackP50();
        int hz = pf.horizonYears() == null ? 10 : pf.horizonYears();
        if (pb != null && pb <= hz) { s += 15; why.add("Orta ssenaridə " + pb + " ildə qayıdır (sizin üfüq " + hz + " il)"); }
        return new Match(Math.min(100, s), why);
    }

    private InterestView interestView(ProjectInterest i, boolean forOwner) {
        Contact c = forOwner && i.getStatus() == InterestStatus.ACCEPTED ? contact(i.getInvestor()) : null;
        Project p = i.getProject();
        return new InterestView(i.getId(), p.getId(), p.getCode(), p.getTitle(),
                forOwner ? i.getInvestor().getFullName() : shortName(i.getInvestor().getFullName()), i.getAmount(), i.getMessage(),
                i.getStatus(), i.getStatus().label(), i.getReply(), c, i.getCreatedAt());
    }

    private static Contact contact(User u) {
        return new Contact(u.getFullName(), u.getEmail(), u.getPhone());
    }

    private Project owned(Long id) {
        AuthPrincipal me = currentUser.require();
        Project p = projects.findFull(id).orElseThrow(() -> ApiException.notFound("Layihə"));
        if (!p.getOwner().getId().equals(me.id())) throw ApiException.notFound("Layihə");
        return p;
    }

    private AuthPrincipal requireInvestor() {
        AuthPrincipal me = currentUser.require();
        if (me.role() != Role.INVESTOR) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bu əməliyyat yalnız investor / sahibkar hesabı üçündür");
        return me;
    }

    private void apply(Project p, ProjectRequest r) {
        Plot plot = r.plotId() == null ? null : plots.findById(r.plotId()).orElseThrow(() -> ApiException.badRequest("PLOT_NOT_FOUND", "Sahə tapılmadı"));
        if (r.dealType() == DealType.EQUITY && r.equityPercent() == null) throw ApiException.badRequest("EQUITY_REQUIRED", "Təklif olunan pay faizini göstərin");
        if (r.dealType() == DealType.LOAN && r.interestPercent() == null) throw ApiException.badRequest("INTEREST_REQUIRED", "Borcun illik faizini göstərin");
        if (r.dealType() == DealType.REVENUE_SHARE && r.revenueSharePercent() == null) throw ApiException.badRequest("SHARE_REQUIRED", "Gəlirdən payın faizini göstərin");
        p.setSector(r.sector());
        p.setUseCase(r.sector().useCase());
        p.setPlot(plot);
        p.setDistrict(r.district() != null ? r.district() : plot != null ? plot.getDistrict() : null);
        p.setTitle(r.title().trim());
        p.setTagline(r.tagline());
        p.setProblem(r.problem());
        p.setSolution(r.solution());
        p.setMarket(r.market());
        p.setTeam(r.team());
        p.setStage(r.stage() == null ? Stage.PLAN : r.stage());
        p.setScale(r.scale());
        p.setCapex(r.capex());
        p.setOwnFunds(r.ownFunds() == null ? BigDecimal.ZERO : r.ownFunds());
        p.setAskAmount(r.askAmount());
        p.setDealType(r.dealType());
        p.setEquityPercent(r.equityPercent());
        p.setInterestPercent(r.interestPercent());
        p.setRevenueSharePercent(r.revenueSharePercent());
        p.setClaimedRevenue(r.claimedRevenue());
        p.setClaimedProfit(r.claimedProfit());
        p.setClaimedYield(r.claimedYield());
        p.setClaimedPrice(r.claimedPrice());
        p.setJobs(r.jobs() == null ? 0 : r.jobs());
    }

    static ProjectAnalysisService.Input input(Project p) {
        return new ProjectAnalysisService.Input(p.getSector(), p.getPlot() == null ? null : p.getPlot().getId(), p.getDistrict(),
                p.getStage(), d(p.getScale()), p.getCapex().doubleValue(), p.getOwnFunds().doubleValue(), p.getAskAmount().doubleValue(),
                p.getDealType(), d(p.getEquityPercent()), d(p.getInterestPercent()), d(p.getRevenueSharePercent()),
                d(p.getClaimedRevenue()), d(p.getClaimedProfit()), d(p.getClaimedYield()), d(p.getClaimedPrice()));
    }

    static ProjectAnalysisService.Input input(ProjectRequest r) {
        return new ProjectAnalysisService.Input(r.sector(), r.plotId(), r.district(), r.stage() == null ? Stage.PLAN : r.stage(),
                d(r.scale()), r.capex().doubleValue(), r.ownFunds() == null ? 0 : r.ownFunds().doubleValue(), r.askAmount().doubleValue(),
                r.dealType(), d(r.equityPercent()), d(r.interestPercent()), d(r.revenueSharePercent()), d(r.claimedRevenue()),
                d(r.claimedProfit()), d(r.claimedYield()), d(r.claimedPrice()));
    }

    private String nextCode() {
        int y = Year.now().getValue();
        long n = projects.count() + 1;
        String code;
        do { code = String.format(Locale.ROOT, "LYH-%d-%03d", y, n++); } while (projectsCodeExists(code));
        return code;
    }

    private boolean projectsCodeExists(String code) {
        return projects.findAll().stream().anyMatch(p -> p.getCode().equals(code));
    }

    static ProfileView view(InvestorProfile p) {
        return new ProfileView(p.getSectors(), p.getTicketMin(), p.getTicketMax(), p.getRisk(), p.getHorizonYears());
    }

    private static Double d(BigDecimal b) {
        return b == null ? null : b.doubleValue();
    }

    static String shortName(String full) {
        String[] parts = full.trim().split("\\s+");
        return parts.length < 2 ? full : parts[0] + " " + parts[1].charAt(0) + ".";
    }

    static String money(BigDecimal v) {
        return ProjectAnalysisService.money(v.doubleValue());
    }

    private static String trim(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
