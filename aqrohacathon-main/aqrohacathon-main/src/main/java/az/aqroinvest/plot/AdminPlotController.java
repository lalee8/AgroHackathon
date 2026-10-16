package az.aqroinvest.plot;

import az.aqroinvest.plot.PlotDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin: Torpaq sahələri", description = "Təsərrüfat sahələrinin əlavə edilməsi və redaktəsi")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/v1/admin/plots")
@RequiredArgsConstructor
public class AdminPlotController {
    private final PlotService service;

    @Operation(summary = "Yeni sahə əlavə et")
    @PostMapping
    public PlotView create(@Valid @RequestBody PlotRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Sahəni redaktə et")
    @PutMapping("/{id}")
    public PlotView update(@PathVariable Long id, @Valid @RequestBody PlotRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Sahəni sil (müraciəti olmayan)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
