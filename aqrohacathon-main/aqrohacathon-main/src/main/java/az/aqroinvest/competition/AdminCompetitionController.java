package az.aqroinvest.competition;

import az.aqroinvest.competition.CompetitionDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin: Müsabiqələr", description = "Müsabiqələrin yaradılması və 'dörd göz' təsdiq mexanizmi")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/v1/admin/competitions")
@RequiredArgsConstructor
public class AdminCompetitionController {
    private final CompetitionService service;

    @Operation(summary = "Bütün müsabiqələr (bütün statuslar)")
    @GetMapping
    public List<CompetitionView> list() {
        return service.listAll();
    }

    @Operation(summary = "Yeni müsabiqə (Layihə statusunda)")
    @PostMapping
    public CompetitionView create(@Valid @RequestBody CompetitionRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Layihə statusunda müsabiqəni redaktə et")
    @PutMapping("/{id}")
    public CompetitionView update(@PathVariable Long id, @Valid @RequestBody CompetitionRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Təsdiqə göndər")
    @PostMapping("/{id}/submit")
    public CompetitionView submit(@PathVariable Long id) {
        return service.submitForApproval(id);
    }

    @Operation(summary = "Təsdiqlə və dərc et (yaradan şəxs təsdiqləyə bilməz)")
    @PostMapping("/{id}/approve")
    public CompetitionView approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest req) {
        return service.approve(id, req == null ? null : req.note());
    }

    @Operation(summary = "Düzəliş üçün geri qaytar")
    @PostMapping("/{id}/reject")
    public CompetitionView reject(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return service.reject(id, req.note());
    }

    @Operation(summary = "Müsabiqəni bağla")
    @PostMapping("/{id}/close")
    public CompetitionView close(@PathVariable Long id) {
        return service.close(id);
    }
}
