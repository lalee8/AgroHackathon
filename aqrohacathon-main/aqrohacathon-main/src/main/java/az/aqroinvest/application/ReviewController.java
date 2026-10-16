package az.aqroinvest.application;

import az.aqroinvest.application.ApplicationDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Baxış və qiymətləndirmə (admin, ekspert)")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/v1/review")
@RequiredArgsConstructor
public class ReviewController {
    private final ApplicationService service;

    @Operation(summary = "Daxil olan müraciətlər (filtrlə)")
    @GetMapping("/applications")
    public List<ApplicationListItem> list(@RequestParam(required = false) ApplicationStatus status,
                                          @RequestParam(required = false) Long competitionId,
                                          @RequestParam(required = false) String district,
                                          @RequestParam(required = false) String q) {
        return service.search(status, competitionId, district, q);
    }

    @Operation(summary = "Müraciətin tam görünüşü (ekspert üçün şəxsi məlumatlar maskalanır)")
    @GetMapping("/applications/{id}")
    public ApplicationView get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Komissiya ekspertinin qiymətləndirməsi (meyar üzrə 0-10)")
    @PostMapping("/applications/{id}/evaluations")
    public ApplicationView evaluate(@PathVariable Long id, @Valid @RequestBody EvaluationRequest req) {
        return service.evaluate(id, req);
    }

    @Operation(summary = "Müsabiqə üzrə reytinq (avtomatik 40% + ekspert 60%)")
    @GetMapping("/competitions/{competitionId}/ranking")
    public List<ApplicationListItem> ranking(@PathVariable Long competitionId) {
        return service.ranking(competitionId);
    }
}
