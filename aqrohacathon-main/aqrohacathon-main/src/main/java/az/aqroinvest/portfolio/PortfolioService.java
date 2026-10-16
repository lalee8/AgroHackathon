package az.aqroinvest.portfolio;

import az.aqroinvest.advisor.Money;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.economics.*;
import az.aqroinvest.economics.MonteCarloService.Exposure;
import az.aqroinvest.economics.MonteCarloService.Joint;
import az.aqroinvest.economics.MonteCarloService.World;
import az.aqroinvest.plot.District;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import az.aqroinvest.plot.PlotStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Portfel qurucusu: büdcəni Qarabağdakı torpaq-istiqamət variantları arasında elə bölür ki, gözlənilən xalis dəyər yüksək,
 * pis ssenaridəki itki isə aşağı olsun. Bütün variantlar eyni "dünya" ssenarilərində birlikdə simulyasiya olunur: quraqlıq ili
 * bütün aran bağlarını eyni vaxtda vurur, günəş stansiyası isə həmin il daha çox istehsal edir. Diversifikasiyanın faydası
 * buradan gəlir və rəqəmlə göstərilir.
 *
 * Optimallaşdırma: məqsəd funksiyası E[NPV] - λ·(E[NPV] - CVaR10), büdcə 20 hissəyə bölünür və hər addımda hər manata ən çox
 * fayda verən variant seçilir (məqsəd funksiyası çökükdür, ona görə acgöz addımlar yaxşı nəticə verir). λ-nın müxtəlif
 * qiymətləri səmərəli sərhədi (efficient frontier) çəkir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {
    static final int RUNS = 400, FINAL_RUNS = 1000;
    /** Hər variant üçün yoxlanılan miqyaslar (standart miqyasın payı); kiçik obyektin vahid xərci daha yüksəkdir. */
    static final double[] LEVELS = {0.1, 0.25, 0.5, 0.75, 1};
    static final long SEED = 2026;
    static final int HIGHLAND_M = 900;
    static final double[] LAMBDAS = {0, 0.1, 0.25, 0.45, 0.7, 1, 1.4, 2};
    static final double TAIL = 0.10, STRESS_Z = -1.2816;

    private final PlotRepository plots;
    private final LandPotentialService potential;
    private final ModelFactory models;
    private final MonteCarloService mc;

    private final Map<String, Pre> cache = new ConcurrentHashMap<>();

    @Value("${app.portfolio.warmup:true}")
    private boolean warmup;

    /** Standart üfüq üçün namizədlərin simulyasiyası fonda əvvəlcədən hesablanır: ilk istifadəçi gözləmir. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!warmup) return;
        Thread t = new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                prepare(15);
                log.info("Portfel qurucusu hazırdır ({} ms)", System.currentTimeMillis() - t0);
            } catch (RuntimeException e) {
                log.warn("Portfel qurucusunun ilkin hesablanması alınmadı: {}", e.getMessage());
            }
        }, "portfolio-warmup");
        t.setDaemon(true);
        t.start();
    }

    /** Torpaq + istiqamət variantı; levels: artan miqyaslarda modellər, idx: onların birgə simulyasiyadakı nömrələri. */
    record Cand(Plot plot, int plotIdx, UseCase use, List<SimulationSpec> levels, int[] idx, Exposure exp, int suitability) {
        SimulationSpec full() {
            return levels.get(levels.size() - 1);
        }
    }

    /** area: hər modelin istifadə etdiyi torpaq (ha), plotArea: sahələrin ölçüsü (eyni sahədə iki istiqamət sığmalıdır). */
    record Pre(List<Cand> cands, double[][] npv, double[] capex, double[] area, double[] plotArea) {}

    public record Request(Long budget, Integer risk, Integer horizon, List<District> districts, List<UseCase> uses, Integer maxSharePct,
                          Integer maxSectorPct) {}

    public record Position(long plotId, String code, String plotName, String district, String zone, String use, String useLabel,
                           String icon, double scale, String unit, long amount, double sharePct, long annualNetP50, long npvP50,
                           double probLoss, int suitability, int jobs, String url) {}

    public record FrontierPoint(double lambda, double riskPct, double retPct, int positions, long invested, boolean recommended, boolean efficient) {}

    public record SinglePoint(String label, String code, String use, double riskPct, double retPct) {}

    public record Totals(long budget, long invested, long cash, long npvMean, long npvP10, long npvP50, long npvP90, double probLoss,
                         long annualNetP10, long annualNetP50, long annualNetP90, long monthlyNetP50, Integer paybackP50,
                         Double irrP50, double riskPct, double retPct) {}

    public record Diversification(double benefitPct, double effectiveN, long shortfallPortfolio, long shortfallStandalone,
                                  String naiveLabel, double naiveRiskPct, double naiveRetPct, long naiveNpvP10, long naiveInvested,
                                  String hedgeLabel, String hedgeWith, double hedgeCorrelation) {}

    public record Stress(String key, String title, String description, double portfolioChangePct, String worstLabel,
                         double worstChangePct, String bestLabel, double bestChangePct) {}

    public record YearBand(int year, long p10, long p25, long p50, long p75, long p90) {}

    public record PositionFlow(String code, String label, List<Long> netP50) {}

    public record Result(long budget, int risk, int horizon, int maxSharePct, int maxSectorPct, int candidates, int runs, Totals totals,
                         List<Position> positions, List<FrontierPoint> frontier, List<SinglePoint> singles, Diversification diversification,
                         List<Stress> stress, List<YearBand> fan, List<PositionFlow> flows, List<String> corrLabels, double[][] corr,
                         Impact impact, double jobsPerMillion, List<String> summary, List<String> assumptions, String notice) {}

    // ------------------------------------------------------------------ giriş nöqtəsi

    public Result build(Request req) {
        long budget = req.budget() == null ? 1_000_000 : req.budget();
        if (budget < 50_000 || budget > 500_000_000) throw ApiException.badRequest("BUDGET_RANGE", "Büdcə 50 000 ₼ ilə 500 mln ₼ arasında olmalıdır");
        int risk = req.risk() == null ? 50 : Math.max(0, Math.min(100, req.risk()));
        int horizon = req.horizon() == null ? 15 : Math.max(10, Math.min(25, req.horizon()));
        int maxShare = req.maxSharePct() == null ? 35 : Math.max(10, Math.min(100, req.maxSharePct()));
        int maxSector = req.maxSectorPct() == null ? 40 : Math.max(20, Math.min(100, req.maxSectorPct()));
        double lambdaRec = lambda(risk);

        Pre pre = prepare(horizon);
        List<Cand> cands = pre.cands();
        boolean[] mask = new boolean[cands.size()];
        int allowed = 0;
        for (int i = 0; i < cands.size(); i++) {
            Cand c = cands.get(i);
            mask[i] = (req.districts() == null || req.districts().isEmpty() || req.districts().contains(c.plot().getDistrict()))
                    && (req.uses() == null || req.uses().isEmpty() || req.uses().contains(c.use()));
            if (mask[i]) allowed++;
        }
        if (allowed == 0) throw ApiException.badRequest("NO_CANDIDATES", "Seçilmiş rayon və istiqamətlər üzrə uyğun torpaq yoxdur");

        double[][] npv = pre.npv();
        double[] capex = pre.capex();

        // Kiçik büdcədə faiz limitləri iqtisadi minimum ölçülü obyekti (məs. 10 ha bağ) bağlamasın
        double cap = Math.max(maxShare / 100.0 * budget, Math.min(budget, 250_000));
        double sectorCap = Math.max(maxSector / 100.0 * budget, Math.min(budget, 300_000));

        // səmərəli sərhəd: gəlir və risk bütün büdcəyə nisbətdə (yerləşdirilməyən pul 0 gəlir, 0 risk)
        List<FrontierPoint> frontier = new ArrayList<>();
        Map<Double, int[]> byLambda = new TreeMap<>();
        Set<String> seen = new HashSet<>();
        double[] lambdas = java.util.stream.DoubleStream.concat(Arrays.stream(LAMBDAS), java.util.stream.DoubleStream.of(lambdaRec)).distinct().sorted().toArray();
        for (double l : lambdas) {
            boolean rec = l == lambdaRec;
            int[] lv = optimize(pre, mask, budget, l, cap, sectorCap, rec);
            double inv = invested(cands, lv, capex);
            if (inv <= 0) continue;
            byLambda.put(l, lv);
            double[] port = portfolio(cands, lv, npv);
            double mean = mean(port), es = mean - cvar(port);
            if (!seen.add(Arrays.toString(lv)) && !rec) continue;
            frontier.add(new FrontierPoint(round2(l), pct(es, budget), pct(mean, budget), (int) Arrays.stream(lv).filter(x -> x >= 0).count(), Math.round(inv), rec, false));
        }
        String notice = null;
        int[] best = byLambda.get(lambdaRec);
        if (best == null) {
            // Seçilmiş risk səviyyəsində heç nə alınmadı: ən ehtiyatlı mümkün portfel təklif olunur
            Map.Entry<Double, int[]> safest = ((TreeMap<Double, int[]>) byLambda).lastEntry();
            if (safest == null)
                throw ApiException.badRequest("NO_PORTFOLIO", "Bu şərtlərlə müsbət gəlirli portfel tapılmadı: pulu nağd saxlamaq daha az risklidir. Üfüqü uzadın və ya filtrləri genişləndirin.");
            best = safest.getValue();
            double l = safest.getKey();
            notice = "Seçdiyiniz ehtiyat səviyyəsində heç bir variant pis ssenaridə də müsbət qalmır. Aşağıda platformanın tapdığı ən ehtiyatlı portfel göstərilir.";
            List<FrontierPoint> f2 = new ArrayList<>();
            for (FrontierPoint f : frontier) f2.add(new FrontierPoint(f.lambda(), f.riskPct(), f.retPct(), f.positions(), f.invested(), f.lambda() == round2(l), false));
            frontier = f2;
        }
        frontier = markEfficient(frontier);

        // tək variantlar: bütün büdcə bir varianta (büdcəyə sığan ən böyük miqyasda), qalan pul nağd
        List<SinglePoint> singles = new ArrayList<>();
        int naive = -1;
        double naiveMean = -1e18;
        for (int i = 0; i < cands.size(); i++) {
            if (!mask[i]) continue;
            Cand c = cands.get(i);
            int fit = -1;
            for (int x : c.idx()) if (capex[x] <= budget) fit = x;
            if (fit < 0) continue;
            double m = mean(npv[fit]), es = m - cvar(npv[fit]);
            singles.add(new SinglePoint(c.plot().getCode() + " · " + c.use().label(), c.plot().getCode(), c.use().name(), pct(es, budget), pct(m, budget)));
            if (m > naiveMean) { naiveMean = m; naive = fit; }
        }

        return evaluate(cands, best, budget, risk, horizon, maxShare, maxSector, frontier, singles, naive, npv, capex, RUNS, allowed, notice);
    }

    /** Risk meyli (0 ehtiyatlı - 100 cəsarətli) → λ: pis ssenari itkisinin çəkisi. */
    static double lambda(int risk) {
        double r = (100 - risk) / 100.0;
        return round2(0.05 + 0.95 * r * r);
    }

    /** Pareto-səmərəli nöqtələr: daha az riskli heç bir nöqtə daha çox gəlir vermir. */
    static List<FrontierPoint> markEfficient(List<FrontierPoint> pts) {
        // eyni portfeli verən λ-lar birləşdirilir (tövsiyə işarəsi saxlanılır)
        Map<String, FrontierPoint> uniq = new LinkedHashMap<>();
        for (FrontierPoint f : pts) {
            String k = f.riskPct() + ":" + f.retPct() + ":" + f.invested();
            FrontierPoint o = uniq.get(k);
            uniq.put(k, o == null || f.recommended() ? f : o);
        }
        List<FrontierPoint> sorted = new ArrayList<>(uniq.values());
        sorted.sort(Comparator.comparingDouble(FrontierPoint::riskPct).thenComparing(Comparator.comparingDouble(FrontierPoint::retPct).reversed()));
        double bestRet = -1e18;
        List<FrontierPoint> out = new ArrayList<>();
        for (FrontierPoint f : sorted) {
            boolean eff = f.retPct() > bestRet;
            if (eff) bestRet = f.retPct();
            out.add(new FrontierPoint(f.lambda(), f.riskPct(), f.retPct(), f.positions(), f.invested(), f.recommended(), eff));
        }
        return out;
    }

    // ------------------------------------------------------------------ namizədlər və birgə simulyasiya

    Pre prepare(int horizon) {
        List<Plot> all = plots.findAll().stream().filter(p -> p.getStatus() != PlotStatus.AWARDED)
                .sorted(Comparator.comparing(Plot::getCode)).toList();
        long sig = 17;
        for (Plot p : all) sig = sig * 31 + p.getId() * 7 + p.getVersion();
        String key = horizon + ":" + sig;
        return cache.computeIfAbsent(key, k -> {
            if (cache.size() > 2) cache.clear();
            List<Cand> cands = new ArrayList<>();
            List<SimulationSpec> specs = new ArrayList<>();
            List<Exposure> exps = new ArrayList<>();
            List<Integer> streams = new ArrayList<>();
            List<Double> areas = new ArrayList<>();
            double[] plotArea = new double[all.size()];
            for (int pi = 0; pi < all.size(); pi++) {
                Plot p = all.get(pi);
                plotArea[pi] = p.getAreaHa().doubleValue();
                List<LandPotentialService.Option> opts = potential.options(p).stream()
                        .filter(o -> o.permitted() && o.suitability() >= 50).limit(2).toList();
                for (LandPotentialService.Option o : opts) {
                    UseCase u = o.useCase();
                    double full = models.defaultScale(p, u);
                    TreeSet<Double> scales = new TreeSet<>();
                    for (double f : LEVELS) {
                        double sc = roundScale(u, full * f);
                        if (sc >= minScale(u) * 0.999) scales.add(sc);
                    }
                    if (scales.isEmpty()) scales.add(roundScale(u, full));
                    Exposure e = Exposure.of(u, zone(p));
                    List<SimulationSpec> lv = new ArrayList<>();
                    int[] idx = new int[scales.size()];
                    int n = 0;
                    for (double sc : scales) {
                        SimulationSpec s = rebuild(p, u, sc, horizon);
                        lv.add(s);
                        idx[n++] = specs.size();
                        specs.add(s);
                        exps.add(e);
                        streams.add(cands.size());
                        areas.add(Impact.areaUsed(s, plotArea[pi]));
                    }
                    cands.add(new Cand(p, pi, u, lv, idx, e, o.suitability()));
                }
            }
            Joint j = mc.joint(specs, exps, RUNS, SEED, false, streams.stream().mapToInt(Integer::intValue).toArray());
            return new Pre(cands, j.npv(), specs.stream().mapToDouble(SimulationSpec::capex).toArray(),
                    areas.stream().mapToDouble(Double::doubleValue).toArray(), plotArea);
        });
    }

    static int zone(Plot p) {
        return p.getAltitudeM() != null && p.getAltitudeM() >= HIGHLAND_M ? 1 : 0;
    }

    /** Ən kiçik məntiqli miqyas: bundan az hissəyə investisiya iqtisadi baxımdan mənasızdır. */
    static double minScale(UseCase u) {
        return switch (u) {
            case GUNES -> 1;
            case AQROTURIZM -> 6;
            case SOYUDUCU -> 500;
            case ISTIXANA -> 0.5;
            case ARICILIQ -> 2;
            default -> 5;
        };
    }

    // ------------------------------------------------------------------ optimallaşdırma

    /** Optimallaşdırmanın vəziyyəti: hər variantın səviyyəsi, portfelin ssenari NPV-ləri, xərclənən məbləğ, sektor və torpaq istifadəsi. */
    static final class State {
        final int[] lv;
        final double[] port, sector, land;
        double spent;

        State(int n, int runs, int plots) {
            lv = new int[n];
            Arrays.fill(lv, -1);
            port = new double[runs];
            sector = new double[UseCase.values().length];
            land = new double[plots];
        }

        State(State o) {
            lv = o.lv.clone();
            port = o.port.clone();
            sector = o.sector.clone();
            land = o.land.clone();
            spent = o.spent;
        }
    }

    /**
     * Acgöz optimallaşdırma: hər addımda bir variantın miqyası artırılır (və ya ilk dəfə alınır), seçim meyarı
     * məqsəd funksiyasının hər əlavə manata artımıdır. İstənilən yüksək səviyyəyə birbaşa keçid mümkündür, ona görə
     * sabit xərci böyük olan obyektlər (anbar, stansiya) kiçik miqyasda zərərli olsa da düzgün qiymətləndirilir.
     * Məhdudiyyətlər: büdcə, bir mövqenin və bir istiqamətin payı, sahənin torpaq ölçüsü.
     * improve=true: sonra hər mövqeni çıxarıb boşalan pulu yenidən bölməyə çalışır (yerli axtarış).
     * @return hər variant üçün seçilmiş səviyyə (-1: alınmayıb)
     */
    static int[] optimize(Pre pre, boolean[] mask, long budget, double lambda, double cap, double sectorCap, boolean improve) {
        List<Cand> cands = pre.cands();
        State st = new State(cands.size(), pre.npv()[0].length, pre.plotArea().length);
        fill(st, pre, mask, budget, lambda, cap, sectorCap);
        if (!improve) return st.lv;
        double obj = objective(st.port, lambda);
        for (int pass = 0; pass < 3; pass++) {
            boolean better = false;
            for (int i = 0; i < st.lv.length; i++) {
                if (st.lv[i] < 0) continue;
                State s2 = new State(st);
                Cand c = cands.get(i);
                int x = c.idx()[s2.lv[i]];
                for (int k = 0; k < s2.port.length; k++) s2.port[k] -= pre.npv()[x][k];
                s2.spent -= pre.capex()[x];
                s2.sector[c.use().ordinal()] -= pre.capex()[x];
                s2.land[c.plotIdx()] -= pre.area()[x];
                s2.lv[i] = -1;
                boolean[] m2 = mask.clone();
                m2[i] = false;
                fill(s2, pre, m2, budget, lambda, cap, sectorCap);
                double o2 = objective(s2.port, lambda);
                if (o2 > obj + Math.abs(obj) * 1e-4 + 1) {
                    st = s2;
                    obj = o2;
                    better = true;
                }
            }
            if (!better) break;
        }
        return st.lv;
    }

    private static void fill(State st, Pre pre, boolean[] mask, long budget, double lambda, double cap, double sectorCap) {
        List<Cand> cands = pre.cands();
        double[][] npv = pre.npv();
        double[] capex = pre.capex(), area = pre.area();
        int n = cands.size(), runs = st.port.length;
        double[] tmp = new double[runs];
        double cur = objective(st.port, lambda);
        for (int step = 0; step < 400; step++) {
            int bi = -1, bl = -1;
            double bScore = 1e-9;
            for (int i = 0; i < n; i++) {
                if (!mask[i]) continue;
                Cand c = cands.get(i);
                int from = st.lv[i];
                double oldCap = from < 0 ? 0 : capex[c.idx()[from]], oldArea = from < 0 ? 0 : area[c.idx()[from]];
                double[] oldN = from < 0 ? null : npv[c.idx()[from]];
                double sec = st.sector[c.use().ordinal()], land = st.land[c.plotIdx()], plotArea = pre.plotArea()[c.plotIdx()];
                for (int l = from + 1; l < c.idx().length; l++) {
                    int x = c.idx()[l];
                    double add = capex[x] - oldCap;
                    if (add <= 0 || capex[x] > cap + 1 || st.spent + add > budget + 1 || sec + add > sectorCap + 1
                            || land - oldArea + area[x] > plotArea + 1e-6) continue;
                    double[] nn = npv[x];
                    for (int k = 0; k < runs; k++) tmp[k] = st.port[k] + nn[k] - (oldN == null ? 0 : oldN[k]);
                    double score = (objective(tmp, lambda) - cur) / add;
                    if (score > bScore) { bScore = score; bi = i; bl = l; }
                }
            }
            if (bi < 0) break;
            Cand c = cands.get(bi);
            int from = st.lv[bi], x = c.idx()[bl];
            double[] nn = npv[x];
            double[] oldN = from < 0 ? null : npv[c.idx()[from]];
            for (int k = 0; k < runs; k++) st.port[k] += nn[k] - (oldN == null ? 0 : oldN[k]);
            double add = capex[x] - (from < 0 ? 0 : capex[c.idx()[from]]);
            st.spent += add;
            st.sector[c.use().ordinal()] += add;
            st.land[c.plotIdx()] += area[x] - (from < 0 ? 0 : area[c.idx()[from]]);
            st.lv[bi] = bl;
            cur = objective(st.port, lambda);
        }
    }

    static double objective(double[] port, double lambda) {
        double m = mean(port);
        return m - lambda * (m - cvar(port));
    }

    static double[] portfolio(List<Cand> cands, int[] lv, double[][] npv) {
        double[] port = new double[npv[0].length];
        for (int i = 0; i < lv.length; i++) {
            if (lv[i] < 0) continue;
            double[] v = npv[cands.get(i).idx()[lv[i]]];
            for (int k = 0; k < port.length; k++) port[k] += v[k];
        }
        return port;
    }

    static double invested(List<Cand> cands, int[] lv, double[] capex) {
        double s = 0;
        for (int i = 0; i < lv.length; i++) if (lv[i] >= 0) s += capex[cands.get(i).idx()[lv[i]]];
        return s;
    }

    // ------------------------------------------------------------------ seçilmiş portfelin dəqiq qiymətləndirilməsi

    private Result evaluate(List<Cand> cands, int[] lv, long budget, int risk, int horizon, int maxShare, int maxSector,
                            List<FrontierPoint> frontier, List<SinglePoint> singles, int naive, double[][] npvAll, double[] capexAll,
                            int runsAll, int candidateCount, String notice) {
        // seçilmiş mövqelər, böyükdən kiçiyə
        List<Cand> ch = new ArrayList<>();
        List<SimulationSpec> sp = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < lv.length; i++) if (lv[i] >= 0) order.add(i);
        order.sort((x, y) -> Double.compare(cands.get(y).levels().get(lv[y]).capex(), cands.get(x).levels().get(lv[x]).capex()));
        for (int i : order) { ch.add(cands.get(i)); sp.add(cands.get(i).levels().get(lv[i])); }

        Joint j = mc.joint(sp, ch.stream().map(Cand::exp).toList(), FINAL_RUNS, SEED + 1);
        int m = sp.size(), runs = j.runs(), h = horizon;
        double invested = sp.stream().mapToDouble(SimulationSpec::capex).sum();

        double[] port = new double[runs];
        double[][] cf = new double[runs][h + 1];
        for (int i = 0; i < m; i++) for (int k = 0; k < runs; k++) {
            port[k] += j.npv()[i][k];
            for (int t = 0; t <= h; t++) cf[k][t] += j.cf()[i][k][t];
        }
        double[] sorted = port.clone();
        Arrays.sort(sorted);
        int matureFrom = Math.min(5, h), matureTo = Math.min(h, matureFrom + 9);
        double[] annual = new double[runs];
        Integer[] payback = new Integer[runs];
        List<Integer> paid = new ArrayList<>();
        for (int k = 0; k < runs; k++) {
            double s = 0, cum = 0;
            for (int t = matureFrom; t <= matureTo; t++) s += cf[k][t];
            annual[k] = s / (matureTo - matureFrom + 1);
            for (int t = 0; t <= h; t++) { cum += cf[k][t]; if (cum >= 0 && t > 0) { payback[k] = t; break; } }
            if (payback[k] != null) paid.add(payback[k]);
        }
        Collections.sort(paid);
        double[] annualS = annual.clone();
        Arrays.sort(annualS);
        double mean = mean(port), es = mean - cvar(port);
        Totals totals = new Totals(budget, Math.round(invested), Math.max(0, Math.round(budget - invested)), Math.round(mean),
                Math.round(q(sorted, .1)), Math.round(q(sorted, .5)), Math.round(q(sorted, .9)),
                round3(Arrays.stream(port).filter(x -> x < 0).count() / (double) runs),
                Math.round(q(annualS, .1)), Math.round(q(annualS, .5)), Math.round(q(annualS, .9)), Math.round(q(annualS, .5) / 12),
                runs / 2 < paid.size() ? paid.get(runs / 2) : null, irr(medianFlows(cf, h)), pct(es, invested), pct(mean, invested));

        // yelpik: məcmu pul axını
        List<YearBand> fan = new ArrayList<>();
        double[] col = new double[runs];
        double[] cum = new double[runs];
        for (int t = 0; t <= h; t++) {
            for (int k = 0; k < runs; k++) { cum[k] += cf[k][t]; col[k] = cum[k]; }
            double[] c2 = col.clone();
            Arrays.sort(c2);
            fan.add(new YearBand(t, Math.round(q(c2, .1)), Math.round(q(c2, .25)), Math.round(q(c2, .5)), Math.round(q(c2, .75)), Math.round(q(c2, .9))));
        }

        // mövqelər
        List<Position> positions = new ArrayList<>();
        List<PositionFlow> flows = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Impact impact = Impact.ZERO;
        double standaloneShortfall = 0;
        for (int i = 0; i < m; i++) {
            Cand c = ch.get(i);
            SimulationSpec s = sp.get(i);
            double[] ni = j.npv()[i].clone();
            double ms = mean(ni);
            standaloneShortfall += ms - cvar(ni);
            Arrays.sort(ni);
            double[] an = new double[runs];
            for (int k = 0; k < runs; k++) {
                double sum = 0;
                for (int t = matureFrom; t <= matureTo; t++) sum += j.cf()[i][k][t];
                an[k] = sum / (matureTo - matureFrom + 1);
            }
            Arrays.sort(an);
            long loss = Arrays.stream(j.npv()[i]).filter(x -> x < 0).count();
            Plot p = c.plot();
            String label = p.getCode() + " · " + c.use().label();
            labels.add(label);
            positions.add(new Position(p.getId(), p.getCode(), p.getName(), p.getDistrict().label(), zone(p) == 1 ? "dağlıq" : "aran",
                    c.use().name(), c.use().label(), c.use().icon(), s.scale(), s.unit(), Math.round(s.capex()), pct(s.capex(), budget),
                    Math.round(q(an, .5)), Math.round(q(ni, .5)), round3(loss / (double) runs), c.suitability(), s.jobs(),
                    "/simulyator.html?plot=" + p.getId() + "&use=" + c.use().name() + "&scale=" + s.scale()));
            List<Long> yearly = new ArrayList<>();
            double[] yc = new double[runs];
            for (int t = 1; t <= h; t++) {
                for (int k = 0; k < runs; k++) yc[k] = j.cf()[i][k][t];
                double[] y2 = yc.clone();
                Arrays.sort(y2);
                yearly.add(Math.round(q(y2, .5)));
            }
            flows.add(new PositionFlow(p.getCode(), label, yearly));
            impact = impact.plus(Impact.of(s, Impact.areaUsed(s, p.getAreaHa().doubleValue())));
        }

        // korrelyasiya matrisi (NPV üzrə)
        double[][] corr = new double[m][m];
        for (int a = 0; a < m; a++) for (int b = 0; b < m; b++) corr[a][b] = a == b ? 1 : round2(corr(j.npv()[a], j.npv()[b]));
        String hedgeA = null, hedgeB = null;
        double hedge = 2;
        for (int a = 0; a < m; a++) for (int b = a + 1; b < m; b++) if (corr[a][b] < hedge) { hedge = corr[a][b]; hedgeA = labels.get(a); hedgeB = labels.get(b); }

        // diversifikasiya
        double sumW2 = 0;
        for (SimulationSpec s : sp) sumW2 += Math.pow(s.capex() / invested, 2);
        Diversification div;
        {
            String nl = null;
            double nr = 0, nt = 0;
            long np10 = 0, ninv = 0;
            if (naive >= 0) {
                Cand c = null;
                for (Cand x : cands) for (int id : x.idx()) if (id == naive) c = x;
                double[] v = npvAll[naive].clone();
                double mn = mean(v);
                nl = c == null ? null : c.plot().getCode() + " · " + c.use().label();
                nr = pct(mn - cvar(v), capexAll[naive]);
                nt = pct(mn, capexAll[naive]);
                Arrays.sort(v);
                np10 = Math.round(q(v, .1));
                ninv = Math.round(capexAll[naive]);
            }
            div = new Diversification(standaloneShortfall <= 0 ? 0 : Math.round((1 - es / standaloneShortfall) * 1000) / 10.0,
                    Math.round(1 / sumW2 * 10) / 10.0, Math.round(es), Math.round(standaloneShortfall), nl, nr, nt, np10, ninv,
                    hedgeA, hedgeB, hedge == 2 ? 0 : hedge);
        }

        List<Stress> stress = stress(j, ch, labels, matureFrom == h ? 1 : 3, h);
        List<String> summary = summary(totals, positions, div, stress, impact, horizon);
        return new Result(budget, risk, horizon, maxShare, maxSector, candidateCount, runsAll, totals, positions, frontier, singles, div, stress, fan,
                flows, labels, corr, impact, Impact.jobsPerMillion(impact.directJobs(), invested), summary, assumptions(), notice);
    }

    private SimulationSpec rebuild(Plot p, UseCase u, double scale, int horizon) {
        return models.build(p, u, new ModelFactory.Overrides(scale, null, null, null, null, null, horizon));
    }

    static double roundScale(UseCase u, double v) {
        return switch (u) {
            case GUNES -> Math.max(0.1, Math.floor(v * 10) / 10);
            case AQROTURIZM -> Math.max(1, Math.floor(v));
            case SOYUDUCU -> Math.max(50, Math.floor(v / 50) * 50);
            default -> Math.max(0.5, Math.floor(v * 2) / 2);
        };
    }

    // ------------------------------------------------------------------ stress testlər

    private List<Stress> stress(Joint j, List<Cand> ch, List<String> labels, int from, int h) {
        record Cond(String key, String title, String desc, java.util.function.BiPredicate<World, Integer> test, boolean relevant) {}
        boolean highland = ch.stream().anyMatch(c -> c.exp().zone() == 1), lowland = ch.stream().anyMatch(c -> c.exp().zone() == 0);
        boolean tourism = ch.stream().anyMatch(c -> c.use() == UseCase.AQROTURIZM);
        List<Cond> conds = List.of(
                new Cond("drought", "Aranda quraqlıq ili", "Ağdam, Füzuli, Cəbrayıl kimi aran rayonlarında hava şəraitinin ən pis 10% illəri", (w, t) -> w.weather(0, t) < STRESS_Z, lowland),
                new Cond("mountain", "Dağlıq zonada sərt il", "Kəlbəcər, Laçın, Şuşa kimi dağlıq rayonlarda şaxta və quraqlığın ən pis 10% illəri", (w, t) -> w.weather(1, t) < STRESS_Z, highland),
                new Cond("price", "Kənd təsərrüfatı qiymətlərinin düşməsi", "İxrac bazarında meyvə, taxıl və tərəvəz qiymətlərinin ən pis 10% illəri", (w, t) -> w.price(t) < STRESS_Z, true),
                new Cond("tourism", "Turizm böhranı", "Turist axınının ən zəif 10% illəri", (w, t) -> w.tourism(t) < STRESS_Z, tourism));
        List<Stress> out = new ArrayList<>();
        int m = ch.size();
        for (Cond c : conds) {
            if (!c.relevant()) continue;
            double[] base = new double[m], hit = new double[m];
            long nb = 0, nh = 0;
            for (int k = 0; k < j.runs(); k++) {
                World w = j.worlds()[k];
                for (int t = from; t <= h; t++) {
                    boolean in = c.test().test(w, t);
                    for (int i = 0; i < m; i++) {
                        double v = j.cf()[i][k][t];
                        base[i] += v;
                        if (in) hit[i] += v;
                    }
                    nb++;
                    if (in) nh++;
                }
            }
            if (nh == 0) continue;
            double pb = 0, ph = 0;
            int worst = -1, bestI = -1;
            double worstV = 1e9, bestV = -1e9;
            for (int i = 0; i < m; i++) {
                double b = base[i] / nb, x = hit[i] / nh;
                pb += b;
                ph += x;
                if (Math.abs(b) < 1) continue;
                double ch1 = (x - b) / Math.abs(b) * 100;
                if (ch1 < worstV) { worstV = ch1; worst = i; }
                if (ch1 > bestV) { bestV = ch1; bestI = i; }
            }
            double pc = Math.abs(pb) < 1 ? 0 : (ph - pb) / Math.abs(pb) * 100;
            out.add(new Stress(c.key(), c.title(), c.desc(), round1(pc), worst < 0 ? null : labels.get(worst), round1(worstV),
                    bestI < 0 || bestI == worst ? null : labels.get(bestI), round1(bestV)));
        }
        return out;
    }

    // ------------------------------------------------------------------ izah

    static List<String> summary(Totals t, List<Position> ps, Diversification d, List<Stress> stress, Impact im, int horizon) {
        List<String> out = new ArrayList<>();
        out.add("Büdcənin " + Money.fmt(t.invested()) + " hissəsi " + ps.size() + " mövqeyə bölündü"
                + (t.cash() > t.budget() * 0.02 ? ", " + Money.fmt(t.cash()) + " ehtiyat kimi nağd qalır (daha çox yerləşdirmək riski artırırdı)" : "") + ".");
        out.add("Orta ssenaridə portfel yetkin illərdə ildə " + Money.fmt(t.annualNetP50()) + " (ayda " + Money.fmt(t.monthlyNetP50())
                + ") xalis gəlir verir, pis ssenaridə (P10) ildə " + Money.fmt(t.annualNetP10()) + ".");
        out.add(t.paybackP50() == null ? "İnvestisiya " + horizon + " illik üfüqdə ssenarilərin yarısından azında tam qayıdır."
                : "Qoyulan pul orta ssenaridə " + t.paybackP50() + "-ci ildə qayıdır, " + horizon + " ildə zərər ehtimalı " + Math.round(t.probLoss() * 100) + "%.");
        if (d.naiveLabel() != null && d.benefitPct() > 0)
            out.add("Diversifikasiya pis ssenari itkisini " + Math.round(d.benefitPct()) + "% azaldır. Bütün pulu ən gəlirli tək varianta (" + d.naiveLabel()
                    + ") qoysaydınız, pis ssenaridə xalis dəyər " + Money.fmt(d.naiveNpvP10()) + " olardı, portfeldə " + Money.fmt(t.npvP10()) + ".");
        if (d.hedgeLabel() != null && d.hedgeCorrelation() < 0.3)
            out.add(d.hedgeLabel() + " və " + d.hedgeWith() + " bir-birini sığortalayır: korrelyasiya " + String.format(Locale.ROOT, "%.2f", d.hedgeCorrelation())
                    + ", yəni birinin pis ili digərinin pis ili ilə üst-üstə düşmür.");
        stress.stream().min(Comparator.comparingDouble(Stress::portfolioChangePct)).ifPresent(s -> {
            if (s.worstLabel() != null)
                out.add(s.title() + " ilində portfelin gəliri " + fmtPct(s.portfolioChangePct()) + " dəyişir, ən çox təsirlənən " + s.worstLabel() + " isə " + fmtPct(s.worstChangePct()) + ".");
        });
        out.add("Portfel " + im.directJobs() + " daimi iş yeri yaradır və " + im.families() + " ailənin (" + im.people() + " nəfər) Qarabağa qayıdışına iqtisadi əsas verir.");
        return out;
    }

    static List<String> assumptions() {
        List<String> a = new ArrayList<>();
        a.add("Hər variant " + RUNS + " (seçilmiş portfel " + FINAL_RUNS + ") ortaq ssenaridə simulyasiya olunur: hava (aran və dağlıq zona), kənd təsərrüfatı bazar qiyməti, turizm tələbi və inflyasiya bütün investisiyalar üçün eynidir.");
        a.add("Həssaslıq: taxıl və bağlar hava ilinə güclü bağlıdır, istixana zəif, günəş stansiyası əks istiqamətdə (quraq il günəşli ildir), aqroturizm turizm tələbinə bağlıdır.");
        a.add("Risk ölçüsü: pis 10% ssenarinin orta xalis dəyərinin gözləniləndən geri qalması (CVaR). Gəlir ölçüsü: gözlənilən xalis dəyər (NPV).");
        a.add("Məhdudiyyətlər: bir mövqe büdcənin seçilmiş faizindən (standart 35%), bir istiqamət (məs. bütün bağlar) 40%-dən çox ola bilməz.");
        a.add("Hər variant 5 miqyasda ayrıca modelləşdirilir (standart ölçünün 10-100%-i, ən azı günəş 1 MWp, otel 6 otaq, anbar 500 t, bağ 5 ha): kiçik obyektin vahid xərci yüksəkdir və bu nəzərə alınır. İcarəyə verilmiş sahələr daxil edilmir.");
        a.addAll(Impact.SOURCES);
        return a;
    }

    // ------------------------------------------------------------------ köməkçi

    static double mean(double[] v) {
        double s = 0;
        for (double x : v) s += x;
        return s / v.length;
    }

    /** Pis 10% ssenarinin ortası (CVaR10). Tam çeşidləmə əvəzinə ən kiçik qiymətlərin bufer ilə seçilməsi. */
    static double cvar(double[] v) {
        int m = Math.max(1, (int) Math.floor(v.length * TAIL));
        double[] buf = new double[m];
        int cnt = 0;
        for (double x : v) {
            if (cnt < m) {
                int j = cnt++ - 1;
                while (j >= 0 && buf[j] > x) { buf[j + 1] = buf[j]; j--; }
                buf[j + 1] = x;
            } else if (x < buf[m - 1]) {
                int j = m - 2;
                while (j >= 0 && buf[j] > x) { buf[j + 1] = buf[j]; j--; }
                buf[j + 1] = x;
            }
        }
        double sum = 0;
        for (int i = 0; i < m; i++) sum += buf[i];
        return sum / m;
    }

    static double q(double[] sorted, double p) {
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx), hi = (int) Math.ceil(idx);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo);
    }

    static double corr(double[] a, double[] b) {
        double ma = mean(a), mb = mean(b), sab = 0, saa = 0, sbb = 0;
        for (int i = 0; i < a.length; i++) {
            sab += (a[i] - ma) * (b[i] - mb);
            saa += (a[i] - ma) * (a[i] - ma);
            sbb += (b[i] - mb) * (b[i] - mb);
        }
        return saa == 0 || sbb == 0 ? 0 : sab / Math.sqrt(saa * sbb);
    }

    /** Hər il üzrə portfel pul axınının medianı (orta ssenarinin IRR-i üçün). */
    static double[] medianFlows(double[][] cf, int h) {
        double[] out = new double[h + 1], col = new double[cf.length];
        for (int t = 0; t <= h; t++) {
            for (int k = 0; k < cf.length; k++) col[k] = cf[k][t];
            Arrays.sort(col);
            out[t] = q(col, .5);
        }
        return out;
    }

    static Double irr(double[] cf) {
        double lo = -0.99, hi = 1.5;
        if (npvAt(cf, lo) * npvAt(cf, hi) > 0) return null;
        for (int i = 0; i < 80; i++) {
            double mid = (lo + hi) / 2;
            if (npvAt(cf, lo) * npvAt(cf, mid) <= 0) hi = mid; else lo = mid;
        }
        return Math.round((lo + hi) / 2 * 1000) / 10.0;
    }

    private static double npvAt(double[] cf, double r) {
        double v = 0;
        for (int t = 0; t < cf.length; t++) v += cf[t] / Math.pow(1 + r, t);
        return v;
    }

    static double pct(double v, double base) {
        return base <= 0 ? 0 : Math.round(v / base * 1000) / 10.0;
    }

    static String fmtPct(double v) {
        return (v > 0 ? "+" : "") + Math.round(v) + "%";
    }

    static double round1(double d) {
        return Math.round(d * 10) / 10.0;
    }

    static double round2(double d) {
        return Math.round(d * 100) / 100.0;
    }

    static double round3(double d) {
        return Math.round(d * 1000) / 1000.0;
    }
}
