package az.aqroinvest.economics;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * "Fermanın 10 ilini 30 saniyədə yaşa": biznesin minlərlə mümkün gələcəyini simulyasiya edir.
 * Hər ssenaridə məhsuldarlıq (hava, xəstəlik, şaxta), bazar qiyməti (orta səviyyəyə qayıdan təsadüfi gəzinti),
 * xərclərin inflyasiyası və tikinti xərcinin artması təsadüfi seçilir. Nəticə tək rəqəm deyil, ehtimal paylanmasıdır:
 * pis (P10), orta (P50) və yaxşı (P90) ssenarilər, geri qayıtma ehtimalı, əsas risk amilləri.
 */
@Service
public class MonteCarloService {
    static final double PRICE_REVERSION = 0.35;

    public record YearPoint(int year, double p10, double p25, double p50, double p75, double p90) {}

    public record PathYear(int year, long revenue, long costs, long net, long cumulative, double yieldIndex,
                           double priceIndex, List<String> events) {}

    public record Path(String label, long npv, Integer paybackYear, Double irr, long capex, List<PathYear> years) {}

    public record Driver(String key, String label, double correlation) {}

    public record Summary(long npvP10, long npvP50, long npvP90, double probPositiveNpv, double probPayback,
                         Integer paybackP50, long finalCashP10, long finalCashP50, long finalCashP90,
                         long annualNetMature, long annualRevenueMature, double probLoss, Double irrP50,
                         long annualNetMatureP10, long annualNetMatureP90) {}

    public record Result(int runs, int horizon, Summary summary, List<YearPoint> fan, int[] paybackHistogram,
                         Path typical, Path bad, Path good, List<Driver> drivers, Summary withoutOfftake) {}

    private record Run(double[] cumulative, long[] revenue, long[] costs, long[] net, double[] yieldIdx,
                       double[] priceIdx, List<List<String>> events, double npv, Integer payback, double capex,
                       double avgYield, double avgPrice, int shocks, double overrun, double inflation) {}

    /** Hər ssenari üzrə yetkin illərin orta gəliri və xalis gəliri (iddiaların ehtimalını hesablamaq üçün), artan sırada. */
    public record Distribution(double[] matureRevenue, double[] matureNet) {
        public double probAtLeast(double[] sorted, double v) {
            int lo = 0, hi = sorted.length;
            while (lo < hi) { int m = (lo + hi) >>> 1; if (sorted[m] < v) lo = m + 1; else hi = m; }
            return (double) (sorted.length - lo) / sorted.length;
        }
    }

    public Distribution distribution(SimulationSpec s, int runs, long seed) {
        int from = Math.min(s.horizon(), firstMatureYear(s));
        double[] rev = new double[runs], net = new double[runs];
        for (int i = 0; i < runs; i++) {
            Run r = path(s, new SplittableRandom(seed * 1_000_003L + i));
            rev[i] = avg(r.revenue, from);
            net[i] = avg(r.net, from);
        }
        Arrays.sort(rev);
        Arrays.sort(net);
        return new Distribution(rev, net);
    }

    public Result simulate(SimulationSpec spec, int runs, long seed) {
        Result r = run(spec, runs, seed, true);
        if (spec.offtakeShare() > 0) {
            Result base = run(spec.withOfftake(0, 1), runs, seed, false);
            return new Result(r.runs, r.horizon, r.summary, r.fan, r.paybackHistogram, r.typical, r.bad, r.good,
                    r.drivers, base.summary);
        }
        return r;
    }

