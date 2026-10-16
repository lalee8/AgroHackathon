package az.aqroinvest.advisor;

import az.aqroinvest.buyer.BuyerDtos.DemandView;
import az.aqroinvest.buyer.BuyerService;
import az.aqroinvest.economics.*;
import az.aqroinvest.market.MarketEnums.Risk;
import az.aqroinvest.market.ProjectDtos.ProfileView;
import az.aqroinvest.market.ProjectDtos.ProjectCard;
import az.aqroinvest.market.ProjectSector;
import az.aqroinvest.market.ProjectService;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotCategory;
import az.aqroinvest.plot.PlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Məsləhətçi üçün kontekst: istifadəçinin niyyətinə uyğun real platforma məlumatı (sahələr, büdcəyə uyğunlaşdırılmış
 * simulyasiya nəticələri, investor axtaran layihələr, təsdiqlənmiş alıcı tələbləri). AI yalnız bu məlumata əsasən
 * danışır. Şəxsi məlumat (ad, telefon, e-poçt, FİN) kontekstə daxil edilmir.
 */
@Service
@RequiredArgsConstructor
public class AdvisorContextService {
    private final PlotRepository plots;
    private final LandPotentialService potential;
    private final ModelFactory models;
    private final MonteCarloService mc;
    private final ProjectService projects;
    private final BuyerService buyers;
    private final EconomicsController economics;

    /** Sahə tövsiyəsi: büdcəyə görə miqyaslanmış ssenari. */
    public record PlotRec(Long plotId, String code, String name, String district, String designation, double areaHa,
                          String useCase, String useLabel, double scale, String unit, long capex, long annualNet,
                          Integer paybackYears, double paybackProbability, int horizon, int suitability, int jobs,
                          List<String> reasons, String url) {}

    public record ProjectRec(Long id, String title, String sector, long ask, String offer, String investorView,
                             int credibility, String credibilityLabel, String risk, Integer matchPercent, String url) {}

    public record DemandRec(Long id, String company, String product, double volumeTons, Double priceMin, Double priceMax,
                            int contractYears, String district, int radiusKm, String url) {}

    public record Context(AdvisorIntent intent, List<PlotRec> plots, List<ProjectRec> projects, List<DemandRec> demands,
                          EconomicsController.GoalView goal, Map<String, String> glossary) {}

    @Transactional(readOnly = true)
    public Context build(AdvisorIntent in) {
        Set<UseCase> uses = new LinkedHashSet<>();
        in.sectors().forEach(s -> { if (s.useCase() != null) uses.add(s.useCase()); });
        if (in.nonArable()) uses.add(UseCase.GUNES);
        // yalnız termin soruşulubsa (məs. "bonitet nədir?") siyahı lazım deyil
        boolean termOnly = in.term() != null && in.sectors().isEmpty() && in.budget() == null && in.monthlyGoal() == null && !in.nonArable();
        List<PlotRec> recs = termOnly ? List.of() : plotRecs(in, uses);
        List<ProjectRec> prj = termOnly ? List.of() : projectRecs(in);
        List<DemandRec> dem = termOnly ? List.of() : demandRecs(in, uses);
        EconomicsController.GoalView goal = null;
        if (in.monthlyGoal() != null) {
            UseCase u = uses.isEmpty() ? UseCase.GUNES : uses.iterator().next();
            Long plotId = recs.stream().filter(r -> r.useCase().equals(u.name())).map(PlotRec::plotId).findFirst()
                    .orElseGet(() -> plots.findAll().stream().filter(p -> LandPotentialService.permitted(p.getDesignation(), u))
                            .max(Comparator.comparing(Plot::getAreaHa)).map(Plot::getId).orElse(null));
            if (plotId != null) {
                try { goal = economics.goal(plotId, u, in.monthlyGoal()); } catch (RuntimeException ignored) { }
            }
        }
        Map<String, String> gl = new LinkedHashMap<>();
        if (in.term() != null) gl.put(in.term(), AdvisorIntent.GLOSSARY.get(in.term()));
        for (String k : List.of("p10", "bar verməyə", "bonitet")) gl.putIfAbsent(k, AdvisorIntent.GLOSSARY.get(k));
        if (in.nonArable() || uses.contains(UseCase.GUNES)) { gl.putIfAbsent("şoran", AdvisorIntent.GLOSSARY.get("şoran")); gl.putIfAbsent("aktiv istehlakçı", AdvisorIntent.GLOSSARY.get("aktiv istehlakçı")); }
        return new Context(in, recs, prj, dem, goal, gl);
    }

