package az.aqroinvest.machinery;

import az.aqroinvest.economics.UseCase;
import az.aqroinvest.machinery.MachineryService.*;
import az.aqroinvest.plot.District;
import az.aqroinvest.plot.PlotCategory;
import az.aqroinvest.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Tag(name = "Texnika icarəsi", description = "Texnika sahibi elan edir, sahibkar və investor sahəsi üçün icarəyə götürür; al və ya icarə müqayisəsi")
@RestController
@RequiredArgsConstructor
public class MachineryController {
    private final MachineryService machinery;
    private final CurrentUser currentUser;

    public record Decision(boolean accept) {}

    @Operation(summary = "Texnika elanları (növ, rayon və fəaliyyət üzrə süzgəc)")
    @GetMapping("/api/v1/equipment")
    public List<EquipmentView> list(@RequestParam(required = false) EquipmentType type, @RequestParam(required = false) District district,
                                    @RequestParam(required = false) PlotCategory category) {
        return machinery.list(type, district, category);
    }

    @Operation(summary = "Texnika növləri")
    @GetMapping("/api/v1/equipment/types")
    public List<Map<String, Object>> types() {
        return Arrays.stream(EquipmentType.values()).map(t -> Map.<String, Object>of("code", t.name(), "label", t.label(), "icon", t.icon(),
                "purpose", t.purpose(), "categories", t.categories().stream().map(Enum::name).toList())).toList();
    }

    @Operation(summary = "Texnika elanı")
    @GetMapping("/api/v1/equipment/{id}")
    public EquipmentView get(@PathVariable Long id) {
        return machinery.get(id);
    }

    @Operation(summary = "Texnikanı elan etmək")
    @SecurityRequirement(name = "bearer")
    @PostMapping("/api/v1/equipment")
    public EquipmentView create(@Valid @RequestBody CreateRequest req) {
        return machinery.create(currentUser.require().id(), req);
    }

    @Operation(summary = "Mənim elanlarım və mənə gələn sorğular; mənim göndərdiyim sorğular")
    @SecurityRequirement(name = "bearer")
    @GetMapping("/api/v1/equipment/mine")
    public Map<String, Object> mine() {
        Long id = currentUser.require().id();
        return Map.of("listings", machinery.mine(id), "incoming", machinery.incoming(id), "outgoing", machinery.myRequests(id));
    }

    @Operation(summary = "İcarə sorğusu göndərmək")
    @SecurityRequirement(name = "bearer")
    @PostMapping("/api/v1/equipment/{id}/requests")
    public RequestView rent(@PathVariable Long id, @Valid @RequestBody RentRequest req) {
        return machinery.rent(id, currentUser.require().id(), req);
    }

    @Operation(summary = "Sorğunu qəbul və ya rədd etmək (texnika sahibi)")
    @SecurityRequirement(name = "bearer")
    @PostMapping("/api/v1/equipment/requests/{id}/decide")
    public RequestView decide(@PathVariable Long id, @RequestBody Decision d) {
        return machinery.decide(id, currentUser.require().id(), d.accept());
    }

    @Operation(summary = "Öz sorğunu ləğv etmək")
    @SecurityRequirement(name = "bearer")
    @PostMapping("/api/v1/equipment/requests/{id}/cancel")
    public RequestView cancel(@PathVariable Long id) {
        return machinery.cancel(id, currentUser.require().id());
    }

    @Operation(summary = "Sahə üçün lazım olan texnika, yaxındakı elanlar və 'al və ya icarə' müqayisəsi")
    @GetMapping("/api/v1/plots/{id}/machinery")
    public PlotMachinery forPlot(@PathVariable Long id, @RequestParam(required = false) UseCase use) {
        return machinery.forPlot(id, use);
    }
}
