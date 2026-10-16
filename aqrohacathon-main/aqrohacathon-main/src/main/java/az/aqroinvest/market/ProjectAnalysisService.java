package az.aqroinvest.market;

import az.aqroinvest.economics.*;
import az.aqroinvest.economics.MonteCarloService.Path;
import az.aqroinvest.economics.MonteCarloService.PathYear;
import az.aqroinvest.market.MarketEnums.DealType;
import az.aqroinvest.market.MarketEnums.Stage;
import az.aqroinvest.plot.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Layihənin "reallıq yoxlaması" və "investor dili". Sahibkarın iddiaları (məhsuldarlıq, qiymət, gəlir, mənfəət)
 * regionun istinad göstəriciləri və 2000 ssenarilik Monte-Carlo simulyasiyası ilə müqayisə olunur.
 * Sonra investora təklif (pay, borc, gəlirdən pay) pis, orta və yaxşı ssenaridə pul ifadəsində göstərilir.
 */
@Service
@RequiredArgsConstructor
public class ProjectAnalysisService {
    static final int RUNS = 1500;

    private final ModelFactory models;
    private final MonteCarloService mc;
    private final PlotRepository plots;

    /** Təhlil üçün giriş (həm saxlanmış layihə, həm də formada yazılan qaralama üçün). */
    public record Input(ProjectSector sector, Long plotId, District district, Stage stage, Double scale, double capex,
                        double ownFunds, double askAmount, DealType dealType, Double equityPercent, Double interestPercent,
                        Double revenueSharePercent, Double claimedRevenue, Double claimedProfit, Double claimedYield,
                        Double claimedPrice) {}

    public record Check(String severity, String title, String detail) {}

    public record Scenario(String label, long investorReceived, double multiple, Integer paybackYear, Double irr) {}

    public record InvestorTerms(DealType dealType, String dealLabel, long ask, String offer, String headline,
                                List<Scenario> scenarios, Double dscr, Double impliedValuation) {}

    public record Analysis(String unit, double scale, double maxScale, long capex, long benchmarkCapex,
                           double benchmarkYield, String yieldUnit, double benchmarkPrice, String priceUnit,
                           long benchmarkRevenue, long benchmarkNet, long revenueP90, long netP90,
                           Double claimRevenueProbability, Double claimProfitProbability, int credibility, String credibilityLabel,
                           List<Check> checks, List<String> investorLanguage, InvestorTerms terms,
                           MonteCarloService.Summary summary, int horizon, Double scaleForClaim, boolean virtualPlot,
                           Impact impact, double jobsPerMillion) {}