    private Result run(SimulationSpec s, int runs, long seed, boolean details) {
        int h = s.horizon();
        Run[] all = new Run[runs];
        for (int i = 0; i < runs; i++) all[i] = path(s, new SplittableRandom(seed * 1_000_003L + i));

        double[] npvs = Arrays.stream(all).mapToDouble(Run::npv).sorted().toArray();
        List<YearPoint> fan = new ArrayList<>();
        double[] col = new double[runs];
        for (int t = 0; t <= h; t++) {
            for (int i = 0; i < runs; i++) col[i] = all[i].cumulative[t];
            Arrays.sort(col);
            fan.add(new YearPoint(t, q(col, .10), q(col, .25), q(col, .50), q(col, .75), q(col, .90)));
        }
        int[] hist = new int[h + 1];
        int paid = 0, positive = 0, loss = 0;
        List<Integer> paybacks = new ArrayList<>();
        for (Run r : all) {
            if (r.payback != null) { hist[r.payback - 1]++; paid++; paybacks.add(r.payback); } else hist[h]++;
            if (r.npv > 0) positive++;
            if (r.cumulative[h] < 0) loss++;
        }
        Collections.sort(paybacks);
        // mediana bütün ssenarilər üzrə: geri qayıtmayanlar "sonsuz" sayılır
        Integer paybackP50 = runs / 2 < paybacks.size() ? paybacks.get(runs / 2) : null;
        Run typical = closest(all, q(npvs, .5));
        int matureFrom = Math.min(h, firstMatureYear(s));
        long[] matureNet = Arrays.stream(all).mapToLong(r -> avg(r.net, matureFrom)).sorted().toArray();
        long[] matureRev = Arrays.stream(all).mapToLong(r -> avg(r.revenue, matureFrom)).sorted().toArray();
        Double irr = irr(typical, h);
        Summary sum = new Summary(Math.round(q(npvs, .10)), Math.round(q(npvs, .50)), Math.round(q(npvs, .90)),
                round3((double) positive / runs), round3((double) paid / runs), paybackP50,
                Math.round(fan.get(h).p10()), Math.round(fan.get(h).p50()), Math.round(fan.get(h).p90()),
                matureNet[runs / 2], matureRev[runs / 2], round3((double) loss / runs), irr,
                matureNet[(int) Math.floor((runs - 1) * 0.1)], matureNet[(int) Math.ceil((runs - 1) * 0.9)]);
        if (!details) return new Result(runs, h, sum, fan, hist, null, null, null, List.of(), null);
        return new Result(runs, h, sum, fan, hist,
                toPath("Orta ssenari (P50)", typical, h),
                toPath("Pis ssenari (P10)", closest(all, q(npvs, .10)), h),
                toPath("Yaxşı ssenari (P90)", closest(all, q(npvs, .90)), h),
                drivers(all, s), null);
    }

    private Run path(SimulationSpec s, SplittableRandom rnd) {
        return path(s, rnd, null, null);
    }

