package az.aqroinvest.analytics;

import az.aqroinvest.application.*;
import az.aqroinvest.buyer.BuyerDemandRepository;
import az.aqroinvest.buyer.BuyerEnums;
import az.aqroinvest.market.MarketEnums;
import az.aqroinvest.market.ProjectRepository;
import az.aqroinvest.competition.CompetitionRepository;
import az.aqroinvest.competition.CompetitionStatus;
import az.aqroinvest.integration.ektis.IntegrationEventRepository;
import az.aqroinvest.integration.ektis.IntegrationEventStatus;
import az.aqroinvest.plot.*;
import az.aqroinvest.user.Role;
import az.aqroinvest.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Tag(name = "Statistika və hesabatlar")
@RestController
@RequiredArgsConstructor
public class StatsController {
    private final PlotRepository plots;
    private final BuyerDemandRepository demands;
    private final ProjectRepository projects;
    private final CompetitionRepository competitions;
    private final ApplicationRepository applications;
    private final ApplicationStatusHistoryRepository history;
    private final IntegrationEventRepository events;
    private final UserRepository users;

    @Operation(summary = "İctimai göstəricilər (ana səhifə)")
    @GetMapping("/api/v1/stats/public")
    @Transactional(readOnly = true)
    public Map<String, Object> publicStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plots", plots.count());
        m.put("totalAreaHa", plots.totalArea());
        m.put("districts", plots.statsByDistrict().size());
        m.put("activeCompetitions", competitions.countByStatus(CompetitionStatus.PUBLISHED));
        m.put("applications", applications.countByStatusNot(ApplicationStatus.DRAFT));
        m.put("proposedInvestment", applications.totalProposedInvestment());
        m.put("plannedJobs", applications.totalPlannedJobs());
        m.put("investors", users.countByRole(Role.INVESTOR));
        return m;
    }

    @Operation(summary = "İdarəetmə paneli üçün analitika")
    @SecurityRequirement(name = "bearer")
    @GetMapping("/api/v1/admin/stats")
    @Transactional(readOnly = true)
    public Map<String, Object> adminStats() {
        Map<String, Object> m = new LinkedHashMap<>(publicStats());
        m.put("plotsAvailable", plots.countByStatus(PlotStatus.AVAILABLE));
        m.put("plotsInCompetition", plots.countByStatus(PlotStatus.IN_COMPETITION));
        m.put("plotsAwarded", plots.countByStatus(PlotStatus.AWARDED));
        m.put("pendingCompetitionApprovals", competitions.countByStatus(CompetitionStatus.PENDING_APPROVAL));
        m.put("pendingDemands", demands.countByStatus(BuyerEnums.DemandStatus.PENDING));
        m.put("publishedProjects", projects.countByStatus(MarketEnums.ProjectStatus.PUBLISHED));
        m.put("approvedInvestment", applications.approvedInvestment());
        m.put("approvedJobs", applications.approvedJobs());

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) if (s != ApplicationStatus.DRAFT) byStatus.put(s.name(), 0L);
        for (Object[] r : applications.countByStatusGrouped()) {
            ApplicationStatus s = (ApplicationStatus) r[0];
            if (s != ApplicationStatus.DRAFT) byStatus.put(s.name(), (Long) r[1]);
        }
        m.put("byStatus", byStatus.entrySet().stream().map(e -> Map.of("code", e.getKey(),
                "label", ApplicationStatus.valueOf(e.getKey()).label(), "count", e.getValue())).toList());

        Map<District, Map<String, Object>> districts = new EnumMap<>(District.class);
        for (Object[] r : plots.statsByDistrict()) {
            Map<String, Object> d = districts.computeIfAbsent((District) r[0], k -> base(k.label()));
            d.put("plots", r[1]);
            d.put("areaHa", r[2]);
        }
        for (Object[] r : applications.statsByDistrict()) {
            Map<String, Object> d = districts.computeIfAbsent((District) r[0], k -> base(k.label()));
            d.put("applications", r[1]);
            d.put("investment", r[2]);
        }
        m.put("byDistrict", districts.values());

        List<Map<String, Object>> cats = new ArrayList<>();
        Map<PlotCategory, Object[]> appCats = new EnumMap<>(PlotCategory.class);
        for (Object[] r : applications.statsByActivity()) if (r[0] != null) appCats.put((PlotCategory) r[0], r);
        for (Object[] r : plots.statsByCategory()) {
            PlotCategory c = (PlotCategory) r[0];
            Object[] a = appCats.get(c);
            cats.add(Map.of("code", c.name(), "label", c.label(), "plots", r[1], "areaHa", r[2],
                    "applications", a == null ? 0L : a[1], "investment", a == null ? BigDecimal.ZERO : a[2]));
        }
        m.put("byCategory", cats);

        // Son 30 gün üzrə gündəlik müraciət dinamikası
        ZoneId baku = ZoneId.of("Asia/Baku");
        LocalDate today = LocalDate.now(baku);
        Map<LocalDate, Long> daily = new TreeMap<>();
        for (int i = 29; i >= 0; i--) daily.put(today.minusDays(i), 0L);
        Instant from = today.minusDays(29).atStartOfDay(baku).toInstant();
        for (Instant t : applications.submittedSince(from)) daily.merge(LocalDate.ofInstant(t, baku), 1L, Long::sum);
        m.put("timeline", daily.entrySet().stream().map(e -> Map.of("date", e.getKey().toString(), "count", e.getValue())).toList());

        // Orta baxılma müddəti (göndərilmə → qərar), gün
        OptionalDouble avg = applications.findAll().stream()
                .filter(a -> a.getSubmittedAt() != null && a.getDecidedAt() != null)
                .mapToLong(a -> Duration.between(a.getSubmittedAt(), a.getDecidedAt()).toHours())
                .average();
        m.put("avgDecisionDays", avg.isPresent() ? Math.round(avg.getAsDouble() / 24.0 * 10) / 10.0 : null);

        m.put("recentActivity", history.findTop15ByOrderByCreatedAtDesc().stream()
                .filter(h -> h.getToStatus() != ApplicationStatus.DRAFT)
                .map(h -> {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("applicationId", h.getApplication().getId());
                    a.put("appNumber", h.getApplication().getAppNumber());
                    a.put("toLabel", h.getToStatus().label());
                    a.put("toStatus", h.getToStatus());
                    a.put("actor", h.getActorName());
                    a.put("createdAt", h.getCreatedAt());
                    return a;
                }).toList());

        Map<String, Long> integration = new LinkedHashMap<>();
        for (IntegrationEventStatus s : IntegrationEventStatus.values()) integration.put(s.name(), events.countByStatus(s));
        m.put("integration", integration);
        return m;
    }

    private static Map<String, Object> base(String label) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("label", label);
        d.put("plots", 0L);
        d.put("areaHa", BigDecimal.ZERO);
        d.put("applications", 0L);
        d.put("investment", BigDecimal.ZERO);
        return d;
    }
}
