package az.aqroinvest.economics;

import az.aqroinvest.plot.LandDesignation;
import az.aqroinvest.plot.Plot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Bu torpaqda nə etmək olar?" - ən yaxşı istifadə mühərriki. Hər ssenari üçün iki sual cavablanır:
 * (1) torpağın təyinatı və fiziki xüsusiyyətləri buna imkan verirmi (uyğunluq balı və səbəbləri),
 * (2) pul baxımından nə qədər cəlbedicidir (Monte-Carlo ilə P50 NPV, geri qayıtma ehtimalı).
 * Məsələn, şoranlaşmış torpaq əkin üçün zəifdir, amma günəş stansiyası üçün ən yaxşı namizəddir.
 */
@Service
@RequiredArgsConstructor
public class LandPotentialService {
    static final int QUICK_RUNS = 600;

    private final ModelFactory models;
    private final MonteCarloService mc;
    private final Map<String, List<Option>> memo = new ConcurrentHashMap<>();

    public record Reason(boolean positive, String text) {}

    public record Option(UseCase useCase, String label, String icon, String hint, int suitability, String verdict,
                         boolean permitted, List<Reason> reasons, double scale, double maxScale, String unit, long capex,
                         long annualRevenue, long annualNet, long npvP50, long totalNetP50, double probPayback, Integer paybackP50,
                         int horizon, int jobs, double rank) {}

    public List<Option> options(Plot p) {
        String key = p.getId() + ":" + p.getVersion();
        return memo.computeIfAbsent(key, k -> compute(p));
    }

    public Option option(Plot p, UseCase u) {
        return options(p).stream().filter(o -> o.useCase() == u).findFirst().orElseThrow();
    }

    private List<Option> compute(Plot p) {
        List<Option> list = new ArrayList<>();
        for (UseCase u : UseCase.values()) {
            List<Reason> reasons = new ArrayList<>();
            boolean permitted = permitted(p.getDesignation(), u);
            int score = suitability(p, u, reasons);
            if (!permitted) score = Math.min(score, 25);
            SimulationSpec spec = models.build(p, u, ModelFactory.Overrides.none());
            MonteCarloService.Result r = mc.simulate(spec, QUICK_RUNS, 42);
            MonteCarloService.Summary s = r.summary();
            double money = Math.max(0, Math.min(1, s.probPayback())) * 60 + Math.max(0, Math.min(1, s.probPositiveNpv())) * 40;
            double rank = (score * 0.65 + money * 0.35) * (permitted ? 1 : 0.4);
            String verdict = score >= 75 ? "Çox uyğun" : score >= 55 ? "Uyğundur" : score >= 35 ? "Şərtlərlə mümkündür" : "Tövsiyə olunmur";
            if (score >= 35 && s.probPayback() < 0.25) {
                verdict = "Zərər riski yüksəkdir";
                score = Math.min(score, 40);
                reasons.add(new Reason(false, "Simulyasiyada ssenarilərin yalnız " + Math.round(s.probPayback() * 100) + "%-də investisiya " + spec.horizon() + " ildə qayıdır"));
            }
            list.add(new Option(u, u.label(), u.icon(), u.hint(), score, verdict, permitted, reasons, spec.scale(),
                    models.maxScale(p, u), spec.unit(), Math.round(spec.capex()), s.annualRevenueMature(), s.annualNetMature(),
                    s.npvP50(), s.finalCashP50(), s.probPayback(), s.paybackP50(), spec.horizon(), spec.jobs(), Math.round(rank * 10) / 10.0));
        }
        // Göstərilən bala görə sıralanır (bərabər balda iqtisadi göstərici həll edir): ekrandakı sıra və "Ən uyğun" ziddiyyət yaratmır
        list.sort(Comparator.comparingInt((Option o) -> -o.suitability()).thenComparingDouble(o -> -o.rank()));
        Option top = list.get(0);
        if (top.suitability() >= 75 && top.verdict().equals("Çox uyğun")) {
            list.set(0, new Option(top.useCase(), top.label(), top.icon(), top.hint(), top.suitability(), "Ən uyğun", top.permitted(), top.reasons(),
                    top.scale(), top.maxScale(), top.unit(), top.capex(), top.annualRevenue(), top.annualNet(), top.npvP50(), top.totalNetP50(),
                    top.probPayback(), top.paybackP50(), top.horizon(), top.jobs(), top.rank()));
        }
        return list;
    }

