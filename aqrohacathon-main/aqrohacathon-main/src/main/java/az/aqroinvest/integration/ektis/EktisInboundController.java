package az.aqroinvest.integration.ektis;

import az.aqroinvest.application.*;
import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.competition.CompetitionDtos.CompetitionView;
import az.aqroinvest.competition.CompetitionService;
import az.aqroinvest.notification.NotificationService;
import az.aqroinvest.plot.PlotController;
import az.aqroinvest.plot.PlotDtos;
import az.aqroinvest.plot.PlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * EKTİS → Portal istiqaməti: dövlət sistemi bu API vasitəsilə sahələri, müsabiqələri və müraciətləri
 * standart formatda oxuyur və geri-çağırış (callback) göndərir. Autentifikasiya: X-API-Key.
 */
@Tag(name = "EKTİS inteqrasiya API (sistemlərarası)", description = "X-API-Key ilə qorunur. Versiyalaşdırılmış mübadilə formatı v1.")
@SecurityRequirement(name = "apiKey")
@RestController
@RequestMapping("/api/v1/integration")
@RequiredArgsConstructor
public class EktisInboundController {
    private final PlotService plots;
    private final CompetitionService competitions;
    private final ApplicationRepository applications;
    private final ApplicationService applicationService;
    private final NotificationService notifications;
    private final AuditService audit;

    public record CallbackRequest(@NotBlank String applicationNumber,
                                  @NotBlank String type,
                                  @Size(max = 60) String reference,
                                  @Size(max = 2000) String message) {}

    @Operation(summary = "Sahələr (GeoJSON FeatureCollection)")
    @GetMapping("/plots")
    public Map<String, Object> plots() {
        return PlotController.toFeatureCollection(plots.search(new PlotDtos.PlotFilter(null, null, null, null, null,
                null, null, null, null, null, null)));
    }

    @Operation(summary = "Müsabiqələr")
    @GetMapping("/competitions")
    public List<CompetitionView> competitions() {
        return competitions.listAll();
    }

    @Operation(summary = "Müraciətlər (status və dəyişmə tarixinə görə filtr - artımlı sinxronizasiya)")
    @GetMapping("/applications")
    @Transactional(readOnly = true)
    public Map<String, Object> applications(@RequestParam(required = false) ApplicationStatus status,
                                            @RequestParam(required = false) Instant updatedSince) {
        List<Map<String, Object>> items = applications.findAll(Sort.by("id")).stream()
                .filter(a -> a.getStatus() != ApplicationStatus.DRAFT)
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> updatedSince == null || a.getUpdatedAt().isAfter(updatedSince))
                .map(this::exchange).toList();
        return Map.of("schemaVersion", IntegrationEventPublisher.SCHEMA_VERSION, "generatedAt", Instant.now(),
                "count", items.size(), "items", items);
    }

    @Operation(summary = "Müraciət nömrə üzrə")
    @GetMapping("/applications/{number}")
    @Transactional(readOnly = true)
    public Map<String, Object> application(@PathVariable String number) {
        return exchange(applications.findByAppNumber(number).orElseThrow(() -> ApiException.notFound("Müraciət")));
    }

    @Operation(summary = "EKTİS-dən geri-çağırış (məs. fermer qeydiyyatı tamamlandı)")
    @PostMapping("/callbacks")
    @Transactional
    public Map<String, Object> callback(@Valid @RequestBody CallbackRequest req) {
        Application a = applications.findByAppNumber(req.applicationNumber())
                .orElseThrow(() -> ApiException.notFound("Müraciət"));
        if (req.reference() != null) a.setEktisReference(req.reference());
        if ("FARMER_REGISTERED".equals(req.type())) a.setEktisFarmerStatus("REGISTERED");
        ApplicationStatusHistory h = new ApplicationStatusHistory();
        h.setApplication(a);
        h.setFromStatus(a.getStatus());
        h.setToStatus(a.getStatus());
        h.setComment("EKTİS bildirişi [" + req.type() + "]: " + Objects.toString(req.message(), ""));
        h.setActorName("EKTİS (sistem)");
        h.setCreatedAt(Instant.now());
        a.getHistory().add(h);
        notifications.notify(a.getApplicant().getId(), "EKTIS", "EKTİS-dən yeni bildiriş",
                a.getAppNumber() + ": " + Objects.toString(req.message(), req.type()), "/muraciet-detal.html?id=" + a.getId());
        audit.logAs(null, "ektis-system", "EKTIS_CALLBACK", "Application", a.getId(), req.type());
        return Map.of("accepted", true, "applicationNumber", a.getAppNumber());
    }

    private Map<String, Object> exchange(Application a) {
        Map<String, Object> m = new LinkedHashMap<>(applicationService.exchangePayload(a));
        m.put("decision", a.getDecision() == null ? null : a.getDecision().name());
        m.put("decidedAt", a.getDecidedAt());
        m.put("updatedAt", a.getUpdatedAt());
        m.put("ownFundsPercent", a.getOwnFundsPercent());
        m.put("implementationMonths", a.getImplementationMonths());
        m.put("plotAreaHa", a.getPlot().getAreaHa());
        m.put("ektisFarmerStatus", a.getEktisFarmerStatus());
        m.put("ektisReference", a.getEktisReference());
        return m;
    }
}
