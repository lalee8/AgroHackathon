package az.aqroinvest.common;

import az.aqroinvest.application.ApplicantType;
import az.aqroinvest.application.ApplicationStatus;
import az.aqroinvest.application.Decision;
import az.aqroinvest.application.DocumentType;
import az.aqroinvest.competition.CompetitionStatus;
import az.aqroinvest.competition.Criterion;
import az.aqroinvest.plot.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Tag(name = "Soraq kitabçaları", description = "Klassifikatorlar - EKTİS kodları ilə uyğunlaşdırma üçün")
@RestController
public class ReferenceController {

    @Operation(summary = "Bütün klassifikatorlar (kod → ad)")
    @GetMapping("/api/v1/reference")
    public Map<String, Object> reference() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("districts", Arrays.stream(District.values()).map(d -> Map.of("code", d.name(), "label", d.label(),
                "region", d.region(), "lat", d.lat(), "lng", d.lng())).toList());
        m.put("categories", Arrays.stream(PlotCategory.values()).map(c -> Map.of("code", c.name(), "label", c.label(),
                "product", c.product())).toList());
        m.put("designations", Arrays.stream(LandDesignation.values()).map(d -> Map.of("code", d.name(), "label", d.label(),
                "description", d.description())).toList());
        m.put("plotStatuses", labels(PlotStatus.values(), PlotStatus::label));
        m.put("deminingStatuses", labels(DeminingStatus.values(), DeminingStatus::label));
        m.put("usageTypes", labels(UsageType.values(), UsageType::label));
        m.put("applicationStatuses", labels(ApplicationStatus.values(), ApplicationStatus::label));
        m.put("decisions", labels(Decision.values(), Decision::label));
        m.put("applicantTypes", labels(ApplicantType.values(), ApplicantType::label));
        m.put("competitionStatuses", labels(CompetitionStatus.values(), CompetitionStatus::label));
        m.put("documentTypes", Arrays.stream(DocumentType.values()).map(d -> Map.of("code", d.name(), "label", d.label(),
                "required", d.required())).toList());
        m.put("technologies", Arrays.stream(Technology.values()).map(t -> Map.of("code", t.name(), "label", t.label(),
                "points", t.points())).toList());
        m.put("defaultCriteria", Criterion.defaults());
        return m;
    }

    private static <E extends Enum<E>> List<Map<String, String>> labels(E[] values, java.util.function.Function<E, String> f) {
        return Arrays.stream(values).map(v -> Map.of("code", v.name(), "label", f.apply(v))).toList();
    }
}
