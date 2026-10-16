package az.aqroinvest.integration.ektis;

import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Admin: EKTİS inteqrasiya monitorinqi")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/v1/admin/integration")
@RequiredArgsConstructor
public class AdminIntegrationController {
    private final IntegrationEventRepository events;
    private final OutboxProcessor processor;
    private final EktisGateway gateway;
    private final MockEktisStore mockStore;
    private final AppProperties props;
    private final AuditService audit;

    @Operation(summary = "Outbox hadisələri")
    @GetMapping("/events")
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "40") int size) {
        Page<IntegrationEvent> p = events.findAllByOrderByIdDesc(PageRequest.of(page, Math.min(size, 200)));
        return Map.of("items", p.getContent(), "total", p.getTotalElements(), "page", page, "pages", p.getTotalPages());
    }

    @Operation(summary = "İnteqrasiya vəziyyəti")
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("endpoint", props.ektis().baseUrl());
        m.put("mode", props.ektis().baseUrl().contains("/mock/ektis") ? "SİMULYATOR" : "REAL");
        for (IntegrationEventStatus s : IntegrationEventStatus.values()) m.put(s.name().toLowerCase(), events.countByStatus(s));
        m.put("mockReceived", mockStore.list().size());
        m.put("mockRejectedSignatures", mockStore.rejectedCount());
        m.put("mockFailureRate", props.ektis().mock().failureRate());
        m.put("maxAttempts", props.ektis().maxAttempts());
        long t0 = System.nanoTime();
        EktisGateway.FarmerCheck ping = gateway.checkFarmer("1234567890");
        m.put("reachable", ping.available());
        m.put("latencyMs", (System.nanoTime() - t0) / 1_000_000);
        m.put("checkedAt", Instant.now());
        return m;
    }

    @Operation(summary = "EKTİS tərəfində qəbul edilmiş hadisələr (simulyator jurnalı)")
    @GetMapping("/ektis-inbox")
    public Object inbox() {
        return mockStore.list();
    }

    @Operation(summary = "Gözləyən hadisələri dərhal göndər")
    @PostMapping("/flush")
    public Map<String, Object> flush() {
        int n = processor.processDue();
        audit.log("INTEGRATION_FLUSH", "IntegrationEvent", null, n + " hadisə emal edildi");
        return Map.of("processed", n);
    }

    @Operation(summary = "Uğursuz (DEAD/FAILED) hadisəni yenidən növbəyə qoy")
    @PostMapping("/events/{id}/retry")
    @Transactional
    public IntegrationEvent retry(@PathVariable Long id) {
        IntegrationEvent e = events.findById(id).orElseThrow(() -> ApiException.notFound("Hadisə"));
        if (e.getStatus() == IntegrationEventStatus.SENT) throw ApiException.conflict("ALREADY_SENT", "Hadisə artıq çatdırılıb");
        e.setStatus(IntegrationEventStatus.PENDING);
        e.setAttempts(0);
        e.setNextAttemptAt(Instant.now());
        audit.log("INTEGRATION_RETRY", "IntegrationEvent", id, e.getEventType());
        return e;
    }

    @Operation(summary = "VÖEN/FİN üzrə EKTİS fermer reyestrində yoxlama")
    @GetMapping("/farmer/{taxId}")
    public EktisGateway.FarmerCheck farmer(@PathVariable String taxId) {
        return gateway.checkFarmer(taxId);
    }
}
