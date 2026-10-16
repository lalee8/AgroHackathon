package az.aqroinvest.matching;

import az.aqroinvest.plot.PlotCategory;
import az.aqroinvest.plot.PlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Ağıllı tövsiyə və ROI", description = "İnvestor profilinə uyğun sahələr və gəlirlilik proqnozu")
@RestController
@RequiredArgsConstructor
public class MatchingController {
    private final RecommendationService recommendations;
    private final RoiService roi;
    private final PlotService plots;

    @Operation(summary = "Profilə uyğun sahələrin izahlı sıralanması")
    @PostMapping("/api/v1/recommendations")
    public List<RecommendationService.Match> recommend(@RequestBody RecommendationService.Request req) {
        return recommendations.recommend(req);
    }

    @Operation(summary = "Profilə uyğun sahələrin sayı (forma dəyişdikcə canlı sayğac)")
    @PostMapping("/api/v1/recommendations/summary")
    public RecommendationService.Summary recommendSummary(@RequestBody RecommendationService.Request req) {
        return recommendations.summary(req);
    }

    @Operation(summary = "Sahə üzrə təxmini gəlirlilik (10 illik proqnoz)")
    @GetMapping("/api/v1/roi/{plotId}")
    @Transactional(readOnly = true)
    public RoiService.RoiResult roi(@PathVariable Long plotId,
                                    @RequestParam(required = false) PlotCategory category,
                                    @RequestParam(required = false) Long investment) {
        return roi.calculate(plots.find(plotId), category, investment);
    }
}