    public Analysis analyze(Input in) {
        Plot plot = in.plotId() != null ? plots.findById(in.plotId()).orElse(null) : null;
        UseCase u = in.sector().useCase();
        boolean virtual = plot == null;
        SimulationSpec spec;
        SimulationSpec bench;
        if (u == null) {
            spec = startupSpec(in);
            bench = spec;
        } else {
            if (plot == null) plot = virtualPlot(in.district(), u, in.scale());
            bench = models.build(plot, u, new ModelFactory.Overrides(in.scale(), null, null, null, null, null, null));
            spec = models.build(plot, u, new ModelFactory.Overrides(in.scale(), in.capex() > 0 ? in.capex() : null, null, null, null, null, null));
        }
        long seed = Objects.hash(in.sector(), in.plotId(), in.scale()) & 0xffffL;
        MonteCarloService.Result r = mc.simulate(spec, RUNS, seed);
        MonteCarloService.Distribution dist = mc.distribution(spec, 800, seed);
        MonteCarloService.Summary s = r.summary();

        List<Check> checks = new ArrayList<>();
        Double pRev = null, pProfit = null;
        double revP90 = q(dist.matureRevenue(), .9), netP90 = q(dist.matureNet(), .9);
        String yieldUnit = spec.yieldUnit(), priceUnit = spec.priceUnit();

        if (u != null) {
            if (in.claimedYield() != null && in.claimedYield() > 0) deviation(checks, "Məhsuldarlıq", in.claimedYield(), bench.yieldPerUnit(), yieldUnit, 0.15, 0.35);
            if (in.claimedPrice() != null && in.claimedPrice() > 0) deviation(checks, "Satış qiyməti", in.claimedPrice(), bench.price(), priceUnit, 0.15, 0.30);
            if (in.capex() > 0) {
                double k = in.capex() / bench.capex();
                if (k < 0.7) checks.add(new Check("WARN", "İnvestisiya az göstərilib", "Göstərilən " + money(in.capex()) + ", oxşar layihələr üçün ~" + money(bench.capex()) + ". Xərclərin bir hissəsi unudula bilər."));
                else if (k > 1.6) checks.add(new Check("WARN", "İnvestisiya yüksəkdir", "Göstərilən " + money(in.capex()) + ", oxşar layihələr üçün ~" + money(bench.capex()) + ". İnvestor xərclərin tərkibini soruşacaq."));
                else checks.add(new Check("OK", "İnvestisiya məbləği realdır", "Oxşar layihələr üçün ~" + money(bench.capex()) + "."));
            }
        }
        if (in.claimedRevenue() != null && in.claimedRevenue() > 0) {
            pRev = dist.probAtLeast(dist.matureRevenue(), in.claimedRevenue());
            claim(checks, "İllik gəlir iddiası", in.claimedRevenue(), s.annualRevenueMature(), revP90, pRev);
        }
        if (in.claimedProfit() != null && in.claimedProfit() > 0) {
            pProfit = dist.probAtLeast(dist.matureNet(), in.claimedProfit());
            claim(checks, "İllik mənfəət iddiası", in.claimedProfit(), s.annualNetMature(), netP90, pProfit);
        }
        double funded = in.ownFunds() + in.askAmount();
        if (funded + 1 < in.capex()) checks.add(new Check("CRIT", "Maliyyə boşluğu", "Öz vəsait və investordan istənilən məbləğ cəmi " + money(funded) + ", layihəyə isə " + money(in.capex()) + " lazımdır. Boşluq: " + money(in.capex() - funded) + "."));
        double ownShare = in.capex() > 0 ? in.ownFunds() / in.capex() : 0;
        if (ownShare < 0.1) checks.add(new Check("WARN", "Sahibkarın öz payı azdır", "Öz vəsait investisiyanın " + Math.round(ownShare * 100) + "%-idir. İnvestorlar adətən sahibkarın ən azı 15-20% riskə girməsini gözləyir."));
        else checks.add(new Check("OK", "Sahibkar öz pulu ilə riskə girir", "Öz vəsait investisiyanın " + Math.round(ownShare * 100) + "%-idir."));
        if (plot != null && !virtual && u != null) {
            double max = models.maxScale(plot, u);
            if (in.scale() != null && in.scale() > max * 1.001) checks.add(new Check("CRIT", "Miqyas sahəyə sığmır", "Bu sahədə maksimum " + num(max) + " " + spec.unit() + " mümkündür."));
            if (!LandPotentialService.permitted(plot.getDesignation(), u)) checks.add(new Check("WARN", "Torpağın təyinatı uyğun deyil", "Sahənin təyinatı \"" + plot.getDesignation().label() + "\": bu fəaliyyət üçün təyinat dəyişikliyi lazımdır."));
        }
        if (in.stage() == Stage.IDEA) checks.add(new Check("WARN", "Layihə ideya mərhələsindədir", "Biznes plan və torpaq hələ hazır deyil: risk yüksəkdir, investor daha böyük pay istəyəcək."));
        if (u == null) checks.add(new Check("WARN", "Gəlir iddiası müstəqil yoxlanılmayıb", "Aqrotexnologiya layihələri üçün regional istinad göstəricisi yoxdur: simulyasiya sahibkarın öz rəqəmlərinə və startap risklərinə əsaslanır."));

        InvestorTerms terms = terms(in, r, s);
        if (terms.dscr() != null && in.dealType() == DealType.LOAN) {
            if (terms.dscr() < 1.2) checks.add(new Check("WARN", "Borc yükü yüksəkdir", "Orta ssenaridə illik gəlir borc ödənişini cəmi " + String.format(Locale.ROOT, "%.1f", terms.dscr()) + " dəfə ödəyir (bank normativi 1.3+)."));
        }
        if (terms.impliedValuation() != null && in.capex() > 0 && terms.impliedValuation() > in.capex() * 2.5) {
            checks.add(new Check("WARN", "Layihənin qiyməti yüksək qoyulub", "Təklif olunan paya görə layihə " + money(terms.impliedValuation()) + " qiymətləndirilir: bu, investisiyanın " + String.format(Locale.ROOT, "%.1f", terms.impliedValuation() / in.capex()) + " misli deməkdir."));
        }

        int cred = 100;
        for (Check c : checks) cred -= c.severity().equals("CRIT") ? 25 : c.severity().equals("WARN") ? 9 : 0;
        cred = Math.max(5, Math.min(100, cred));
        // Rəng şkalası ilə eyni hədlər: 76-100 yaşıl, 41-75 sarı, 0-40 qırmızı
        String credLabel = cred >= 76 ? "Rəqəmlər inandırıcıdır" : cred >= 58 ? "Bəzi iddialar optimistdir" : cred >= 41 ? "Ciddi suallar var" : "İddialar reallıqdan uzaqdır";

        Double scaleForClaim = null;
        if (u != null && in.claimedProfit() != null && in.claimedProfit() > s.annualNetMature() * 1.15 && s.annualNetMature() > 0 && spec.scale() > 0) {
            scaleForClaim = Math.round(spec.scale() * in.claimedProfit() / s.annualNetMature() * 10) / 10.0;
            checks.add(new Check("WARN", "İddia olunan mənfəət üçün lazım olan miqyas", "Bu mənfəət üçün təxminən " + num(scaleForClaim) + " " + spec.unit() + " lazımdır, layihədə isə " + num(spec.scale()) + " " + spec.unit() + " göstərilib."));
        }
        checks.sort(Comparator.comparingInt(c -> c.severity().equals("CRIT") ? 0 : c.severity().equals("WARN") ? 1 : 2));
        // Böyük Qayıdış təsiri: aqrotexnologiya startapı üçün yalnız iş yerləri hesablanır
        Impact impact = u == null
                ? new Impact(spec.jobs(), 0, (int) Math.round(spec.jobs() * Impact.FAMILY_PER_JOB), (int) Math.round(spec.jobs() * Impact.FAMILY_PER_JOB * Impact.PEOPLE_PER_FAMILY), Math.round(spec.jobs() * 900 * 12L), 0, 0, 0, 0)
                : Impact.of(spec, Impact.areaUsed(spec, plot.getAreaHa().doubleValue()));

        return new Analysis(spec.unit(), spec.scale(), plot != null && u != null ? models.maxScale(plot, u) : spec.scale(),
                Math.round(spec.capex()), Math.round(bench.capex()), round1(bench.yieldPerUnit()), yieldUnit, bench.price(), priceUnit,
                s.annualRevenueMature(), s.annualNetMature(), Math.round(revP90), Math.round(netP90), pRev, pProfit, cred, credLabel,
                checks, language(in, plot, virtual, spec, s, terms), terms, s, spec.horizon(), scaleForClaim, virtual,
                impact, Impact.jobsPerMillion(impact.directJobs(), spec.capex()));
    }

