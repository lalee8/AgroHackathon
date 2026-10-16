package az.aqroinvest.application;

import az.aqroinvest.application.ApplicationDtos.*;
import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.common.Masking;
import az.aqroinvest.competition.Competition;
import az.aqroinvest.competition.CompetitionService;
import az.aqroinvest.document.DocumentStorageService;
import az.aqroinvest.integration.ektis.EktisGateway;
import az.aqroinvest.integration.ektis.IntegrationEventPublisher;
import az.aqroinvest.notification.NotificationService;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotService;
import az.aqroinvest.plot.PlotStatus;
import az.aqroinvest.plot.Technology;
import az.aqroinvest.security.AuthPrincipal;
import az.aqroinvest.security.CurrentUser;
import az.aqroinvest.user.Role;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.Year;
import java.util.*;

/**
 * Elektron müraciət sisteminin əsas biznes məntiqi:
 * qaralama → sənədlər → göndərmə (nömrə + avtomatik bal + EKTİS yoxlaması) → baxış → qərar.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApplicationRepository applications;
    private final ApplicationDocumentRepository documents;
    private final EvaluationRepository evaluations;
    private final CompetitionService competitionService;
    private final PlotService plotService;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final DocumentStorageService storage;
    private final ScoringService scoring;
    private final NotificationService notifications;
    private final AuditService audit;
    private final IntegrationEventPublisher events;
    private final EktisGateway ektis;
    private final ObjectMapper mapper;
    private final az.aqroinvest.commission.CommissionService commission;

    // ------------------------------------------------------------------ investor

    @Transactional
    public ApplicationView createDraft(ApplicationRequest req) {
        AuthPrincipal me = requireInvestor();
        Competition comp = competitionService.find(req.competitionId());
        Plot plot = plotService.find(req.plotId());
        ensureOpenFor(comp, plot);
        if (applications.existsByApplicantIdAndPlotIdAndCompetitionIdAndStatusNot(me.id(), plot.getId(), comp.getId(),
                ApplicationStatus.WITHDRAWN)) {
            throw ApiException.conflict("DUPLICATE_APPLICATION",
                    "Bu sahə üzrə artıq müraciətiniz var. 'Kabinetim' bölməsindən davam edin.");
        }
        Application a = new Application();
        a.setApplicant(users.getReferenceById(me.id()));
        a.setCompetition(comp);
        a.setPlot(plot);
        a.setTrackingCode(randomCode(8));
        a.setStatus(ApplicationStatus.DRAFT);
        applyRequest(a, req);
        if (a.getActivityType() == null) a.setActivityType(plot.getCategory());
        applications.save(a);
        addHistory(a, null, ApplicationStatus.DRAFT, "Qaralama yaradıldı", me);
        audit.log("APPLICATION_DRAFT_CREATED", "Application", a.getId(), plot.getCode());
        return toView(a, me);
    }

    @Transactional
    public ApplicationView updateDraft(Long id, ApplicationRequest req) {
        AuthPrincipal me = currentUser.require();
        Application a = findOwned(id, me);
        if (!a.getStatus().isEditableByApplicant()) {
            throw ApiException.conflict("NOT_EDITABLE", "Müraciət '" + a.getStatus().label() + "' statusundadır və redaktə edilə bilməz");
        }
        if (!a.getPlot().getId().equals(req.plotId()) || !a.getCompetition().getId().equals(req.competitionId())) {
            throw ApiException.badRequest("PLOT_LOCKED", "Müraciətin sahəsi və müsabiqəsi dəyişdirilə bilməz");
        }
        applyRequest(a, req);
        if (a.getStatus() == ApplicationStatus.INFO_REQUESTED) scoring.apply(a);
        return toView(a, me);
    }

    @Transactional
    public ApplicationView uploadDocument(Long id, DocumentType type, MultipartFile file) {
        AuthPrincipal me = currentUser.require();
        Application a = findOwned(id, me);
        if (!a.getStatus().isEditableByApplicant()) {
            throw ApiException.conflict("NOT_EDITABLE", "Bu mərhələdə sənəd əlavə etmək mümkün deyil");
        }
        if (a.getDocuments().size() >= 15) {
            throw ApiException.badRequest("TOO_MANY_FILES", "Bir müraciətə ən çox 15 sənəd əlavə edilə bilər");
        }
        DocumentStorageService.StoredFile f = storage.store(file);
        ApplicationDocument d = new ApplicationDocument();
        d.setApplication(a);
        d.setDocType(type);
        d.setOriginalName(f.originalName());
        d.setStoredName(f.storedName());
        d.setContentType(f.contentType());
        d.setSizeBytes(f.size());
        d.setSha256(f.sha256());
        d.setUploadedBy(users.getReferenceById(me.id()));
        a.getDocuments().add(d);
        a.setUpdatedAt(Instant.now());
        audit.log("DOCUMENT_UPLOADED", "Application", a.getId(), type + " " + f.originalName() + " sha256=" + f.sha256());
        return toView(a, me);
    }

    @Transactional
    public ApplicationView deleteDocument(Long id, Long docId) {
        AuthPrincipal me = currentUser.require();
        Application a = findOwned(id, me);
        if (!a.getStatus().isEditableByApplicant()) {
            throw ApiException.conflict("NOT_EDITABLE", "Bu mərhələdə sənəd silmək mümkün deyil");
        }
        ApplicationDocument d = a.getDocuments().stream().filter(x -> x.getId().equals(docId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Sənəd"));
        if (a.getStatus() != ApplicationStatus.DRAFT) {
            throw ApiException.conflict("NOT_EDITABLE", "Göndərilmiş sənədlər silinə bilməz - yalnız yeni sənəd əlavə edin");
        }
        a.getDocuments().remove(d);
        storage.delete(d.getStoredName());
        return toView(a, me);
    }

    @Transactional
    public ApplicationView submit(Long id) {
        AuthPrincipal me = currentUser.require();
        Application a = findOwned(id, me);
        if (a.getStatus() != ApplicationStatus.DRAFT) {
            throw ApiException.conflict("INVALID_TRANSITION", "Yalnız qaralama müraciət göndərilə bilər");
        }
        ensureOpenFor(a.getCompetition(), a.getPlot());
        List<String> missing = missingRequirements(a);
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("INCOMPLETE_APPLICATION", "Müraciət tam deyil: " + String.join("; ", missing));
        }
        a.setAppNumber(String.format("AQR-%d-%06d", Year.now().getValue(), applications.nextApplicationNumber()));
        a.setStatus(ApplicationStatus.SUBMITTED);
        a.setSubmittedAt(Instant.now());
        scoring.apply(a);

        // EKTİS fermer reyestrində sinxron yoxlama (əlçatmaz olduqda müraciət bloklanmır)
        String taxId = a.getVoen() != null && !a.getVoen().isBlank() ? a.getVoen() : a.getApplicantType() == ApplicantType.FOREIGN ? null : a.getFin();
        if (taxId == null || taxId.isBlank()) {
            // Xarici vətəndaşın FİN-i yoxdur: EKTİS fermer reyestri üzrə yoxlama qalib elan edildikdən sonra qeydiyyatla aparılır
            a.setEktisFarmerStatus("NOT_APPLICABLE");
        } else {
            EktisGateway.FarmerCheck check = ektis.checkFarmer(taxId);
            a.setEktisFarmerStatus(check.status());
        }

        addHistory(a, ApplicationStatus.DRAFT, ApplicationStatus.SUBMITTED,
                "Müraciət elektron qaydada qəbul edildi. Nömrə: " + a.getAppNumber(), me);
        events.publish("APPLICATION_SUBMITTED", "Application", a.getId(), exchangePayload(a));
        notifications.notify(me.id(), "STATUS", "Müraciətiniz qəbul edildi",
                "Müraciət nömrəsi: " + a.getAppNumber() + ". İzləmə kodu: " + a.getTrackingCode(),
                "/muraciet-detal.html?id=" + a.getId());
        notifyStaff("Yeni müraciət: " + a.getAppNumber(), a.displayName() + " - " + a.getPlot().getCode(), a.getId());
        audit.log("APPLICATION_SUBMITTED", "Application", a.getId(), a.getAppNumber() + " score=" + a.getAutoScore());
        return toView(a, me);
    }

    @Transactional
    public ApplicationView respond(Long id, String comment) {
        AuthPrincipal me = currentUser.require();
        Application a = findOwned(id, me);
        if (a.getStatus() != ApplicationStatus.INFO_REQUESTED) {
            throw ApiException.conflict("INVALID_TRANSITION", "Müraciət üzrə əlavə məlumat tələb olunmayıb");
        }
        scoring.apply(a);
        changeStatusInternal(a, ApplicationStatus.UNDER_REVIEW, "İnvestorun cavabı: " + comment, me);
        notifyStaff("Əlavə məlumat təqdim edildi: " + a.getAppNumber(), comment, a.getId());
        return toView(a, me);
    }

    @Transactional
    public void withdraw(Long id) {
        AuthPrincipal me = currentUser.require();
        Application a = findOwned(id, me);
        if (a.getStatus() == ApplicationStatus.DRAFT) {
            a.getDocuments().forEach(d -> storage.delete(d.getStoredName()));
            applications.delete(a);
            audit.log("APPLICATION_DRAFT_DELETED", "Application", id, null);
            return;
        }
        if (a.getStatus().isFinal()) {
            throw ApiException.conflict("INVALID_TRANSITION", "Yekunlaşmış müraciət geri çağırıla bilməz");
        }
        changeStatusInternal(a, ApplicationStatus.WITHDRAWN, "Müraciət investor tərəfindən geri çağırıldı", me);
    }

    @Transactional(readOnly = true)
    public List<ApplicationListItem> my() {
        AuthPrincipal me = currentUser.require();
        return applications.findByApplicantIdOrderByCreatedAtDesc(me.id()).stream().map(this::toListItem).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationView get(Long id) {
        AuthPrincipal me = currentUser.require();
        Application a = me.isStaff() ? find(id) : findOwned(id, me);
        return toView(a, me);
    }

    /** Qaralama üzrə gözlənilən avtomatik balın canlı proqnozu (investorun özü üçün). */
    @Transactional(readOnly = true)
    public ScoringService.ScoreResult scorePreview(Long id) {
        AuthPrincipal me = currentUser.require();
        Application a = me.isStaff() ? find(id) : findOwned(id, me);
        return scoring.score(a);
    }

    @Transactional(readOnly = true)
    public ApplicationDocument document(Long id, Long docId) {
        AuthPrincipal me = currentUser.require();
        Application a = me.isStaff() ? find(id) : findOwned(id, me);
        ApplicationDocument d = a.getDocuments().stream().filter(x -> x.getId().equals(docId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Sənəd"));
        if (me.isStaff()) audit.log("DOCUMENT_VIEWED", "Application", a.getId(), d.getOriginalName());
        return d;
    }

    @Transactional(readOnly = true)
    public TrackView track(String number, String code) {
        if (number == null || code == null) throw ApiException.badRequest("BAD_REQUEST", "Nömrə və izləmə kodu tələb olunur");
        Application a = applications.findByAppNumberAndTrackingCode(number.trim().toUpperCase(Locale.ROOT),
                        code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Bu nömrə və kodla müraciət"));
        String name = a.displayName();
        String masked = name.length() <= 3 ? name : name.substring(0, 3) + "***";
        return new TrackView(a.getAppNumber(), a.getStatus(), a.getStatus().label(), a.getDecision(),
                a.getDecision() == null ? null : a.getDecision().label(), masked,
                a.getPlot().getCode() + " - " + a.getPlot().getName(), a.getCompetition().getTitle(),
                a.getSubmittedAt(), a.getUpdatedAt(),
                a.getHistory().stream().filter(h -> h.getToStatus() != ApplicationStatus.DRAFT)
                        .map(h -> new HistoryView(h.getFromStatus(), label(h.getFromStatus()), h.getToStatus(),
                                h.getToStatus().label(), null, null, h.getCreatedAt())).toList());
    }

    // ------------------------------------------------------------------ staff

    @Transactional(readOnly = true)
    public List<ApplicationListItem> search(ApplicationStatus status, Long competitionId, String district, String q) {
        Specification<Application> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.notEqual(root.get("status"), ApplicationStatus.DRAFT));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (competitionId != null) p.add(cb.equal(root.get("competition").get("id"), competitionId));
            if (district != null && !district.isBlank()) p.add(cb.equal(root.get("plot").get("district").as(String.class), district));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("appNumber")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("companyName"), "")), like),
                        cb.like(cb.lower(root.get("applicant").get("fullName")), like),
                        cb.like(cb.lower(root.get("plot").get("code")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return applications.findAll(spec, Sort.by(Sort.Direction.DESC, "submittedAt")).stream().map(this::toListItem).toList();
    }

    /** Reytinq: yekun bala görə; bərabər balda yerli müraciətçiyə üstünlük verilir (Qaydalar 4.10.7). */
    @Transactional(readOnly = true)
    public List<ApplicationListItem> ranking(Long competitionId) {
        return applications.findByCompetitionIdAndStatusNotOrderByAutoScoreDesc(competitionId, ApplicationStatus.DRAFT)
                .stream().filter(a -> a.getStatus() != ApplicationStatus.WITHDRAWN)
                .map(a -> Map.entry(toListItem(a), isLocal(a)))
                .sorted(Comparator.comparing((Map.Entry<ApplicationListItem, Boolean> e) -> e.getKey().finalScore(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(e -> !e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Yerli müraciətçi: məcburi köçkün / Qarabağ əsilli və ya torpağın yerləşdiyi rayonun sakini. */
    static boolean isLocal(Application a) {
        az.aqroinvest.user.User u = a.getApplicant();
        return a.isIdpStatus() || (u != null && u.isLocalResident() && u.getHomeDistrict() == a.getPlot().getDistrict());
    }

    @Transactional
    public ApplicationView changeStatus(Long id, StatusChangeRequest req) {
        AuthPrincipal me = currentUser.require();
        Application a = find(id);
        ApplicationStatus target = req.status();
        if (!a.getStatus().canAdminMoveTo(target)) {
            throw ApiException.conflict("INVALID_TRANSITION",
                    "'" + a.getStatus().label() + "' statusundan '" + target.label() + "' statusuna keçid mümkün deyil");
        }
        String comment = req.comment() == null ? null : req.comment().trim();
        if (target == ApplicationStatus.INFO_REQUESTED && (comment == null || comment.isEmpty())) {
            throw ApiException.badRequest("COMMENT_REQUIRED", "Hansı məlumatın tələb olunduğunu şərhdə qeyd edin");
        }
        if (target == ApplicationStatus.DECIDED) {
            if (req.decision() == null) throw ApiException.badRequest("DECISION_REQUIRED", "Qərar (təsdiq/imtina) seçilməlidir");
            if (req.decision() == Decision.APPROVED && applications.existsApprovedForPlot(a.getPlot().getId())) {
                throw ApiException.conflict("PLOT_ALREADY_AWARDED", "Bu sahə üzrə artıq qalib müəyyən edilib");
            }
            // Qərar komissiyanın səsverməsinə zidd ola bilməz
            commission.checkDecision(a, req.decision());
            a.setDecision(req.decision());
            a.setDecisionNote(comment);
            a.setDecidedAt(Instant.now());
            if (req.decision() == Decision.APPROVED) a.getPlot().setStatus(PlotStatus.AWARDED);
        }
        String historyComment = target == ApplicationStatus.DECIDED
                ? req.decision().label() + (comment == null || comment.isEmpty() ? "" : ". " + comment)
                : comment;
        changeStatusInternal(a, target, historyComment, me);
        if (target == ApplicationStatus.DECIDED) {
            events.publish("APPLICATION_DECIDED", "Application", a.getId(), Map.of(
                    "applicationNumber", a.getAppNumber(), "decision", a.getDecision().name(),
                    "plotCode", a.getPlot().getCode(), "investmentAmount", a.getInvestmentAmount(),
                    "jobsPlanned", a.getJobsPlanned(), "taxId", Objects.toString(a.getVoen() != null ? a.getVoen() : a.getFin(), "")));
        }
        return toView(a, me);
    }

    @Transactional
    public ApplicationView evaluate(Long id, EvaluationRequest req) {
        AuthPrincipal me = currentUser.require();
        Application a = find(id);
        if (a.getStatus() != ApplicationStatus.UNDER_REVIEW && a.getStatus() != ApplicationStatus.SUBMITTED
                && a.getStatus() != ApplicationStatus.INFO_REQUESTED) {
            throw ApiException.conflict("NOT_EVALUABLE", "Bu statusda qiymətləndirmə aparmaq mümkün deyil");
        }
        Evaluation e = evaluations.findByApplicationIdAndExpertId(id, me.id()).orElseGet(Evaluation::new);
        e.setApplication(a);
        e.setExpert(users.getReferenceById(me.id()));
        int total = scoring.expertTotal(req.scores(), competitionService.criteriaOf(a.getCompetition()));
        try {
            e.setScores(mapper.writeValueAsString(req.scores()));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        e.setTotal(total);
        e.setComment(req.comment());
        e.setCreatedAt(Instant.now());
        evaluations.save(e);
        audit.log("APPLICATION_EVALUATED", "Application", a.getId(), "total=" + total);
        return toView(a, me);
    }

    // ------------------------------------------------------------------ helpers

    private void changeStatusInternal(Application a, ApplicationStatus target, String comment, AuthPrincipal actor) {
        ApplicationStatus from = a.getStatus();
        a.setStatus(target);
        a.setUpdatedAt(Instant.now());
        addHistory(a, from, target, comment, actor);
        audit.log("APPLICATION_STATUS_CHANGED", "Application", a.getId(), from + " → " + target);
        events.publish("APPLICATION_STATUS_CHANGED", "Application", a.getId(), Map.of(
                "applicationNumber", Objects.toString(a.getAppNumber(), ""), "from", from.name(), "to", target.name(),
                "changedAt", Instant.now().toString()));
        if (!actor.id().equals(a.getApplicant().getId())) {
            String title = switch (target) {
                case UNDER_REVIEW -> "Müraciətinizə baxılır";
                case INFO_REQUESTED -> "Əlavə məlumat tələb olunur";
                case DECIDED -> a.getDecision() == Decision.APPROVED ? "Təbriklər! Müraciətiniz təsdiqləndi" : "Müraciət üzrə qərar verildi";
                default -> "Müraciətin statusu dəyişdi";
            };
            notifications.notify(a.getApplicant().getId(), "STATUS", title,
                    a.getAppNumber() + ": " + target.label() + (comment == null || comment.isBlank() ? "" : " - " + comment),
                    "/muraciet-detal.html?id=" + a.getId());
        }
    }

    private void addHistory(Application a, ApplicationStatus from, ApplicationStatus to, String comment, AuthPrincipal actor) {
        ApplicationStatusHistory h = new ApplicationStatusHistory();
        h.setApplication(a);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setComment(comment);
        if (actor != null) {
            h.setActor(users.getReferenceById(actor.id()));
            h.setActorName(actor.role() == Role.INVESTOR ? actor.fullName() : actor.fullName() + " (" + actor.role().label() + ")");
        }
        h.setCreatedAt(Instant.now());
        a.getHistory().add(h);
    }

    private void notifyStaff(String title, String message, Long appId) {
        for (User u : users.findByRole(Role.ADMIN)) {
            notifications.notify(u.getId(), "APPLICATION", title, message, "/admin.html#applications/" + appId);
        }
    }

    private void applyRequest(Application a, ApplicationRequest r) {
        a.setApplicantType(r.applicantType());
        a.setCompanyName(trim(r.companyName()));
        a.setVoen(trim(r.voen()));
        a.setFin(r.fin() == null || r.fin().isBlank() ? null : r.fin().trim().toUpperCase(Locale.ROOT));
        a.setCitizenship(r.applicantType() == ApplicantType.FOREIGN ? trim(r.citizenship()) : null);
        a.setPassportNo(r.applicantType() == ApplicantType.FOREIGN && r.passportNo() != null && !r.passportNo().isBlank()
                ? r.passportNo().replace(" ", "").toUpperCase(Locale.ROOT) : null);
        a.setContactPhone(trim(r.contactPhone()));
        a.setContactEmail(trim(r.contactEmail()));
        a.setLegalAddress(trim(r.legalAddress()));
        a.setActivityType(r.activityType());
        a.setInvestmentAmount(r.investmentAmount());
        a.setOwnFundsPercent(r.ownFundsPercent());
        a.setJobsPlanned(r.jobsPlanned());
        a.setImplementationMonths(r.implementationMonths());
        a.setExperienceYears(r.experienceYears());
        a.setTechnologies(r.technologies() == null ? new ArrayList<>()
                : new ArrayList<>(r.technologies().stream().filter(t -> Technology.parse(t) != null).distinct().toList()));
        a.setBusinessPlan(trim(r.businessPlan()));
        a.setExpectedAnnualRevenue(r.expectedAnnualRevenue());
        a.setIdpStatus(r.idpStatus());
        a.setConsentGiven(r.consentGiven());
        a.setUpdatedAt(Instant.now());
    }

    public List<String> missingRequirements(Application a) {
        List<String> m = new ArrayList<>();
        Plot plot = a.getPlot();
        // Qaydalar 2.4.6: müraciətçi sahibkarlıq subyektidir, hər üç növ üçün VÖEN tələb olunur
        if (a.getApplicantType() == ApplicantType.LEGAL) {
            if (isBlank(a.getCompanyName())) m.add("Şirkətin adı");
        } else if (a.getApplicantType() == ApplicantType.FOREIGN) {
            if (isBlank(a.getCitizenship())) m.add("Vətəndaşlıq");
            if (isBlank(a.getPassportNo())) m.add("Pasport nömrəsi");
        } else if (isBlank(a.getFin())) {
            m.add("FİN kod");
        }
        if (isBlank(a.getVoen())) m.add("VÖEN (vergi ödəyicisi kimi qeydiyyat, Qaydalar 2.4.6)");
        if (isBlank(a.getContactPhone())) m.add("Əlaqə telefonu");
        if (isBlank(a.getContactEmail())) m.add("Əlaqə e-poçtu");
        if (a.getInvestmentAmount() == null) m.add("İnvestisiya məbləği");
        else if (a.getInvestmentAmount().compareTo(plot.getMinInvestment()) < 0)
            m.add(String.format(Locale.ROOT, "İnvestisiya məbləği minimum tələbdən (%,.0f AZN) az ola bilməz", plot.getMinInvestment()).replace(',', ' '));
        if (a.getJobsPlanned() == null) m.add("Yaradılacaq iş yerlərinin sayı");
        else if (a.getJobsPlanned() < plot.getMinJobs()) m.add("İş yerlərinin sayı minimum tələbdən (" + plot.getMinJobs() + ") az ola bilməz");
        if (a.getOwnFundsPercent() == null) m.add("Öz vəsaitinin payı");
        if (a.getImplementationMonths() == null) m.add("Layihənin icra müddəti");
        if (a.getBusinessPlan() == null || a.getBusinessPlan().length() < 200) m.add("Biznes planın xülasəsi (ən azı 200 simvol)");
        Set<DocumentType> types = new HashSet<>();
        a.getDocuments().forEach(d -> types.add(d.getDocType()));
        for (DocumentType t : DocumentType.values()) {
            if (t.required() && !types.contains(t)) m.add("Sənəd: " + t.label());
        }
        if (!a.isConsentGiven()) m.add("Fərdi məlumatların emalına razılıq");
        return m;
    }

    private void ensureOpenFor(Competition comp, Plot plot) {
        if (!comp.isAcceptingApplications()) {
            throw ApiException.conflict("COMPETITION_CLOSED", "Müsabiqə hazırda müraciət qəbul etmir");
        }
        if (comp.getPlots().stream().noneMatch(p -> p.getId().equals(plot.getId()))) {
            throw ApiException.badRequest("PLOT_NOT_IN_COMPETITION", "Seçilmiş sahə bu müsabiqəyə daxil deyil");
        }
        if (plot.getStatus() == PlotStatus.AWARDED) {
            throw ApiException.conflict("PLOT_AWARDED", "Bu sahə artıq icarəyə verilib");
        }
    }

    private AuthPrincipal requireInvestor() {
        AuthPrincipal me = currentUser.require();
        if (me.role() != Role.INVESTOR) throw ApiException.forbidden("Müraciəti yalnız investor hesabı ilə vermək olar");
        return me;
    }

    private Application find(Long id) {
        return applications.findById(id).orElseThrow(() -> ApiException.notFound("Müraciət"));
    }

    /** IDOR qorunması: investor yalnız öz müraciətini görə bilər. */
    private Application findOwned(Long id, AuthPrincipal me) {
        Application a = find(id);
        if (!a.getApplicant().getId().equals(me.id())) throw ApiException.notFound("Müraciət");
        return a;
    }

    public Map<String, Object> exchangePayload(Application a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationNumber", a.getAppNumber());
        m.put("status", a.getStatus().name());
        m.put("competitionCode", a.getCompetition().getCode());
        m.put("plotCode", a.getPlot().getCode());
        m.put("district", a.getPlot().getDistrict().name());
        m.put("applicantType", a.getApplicantType().name());
        m.put("applicantName", a.displayName());
        m.put("taxId", a.getVoen() != null ? a.getVoen() : a.getFin());
        if (a.getApplicantType() == ApplicantType.FOREIGN) {
            m.put("citizenship", a.getCitizenship());
            m.put("passportNo", a.getPassportNo());
        }
        m.put("activityType", a.getActivityType() == null ? null : a.getActivityType().name());
        m.put("investmentAmount", a.getInvestmentAmount());
        m.put("jobsPlanned", a.getJobsPlanned());
        m.put("autoScore", a.getAutoScore());
        m.put("submittedAt", a.getSubmittedAt() == null ? null : a.getSubmittedAt().toString());
        // Portal istifadəçisinin identifikatoru və torpağın sərhədi (GeoJSON, EPSG:4326): EKTİS sahəni öz reyestri ilə tutuşdura bilsin
        m.put("applicantUserId", a.getApplicant().getId());
        m.put("plotAreaHa", a.getPlot().getAreaHa());
        m.put("plotGeometry", geoJson(a.getPlot()));
        return m;
    }

    private static Map<String, Object> geoJson(az.aqroinvest.plot.Plot p) {
        List<List<Double>> ring = new ArrayList<>();
        if (p.getPolygon() != null) {
            String[] n = p.getPolygon().replace("[", " ").replace("]", " ").trim().split("[ ,]+");
            for (int i = 0; i + 1 < n.length; i += 2) ring.add(List.of(Double.parseDouble(n[i]), Double.parseDouble(n[i + 1])));
            if (!ring.isEmpty() && !ring.get(0).equals(ring.get(ring.size() - 1))) ring.add(ring.get(0));
        }
        return ring.size() >= 4
                ? Map.of("type", "Polygon", "coordinates", List.of(ring))
                : Map.of("type", "Point", "coordinates", List.of(p.getLongitude(), p.getLatitude()));
    }

    // ------------------------------------------------------------------ mapping

    private Integer expertAverage(Long appId) {
        List<Evaluation> list = evaluations.findByApplicationIdOrderByCreatedAtAsc(appId);
        if (list.isEmpty()) return null;
        return (int) Math.round(list.stream().mapToInt(Evaluation::getTotal).average().orElse(0));
    }

    private static Integer finalScore(Integer auto, Integer expert) {
        if (auto == null) return expert;
        if (expert == null) return auto;
        return (int) Math.round(auto * 0.4 + expert * 0.6);
    }

    public ApplicationListItem toListItem(Application a) {
        Integer exp = expertAverage(a.getId());
        return new ApplicationListItem(a.getId(), a.getAppNumber(), a.getStatus(), a.getStatus().label(), a.getDecision(),
                a.getDecision() == null ? null : a.getDecision().label(), a.displayName(), a.getPlot().getCode(),
                a.getPlot().getName(), a.getPlot().getDistrict().label(), a.getPlot().getCategory().label(),
                a.getCompetition().getCode(), a.getInvestmentAmount(), a.getJobsPlanned(), a.getAutoScore(), exp,
                finalScore(a.getAutoScore(), exp), a.getSubmittedAt(), a.getUpdatedAt());
    }

    public ApplicationView toView(Application a, AuthPrincipal viewer) {
        boolean owner = viewer != null && a.getApplicant().getId().equals(viewer.id());
        boolean admin = viewer != null && viewer.isAdmin();
        // Məlumat minimallaşdırılması: ekspert şəxsi identifikatorları maskalanmış görür
        boolean full = owner || admin;
        Competition c = a.getCompetition();
        Plot p = a.getPlot();
        Integer exp = expertAverage(a.getId());
        List<EvaluationView> evals = viewer != null && viewer.isStaff()
                ? evaluations.findByApplicationIdOrderByCreatedAtAsc(a.getId()).stream()
                .map(e -> new EvaluationView(e.getId(), e.getExpert().getFullName(), e.getScores(), e.getTotal(),
                        e.getComment(), e.getCreatedAt())).toList()
                : List.of();
        List<StatusOption> transitions = admin
                ? a.getStatus().adminTransitions().stream().map(s -> new StatusOption(s, s.label())).toList()
                : List.of();
        return new ApplicationView(
                a.getId(), a.getAppNumber(), owner || admin ? a.getTrackingCode() : null,
                a.getStatus(), a.getStatus().label(), a.getDecision(),
                a.getDecision() == null ? null : a.getDecision().label(), a.getDecisionNote(),
                new CompetitionRef(c.getId(), c.getCode(), c.getTitle(), c.getDeadlineAt(), c.isAcceptingApplications()),
                new PlotRef(p.getId(), p.getCode(), p.getName(), p.getDistrict().label(), p.getCategory().label(),
                        p.getAreaHa(), p.getLatitude(), p.getLongitude(), p.getMinInvestment(), p.getMinJobs()),
                a.getApplicant().getFullName(), full ? a.getApplicant().getEmail() : Masking.email(a.getApplicant().getEmail()),
                a.getApplicantType(), a.getApplicantType() == null ? null : a.getApplicantType().label(),
                a.getCompanyName(), a.getVoen(), full ? a.getFin() : Masking.fin(a.getFin()),
                a.getCitizenship(), full ? a.getPassportNo() : Masking.fin(a.getPassportNo()),
                full ? a.getContactPhone() : Masking.phone(a.getContactPhone()),
                full ? a.getContactEmail() : Masking.email(a.getContactEmail()),
                full ? a.getLegalAddress() : null,
                a.getActivityType(), a.getActivityType() == null ? null : a.getActivityType().label(),
                a.getInvestmentAmount(), a.getOwnFundsPercent(), a.getJobsPlanned(), a.getImplementationMonths(),
                a.getExperienceYears(),
                a.getTechnologies().stream().map(Technology::parse).filter(Objects::nonNull)
                        .map(t -> new TechView(t.name(), t.label())).toList(),
                a.getBusinessPlan(), a.getExpectedAnnualRevenue(), a.isIdpStatus(), a.isConsentGiven(),
                a.getAutoScore(), a.getScoreBreakdown(), exp, finalScore(a.getAutoScore(), exp),
                a.getEktisFarmerStatus(), a.getEktisReference(),
                a.getSubmittedAt(), a.getDecidedAt(), a.getCreatedAt(), a.getUpdatedAt(),
                owner && a.getStatus().isEditableByApplicant(),
                owner && a.getStatus() == ApplicationStatus.DRAFT ? missingRequirements(a) : List.of(),
                transitions,
                a.getDocuments().stream().map(d -> new DocumentView(d.getId(), d.getDocType(), d.getDocType().label(),
                        d.getOriginalName(), d.getContentType(), d.getSizeBytes(), d.getSha256(), d.getUploadedAt())).toList(),
                a.getHistory().stream().map(h -> new HistoryView(h.getFromStatus(), label(h.getFromStatus()), h.getToStatus(),
                        h.getToStatus().label(), h.getComment(), h.getActorName(), h.getCreatedAt())).toList(),
                evals);
    }

    private static String label(ApplicationStatus s) {
        return s == null ? null : s.label();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        return sb.toString();
    }
}
