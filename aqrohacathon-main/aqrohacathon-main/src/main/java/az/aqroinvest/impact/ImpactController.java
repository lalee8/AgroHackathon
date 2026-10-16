package az.aqroinvest.impact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Böyük Qayıdış paneli", description = "Rayonlar üzrə iqtisadi potensial, sosial təsir və investisiyaya hazırlıq")
@RestController
@RequestMapping("/api/v1/impact")
@RequiredArgsConstructor
public class ImpactController {
    private final RegionService regions;
    private final OfficialStatsService official;
    private final DeliveryService delivery;

    @Operation(summary = "Rayonlar üzrə potensial investisiya, iş yerləri, qayıdan ailələr, investor marağı və hazırlıq indeksi")
    @GetMapping("/regions")
    public RegionService.Overview regions() {
        return regions.overview();
    }

    @Operation(summary = "Keçirilmiş investisiya müsabiqələrinin rəsmi göstəriciləri (Nazirlik) və bazar həcmi: TAM / SAM / SOM")
    @GetMapping("/official")
    public OfficialStatsService.Official official() {
        return official.official();
    }

    @Operation(summary = "Vəd və reallıq: icarəyə verilmiş bütün sahələr üzrə öhdəliklərin icrası (investisiya, iş yerləri, peyk NDVI)")
    @GetMapping("/delivery")
    public java.util.List<DeliveryService.Summary> delivery() {
        return delivery.all();
    }

    @Operation(summary = "Vəd və reallıq: bir sahə üzrə qalib müraciətin öhdəlikləri, aylıq icra və peyk yoxlaması")
    @GetMapping("/delivery/{plotId}")
    public DeliveryService.Delivery deliveryForPlot(@org.springframework.web.bind.annotation.PathVariable Long plotId) {
        return delivery.forPlot(plotId);
    }
}
