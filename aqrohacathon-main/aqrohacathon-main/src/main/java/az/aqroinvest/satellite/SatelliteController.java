package az.aqroinvest.satellite;

import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import az.aqroinvest.plot.PlotService;
import az.aqroinvest.plot.PlotStatus;
import az.aqroinvest.satellite.SatelliteDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag(name = "Peyk arxivi və monitorinq", description = "Torpağın zaman maşını (Landsat 1990-2010, Sentinel-2 2018-2025 və son görüntü) və NDVI tarixçəsi")
@RestController
@RequiredArgsConstructor
public class SatelliteController {
    private final SatelliteService satellite;
    private final PlotService plots;
    private final PlotRepository plotRepository;

    @Operation(summary = "Sahənin peyk arxivi: epoxalar, çərçivə, poliqon")
    @GetMapping("/api/v1/plots/{id}/imagery")
    @Transactional(readOnly = true)
    public ImageryView imagery(@PathVariable Long id) {
        return satellite.imagery(plots.find(id));
    }

    @Operation(summary = "Epoxa üzrə peyk şəkli (JPEG)")
    @GetMapping(value = "/api/v1/plots/{id}/imagery/{epoch}", produces = MediaType.IMAGE_JPEG_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> image(@PathVariable Long id, @PathVariable String epoch) {
        ImageryEpoch e = ImageryEpoch.of(epoch);
        byte[] bytes = satellite.image(plots.find(id), e);
        // Son görüntü dəyişir: qısa keş (URL-də səhnə nömrəsi var, yeni səhnə yeni ünvan deməkdir)
        CacheControl cc = e == ImageryEpoch.LIVE ? CacheControl.maxAge(6, TimeUnit.HOURS).cachePublic() : CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(cc).body(bytes);
    }

    @Operation(summary = "Sahə üzrə NDVI illik seriyası (1990-dan bu günə) və izahı")
    @GetMapping("/api/v1/plots/{id}/ndvi")
    @Transactional(readOnly = true)
    public NdviSeries ndvi(@PathVariable Long id) {
        return satellite.ndvi(plots.find(id));
    }

    @Operation(summary = "Peykdən nəzarət: icarəyə verilmiş və müsabiqədəki sahələr")
    @SecurityRequirement(name = "bearer")
    @GetMapping("/api/v1/admin/monitoring")
    @Transactional(readOnly = true)
    public List<MonitoringRow> monitoring() {
        List<Plot> all = plotRepository.findAll(Sort.by("code"));
        return all.stream().map(satellite::monitoring)
                .sorted(Comparator.comparingInt((MonitoringRow r) -> switch (r.status()) {
                    case "ALERT" -> 0;
                    case "ACTIVE" -> 1;
                    case "BASELINE" -> 2;
                    default -> 3;
                }).thenComparing(r -> PlotStatus.AWARDED.name().equals(r.plotStatus()) ? 0 : 1).thenComparing(MonitoringRow::code))
                .toList();
    }
}
