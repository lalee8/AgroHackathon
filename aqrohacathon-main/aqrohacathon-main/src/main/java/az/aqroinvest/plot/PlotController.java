package az.aqroinvest.plot;

import az.aqroinvest.plot.PlotDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Tag(name = "Torpaq sahələri", description = "Aqrar investisiya xəritəsi və kataloq")
@RestController
@RequestMapping("/api/v1/plots")
@RequiredArgsConstructor
public class PlotController {
    private final PlotService service;

    @Operation(summary = "Sahələrin siyahısı (filtr və sıralama ilə)")
    @GetMapping
    public List<PlotView> list(@RequestParam(required = false) List<PlotCategory> category,
                               @RequestParam(required = false) List<District> district,
                               @RequestParam(required = false) BigDecimal minArea,
                               @RequestParam(required = false) BigDecimal maxArea,
                               @RequestParam(required = false) BigDecimal maxRent,
                               @RequestParam(required = false) Boolean irrigation,
                               @RequestParam(required = false) Boolean electricity,
                               @RequestParam(required = false) PlotStatus status,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) Boolean openOnly,
                               @RequestParam(required = false) String sort) {
        return service.search(new PlotFilter(category, district, minArea, maxArea, maxRent, irrigation, electricity,
                status, q, openOnly, sort));
    }

    @Operation(summary = "Sahə haqqında ətraflı məlumat")
    @GetMapping("/{id}")
    public PlotView get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Seçilmiş sahələrin müqayisəsi (maks. 4)")
    @GetMapping("/compare")
    public List<PlotView> compare(@RequestParam List<Long> ids) {
        return service.byIds(ids.stream().distinct().limit(4).toList());
    }

    @Operation(summary = "Bütün sahələr GeoJSON formatında (CBS/GIS sistemləri üçün)")
    @GetMapping(value = "/geojson", produces = "application/geo+json")
    public Map<String, Object> geojson() {
        return toFeatureCollection(service.search(new PlotFilter(null, null, null, null, null, null, null, null, null, null, null)));
    }

    public static Map<String, Object> toFeatureCollection(List<PlotView> plots) {
        List<Map<String, Object>> features = new ArrayList<>();
        for (PlotView p : plots) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("id", p.id());
            props.put("code", p.code());
            props.put("name", p.name());
            props.put("district", p.district());
            props.put("category", p.category());
            props.put("areaHa", p.areaHa());
            props.put("status", p.status());
            props.put("deminingStatus", p.deminingStatus());
            props.put("annualRentPerHa", p.annualRentPerHa());
            props.put("minInvestment", p.minInvestment());
            Map<String, Object> geometry = new LinkedHashMap<>();
            geometry.put("type", "Polygon");
            geometry.put("coordinates", List.of(parse(p.polygon())));
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("type", "Feature");
            f.put("id", p.code());
            f.put("geometry", geometry);
            f.put("properties", props);
            features.add(f);
        }
        return Map.of("type", "FeatureCollection", "crs", Map.of("type", "name", "properties", Map.of("name", "EPSG:4326")),
                "features", features);
    }

    private static List<List<Double>> parse(String polygon) {
        List<List<Double>> pts = new ArrayList<>();
        if (polygon == null) return pts;
        String s = polygon.replace("[", " ").replace("]", " ");
        String[] nums = s.trim().split("[ ,]+");
        for (int i = 0; i + 1 < nums.length; i += 2) {
            pts.add(List.of(Double.parseDouble(nums[i]), Double.parseDouble(nums[i + 1])));
        }
        return pts;
    }
}