    // ------------------------------------------------------------------ investor terms

    private InvestorTerms terms(Input in, MonteCarloService.Result r, MonteCarloService.Summary s) {
        double ask = Math.max(1, in.askAmount());
        List<Scenario> sc = new ArrayList<>();
        String offer, headline;
        Double dscr = null, valuation = null;
        int h = r.horizon();
        switch (in.dealType()) {
            case EQUITY -> {
                double e = (in.equityPercent() == null ? 25 : in.equityPercent()) / 100.0;
                valuation = ask / Math.max(0.01, e);
                for (Path p : List.of(r.bad(), r.typical(), r.good())) sc.add(equity(p, e, ask, h));
                sc = ordered(sc);
                offer = String.format(Locale.ROOT, "%s qarşılığında layihənin %.0f%% payı", money(ask), e * 100);
                Scenario mid = sc.get(1);
                headline = "Siz " + money(ask) + " qoyursunuz, layihənin " + Math.round(e * 100) + "%-i sizindir. Orta ssenaridə " + h + " ildə "
                        + money(mid.investorReceived()) + " alırsınız (" + String.format(Locale.ROOT, "%.1f", mid.multiple()) + "×)"
                        + (mid.paybackYear() != null ? ", pulunuz " + mid.paybackYear() + "-ci ildə qayıdır." : ", amma pul " + h + " ildə tam qayıtmır.");
            }
            case LOAN -> {
                double i = (in.interestPercent() == null ? 12 : in.interestPercent()) / 100.0;
                int n = Math.min(7, h);
                double pay = ask * i / (1 - Math.pow(1 + i, -n));
                dscr = s.annualNetMature() / pay;
                for (Path p : List.of(r.bad(), r.typical(), r.good())) sc.add(loan(p, ask, pay, n));
                sc = ordered(sc);
                offer = String.format(Locale.ROOT, "%s borc, illik %.1f%%, %d il", money(ask), i * 100, n);
                Scenario bad = sc.get(0);
                headline = "Siz " + money(ask) + " borc verirsiniz, " + n + " il ərzində ildə " + money(pay) + " geri alırsınız (cəmi " + money(pay * n) + "). "
                        + (bad.multiple() >= pay * n / ask * 0.99 ? "Hətta pis ssenaridə ödənişlər tam gedir." : "Pis ssenaridə ödənişlərin " + Math.round(bad.multiple() / (pay * n / ask) * 100) + "%-i vaxtında gəlir.");
            }
            default -> {
                double share = (in.revenueSharePercent() == null ? 10 : in.revenueSharePercent()) / 100.0;
                double cap = ask * 1.8;
                for (Path p : List.of(r.bad(), r.typical(), r.good())) sc.add(revenueShare(p, share, ask, cap));
                sc = ordered(sc);
                offer = String.format(Locale.ROOT, "%s qarşılığında gəlirin %.0f%%-i, cəmi %s olana qədər (1.8×)", money(ask), share * 100, money(cap));
                Scenario mid = sc.get(1);
                headline = "Siz " + money(ask) + " qoyursunuz və satışdan " + Math.round(share * 100) + "% alırsınız, cəmi " + money(cap) + " olana qədər. "
                        + (mid.paybackYear() != null ? "Orta ssenaridə pulunuz " + mid.paybackYear() + "-ci ildə qayıdır." : "Orta ssenaridə " + h + " ildə " + money(mid.investorReceived()) + " alırsınız.");
            }
        }
        return new InvestorTerms(in.dealType(), in.dealType().label(), Math.round(ask), offer, headline, ordered(sc),
                dscr == null ? null : Math.round(dscr * 100) / 100.0, valuation == null ? null : (double) Math.round(valuation));
    }

