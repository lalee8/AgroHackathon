package az.aqroinvest.competition;

import az.aqroinvest.application.ApplicationRepository;
import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.competition.CompetitionDtos.*;
import az.aqroinvest.config.AppProperties;
import az.aqroinvest.integration.ektis.IntegrationEventPublisher;
import az.aqroinvest.notification.NotificationService;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import az.aqroinvest.plot.PlotStatus;
import az.aqroinvest.security.AuthPrincipal;
import az.aqroinvest.security.CurrentUser;
import az.aqroinvest.user.Role;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CompetitionService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Asia/Baku"));

    private final CompetitionRepository competitions;
    private final PlotRepository plots;
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final NotificationService notifications;
    private final az.aqroinvest.watch.WatchService watches;
    private final IntegrationEventPublisher events;
    private final AppProperties props;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public List<CompetitionView> listPublic() {
        return competitions.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> c.getStatus() == CompetitionStatus.PUBLISHED || c.getStatus() == CompetitionStatus.CLOSED)
                .sorted(Comparator.comparing((Competition c) -> c.getStatus() == CompetitionStatus.PUBLISHED ? 0 : 1)
                        .thenComparing(Competition::getDeadlineAt))
                .map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public CompetitionView getPublic(Long id) {
        Competition c = find(id);
        if (c.getStatus() == CompetitionStatus.DRAFT || c.getStatus() == CompetitionStatus.PENDING_APPROVAL) {
            AuthPrincipal p = currentUser.optional().orElse(null);
            if (p == null || !p.isStaff()) throw ApiException.notFound("Müsabiqə");
        }
        return toView(c);
    }

    @Transactional(readOnly = true)
    public List<CompetitionView> listAll() {
        return competitions.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    public Competition find(Long id) {
        return competitions.findById(id).orElseThrow(() -> ApiException.notFound("Müsabiqə"));
    }

    @Transactional
    public CompetitionView create(CompetitionRequest req) {
        validateDates(req);
        Competition c = new Competition();
        c.setCode(nextCode());
        c.setCreatedBy(users.getReferenceById(currentUser.require().id()));
        applyRequest(c, req);
        competitions.save(c);
        audit.log("COMPETITION_CREATED", "Competition", c.getId(), c.getCode() + " - " + c.getTitle());
        return toView(c);
    }

    @Transactional
    public CompetitionView update(Long id, CompetitionRequest req) {
        Competition c = find(id);
        if (c.getStatus() != CompetitionStatus.DRAFT) {
            throw ApiException.conflict("NOT_EDITABLE", "Yalnız 'Layihə' statusunda olan müsabiqə redaktə edilə bilər");
        }
        validateDates(req);
        applyRequest(c, req);
        audit.log("COMPETITION_UPDATED", "Competition", c.getId(), c.getCode());
        return toView(c);
    }

    @Transactional
    public CompetitionView submitForApproval(Long id) {
        Competition c = find(id);
        if (c.getStatus() != CompetitionStatus.DRAFT) {
            throw ApiException.conflict("INVALID_TRANSITION", "Təsdiqə yalnız 'Layihə' statusundan göndərilə bilər");
        }
        c.setStatus(CompetitionStatus.PENDING_APPROVAL);
        c.setReviewNote(null);
        audit.log("COMPETITION_SUBMITTED", "Competition", c.getId(), c.getCode());
        Long me = currentUser.require().id();
        users.findByRole(Role.ADMIN).stream().filter(u -> !u.getId().equals(me)).forEach(u ->
                notifications.notify(u.getId(), "APPROVAL", "Müsabiqə təsdiq gözləyir",
                        c.getCode() + " - " + c.getTitle(), "/admin.html#competitions"));
        return toView(c);
    }

    @Transactional
    public CompetitionView approve(Long id, String note) {
        Competition c = find(id);
        if (c.getStatus() != CompetitionStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("INVALID_TRANSITION", "Müsabiqə təsdiq gözləmir");
        }
        AuthPrincipal me = currentUser.require();
        if (props.competition().fourEyes() && c.getCreatedBy() != null && c.getCreatedBy().getId().equals(me.id())) {
            throw ApiException.forbidden("'Dörd göz' prinsipi: müsabiqəni yaradan şəxs onu təsdiqləyə bilməz");
        }
        if (!c.getDeadlineAt().isAfter(Instant.now())) {
            throw ApiException.conflict("DEADLINE_PASSED", "Son müraciət tarixi keçib - tarixi yeniləyin");
        }
        c.setStatus(CompetitionStatus.PUBLISHED);
        c.setApprovedBy(users.getReferenceById(me.id()));
        c.setApprovedAt(Instant.now());
        c.setReviewNote(note);
        c.getPlots().forEach(p -> {
            if (p.getStatus() == PlotStatus.AVAILABLE) p.setStatus(PlotStatus.IN_COMPETITION);
        });
        audit.log("COMPETITION_APPROVED", "Competition", c.getId(), c.getCode() + " dərc edildi");
        events.publish("COMPETITION_PUBLISHED", "Competition", c.getId(), Map.of(
                "code", c.getCode(), "title", c.getTitle(),
                "startAt", c.getStartAt().toString(), "deadlineAt", c.getDeadlineAt().toString(),
                "plotCodes", c.getPlots().stream().map(Plot::getCode).toList()));
        // Sahəni sevimlilərə əlavə edənlərə ayrıca, şəxsi bildiriş
        watches.notifyCompetition(c.getPlots(), c.getTitle(), c.getId());
        // Bütün investorlara yeni müsabiqə barədə bildiriş
        String msg = c.getPlots().size() + " sahə, son müraciət: " + DATE.format(c.getDeadlineAt());
        for (User u : users.findByRole(Role.INVESTOR)) {
            notifications.notify(u.getId(), "COMPETITION", "Yeni müsabiqə elan edildi: " + c.getTitle(), msg,
                    "/musabiqeler.html#c" + c.getId());
        }
        if (c.getCreatedBy() != null) {
            notifications.notify(c.getCreatedBy().getId(), "APPROVAL", "Müsabiqə təsdiqləndi", c.getCode(), "/admin.html#competitions");
        }
        return toView(c);
    }

    @Transactional
    public CompetitionView reject(Long id, String note) {
        Competition c = find(id);
        if (c.getStatus() != CompetitionStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("INVALID_TRANSITION", "Müsabiqə təsdiq gözləmir");
        }
        if (note == null || note.isBlank()) throw ApiException.badRequest("NOTE_REQUIRED", "Geri qaytarma səbəbi göstərilməlidir");
        c.setStatus(CompetitionStatus.DRAFT);
        c.setReviewNote(note);
        audit.log("COMPETITION_RETURNED", "Competition", c.getId(), note);
        if (c.getCreatedBy() != null) {
            notifications.notify(c.getCreatedBy().getId(), "APPROVAL", "Müsabiqə düzəliş üçün qaytarıldı", note, "/admin.html#competitions");
        }
        return toView(c);
    }

    @Transactional
    public CompetitionView close(Long id) {
        Competition c = find(id);
        if (c.getStatus() != CompetitionStatus.PUBLISHED) {
            throw ApiException.conflict("INVALID_TRANSITION", "Yalnız elan edilmiş müsabiqə bağlana bilər");
        }
        c.setStatus(CompetitionStatus.CLOSED);
        c.getPlots().forEach(p -> {
            if (p.getStatus() == PlotStatus.IN_COMPETITION && !applications.existsApprovedForPlot(p.getId())) {
                p.setStatus(PlotStatus.AVAILABLE);
            }
        });
        audit.log("COMPETITION_CLOSED", "Competition", c.getId(), c.getCode());
        events.publish("COMPETITION_CLOSED", "Competition", c.getId(), Map.of("code", c.getCode()));
        return toView(c);
    }

    private void applyRequest(Competition c, CompetitionRequest req) {
        c.setTitle(req.title().trim());
        c.setDescription(req.description());
        c.setStartAt(req.startAt());
        c.setDeadlineAt(req.deadlineAt());
        List<Plot> selected = plots.findAllById(req.plotIds());
        if (selected.size() != new HashSet<>(req.plotIds()).size()) {
            throw ApiException.badRequest("PLOT_NOT_FOUND", "Seçilmiş sahələrdən bəziləri tapılmadı");
        }
        selected.stream().filter(p -> p.getStatus() == PlotStatus.AWARDED).findAny().ifPresent(p -> {
            throw ApiException.conflict("PLOT_AWARDED", p.getCode() + " artıq icarəyə verilib");
        });
        c.setPlots(new LinkedHashSet<>(selected));
        List<Criterion> criteria = req.criteria() == null || req.criteria().isEmpty() ? Criterion.defaults() : req.criteria();
        int sum = criteria.stream().mapToInt(Criterion::weight).sum();
        if (sum != 100) throw ApiException.badRequest("CRITERIA_WEIGHT", "Meyar çəkilərinin cəmi 100 olmalıdır (indi: " + sum + ")");
        try {
            c.setCriteria(mapper.writeValueAsString(criteria));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void validateDates(CompetitionRequest req) {
        if (!req.deadlineAt().isAfter(req.startAt())) {
            throw ApiException.badRequest("INVALID_DATES", "Son müraciət tarixi başlama tarixindən sonra olmalıdır");
        }
    }

    private String nextCode() {
        int year = Year.now().getValue();
        int n = (int) competitions.count() + 1;
        String code;
        do {
            code = String.format("MSB-%d-%02d", year, n++);
        } while (competitions.findByCode(code).isPresent());
        return code;
    }

    public List<Criterion> criteriaOf(Competition c) {
        if (c.getCriteria() == null) return Criterion.defaults();
        try {
            return mapper.readValue(c.getCriteria(), new TypeReference<>() {});
        } catch (Exception e) {
            return Criterion.defaults();
        }
    }

    public CompetitionView toView(Competition c) {
        List<PlotSummary> ps = c.getPlots().stream().map(p -> new PlotSummary(p.getId(), p.getCode(), p.getName(),
                p.getDistrict(), p.getDistrict().label(), p.getCategory(), p.getCategory().label(), p.getAreaHa(),
                p.getMinInvestment(), p.getStatus().label())).toList();
        BigDecimal area = c.getPlots().stream().map(Plot::getAreaHa).reduce(BigDecimal.ZERO, BigDecimal::add);
        long appCount = applications.countByCompetitionIdAndStatusNot(c.getId(),
                az.aqroinvest.application.ApplicationStatus.DRAFT);
        return new CompetitionView(c.getId(), c.getCode(), c.getTitle(), c.getDescription(), c.getStartAt(),
                c.getDeadlineAt(), c.getStatus(), c.getStatus().label(), c.isAcceptingApplications(), criteriaOf(c),
                ps, ps.size(), area, appCount,
                c.getCreatedBy() == null ? null : c.getCreatedBy().getFullName(),
                c.getApprovedBy() == null ? null : c.getApprovedBy().getFullName(),
                c.getApprovedAt(), c.getReviewNote(), c.getCreatedAt());
    }
}
