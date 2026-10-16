package az.aqroinvest.matching;

import az.aqroinvest.plot.*;
import az.aqroinvest.plot.PlotDtos.PlotView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * "Mənə uyğun sahə" - investorun profilinə (büdcə, istiqamət, ölçü, rayon, infrastruktur tələbləri)
 * əsasən sahələri izahlı uyğunluq balı (0-100) ilə sıralayan tövsiyə mühərriki.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final PlotService plotService;

    public record Request(BigDecimal budget, List<PlotCategory> categories, BigDecimal minArea, BigDecimal maxArea,
                          List<District> districts, boolean needIrrigation, boolean needElectricity,
                          Integer experienceYears) {}

    /** Balın izahı: hər meyar neçə bal verdi (cərimələr mənfi, maks 0). */
    public record Part(String label, int points, int max) {}

    public record Match(PlotView plot, int score, List<String> reasons, List<String> warnings, List<Part> breakdown) {}

    /** Sayğac üçün: neçə sahə profilə "uyğun" sayılır (bal 60+). */
    public record Summary(int suitable, int total, int best) {}

    public static final int SUITABLE = 60;

    @Transactional(readOnly = true)
    public List<Match> recommend(Request r) {
        // Sayğacla eyni: bütün "uyğun" sahələr (60+), ən azı 12 variant
        List<Match> out = recommendAll(r);
        int limit = Math.max(12, (int) out.stream().filter(m -> m.score() >= SUITABLE).count());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    @Transactional(readOnly = true)
    public Summary summary(Request r) {
        List<Match> all = recommendAll(r);
        return new Summary((int) all.stream().filter(m -> m.score() >= SUITABLE).count(), all.size(), all.isEmpty() ? 0 : all.get(0).score());
    }

    private List<Match> recommendAll(Request r) {
        List<PlotView> all = plotService.search(new PlotDtos.PlotFilter(null, null, null, null, null, null, null, null,
                null, null, null));
        List<Match> out = new ArrayList<>();
        for (PlotView p : all) {
            if (p.status() == PlotStatus.AWARDED) continue;
            double score = 0, part;
            List<String> reasons = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            List<Part> parts = new ArrayList<>();

            // 1) Fəaliyyət istiqaməti - 30
            part = 0;
            if (r.categories() == null || r.categories().isEmpty()) {
                part = 15;
            } else if (r.categories().contains(p.category())) {
                part = 30;
                reasons.add("Təyinat seçdiyiniz istiqamətə uyğundur: " + p.categoryLabel());
            } else {
                warnings.add("Təyinat fərqlidir: " + p.categoryLabel());
            }
            score += part;
            parts.add(new Part("İstiqamət", (int) Math.round(part), 30));

            // 2) Büdcə - 25
            if (r.budget() != null && r.budget().signum() > 0) {
                double ratio = r.budget().doubleValue() / p.minInvestment().doubleValue();
                if (ratio >= 1) {
                    part = 25;
                    reasons.add(String.format("Büdcəniz minimum investisiya tələbini %.1f dəfə qarşılayır", ratio));
                } else {
                    part = 25 * ratio * 0.4;
                    warnings.add(String.format("Büdcə minimum tələbdən %,.0f AZN azdır",
                            p.minInvestment().doubleValue() - r.budget().doubleValue()));
                }
            } else {
                part = 12;
            }
            score += part;
            parts.add(new Part("Büdcə", (int) Math.round(part), 25));

            // 3) Sahənin ölçüsü - 15
            double area = p.areaHa().doubleValue();
            double min = r.minArea() == null ? 0 : r.minArea().doubleValue();
            double max = r.maxArea() == null ? Double.MAX_VALUE : r.maxArea().doubleValue();
            if (area >= min && area <= max) {
                part = 15;
                if (r.minArea() != null || r.maxArea() != null) reasons.add("Sahənin ölçüsü istədiyiniz aralıqdadır (" + p.areaHa() + " ha)");
            } else {
                double dist = area < min ? (min - area) / Math.max(min, 1) : (area - max) / Math.max(max, 1);
                part = Math.max(0, 15 * (1 - dist));
                warnings.add("Ölçü istədiyiniz aralıqdan kənardadır (" + p.areaHa() + " ha)");
            }
            score += part;
            parts.add(new Part("Ölçü", (int) Math.round(part), 15));

            // 4) Rayon - 10
            part = 0;
            if (r.districts() == null || r.districts().isEmpty()) {
                part = 5;
            } else if (r.districts().contains(p.district())) {
                part = 10;
                reasons.add("Seçdiyiniz rayonda yerləşir: " + p.districtLabel());
            }
            score += part;
            parts.add(new Part("Rayon", (int) Math.round(part), 10));

            // 5) İnfrastruktur - 20
            part = p.infrastructureScore() * 0.2;
            score += part;
            parts.add(new Part("İnfrastruktur", (int) Math.round(part), 20));
            if (p.irrigation()) reasons.add("Suvarma imkanı var: " + Objects.toString(p.waterSource(), ""));
            else if (r.needIrrigation()) {
                score -= 10;
                parts.add(new Part("Suvarma yoxdur", -10, 0));
                warnings.add("Suvarma infrastrukturu yoxdur");
            }
            if (!p.electricity() && r.needElectricity()) {
                score -= 6;
                parts.add(new Part("Elektrik yoxdur", -6, 0));
                warnings.add("Elektrik xətti yoxdur");
            }
            if (p.soilQuality() != null && p.soilQuality() >= 75) reasons.add("Yüksək torpaq keyfiyyəti (bonitet " + p.soilQuality() + ")");
            if (p.deminingStatus() == DeminingStatus.CLEARED) reasons.add("Minalardan tam təmizlənib");

            // Müraciət imkanı
            if (p.competition() != null && p.competition().open()) {
                reasons.add("Aktiv müsabiqə: " + p.competition().code());
            } else {
                score -= 8;
                parts.add(new Part("Müsabiqə yoxdur", -8, 0));
                warnings.add("Hazırda aktiv müsabiqədə deyil");
            }
            out.add(new Match(p, (int) Math.max(0, Math.min(100, Math.round(score))), reasons, warnings, parts));
        }
        out.sort(Comparator.comparingInt(Match::score).reversed());
        return out;
    }
}
