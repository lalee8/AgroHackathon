package az.aqroinvest.competition;

import az.aqroinvest.competition.CompetitionDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Müsabiqələr", description = "Elan edilmiş investisiya müsabiqələri")
@RestController
@RequestMapping("/api/v1/competitions")
@RequiredArgsConstructor
public class CompetitionController {
    private final CompetitionService service;

    @Operation(summary = "Elan edilmiş və bağlanmış müsabiqələr")
    @GetMapping
    public List<CompetitionView> list() {
        return service.listPublic();
    }

    @Operation(summary = "Müsabiqə haqqında ətraflı məlumat")
    @GetMapping("/{id}")
    public CompetitionView get(@PathVariable Long id) {
        return service.getPublic(id);
    }
}
