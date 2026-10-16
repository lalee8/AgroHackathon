package az.aqroinvest.impact;

import az.aqroinvest.application.ApplicationRepository;
import az.aqroinvest.buyer.BuyerDemand;
import az.aqroinvest.buyer.BuyerDemandRepository;
import az.aqroinvest.buyer.BuyerEnums.DemandStatus;
import az.aqroinvest.economics.*;
import az.aqroinvest.market.MarketEnums.ProjectStatus;
import az.aqroinvest.market.Project;
import az.aqroinvest.market.ProjectRepository;
import az.aqroinvest.plot.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * "Böyük Qayıdış" paneli: hər rayon üzrə portalda olan torpağın iqtisadi potensialı (ən uyğun istiqamət ilə tam
 * istifadə olunsa nə qədər investisiya, iş yeri, ailə, ərzaq), investor marağı və investisiyaya hazırlıq indeksi.
 * Dövlət qurumu üçün sual: hansı rayon növbəti investisiya dalğasına hazırdır və nə yaradacaq?
 */
@Service
@RequiredArgsConstructor
public class RegionService {
    private final PlotRepository plots;
    private final LandPotentialService potential;
    private final ModelFactory models;
    private final ApplicationRepository applications;
    private final ProjectRepository projects;
    private final BuyerDemandRepository demands;

    /** Hazırlıq indeksinin tərkib hissələri (0-100 bal cəmi): minatəmizləmə 30, infrastruktur 25, iqtisadi potensial 25, bazar marağı 20. */
    public record Readiness(int total, int demining, int infrastructure, int economics, int market) {}

    public record UseShare(String useCase, String label, int plots, long capex) {}

    public record DistrictRow(String district, String label, String region, double lat, double lng, int plots, double areaHa,
                              double clearedPct, double infraPct, int availablePlots, int awardedPlots, long potentialCapex,
                              long potentialAnnualNet, Impact potential, List<UseShare> uses, long applications, long applicationInvestment,
                              int projects, long projectAsk, int demands, double demandTons, Readiness readiness, String headline) {}

    public record Totals(int plots, double areaHa, long potentialCapex, long potentialAnnualNet, Impact potential, long applications,
                         long applicationInvestment, int projects, long projectAsk, int demands, double demandTons, double jobsPerMillion) {}

    public record Overview(Totals totals, List<DistrictRow> districts, List<String> method) {}

