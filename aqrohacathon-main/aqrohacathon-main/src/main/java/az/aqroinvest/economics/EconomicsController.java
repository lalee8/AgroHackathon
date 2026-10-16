package az.aqroinvest.economics;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "Torpağın potensialı və simulyator", description = "Ən yaxşı istifadə (xanalar), Monte-Carlo simulyasiyası, hədəf kalkulyatoru")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EconomicsController {
    private final PlotRepository plots;
    private final LandPotentialService potential;
    private final ModelFactory models;
    private final MonteCarloService mc;
    private final SolarYieldService solar;

    public record SimRequest(Long plotId, UseCase useCase, Double scale, Double capex, Double price, Double yieldPerUnit,
                             Double offtakeShare, Double offtakePriceFactor, Integer horizon, Integer runs) {}

    public record SpecView(UseCase useCase, String label, String unit, double scale, double maxScale, long capex,
                           double yieldPerUnit, String yieldUnit, double price, String priceUnit, long expectedRevenue,
                           int horizon, double discountRate, double offtakeShare, double offtakePriceFactor, int jobs,
                           double landHa, List<SimulationSpec.Assumption> assumptions, List<SimulationSpec.Shock> shocks) {}

    public record GoalView(UseCase useCase, String label, String unit, long monthlyTarget, double scaleP50, double scaleP90,
                           long capexP50, long capexP90, double landHaP50, double landHaP90, int jobsP50, double maxScale,
                           boolean feasibleOnPlot, String explanation) {}

    @Operation(summary = "Bu torpaqda nə etmək olar? Bütün istifadə ssenariləri uyğunluq və gəlirlilik üzrə")
    @GetMapping("/plots/{id}/potential")
    @Transactional(readOnly = true)
    public Map<String, Object> potential(@PathVariable Long id) {
        Plot p = plot(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plotId", p.getId());
        m.put("code", p.getCode());
        m.put("designation", p.getDesignation());
        m.put("designationLabel", p.getDesignation().label());
        m.put("designationDescription", p.getDesignation().description());
        m.put("options", potential.options(p));
        SolarYieldService.SolarYield sy = solar.yield(p.getLatitude(), p.getLongitude());
        m.put("solar", sy);
        return m;
    }

    @Operation(summary = "Monte-Carlo simulyasiyası: biznesin minlərlə mümkün gələcəyi (P10/P50/P90, geri qayıtma ehtimalı, risk amilləri)")
    @PostMapping("/simulate")
    @Transactional(readOnly = true)
    public Map<String, Object> simulate(@RequestBody SimRequest req) {
        if (req.plotId() == null) throw ApiException.badRequest("PLOT_REQUIRED", "Sahə seçilməlidir");
        Plot p = plot(req.plotId());
        UseCase u = req.useCase() != null ? req.useCase() : UseCase.of(p.getCategory());
        SimulationSpec spec = models.build(p, u, new ModelFactory.Overrides(req.scale(), req.capex(), req.price(),
                req.yieldPerUnit(), req.offtakeShare(), req.offtakePriceFactor(), req.horizon()));
        int runs = req.runs() == null ? 2000 : Math.max(200, Math.min(5000, req.runs()));
        MonteCarloService.Result r = mc.simulate(spec, runs, seed(p, u));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plot", Map.of("id", p.getId(), "code", p.getCode(), "name", p.getName(), "district", p.getDistrict().label(),
                "areaHa", p.getAreaHa(), "designation", p.getDesignation().label()));
        m.put("spec", view(p, spec));
        m.put("option", potential.option(p, u));
        m.put("result", r);
        if (u == UseCase.GUNES) m.put("solar", solar.yield(p.getLatitude(), p.getLongitude()));
        return m;
    }

    @Operation(summary = "Hədəf kalkulyatoru: \"ayda X ₼ qazanmaq üçün\" nə qədər miqyas, investisiya və torpaq lazımdır")
    @GetMapping("/simulate/goal")
    @Transactional(readOnly = true)
    public GoalView goal(@RequestParam Long plotId, @RequestParam UseCase useCase, @RequestParam long monthlyNet) {
        if (monthlyNet <= 0 || monthlyNet > 50_000_000) throw ApiException.badRequest("BAD_TARGET", "Aylıq hədəf 1 ₼ ilə 50 mln ₼ arasında olmalıdır");
        Plot p = plot(plotId);
        double annual = monthlyNet * 12.0;
        double s50 = solve(p, useCase, annual, false), s90 = solve(p, useCase, annual, true);
        if (useCase == UseCase.AQROTURIZM) { s50 = Math.ceil(s50); s90 = Math.ceil(s90); }
        SimulationSpec at50 = models.build(p, useCase, new ModelFactory.Overrides(s50, null, null, null, null, null, null));
        SimulationSpec at90 = models.build(p, useCase, new ModelFactory.Overrides(s90, null, null, null, null, null, null));
        double max = models.maxScale(p, useCase);
        boolean feasible = s50 <= max;
        String unit = useCase.unit();
        String expl = Double.isInfinite(s50)
                ? "Bu fəaliyyət istənilən miqyasda bu hədəfə çatmır: xərclər gəliri üstələyir."
                : String.format(Locale.ROOT, "Ayda %s ₼ xalis gəlir üçün orta ssenaridə %s %s lazımdır (təxminən %s ₼ investisiya, %s ha torpaq). "
                        + "90%% əminlik üçün %s %s. %s", fmtMoney(monthlyNet), fmtNum(s50), unit, fmtMoney(Math.round(capexOf(at50, s50, p, useCase))),
                fmtNum(land(useCase, s50)), fmtNum(s90), unit,
                feasible ? "Bu sahə buna imkan verir." : "Bu sahədə maksimum " + fmtNum(max) + " " + unit + " yerləşir: hədəf üçün daha böyük sahə və ya bir neçə sahə lazımdır.");
        return new GoalView(useCase, useCase.label(), unit, monthlyNet, round1(s50), round1(s90),
                Math.round(capexOf(at50, s50, p, useCase)), Math.round(capexOf(at90, s90, p, useCase)),
                round1(land(useCase, s50)), round1(land(useCase, s90)), at50.jobs(), max, feasible, expl);
    }

    @Operation(summary = "Simulyasiya ssenariləri və istinad göstəriciləri")
    @GetMapping("/simulate/use-cases")
    public List<Map<String, Object>> useCases() {
        return Arrays.stream(UseCase.values()).map(u -> Map.<String, Object>of("code", u.name(), "label", u.label(),
                "icon", u.icon(), "unit", u.unit(), "hint", u.hint(), "horizon", u.horizon(), "agro", u.agro())).toList();
    }

    // ------------------------------------------------------------------

    /** Hədəfə çatmaq üçün lazım olan miqyas: P50 (və ya P10 - 90% əminlik) yetkin illik xalis gəlir hədəfə bərabər olur. */
    private double solve(Plot p, UseCase u, double annualTarget, boolean conservative) {
        double lo = 0.01, hi = 1;
        while (net(p, u, hi, conservative) < annualTarget) {
            hi *= 2;
            if (hi > 1e6) return Double.POSITIVE_INFINITY;
        }
        for (int i = 0; i < 28; i++) {
            double mid = (lo + hi) / 2;
            if (net(p, u, mid, conservative) >= annualTarget) hi = mid; else lo = mid;
        }
        return hi;
    }

    private double net(Plot p, UseCase u, double scale, boolean conservative) {
        SimulationSpec base = models.build(p, u, ModelFactory.Overrides.none());
        // miqyas sahənin ölçüsü ilə məhdudlaşmasın deyə spesifikasiya birbaşa qurulur
        SimulationSpec s = scaled(base, scale);
        MonteCarloService.Summary sum = mc.simulate(s, 300, 7).summary();
        return conservative ? sum.annualNetMatureP10() : sum.annualNetMature();
    }

    private static SimulationSpec scaled(SimulationSpec b, double scale) {
        double k = scale / b.scale();
        // sabit xərclərin miqyasla dəyişən hissəsi (icarə, istismar) mütənasib artırılır
        return new SimulationSpec(b.useCase(), b.unit(), scale, b.capex() * k, b.yieldPerUnit(), b.yieldUnit(), b.price(),
                b.priceUnit(), b.opexFixed() * (b.useCase() == UseCase.GUNES ? k : Math.max(1, Math.sqrt(k))), b.opexPerUnit(),
                b.opexRevenueShare(), b.ramp(), b.yieldSigma(), b.priceSigma(), b.priceDrift(), b.shocks(), b.degradation(),
                b.horizon(), b.discountRate(), b.offtakeShare(), b.offtakePriceFactor(), b.capexOverrunMean(),
                b.capexOverrunSigma(), b.jobs(), b.assumptions());
    }

    private double capexOf(SimulationSpec atScale, double scale, Plot p, UseCase u) {
        if (Double.isInfinite(scale)) return 0;
        SimulationSpec base = models.build(p, u, ModelFactory.Overrides.none());
        return base.capex() / base.scale() * scale;
    }

    static double land(UseCase u, double scale) {
        return switch (u) {
            case GUNES -> scale * ModelFactory.SOLAR_HA_PER_MWP;
            case AQROTURIZM -> Math.max(2, scale * 0.4);
            case SOYUDUCU -> Math.max(1, scale / 1000.0);
            case ISTIXANA -> scale / 0.6;
            default -> scale;
        };
    }

    private SpecView view(Plot p, SimulationSpec s) {
        return new SpecView(s.useCase(), s.useCase().label(), s.unit(), round1(s.scale()), models.maxScale(p, s.useCase()),
                Math.round(s.capex()), round1(s.yieldPerUnit()), s.yieldUnit(), s.price(), s.priceUnit(),
                Math.round(s.expectedAnnualRevenue()), s.horizon(), s.discountRate(), s.offtakeShare(), s.offtakePriceFactor(),
                s.jobs(), round1(land(s.useCase(), s.scale())), s.assumptions(), s.shocks());
    }

    private Plot plot(Long id) {
        return plots.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Sahə tapılmadı"));
    }

    private static long seed(Plot p, UseCase u) {
        return p.getId() * 31 + u.ordinal();
    }

    private static double round1(double d) {
        return Double.isInfinite(d) ? -1 : Math.round(d * 10) / 10.0;
    }

    static String fmtMoney(long v) {
        if (Math.abs(v) >= 1_000_000) return String.format(Locale.ROOT, "%.1f mln", v / 1e6);
        if (Math.abs(v) >= 10_000) return String.format(Locale.ROOT, "%.0f min", v / 1e3);
        return String.valueOf(v);
    }

    static String fmtNum(double d) {
        return d >= 100 ? String.valueOf(Math.round(d)) : String.format(Locale.ROOT, "%.1f", d);
    }
}