    /**
     * Bir ssenari. w və e verilibsə, təsadüfi amillər ortaq "dünya" ilə korrelyasiyalıdır (portfel üçün):
     * hər amil = yüklənmə × ortaq amil + √(1 - yüklənmə²) × fərdi amil, ona görə hər investisiyanın öz paylanması dəyişmir.
     */
    private Run path(SimulationSpec s, SplittableRandom rnd, World w, Exposure e) {
        int h = s.horizon();
        double overrun = Math.max(0, s.capexOverrunMean() + s.capexOverrunSigma() * gauss(rnd));
        double capex = s.capex() * (1 + overrun);
        double[] cum = new double[h + 1];
        long[] rev = new long[h + 1], cost = new long[h + 1], net = new long[h + 1];
        double[] yi = new double[h + 1], pi = new double[h + 1];
        List<List<String>> ev = new ArrayList<>();
        ev.add(List.of());
        cum[0] = -capex;
        double npv = -capex, x = 0, infl = 1, sumY = 0, sumP = 0;
        int shocks = 0;
        Integer payback = null;
        for (int t = 1; t <= h; t++) {
            double ramp = t <= s.ramp().length ? s.ramp()[t - 1] : 1.0;
            List<String> events = new ArrayList<>();
            double ys = s.yieldSigma();
            double zy = gauss(rnd);
            if (w != null) zy = mix(zy, e.yieldWeather(), w.weather(e.zone(), t), e.yieldTourism(), w.tourism[t]);
            double yMult = Math.exp(ys * zy - ys * ys / 2);
            for (SimulationSpec.Shock sh : s.shocks()) {
                boolean hit = false;
                if (ramp > 0 && w == null) hit = rnd.nextDouble() < sh.probability();
                else if (ramp > 0) {
                    // Qauss kopulası: şokun öz ehtimalı saxlanılır, amma pis hava ilində bütün sahələrdə birlikdə baş verir
                    boolean weather = weatherShock(sh.name()), tour = tourismShock(sh.name());
                    double common = weather ? -w.weather(e.zone(), t) : tour ? -w.tourism[t] : 0;
                    hit = mix(gauss(rnd), weather || tour ? e.shock() : 0, common, 0, 0) < invNorm(sh.probability());
                }
                if (hit) {
                    yMult *= 1 - sh.impact();
                    events.add(sh.name() + " (-" + Math.round(sh.impact() * 100) + "%)");
                    shocks++;
                }
            }
            double ps = s.priceSigma();
            double zp = gauss(rnd);
            if (w != null) zp = mix(zp, e.price(), w.price[t], e.priceTourism(), w.tourism[t]);
            x = (1 - PRICE_REVERSION) * x + ps * zp;
            double trend = Math.pow(1 + s.priceDrift(), t - 1);
            double pIdx = Math.exp(x - ps * ps / 2) * trend;
            if (Math.abs(pIdx / trend - 1) >= 0.2 && ramp > 0) {
                events.add((pIdx > trend ? "Bazar qiyməti yüksəldi (+" : "Bazar qiyməti düşdü (") + Math.round((pIdx / trend - 1) * 100) + "%)");
            }
            double degr = Math.pow(1 - s.degradation(), t - 1);
            double volume = s.scale() * s.yieldPerUnit() * ramp * yMult * degr;
            double contract = s.price() * s.offtakePriceFactor() * trend;
            double revenue = volume * (s.offtakeShare() * contract + (1 - s.offtakeShare()) * s.price() * pIdx);
            double zi = gauss(rnd);
            if (w != null) zi = mix(zi, 0.8, w.macro[t], 0, 0);
            infl *= 1 + 0.04 + 0.015 * zi;
            double activity = ramp == 0 ? 0.45 : 0.6 + 0.4 * ramp;
            double costs = (s.opexFixed() + s.opexPerUnit() * s.scale() * activity) * infl + revenue * s.opexRevenueShare();
            double n = revenue - costs;
            cum[t] = cum[t - 1] + n;
            npv += n / Math.pow(1 + s.discountRate(), t);
            if (payback == null && cum[t] >= 0) payback = t;
            rev[t] = Math.round(revenue);
            cost[t] = Math.round(costs);
            net[t] = Math.round(n);
            yi[t] = yMult;
            pi[t] = pIdx / trend;
            sumY += yMult;
            sumP += pIdx / trend;
            ev.add(events);
        }
        return new Run(cum, rev, cost, net, yi, pi, ev, npv, payback, capex, sumY / h, sumP / h, shocks, overrun, infl);
    }

    // ------------------------------------------------------------------ portfel: ortaq dünya

    /**
     * İnvestisiyanın ortaq amillərə həssaslığı (yüklənmə, -1..1). zone: 0 aran, 1 dağlıq.
     * yieldWeather: məhsuldarlıq hava ilə, yieldTourism: doluluq turizm tələbi ilə, price: kənd təsərrüfatı bazar qiyməti ilə,
     * priceTourism: otaq qiyməti turizm tələbi ilə, shock: təbii şokların hava (və ya turizm) ili ilə əlaqəsi.
     */
    public record Exposure(double yieldWeather, double yieldTourism, double price, double priceTourism, double shock, int zone) {
        public static Exposure of(UseCase u, int zone) {
            return switch (u) {
                case EKINCILIK -> new Exposure(0.6, 0, 0.6, 0, 0.75, zone);
                case BAGCILIQ, UZUMCULUK -> new Exposure(0.5, 0, 0.55, 0, 0.7, zone);
                case HEYVANDARLIQ -> new Exposure(0.35, 0, 0.4, 0, 0.5, zone);
                case ARICILIQ -> new Exposure(0.5, 0, 0.35, 0, 0.6, zone);
                case ISTIXANA -> new Exposure(0.15, 0, 0.6, 0, 0.2, zone);
                // Quraq illər günəşli illərdir: istehsal hava amili ilə əks korrelyasiyalıdır, tarif sabitdir
                case GUNES -> new Exposure(-0.35, 0, 0, 0, 0, zone);
                case AQROTURIZM -> new Exposure(0.1, 0.7, 0, 0.5, 0.6, zone);
                case SOYUDUCU -> new Exposure(0.45, 0, 0.45, 0, 0.6, zone);
            };
        }
    }

    /** Bir ssenaridə bütün investisiyalar üçün ortaq olan illik amillər (standart normal). */
    public static final class World {
        final double[] global, price, tourism, macro;
        final double[][] zone;