    @Transactional(readOnly = true)
    public Overview overview() {
        Map<District, List<Plot>> byDistrict = new EnumMap<>(District.class);
        for (Plot p : plots.findAll()) byDistrict.computeIfAbsent(p.getDistrict(), k -> new ArrayList<>()).add(p);

        Map<District, long[]> apps = new EnumMap<>(District.class);
        for (Object[] r : applications.statsByDistrict()) apps.put((District) r[0], new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
        Map<District, List<Project>> projByD = new EnumMap<>(District.class);
        for (Project p : projects.findPublished(ProjectStatus.PUBLISHED)) {
            District d = p.getPlot() != null ? p.getPlot().getDistrict() : p.getDistrict();
            if (d != null) projByD.computeIfAbsent(d, k -> new ArrayList<>()).add(p);
        }
        Map<District, List<BuyerDemand>> demByD = new EnumMap<>(District.class);
        for (BuyerDemand d : demands.findByStatusFull(DemandStatus.VERIFIED)) if (d.getDistrict() != null) demByD.computeIfAbsent(d.getDistrict(), k -> new ArrayList<>()).add(d);

        List<DistrictRow> rows = new ArrayList<>();
        for (Map.Entry<District, List<Plot>> e : byDistrict.entrySet()) rows.add(row(e.getKey(), e.getValue(), apps.get(e.getKey()), projByD.getOrDefault(e.getKey(), List.of()), demByD.getOrDefault(e.getKey(), List.of())));

        // bazar marağı balı rayonlar arasında nisbi hesablanır
        double maxPull = rows.stream().mapToDouble(RegionService::pull).max().orElse(1);
        List<DistrictRow> scored = new ArrayList<>();
        for (DistrictRow r : rows) {
            int market = maxPull <= 0 ? 0 : (int) Math.round(20 * pull(r) / maxPull);
            Readiness rd = new Readiness(r.readiness().demining() + r.readiness().infrastructure() + r.readiness().economics() + market,
                    r.readiness().demining(), r.readiness().infrastructure(), r.readiness().economics(), market);
            scored.add(new DistrictRow(r.district(), r.label(), r.region(), r.lat(), r.lng(), r.plots(), r.areaHa(), r.clearedPct(), r.infraPct(),
                    r.availablePlots(), r.awardedPlots(), r.potentialCapex(), r.potentialAnnualNet(), r.potential(), r.uses(), r.applications(),
                    r.applicationInvestment(), r.projects(), r.projectAsk(), r.demands(), r.demandTons(), rd, headline(r, rd)));
        }
        scored.sort(Comparator.comparingInt((DistrictRow r) -> -r.readiness().total()).thenComparing(DistrictRow::label));

        Impact sum = Impact.ZERO;
        long capex = 0, net = 0, appsN = 0, appsInv = 0, ask = 0;
        int plotsN = 0, proj = 0, dem = 0;
        double area = 0, tons = 0;
        for (DistrictRow r : scored) {
            sum = sum.plus(r.potential());
            capex += r.potentialCapex();
            net += r.potentialAnnualNet();
            plotsN += r.plots();
            area += r.areaHa();
            appsN += r.applications();
            appsInv += r.applicationInvestment();
            proj += r.projects();
            ask += r.projectAsk();
            dem += r.demands();
            tons += r.demandTons();
        }
        Totals totals = new Totals(plotsN, round1(area), capex, net, sum, appsN, appsInv, proj, ask, dem, round1(tons), Impact.jobsPerMillion(sum.directJobs(), capex));
        List<String> method = new ArrayList<>();
        method.add("Potensial: portaldakı hər sahə üçün torpaq təyinatına icazəli və ən yüksək uyğunluq balı alan istiqamət seçilir və standart miqyasda modelləşdirilir (icarəyə verilmiş sahələr də daxildir).");
        method.add("Hazırlıq indeksi (100 bal): minatəmizləmə 30 (tam təmizlənmiş sahə payı, texniki sorğu 80% sayılır), infrastruktur 25 (elektrik, suvarma, yola 5 km-dən yaxın), iqtisadi potensial 25 (ən uyğun istiqamətin orta balı), bazar marağı 20 (müraciətlər, layihələr və təsdiqlənmiş alıcı tələbləri, rayonlar arasında nisbi).");
        method.addAll(Impact.SOURCES);
        return new Overview(totals, scored, method);
    }

    private DistrictRow row(District d, List<Plot> ps, long[] app, List<Project> prj, List<BuyerDemand> dem) {
        double area = 0, cleared = 0, infra = 0, suit = 0, capex = 0, net = 0;
        int available = 0, awarded = 0;
        Impact im = Impact.ZERO;
        Map<UseCase, long[]> uses = new EnumMap<>(UseCase.class);
        for (Plot p : ps) {
            double a = p.getAreaHa().doubleValue();
            area += a;
            cleared += a * (p.getDeminingStatus() == DeminingStatus.CLEARED ? 1 : p.getDeminingStatus() == DeminingStatus.SURVEYED ? 0.8 : 0.2);
            infra += ((p.isElectricity() ? 1 : 0) + (p.isIrrigation() ? 1 : 0)
                    + (p.getRoadDistanceKm() == null || p.getRoadDistanceKm().compareTo(BigDecimal.valueOf(5)) <= 0 ? 1 : 0)) / 3.0;
            if (p.getStatus() == PlotStatus.AWARDED) awarded++; else available++;
            LandPotentialService.Option best = potential.options(p).stream().filter(o -> o.permitted() && o.suitability() >= 50).findFirst()
                    .orElseGet(() -> potential.options(p).stream().filter(LandPotentialService.Option::permitted).findFirst().orElse(null));
            if (best == null) continue;
            suit += best.suitability();
            SimulationSpec s = models.build(p, best.useCase(), ModelFactory.Overrides.none());
            capex += s.capex();
            net += Math.max(0, best.annualNet());
            im = im.plus(Impact.of(s, Impact.areaUsed(s, a)));
            long[] u = uses.computeIfAbsent(best.useCase(), k -> new long[2]);
            u[0]++;
            u[1] += Math.round(s.capex());
        }
        int n = Math.max(1, ps.size());
        double clearedPct = area <= 0 ? 0 : cleared / area;
        double infraPct = infra / n;
        Readiness base = new Readiness(0, (int) Math.round(30 * clearedPct), (int) Math.round(25 * infraPct), (int) Math.round(25 * suit / n / 100.0), 0);
        List<UseShare> us = uses.entrySet().stream()
                .sorted((x, y) -> Long.compare(y.getValue()[1], x.getValue()[1]))
                .map(x -> new UseShare(x.getKey().name(), x.getKey().label(), (int) x.getValue()[0], x.getValue()[1])).toList();
        long ask = prj.stream().mapToLong(p -> p.getAskAmount() == null ? 0 : p.getAskAmount().longValue()).sum();
        double tons = dem.stream().mapToDouble(x -> x.getVolumeTons() == null ? 0 : x.getVolumeTons().doubleValue()).sum();
        return new DistrictRow(d.name(), d.label(), d.region(), d.lat(), d.lng(), ps.size(), round1(area), Math.round(clearedPct * 1000) / 10.0,
                Math.round(infraPct * 1000) / 10.0, available, awarded, Math.round(capex), Math.round(net), im, us,
                app == null ? 0 : app[0], app == null ? 0 : app[1], prj.size(), ask, dem.size(), round1(tons), base, null);
    }

    private static double pull(DistrictRow r) {
        return r.applications() * 1.0 + r.projects() * 2.0 + r.demands() * 2.0;
    }

    private static String headline(DistrictRow r, Readiness rd) {
        String top = r.uses().isEmpty() ? "kənd təsərrüfatı" : r.uses().get(0).label().toLowerCase(Locale.forLanguageTag("az"));
        String level = rd.total() >= 75 ? "İnvestisiyaya hazırdır" : rd.total() >= 55 ? "Yaxın vaxtda hazır" : "İlkin mərhələ";
        return level + ": əsas imkan " + top + ", " + r.potential().directJobs() + " daimi iş yeri, " + r.potential().families() + " ailənin qayıdışı";
    }

    private static double round1(double d) {
        return Math.round(d * 10) / 10.0;
    }
}