    /** Ssenariləri investorun nəticəsinə görə sıralayır (layihənin NPV sırası tikinti xərcinin artmasına görə fərqlənə bilər). */
    private static List<Scenario> ordered(List<Scenario> sc) {
        List<Scenario> s = new ArrayList<>(sc);
        s.sort(Comparator.comparingLong(Scenario::investorReceived));
        String[] labels = {"Pis ssenari", "Orta ssenari", "Yaxşı ssenari"};
        List<Scenario> out = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) out.add(new Scenario(labels[i], s.get(i).investorReceived(), s.get(i).multiple(), s.get(i).paybackYear(), s.get(i).irr()));
        return out;
    }

    private static Scenario equity(Path p, double e, double ask, int h) {
        double got = 0, cum = -ask;
        Integer pb = null;
        double[] cf = new double[h + 1];
        cf[0] = -ask;
        for (PathYear y : p.years()) {
            double v = e * y.net();
            got += v;
            cum += v;
            cf[y.year()] = v;
            if (pb == null && cum >= 0) pb = y.year();
        }
        return new Scenario(label(p), Math.round(got), round2(got / ask), pb, irr(cf));
    }

    private static Scenario loan(Path p, double ask, double pay, int n) {
        double got = 0, arrears = 0, cum = -ask;
        Integer pb = null;
        double[] cf = new double[p.years().size() + 1];
        cf[0] = -ask;
        for (PathYear y : p.years()) {
            double due = (y.year() <= n ? pay : 0) + arrears;
            double v = Math.max(0, Math.min(due, y.net()));
            arrears = due - v;
            got += v;
            cum += v;
            cf[y.year()] = v;
            if (pb == null && cum >= 0) pb = y.year();
        }
        return new Scenario(label(p), Math.round(got), round2(got / ask), pb, irr(cf));
    }

    private static Scenario revenueShare(Path p, double share, double ask, double cap) {
        double got = 0, cum = -ask;
        Integer pb = null;
        double[] cf = new double[p.years().size() + 1];
        cf[0] = -ask;
        for (PathYear y : p.years()) {
            double v = Math.min(cap - got, share * y.revenue());
            if (v < 0) v = 0;
            got += v;
            cum += v;
            cf[y.year()] = v;
            if (pb == null && cum >= 0) pb = y.year();
        }
        return new Scenario(label(p), Math.round(got), round2(got / ask), pb, irr(cf));
    }

    private static String label(Path p) {
        return p.label().startsWith("Pis") ? "Pis ssenari" : p.label().startsWith("Yaxşı") ? "Yaxşı ssenari" : "Orta ssenari";
    }

    // ------------------------------------------------------------------ "investor dili"

    private List<String> language(Input in, Plot plot, boolean virtual, SimulationSpec spec, MonteCarloService.Summary s, InvestorTerms t) {
        List<String> l = new ArrayList<>();
        l.add(t.headline());
        if (s.paybackP50() != null) l.add("Layihənin özü (bütün investisiya) orta ssenaridə " + s.paybackP50() + "-ci ildə qayıdır, ehtimal " + Math.round(s.probPayback() * 100) + "%.");
        else l.add("Layihənin özü " + spec.horizon() + " ildə orta ssenaridə tam qayıtmır: bu, uzunmüddətli investisiyadır.");
        UseCase u = in.sector().useCase();
        if (u != null && u.category() != null) {
            PlotCategory c = u.category();
            if (c.yearsToFirstHarvest() > 0) l.add("\"Bar verməyə " + c.yearsToFirstHarvest() + " il\" o deməkdir ki, ilk " + c.yearsToFirstHarvest() + " il satış olmayacaq: pul yalnız xərclənir.");
            if (plot != null && !virtual && plot.getSoilQuality() != null) {
                int q = plot.getSoilQuality();
                l.add("\"Bonitet " + q + "/100\" torpağın keyfiyyətidir: bu sahədə məhsul orta göstəricidən təxminən " + Math.abs(Math.round((0.7 + 0.3 * q / 100.0 - 0.88) / 0.88 * 100)) + "% " + (q >= 60 ? "çox" : "az") + " olacaq.");
            }
            if (plot != null && !virtual) l.add(plot.isIrrigation() ? "Suvarma var: quraqlıq ilində məhsul itkisi ehtimalı ~3 dəfə azdır." : "Suvarma yoxdur: quraqlıq ilində məhsulun 40%-ə qədəri itə bilər.");
        }
        if (u == UseCase.GUNES) l.add("Elektrik dövlət tarifi ilə (8.5 qəpik/kVts) satılır: gəlir hava və bazardan az asılıdır, risk aşağıdır, amma geri qayıtma uzundur.");
        if (in.stage() == Stage.LAND) l.add("Torpaq artıq təmin olunub: layihə icazə mərhələsini keçib.");
        return l;
    }

    // ------------------------------------------------------------------ helpers

    private void deviation(List<Check> checks, String what, double claimed, double bench, String unit, double warn, double crit) {
        double d = claimed / bench - 1;
        String txt = what + " " + num(claimed) + " " + unit + " göstərilib, region üzrə gözlənilən " + num(bench) + " " + unit + " (" + (d >= 0 ? "+" : "") + Math.round(d * 100) + "%).";
        if (d > crit) checks.add(new Check("CRIT", what + " çox optimistdir", txt));
        else if (d > warn) checks.add(new Check("WARN", what + " optimistdir", txt));
        else checks.add(new Check("OK", what + " realdır", txt));
    }

    private static void claim(List<Check> checks, String what, double claimed, double p50, double p90, double prob) {
        String txt = money(claimed) + " göstərilib. Simulyasiyada orta ssenari " + money(p50) + ", yaxşı ssenari (P90) " + money(p90) + ". Bu iddia ssenarilərin " + Math.round(prob * 100) + "%-ində mümkündür.";
        if (claimed <= p50 * 1.08) checks.add(new Check("OK", what + " realdır", txt));
        else if (prob < 0.1) checks.add(new Check("CRIT", what + " reallıqdan uzaqdır", txt));
        else if (prob < 0.35) checks.add(new Check("WARN", what + " optimistdir", txt));
        else checks.add(new Check("OK", what + " realdır", txt));
    }

    /** Sahə seçilməyibsə, rayonun orta şərtləri ilə virtual sahə. */
    private Plot virtualPlot(District d, UseCase u, Double scale) {
        Plot p = new Plot();
        District dd = d != null ? d : District.AGDAM;
        p.setId(-1L);
        p.setCode("VIRTUAL");
        p.setName("Orta şərtli sahə");
        p.setDistrict(dd);
        p.setCategory(u.category() != null ? u.category() : PlotCategory.EKINCILIK);
        p.setDesignation(u == UseCase.GUNES ? LandDesignation.YARARSIZ : u == UseCase.SOYUDUCU ? LandDesignation.SENAYE : LandDesignation.defaultFor(p.getCategory()));
        double area = scale == null ? 20 : u == UseCase.GUNES ? scale * ModelFactory.SOLAR_HA_PER_MWP : u == UseCase.AQROTURIZM ? Math.max(2, scale * 0.5) : u == UseCase.SOYUDUCU ? Math.max(1, scale / 600) : u == UseCase.ISTIXANA ? scale / 0.6 : scale;
        p.setAreaHa(BigDecimal.valueOf(Math.max(1, Math.round(area * 10) / 10.0)));
        p.setLatitude(dd.lat());
        p.setLongitude(dd.lng());
        p.setSoilQuality(68);
        p.setIrrigation(true);
        p.setElectricity(true);
        p.setRoadDistanceKm(BigDecimal.valueOf(2));
        p.setSlopePercent(BigDecimal.valueOf(3));
        p.setAltitudeM(500);
        p.setAnnualRentPerHa(BigDecimal.valueOf(30));
        p.setMinInvestment(BigDecimal.ZERO);
        return p;
    }

    private SimulationSpec startupSpec(Input in) {
        double rev = in.claimedRevenue() != null && in.claimedRevenue() > 0 ? in.claimedRevenue() : Math.max(50_000, in.capex() * 0.6);
        double margin = in.claimedProfit() != null && in.claimedRevenue() != null && in.claimedRevenue() > 0 ? Math.max(0.02, Math.min(0.9, in.claimedProfit() / in.claimedRevenue())) : 0.25;
        List<SimulationSpec.Shock> shocks = List.of(new SimulationSpec.Shock("Müştəri cəlbi gecikir", 0.2, 0.4), new SimulationSpec.Shock("Rəqib məhsul bazara çıxır", 0.1, 0.3));
        List<SimulationSpec.Assumption> a = List.of(
                new SimulationSpec.Assumption("Gəlir", money(rev) + "/il (sahibkarın iddiası, 4-cü ildən tam həcm)", "Sahibkarın biznes planı"),
                new SimulationSpec.Assumption("Mənfəətlilik", Math.round(margin * 100) + "%", "Sahibkarın biznes planı"),
                new SimulationSpec.Assumption("Risklər", "gəlir dəyişkənliyi ±35%, müştəri cəlbinin gecikməsi 20% ehtimal", "Startap riskləri üzrə ekspert qiymətləndirməsi"));
        return new SimulationSpec(null, "layihə", 1, Math.max(1, in.capex()), 1, "", rev, "₼", 0, 0, 1 - margin,
                new double[]{0.25, 0.55, 0.8, 1}, 0.35, 0.1, 0.03, shocks, 0, 10, 0.15, 0, 1, 0.05, 0.1, 0, a);
    }

    private static Double irr(double[] cf) {
        double lo = -0.99, hi = 2;
        if (npv(cf, lo) * npv(cf, hi) > 0) return null;
        for (int i = 0; i < 80; i++) { double m = (lo + hi) / 2; if (npv(cf, lo) * npv(cf, m) <= 0) hi = m; else lo = m; }
        return Math.round((lo + hi) / 2 * 1000) / 10.0;
    }

    private static double npv(double[] cf, double r) {
        double v = 0;
        for (int t = 0; t < cf.length; t++) v += cf[t] / Math.pow(1 + r, t);
        return v;
    }

    private static double q(double[] sorted, double p) {
        return sorted[(int) Math.round(p * (sorted.length - 1))];
    }

    static String money(double v) {
        double a = Math.abs(v);
        String s = a >= 1e6 ? String.format(Locale.ROOT, "%.1f mln ₼", a / 1e6) : a >= 1e4 ? String.format(Locale.ROOT, "%.0f min ₼", a / 1e3) : String.format(Locale.ROOT, "%.0f ₼", a);
        return (v < 0 ? "−" : "") + s;
    }

    static String num(double d) {
        return d >= 100 ? String.valueOf(Math.round(d)) : String.format(Locale.ROOT, "%.1f", d);
    }

    private static double round1(double d) { return Math.round(d * 10) / 10.0; }
    private static double round2(double d) { return Math.round(d * 100) / 100.0; }
}