        World(int h, SplittableRandom r) {
            global = new double[h + 1];
            price = new double[h + 1];
            tourism = new double[h + 1];
            macro = new double[h + 1];
            zone = new double[2][h + 1];
            for (int t = 0; t <= h; t++) {
                global[t] = gauss(r);
                zone[0][t] = gauss(r);
                zone[1][t] = gauss(r);
                price[t] = gauss(r);
                tourism[t] = gauss(r);
                macro[t] = gauss(r);
            }
        }

        /** Zonanın hava amili: 75% bütün region üçün ortaq, qalanı zonaya (aran, dağlıq) xas. */
        public double weather(int z, int t) {
            return 0.75 * global[t] + 0.6614 * zone[z][t];
        }

        public double price(int t) {
            return price[t];
        }

        public double tourism(int t) {
            return tourism[t];
        }
    }

    /** Birgə simulyasiyanın nəticəsi: cf[aktiv][ssenari][il] (0-cı il: -investisiya), npv[aktiv][ssenari]. */
    public record Joint(int runs, int horizon, float[][][] cf, double[][] npv, World[] worlds) {}

    public Joint joint(List<SimulationSpec> specs, List<Exposure> exposures, int runs, long seed) {
        return joint(specs, exposures, runs, seed, true);
    }

    public Joint joint(List<SimulationSpec> specs, List<Exposure> exposures, int runs, long seed, boolean cashflows) {
        return joint(specs, exposures, runs, seed, cashflows, null);
    }

    /**
     * cashflows=false: yalnız NPV saxlanılır (optimallaşdırma üçün, yaddaşa qənaət).
     * streams: aktivin fərdi təsadüfi axınının nömrəsi. Eyni təsərrüfatın müxtəlif miqyasları eyni axını paylaşır,
     * ona görə miqyaslar arasındakı fərq təsadüfi səs-küydən yox, iqtisadiyyatdan gəlir.
     */
    public Joint joint(List<SimulationSpec> specs, List<Exposure> exposures, int runs, long seed, boolean cashflows, int[] streams) {
        int h = specs.stream().mapToInt(SimulationSpec::horizon).max().orElse(10);
        World[] worlds = new World[runs];
        for (int k = 0; k < runs; k++) worlds[k] = new World(h, new SplittableRandom(seed * 7_919L + k));
        float[][][] cf = cashflows ? new float[specs.size()][runs][h + 1] : null;
        double[][] npv = new double[specs.size()][runs];
        for (int i = 0; i < specs.size(); i++) {
            SimulationSpec s = specs.get(i);
            for (int k = 0; k < runs; k++) {
                long stream = streams == null ? i : streams[i];
                Run r = path(s, new SplittableRandom(seed * 1_000_003L + 104_729L * stream + k), worlds[k], exposures.get(i));
                if (cf != null) {
                    cf[i][k][0] = (float) -r.capex;
                    for (int t = 1; t <= s.horizon(); t++) cf[i][k][t] = r.net[t];
                }
                npv[i][k] = r.npv;
            }
        }
        return new Joint(runs, h, cf, npv, worlds);
    }

    private static boolean weatherShock(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("quraq") || n.contains("şaxta") || n.contains("dolu") || n.contains("yem") || n.contains("bal mövsümü") || n.contains("məhsul qıtlığı");
    }

    private static boolean tourismShock(String name) {
        return name.toLowerCase(Locale.ROOT).contains("turizm");
    }

    /** Fərdi amili ortaq amillərlə qarışdırır, vahid dispersiyanı saxlayır (ortaq amillər bir-birindən asılı deyil). */
    static double mix(double own, double l1, double f1, double l2, double f2) {
        double rest = Math.max(0, 1 - l1 * l1 - l2 * l2);
        return l1 * f1 + l2 * f2 + Math.sqrt(rest) * own;
    }