    /** Torpaq təyinatı baxımından icazə: əkin yerində günəş stansiyası və anbar təyinat dəyişikliyi tələb edir. */
    public static boolean permitted(LandDesignation d, UseCase u) {
        return switch (d) {
            case EKIN -> u != UseCase.GUNES && u != UseCase.SOYUDUCU && u != UseCase.AQROTURIZM;
            case COXILLIK -> u != UseCase.GUNES && u != UseCase.SOYUDUCU;
            case OTLAQ, YARARSIZ -> u != UseCase.SOYUDUCU;
            case SENAYE -> u == UseCase.SOYUDUCU || u == UseCase.ISTIXANA || u == UseCase.GUNES;
        };
    }

    private static final Map<LandDesignation, Map<UseCase, Integer>> BASE = new EnumMap<>(LandDesignation.class);

    static {
        BASE.put(LandDesignation.EKIN, Map.of(UseCase.EKINCILIK, 88, UseCase.BAGCILIQ, 78, UseCase.UZUMCULUK, 72, UseCase.ISTIXANA, 78,
                UseCase.HEYVANDARLIQ, 62, UseCase.ARICILIQ, 58, UseCase.GUNES, 12, UseCase.AQROTURIZM, 15, UseCase.SOYUDUCU, 30));
        BASE.put(LandDesignation.COXILLIK, Map.of(UseCase.EKINCILIK, 58, UseCase.BAGCILIQ, 88, UseCase.UZUMCULUK, 88, UseCase.ISTIXANA, 60,
                UseCase.HEYVANDARLIQ, 50, UseCase.ARICILIQ, 72, UseCase.GUNES, 10, UseCase.AQROTURIZM, 30, UseCase.SOYUDUCU, 32));
        BASE.put(LandDesignation.OTLAQ, Map.of(UseCase.EKINCILIK, 35, UseCase.BAGCILIQ, 40, UseCase.UZUMCULUK, 40, UseCase.ISTIXANA, 30,
                UseCase.HEYVANDARLIQ, 88, UseCase.ARICILIQ, 80, UseCase.GUNES, 55, UseCase.AQROTURIZM, 30, UseCase.SOYUDUCU, 18));
        BASE.put(LandDesignation.YARARSIZ, Map.of(UseCase.EKINCILIK, 10, UseCase.BAGCILIQ, 14, UseCase.UZUMCULUK, 14, UseCase.ISTIXANA, 48,
                UseCase.HEYVANDARLIQ, 52, UseCase.ARICILIQ, 45, UseCase.GUNES, 92, UseCase.AQROTURIZM, 20, UseCase.SOYUDUCU, 30));
        BASE.put(LandDesignation.SENAYE, Map.of(UseCase.EKINCILIK, 5, UseCase.BAGCILIQ, 5, UseCase.UZUMCULUK, 5, UseCase.ISTIXANA, 82,
                UseCase.HEYVANDARLIQ, 12, UseCase.ARICILIQ, 8, UseCase.GUNES, 62, UseCase.AQROTURIZM, 15, UseCase.SOYUDUCU, 94));
    }