    private List<PlotRec> plotRecs(AdvisorIntent in, Set<UseCase> uses) {
        record Cand(Plot p, LandPotentialService.Option o) {}
        List<Cand> cands = new ArrayList<>();
        for (Plot p : plots.findAll()) {
            if (!in.districts().isEmpty() && !in.districts().contains(p.getDistrict())) continue;
            for (LandPotentialService.Option o : potential.options(p)) {
                if (!o.permitted() || o.suitability() < 50) continue;
                if (!uses.isEmpty() && !uses.contains(o.useCase())) continue;
                cands.add(new Cand(p, o));
            }
        }
        List<PlotRec> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        cands.sort(Comparator.comparingDouble((Cand c) -> -(c.o().rank() + budgetFit(in.budget(), c.o()))));
        for (Cand c : cands) {
            if (out.size() >= 4 || seen.contains(c.p().getId())) continue;
            LandPotentialService.Option o = c.o();
            double scale = o.scale();
            if (in.budget() != null && o.capex() > 0) {
                double perUnit = o.capex() / Math.max(0.0001, o.scale());
                scale = Math.min(o.maxScale(), in.budget() / perUnit);
                if (scale < o.scale() * 0.08) continue;
                scale = round(scale, o.unit());
            }
            SimulationSpec spec = models.build(c.p(), o.useCase(), new ModelFactory.Overrides(scale, null, null, null, null, null, null));
            MonteCarloService.Summary s = mc.simulate(spec, 400, 11).summary();
            if (in.risk() == Risk.LOW && s.probPayback() < 0.8) continue;
            if (in.horizon() != null && s.paybackP50() != null && s.paybackP50() > in.horizon() + 2) continue;
            seen.add(c.p().getId());
            out.add(new PlotRec(c.p().getId(), c.p().getCode(), c.p().getName(), c.p().getDistrict().label(), c.p().getDesignation().label(),
                    c.p().getAreaHa().doubleValue(), o.useCase().name(), o.label(), spec.scale(), spec.unit(), Math.round(spec.capex()),
                    s.annualNetMature(), s.paybackP50(), s.probPayback(), spec.horizon(), o.suitability(), spec.jobs(),
                    o.reasons().stream().filter(LandPotentialService.Reason::positive).limit(2).map(LandPotentialService.Reason::text).toList(),
                    "/simulyator.html?plot=" + c.p().getId() + "&use=" + o.useCase().name()));
        }
        return out;
    }

    private List<ProjectRec> projectRecs(AdvisorIntent in) {
        List<String> sectors = in.sectors().stream().map(Enum::name).toList();
        BigDecimal max = in.budget() == null ? null : BigDecimal.valueOf(in.budget());
        ProfileView pf = new ProfileView(sectors, null, max, in.risk() == null ? Risk.MID : in.risk(), in.horizon() == null ? 10 : in.horizon());
        List<ProjectCard> list = projects.list(null, null, pf, "match");
        return list.stream()
                .filter(c -> sectors.isEmpty() || sectors.contains(c.sector().name()) || (in.sectors().contains(ProjectSector.EMAL) && c.sector() == ProjectSector.EMAL))
                .limit(3).map(c -> {
                    var a = projects.detailAnalysisHeadline(c.id());
                    return new ProjectRec(c.id(), c.title(), c.sectorLabel(), c.askAmount().longValue(), c.offer(), a, c.credibility(),
                            c.credibilityLabel(), c.riskLabel(), c.match() == null ? null : c.match().score(), "/layihe.html?id=" + c.id());
                }).toList();
    }

    private List<DemandRec> demandRecs(AdvisorIntent in, Set<UseCase> uses) {
        Set<PlotCategory> cats = new HashSet<>();
        uses.forEach(u -> { if (u.category() != null) cats.add(u.category()); });
        if (uses.contains(UseCase.SOYUDUCU)) cats.addAll(List.of(PlotCategory.BAGCILIQ, PlotCategory.ISTIXANA));
        List<DemandView> all = buyers.verified(null);
        return all.stream().filter(d -> cats.isEmpty() || cats.contains(d.category())).limit(3)
                .map(d -> new DemandRec(d.id(), d.company(), d.productLabel(), d.volumeTons().doubleValue(),
                        d.priceMin() == null ? null : d.priceMin().doubleValue(), d.priceMax() == null ? null : d.priceMax().doubleValue(),
                        d.contractYears(), d.districtLabel(), d.radiusKm(), "/alicilar.html?demand=" + d.id())).toList();
    }

    /** Büdcəni yaxşı istifadə edən variantlara üstünlük: investisiya büdcənin 40-100%-i olanda bonus, çox kiçik olanda cərimə. */
    private static double budgetFit(Long budget, LandPotentialService.Option o) {
        if (budget == null || o.capex() <= 0) return 0;
        double maxCapex = o.capex() / Math.max(0.0001, o.scale()) * o.maxScale();
        double usable = Math.min(budget, maxCapex) / budget;
        return usable >= 0.4 ? 10 : usable < 0.15 ? -15 : 0;
    }

    private static double round(double v, String unit) {
        return switch (unit) {
            case "otaq" -> Math.max(4, Math.floor(v));
            case "t" -> Math.max(100, Math.floor(v / 50) * 50);
            default -> Math.floor(v * 10) / 10.0;
        };
    }
}
