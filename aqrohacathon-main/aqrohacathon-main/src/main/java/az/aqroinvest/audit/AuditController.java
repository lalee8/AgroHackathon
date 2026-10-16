package az.aqroinvest.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Admin: Audit jurnalı")
@SecurityRequirement(name = "bearer")
@RestController
@RequiredArgsConstructor
public class AuditController {
    private final AuditLogRepository repository;

    @Operation(summary = "Audit jurnalı (səhifələnmiş)")
    @GetMapping("/api/v1/admin/audit")
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        Page<AuditLog> p = repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 200)));
        return Map.of("items", p.getContent(), "total", p.getTotalElements(), "page", page, "pages", p.getTotalPages());
    }
}