    static int suitability(Plot p, UseCase u, List<Reason> r) {
        LandDesignation d = p.getDesignation();
        double s = BASE.get(d).get(u);
        double slope = p.getSlopePercent() == null ? 3 : p.getSlopePercent().doubleValue();
        double road = p.getRoadDistanceKm() == null ? 5 : p.getRoadDistanceKm().doubleValue();
        int alt = p.getAltitudeM() == null ? 500 : p.getAltitudeM();
        int soil = p.getSoilQuality() == null ? 60 : p.getSoilQuality();
        double area = p.getAreaHa().doubleValue();

        if (!permitted(d, u)) {
            r.add(new Reason(false, d == LandDesignation.EKIN && u == UseCase.GUNES
                    ? "Əkin yerində günəş stansiyası təyinat dəyişikliyi tələb edir və ərzaq təhlükəsizliyi baxımından tövsiyə olunmur"
                    : "Torpağın təyinatı (" + d.label().toLowerCase() + ") bu fəaliyyət üçün təyinat dəyişikliyi tələb edir"));
        } else if (d == LandDesignation.YARARSIZ && u == UseCase.GUNES) {
            r.add(new Reason(true, "Əkinə yararsız torpaq: günəş stansiyası ərzaq istehsalı ilə rəqabət aparmır"));
        } else if (d == LandDesignation.OTLAQ && u == UseCase.GUNES) {
            r.add(new Reason(true, "Aqrovoltaika: panellərin altında qoyun otarmaq mümkündür, otlaq itirilmir"));
        } else if (d == LandDesignation.SENAYE && u == UseCase.SOYUDUCU) {
            r.add(new Reason(true, "Sənaye təyinatlı torpaq: anbar və emal obyekti üçün icazə tələb olunmur"));
        }

        boolean needsWater = u == UseCase.BAGCILIQ || u == UseCase.ISTIXANA || u == UseCase.UZUMCULUK;
        if (needsWater) {
            if (p.isIrrigation()) { s += 6; r.add(new Reason(true, "Suvarma var: " + nz(p.getWaterSource(), "su mənbəyi mövcuddur"))); }
            else { s -= 25; r.add(new Reason(false, "Suvarma yoxdur: damcı suvarma və ya quyu üçün əlavə investisiya lazımdır")); }
        }
        if (u == UseCase.EKINCILIK || u == UseCase.BAGCILIQ || u == UseCase.UZUMCULUK) {
            s += (soil - 60) / 2.5;
            r.add(new Reason(soil >= 60, "Torpağın bonitet balı " + soil + "/100" + (soil >= 75 ? ": yüksək məhsuldarlıq" : soil < 45 ? ": zəif, məhsul az olacaq" : "")));
        }
        if (u == UseCase.GUNES || u == UseCase.ISTIXANA || u == UseCase.SOYUDUCU) {
            if (p.isElectricity()) { s += 4; r.add(new Reason(true, "Elektrik şəbəkəsinə qoşulma mümkündür")); }
            else { s -= 25; r.add(new Reason(false, "Elektrik xətti yoxdur: şəbəkəyə qoşulma xərci artır")); }
        }
        if (u == UseCase.GUNES || u == UseCase.ISTIXANA || u == UseCase.EKINCILIK || u == UseCase.SOYUDUCU) {
            if (slope > 8) { s -= 20; r.add(new Reason(false, "Maillik " + slope + "%: əlavə yerqazma işləri tələb olunur")); }
            else if (slope <= 3) { s += 4; if (u == UseCase.GUNES) r.add(new Reason(true, "Düzənlik (maillik " + slope + "%): panellərin quraşdırılması ucuzdur")); }
        }
        if (u == UseCase.GUNES) {
            if (area < 3) { s -= 30; r.add(new Reason(false, "Sahə kiçikdir: yalnız aktiv istehlakçı (150 kVt-a qədər) modeli mümkündür")); }
            else r.add(new Reason(true, "Sahə " + Math.round(area / ModelFactory.SOLAR_HA_PER_MWP) + " MWp-a qədər stansiyaya imkan verir"));
        }
        if (u == UseCase.AQROTURIZM || u == UseCase.SOYUDUCU) {
            if (road <= 2) { s += 6; r.add(new Reason(true, "Əsas yola " + road + " km: qonaq və yük üçün rahat giriş")); }
            else if (road > 5) { s -= 18; r.add(new Reason(false, "Əsas yoldan " + road + " km uzaqdır")); }
        }
        if (u == UseCase.AQROTURIZM) {
            if (p.isScenic()) { s += 45; r.add(new Reason(true, "Mənzərəli yer: göl, çay və ya dağ mənzərəsi turist cəlb edir")); }
            else r.add(new Reason(false, "Xüsusi mənzərə və ya turistik obyekt yaxınlığı qeyd olunmayıb"));
            if (alt > 1200) { s += 5; r.add(new Reason(true, "Dağ iqlimi: yay turizmi üçün sərin hava")); }
        }
        if (u == UseCase.ISTIXANA) {
            if (alt > 1400) { s -= 20; r.add(new Reason(false, "Hündürlük " + alt + " m: qışda istilik xərci yüksəkdir")); }
            if (p.isGas()) { s += 5; r.add(new Reason(true, "Təbii qaz xətti var: istilik xərci aşağıdır")); }
            if (d == LandDesignation.YARARSIZ || d == LandDesignation.SENAYE) r.add(new Reason(true, "Torpaqsız (hidroponik) istixana: torpağın keyfiyyəti rol oynamır"));
        }
        if (u == UseCase.SOYUDUCU && (p.getDistrict().name().equals("AGDAM") || p.getDistrict().name().equals("FUZULI"))) {
            s += 4;
            r.add(new Reason(true, "Ətrafda meyvəçilik və taxılçılıq sahələri genişlənir: xammal bazası var"));
        }
        if (u == UseCase.ARICILIQ && alt > 900) { s += 6; r.add(new Reason(true, "Dağ florası: yüksək keyfiyyətli bal")); }
        if (u == UseCase.HEYVANDARLIQ && (d == LandDesignation.OTLAQ || d == LandDesignation.YARARSIZ)) {
            r.add(new Reason(true, "Ekstensiv otarma üçün geniş sahə"));
        }
        return (int) Math.max(0, Math.min(100, Math.round(s)));
    }

    private static String nz(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }
}