    /** Standart normal paylanmanın tərs funksiyası (Acklam yaxınlaşması, xəta < 1.2e-9). */
    static double invNorm(double p) {
        if (p <= 0) return Double.NEGATIVE_INFINITY;
        if (p >= 1) return Double.POSITIVE_INFINITY;
        double[] a = {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
        double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01};
        double[] c = {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
        double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00};
        double pl = 0.02425;
        if (p < pl) {
            double q = Math.sqrt(-2 * Math.log(p));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        if (p > 1 - pl) {
            double q = Math.sqrt(-2 * Math.log(1 - p));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        double q = p - 0.5, r = q * q;
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
    }

    private static Path toPath(String label, Run r, int h) {
        List<PathYear> ys = new ArrayList<>();
        for (int t = 1; t <= h; t++) {
            ys.add(new PathYear(t, r.revenue[t], r.costs[t], r.net[t], Math.round(r.cumulative[t]),
                    round3(r.yieldIdx[t]), round3(r.priceIdx[t]), r.events.get(t)));
        }
        return new Path(label, Math.round(r.npv), r.payback, irr(r, h), Math.round(r.capex), ys);
    }

    /** NPV ilə hər amil arasındakı korrelyasiya: hansı risk nəticəyə daha çox təsir edir. */
    private static List<Driver> drivers(Run[] all, SimulationSpec s) {
        double[] npv = Arrays.stream(all).mapToDouble(Run::npv).toArray();
        List<Driver> d = new ArrayList<>();
        d.add(new Driver("yield", s.useCase() == UseCase.GUNES ? "Günəş radiasiyası və istehsal" :
                s.useCase() == UseCase.AQROTURIZM ? "Otaqların doluluğu" :
                s.useCase() == UseCase.SOYUDUCU ? "Anbarın yüklənməsi" : "Məhsuldarlıq (hava, xəstəlik)",
                corr(npv, Arrays.stream(all).mapToDouble(Run::avgYield).toArray())));
        if (s.offtakeShare() < 1) d.add(new Driver("price", "Bazar qiyməti", corr(npv, Arrays.stream(all).mapToDouble(Run::avgPrice).toArray())));
        if (!s.shocks().isEmpty()) d.add(new Driver("shocks", "Təbii və bazar şokları", corr(npv, Arrays.stream(all).mapToDouble(r -> r.shocks).toArray())));
        d.add(new Driver("capex", "Tikinti xərcinin artması", corr(npv, Arrays.stream(all).mapToDouble(Run::overrun).toArray())));
        d.add(new Driver("inflation", "Xərclərin inflyasiyası", corr(npv, Arrays.stream(all).mapToDouble(Run::inflation).toArray())));
        d.sort(Comparator.comparingDouble((Driver x) -> -Math.abs(x.correlation())));
        return d;
    }

    private static int firstMatureYear(SimulationSpec s) {
        for (int i = 0; i < s.ramp().length; i++) if (s.ramp()[i] >= 1) return i + 1;
        return s.ramp().length + 1;
    }

    /** Yetkin illərin ortası: tam gücə çıxdıqdan sonrakı ilk 10 il (iddialarla müqayisə üçün). */
    private static long avg(long[] a, int from) {
        long sum = 0;
        int n = 0;
        for (int t = from; t < a.length && t < from + 10; t++) { sum += a[t]; n++; }
        return n == 0 ? 0 : sum / n;
    }

    private static Run closest(Run[] all, double npv) {
        Run best = all[0];
        for (Run r : all) if (Math.abs(r.npv - npv) < Math.abs(best.npv - npv)) best = r;
        return best;
    }

    static Double irr(Run r, int h) {
        double[] cf = new double[h + 1];
        cf[0] = -r.capex;
        for (int t = 1; t <= h; t++) cf[t] = r.net[t];
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

    private static double corr(double[] a, double[] b) {
        int n = a.length;
        double ma = Arrays.stream(a).average().orElse(0), mb = Arrays.stream(b).average().orElse(0);
        double sab = 0, saa = 0, sbb = 0;
        for (int i = 0; i < n; i++) {
            sab += (a[i] - ma) * (b[i] - mb);
            saa += (a[i] - ma) * (a[i] - ma);
            sbb += (b[i] - mb) * (b[i] - mb);
        }
        return saa == 0 || sbb == 0 ? 0 : round3(sab / Math.sqrt(saa * sbb));
    }

    private static double q(double[] sorted, double p) {
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx), hi = (int) Math.ceil(idx);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo);
    }

    private static double gauss(SplittableRandom r) {
        double u1 = r.nextDouble(), u2 = r.nextDouble();
        return Math.sqrt(-2 * Math.log(Math.max(u1, 1e-12))) * Math.cos(2 * Math.PI * u2);
    }

    private static double round3(double d) {
        return Math.round(d * 1000) / 1000.0;
    }
}
