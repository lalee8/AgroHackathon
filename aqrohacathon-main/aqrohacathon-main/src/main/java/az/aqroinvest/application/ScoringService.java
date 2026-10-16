package az.aqroinvest.application;

import az.aqroinvest.competition.CompetitionService;
import az.aqroinvest.competition.Criterion;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.Technology;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Şəffaf avtomatik ilkin qiymətləndirmə: hər meyar üzrə 0..1 nisbət hesablanır və müsabiqənin
 * meyar çəkisinə vurulur. Nəticə izahlı şəkildə (breakdown) saxlanılır - "qara qutu" yoxdur.
 * Yekun qərarı komissiya verir; avtomatik bal yalnız sıralama və prioritetləşdirmə üçündür.
 */
@Service
@RequiredArgsConstructor
public class ScoringService {
    private final CompetitionService competitions;
    private final ObjectMapper mapper;

    public record CriterionScore(String key, String label, int weight, double ratio, double points, String explanation) {}

    public record ScoreResult(int total, List<CriterionScore> breakdown) {}

    public ScoreResult score(Application a) {
        Plot plot = a.getPlot();
        List<CriterionScore> parts = new ArrayList<>();
        for (Criterion c : competitions.criteriaOf(a.getCompetition())) {
            double ratio;
            String why;
            switch (c.key()) {
                case "investment" -> {
                    double inv = num(a.getInvestmentAmount());
                    double min = Math.max(1, num(plot.getMinInvestment()));
                    ratio = inv < min ? 0.5 * inv / min : Math.min(1, 0.6 + 0.4 * (inv - min) / min);
                    why = String.format(Locale.ROOT, "Təklif: %,.0f AZN, minimum: %,.0f AZN (%.1fx)", inv, min, inv / min).replace(',', ' ');
                }
                case "jobs" -> {
                    int jobs = a.getJobsPlanned() == null ? 0 : a.getJobsPlanned();
                    int min = Math.max(1, plot.getMinJobs());
                    ratio = jobs < min ? 0.5 * jobs / min : Math.min(1, 0.6 + 0.4 * (jobs - min) / (double) min);
                    why = jobs + " iş yeri (minimum " + min + ")";
                }
                case "businessPlan" -> {
                    int exp = a.getExperienceYears() == null ? 0 : a.getExperienceYears();
                    int len = a.getBusinessPlan() == null ? 0 : a.getBusinessPlan().trim().length();
                    double expPart = Math.min(1, exp / 10.0) * 0.5;
                    double planPart = Math.min(1, len / 1200.0) * 0.3;
                    double revPart = a.getExpectedAnnualRevenue() != null && a.getExpectedAnnualRevenue().signum() > 0 ? 0.2 : 0;
                    ratio = expPart + planPart + revPart;
                    why = exp + " il təcrübə, biznes plan xülasəsi " + len + " simvol"
                            + (revPart > 0 ? ", gəlir proqnozu var" : ", gəlir proqnozu yoxdur");
                }
                case "innovation" -> {
                    List<Technology> techs = a.getTechnologies().stream().map(Technology::parse).filter(Objects::nonNull).toList();
                    int pts = techs.stream().mapToInt(Technology::points).sum();
                    ratio = Math.min(1, pts / 10.0);
                    why = techs.isEmpty() ? "Müasir texnologiya göstərilməyib"
                            : techs.stream().map(Technology::label).collect(Collectors.joining(", "));
                }
                case "ownFunds" -> {
                    int pct = a.getOwnFundsPercent() == null ? 0 : a.getOwnFundsPercent();
                    ratio = Math.min(1, pct / 60.0);
                    why = "Öz vəsaiti: " + pct + "% (60%+ tam bal)";
                }
                case "documents" -> {
                    Set<DocumentType> types = a.getDocuments().stream().map(ApplicationDocument::getDocType).collect(Collectors.toSet());
                    long all = Arrays.stream(DocumentType.values()).filter(DocumentType::required).count();
                    long req = Arrays.stream(DocumentType.values()).filter(DocumentType::required).filter(types::contains).count();
                    boolean tax = types.contains(DocumentType.TAX_CERTIFICATE);
                    ratio = all == 0 ? 1 : (double) req / all;
                    why = req + "/" + all + " sənəd (Qaydalar 2.4.6)" + (tax ? ", vergi borcu yoxdur arayışı var" : ", vergi arayışı yoxdur");
                }
                case "social" -> {
                    ratio = a.isIdpStatus() ? 1 : 0.3;
                    why = a.isIdpStatus() ? "Yerli müraciətçi: məcburi köçkün / Qarabağ əsilli (bərabər balda üstünlük, 4.10.7)" : "Yerli müraciətçi deyil";
                }
                default -> {
                    ratio = 0.5;
                    why = "Ekspert qiymətləndirməsi ilə müəyyən edilir";
                }
            }
            ratio = Math.max(0, Math.min(1, ratio));
            parts.add(new CriterionScore(c.key(), c.label(), c.weight(), round2(ratio), round2(ratio * c.weight()), why));
        }
        int total = (int) Math.round(parts.stream().mapToDouble(CriterionScore::points).sum());
        return new ScoreResult(Math.min(100, total), parts);
    }

    public void apply(Application a) {
        ScoreResult r = score(a);
        a.setAutoScore(r.total());
        try {
            a.setScoreBreakdown(mapper.writeValueAsString(r.breakdown()));
        } catch (Exception e) {
            a.setScoreBreakdown(null);
        }
    }

    /** Ekspert ballarından (0-10) çəkili yekun (0-100). */
    public int expertTotal(Map<String, Integer> scores, List<Criterion> criteria) {
        double t = 0;
        for (Criterion c : criteria) {
            Integer s = scores.get(c.key());
            if (s != null) t += c.weight() * Math.max(0, Math.min(10, s)) / 10.0;
        }
        return (int) Math.round(t);
    }

    private static double num(BigDecimal b) {
        return b == null ? 0 : b.doubleValue();
    }

    private static double round2(double d) {
        return Math.round(d * 100) / 100.0;
    }
}
