package az.aqroinvest.economics;

import az.aqroinvest.economics.SimulationSpec.Assumption;
import az.aqroinvest.economics.SimulationSpec.Shock;
import az.aqroinvest.plot.LandDesignation;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sahə və istifadə ssenarisi üçün Monte-Carlo modelinin qurulması. Bütün istinad göstəriciləri və onların mənbəyi
 * fərziyyələr siyahısında istifadəçiyə açıq göstərilir. Hər parametr simulyatorda dəyişdirilə bilər.
 */
@Component
@RequiredArgsConstructor
public class ModelFactory {
    /** Günəş və digər bərpa olunan mənbələrdən elektrik enerjisinin topdansatış tarifi, AZN/MWh (8.5 qəpik/kVts). */
    public static final double SOLAR_TARIFF = 85;
    public static final String TARIFF_SOURCE = "Tarif Şurasının 29.12.2024 tarixli 19 nömrəli qərarı: günəş enerjisi üzrə topdansatış tarifi 8.5 qəpik/kVts";
    public static final double SOLAR_HA_PER_MWP = 1.5;
    static final double DISCOUNT = 0.12;
    /** Kənd təsərrüfatında kapitalın dəyəri: sahibkarın kapitalı ~14%, güzəştli aqrar kreditlər ~5-6% nəzərə alınmaqla. */
    static final double AGRO_DISCOUNT = 0.10;
    static final String REF = "Portalın istinad göstəricisi (təxmini, pilotda Nazirliyin rəsmi məlumatı ilə əvəz olunacaq)";

    private final SolarYieldService solar;

    /** İstifadəçinin simulyatorda dəyişdirdiyi parametrlər (null olanlar standart qalır). */
    public record Overrides(Double scale, Double capex, Double price, Double yieldPerUnit, Double offtakeShare,
                            Double offtakePriceFactor, Integer horizon) {
        public static Overrides none() {
            return new Overrides(null, null, null, null, null, null, null);
        }
    }

    /** Sahədə bu ssenari üçün mümkün maksimum miqyas. */
    public double maxScale(Plot p, UseCase u) {
        double area = p.getAreaHa().doubleValue();
        return switch (u) {
            case GUNES -> Math.floor(area / SOLAR_HA_PER_MWP * 10) / 10;
            case AQROTURIZM -> Math.max(4, Math.min(60, Math.round(area * 1.2)));
            case SOYUDUCU -> Math.max(500, Math.min(20000, Math.round(area * 600 / 100.0) * 100));
            case ISTIXANA -> Math.round(area * 0.6 * 10) / 10.0;
            default -> area;
        };
    }

    public double defaultScale(Plot p, UseCase u) {
        double area = p.getAreaHa().doubleValue();
        return switch (u) {
            case GUNES -> Math.max(0.1, Math.min(25, maxScale(p, u)));
            case AQROTURIZM -> Math.max(6, Math.min(24, Math.round(area / 1.5)));
            case SOYUDUCU -> Math.max(500, Math.min(5000, Math.round(area * 250 / 50.0) * 50));
            case ISTIXANA -> Math.min(10, Math.round(area * 0.15 * 10) / 10.0);
            case ARICILIQ -> Math.min(30, area);
            default -> area;
        };
    }

    public SimulationSpec build(Plot p, UseCase u, Overrides o) {
        if (o == null) o = Overrides.none();
        double scale = o.scale() != null && o.scale() > 0 ? Math.min(o.scale(), maxScale(p, u) * 1.0001) : defaultScale(p, u);
        SimulationSpec s = switch (u) {
            case GUNES -> solar(p, scale);
            case AQROTURIZM -> tourism(p, scale);
            case SOYUDUCU -> storage(p, scale);
            default -> agro(p, u, scale);
        };
        double capex = o.capex() != null && o.capex() > 0 ? o.capex() : s.capex();
        double price = o.price() != null && o.price() > 0 ? o.price() : s.price();
        double yield = o.yieldPerUnit() != null && o.yieldPerUnit() > 0 ? o.yieldPerUnit() : s.yieldPerUnit();
        double share = o.offtakeShare() != null ? clamp(o.offtakeShare(), 0, 1) : 0;
        double factor = o.offtakePriceFactor() != null ? clamp(o.offtakePriceFactor(), 0.5, 1.5) : 0.95;
        int horizon = o.horizon() != null ? (int) clamp(o.horizon(), 5, 30) : s.horizon();
        double opexFixed = s.opexFixed();
        if (u == UseCase.GUNES && o.capex() != null && o.capex() > 0) opexFixed += 0.015 * (capex - s.capex());
        return new SimulationSpec(u, s.unit(), scale, capex, yield, s.yieldUnit(), price, s.priceUnit(), opexFixed,
                s.opexPerUnit(), s.opexRevenueShare(), s.ramp(), s.yieldSigma(), s.priceSigma(), s.priceDrift(), s.shocks(),
                s.degradation(), horizon, s.discountRate(), share, factor, s.capexOverrunMean(), s.capexOverrunSigma(), s.jobs(),
                s.assumptions());
    }

    // ------------------------------------------------------------------ kənd təsərrüfatı

    private SimulationSpec agro(Plot p, UseCase u, double scale) {
        PlotCategory c = u.category();
        double soil = p.getSoilQuality() == null ? 60 : p.getSoilQuality();
        boolean irr = p.isIrrigation();
        boolean hydro = c == PlotCategory.ISTIXANA && (p.getDesignation() == LandDesignation.YARARSIZ || p.getDesignation() == LandDesignation.SENAYE);
        double factor;
        if (hydro) factor = 0.95;
        // Bal suvarmadan yox, bitki örtüyündən asılıdır; otlaq heyvandarlığı torpağın keyfiyyətinə daha az həssasdır
        else if (c == PlotCategory.ARICILIQ) factor = 0.9 + 0.1 * soil / 100.0;
        else if (c == PlotCategory.HEYVANDARLIQ) factor = (0.75 + 0.25 * soil / 100.0) * (irr ? 1.05 : 0.92);
        else factor = (0.7 + 0.3 * soil / 100.0) * (irr ? 1.1 : (c == PlotCategory.ISTIXANA ? 0.85 : 0.8));
        if (p.getDesignation() == LandDesignation.YARARSIZ && !hydro) factor *= c == PlotCategory.HEYVANDARLIQ || c == PlotCategory.ARICILIQ ? 0.75 : 0.55;
        double yield = c.yieldTonPerHa() * factor;
        double rent = p.getAnnualRentPerHa().doubleValue() * p.getAreaHa().doubleValue();
        int start = c.yearsToFirstHarvest();
        double[] ramp = new double[start + 3];
        for (int i = 0; i < ramp.length; i++) ramp[i] = i < start ? 0 : i == start ? 0.5 : i == start + 1 ? 0.8 : 1;
        List<Shock> shocks = new ArrayList<>();
        double ys, ps, drift;
        switch (c) {
            case EKINCILIK -> { ys = irr ? 0.12 : 0.2; ps = 0.14; drift = 0.03; shocks.add(new Shock("Quraqlıq", irr ? 0.04 : 0.12, 0.4)); }
            case HEYVANDARLIQ -> { ys = 0.08; ps = 0.08; drift = 0.035; shocks.add(new Shock("Xəstəlik (şap, brusellyoz)", 0.05, 0.3)); shocks.add(new Shock("Yem qıtlığı", irr ? 0.04 : 0.09, 0.15)); }
            case BAGCILIQ -> { ys = 0.14; ps = 0.16; drift = 0.03; shocks.add(new Shock("Yaz şaxtası", 0.1, 0.45)); shocks.add(new Shock("Dolu", 0.06, 0.3)); }
            case UZUMCULUK -> { ys = 0.13; ps = 0.12; drift = 0.03; shocks.add(new Shock("Dolu", 0.08, 0.35)); shocks.add(new Shock("Şaxta", 0.05, 0.4)); }
            case ISTIXANA -> { ys = 0.08; ps = 0.2; drift = 0.03; shocks.add(new Shock("Xəstəlik və zərərvericilər", 0.07, 0.25)); }
            default -> { ys = 0.25; ps = 0.1; drift = 0.03; shocks.add(new Shock("Zəif bal mövsümü", 0.15, 0.5)); }
        }
        int jobs = (int) Math.max(p.getMinJobs() > 0 && scale >= p.getAreaHa().doubleValue() * 0.9 ? p.getMinJobs() : 1, Math.round(scale * jobsPerHa(c)));
        List<Assumption> a = new ArrayList<>();
        a.add(new Assumption("Məhsuldarlıq", fmt1(yield) + " t/ha (orta " + fmt1(c.yieldTonPerHa()) + " t/ha × torpaq və suvarma əmsalı " + fmt2(factor) + ")", REF));
        a.add(new Assumption("Satış qiyməti", Math.round(c.pricePerTon()) + " ₼/t (" + c.product() + ")", REF));
        a.add(new Assumption("İlkin investisiya", Math.round(c.capexPerHa()) + " ₼/ha", REF));
        a.add(new Assumption("İllik xərc", Math.round(c.opexPerHa()) + " ₼/ha + icarə " + Math.round(rent) + " ₼/il", "Sahənin icarə şərtləri və " + REF.toLowerCase(Locale.ROOT)));
        a.add(new Assumption("Bar verməyə qədər", start == 0 ? "ilk ildən məhsul" : start + " il gəlirsiz dövr", REF));
        a.add(new Assumption("Risklər", shocksText(shocks) + "; qiymət dəyişkənliyi ±" + Math.round(ps * 100) + "%/il", "Regionun iqlim və bazar xüsusiyyətlərinə əsaslanan ekspert qiymətləndirməsi"));
        a.add(new Assumption("Qiymət və xərc artımı", "məhsul qiyməti ildə " + fmt1(drift * 100) + "%, xərclər ildə ~4% (inflyasiya)", "Mərkəzi Bankın inflyasiya hədəfi diapazonu əsasında"));
        a.add(new Assumption("Kapitalın dəyəri", "10% (sahibkar kapitalı və güzəştli aqrar kreditlərin qarışığı)", "Ekspert qiymətləndirməsi"));
        return new SimulationSpec(u, "ha", scale, c.capexPerHa() * scale, yield, "t/ha", c.pricePerTon(), "₼/t",
                rent, c.opexPerHa(), 0, ramp, ys, ps, drift, shocks, 0, u.horizon(), AGRO_DISCOUNT, 0, 0.95, 0.03, 0.06, jobs, a);
    }

    // ------------------------------------------------------------------ günəş enerjisi

    private SimulationSpec solar(Plot p, double mwp) {
        SolarYieldService.SolarYield y = solar.yield(p.getLatitude(), p.getLongitude());
        double mwhPerMwp = y.kwhPerKwp();
        double capexPerMwp = mwp >= 5 ? 1_200_000 : 1_450_000;
        double capex = capexPerMwp * mwp;
        double usedHa = Math.min(p.getAreaHa().doubleValue(), mwp * SOLAR_HA_PER_MWP);
        double rent = p.getAnnualRentPerHa().doubleValue() * usedHa;
        List<Shock> shocks = List.of(new Shock("Şəbəkə məhdudiyyəti (curtailment)", 0.05, 0.08), new Shock("Avadanlıq nasazlığı", 0.03, 0.05));
        List<Assumption> a = new ArrayList<>();
        a.add(new Assumption("İstehsal", Math.round(mwhPerMwp) + " MWh/MWp/il, illər arası dəyişkənlik ±" + Math.round(y.sdKwh()) + " kWh/kWp, optimal bucaq " + Math.round(y.optimalTilt()) + "°", y.source()));
        a.add(new Assumption("Satış tarifi", "8.5 qəpik/kVts (85 ₼/MWh)", TARIFF_SOURCE));
        a.add(new Assumption("İnvestisiya", String.format(Locale.ROOT, "%.2f mln ₼/MWp", capexPerMwp / 1e6),
                "Şəfəq GES (Cəbrayıl, 240 MW, bp və SOCAR Green) layihəsinin açıqlanmış dəyəri ~200 mln $ əsasında. Kiçik stansiyalar üçün yüksək götürülüb"));
        a.add(new Assumption("Torpaq tələbi", SOLAR_HA_PER_MWP + " ha/MWp (stasionar panellər)", "Beynəlxalq təcrübə üzrə orta göstərici"));
        a.add(new Assumption("İstismar xərci", "investisiyanın 1.5%-i/il + icarə " + Math.round(rent) + " ₼/il", "Beynəlxalq təcrübə üzrə orta göstərici"));
        a.add(new Assumption("Panellərin köhnəlməsi", "ildə 0.5% istehsal azalması", "Panel istehsalçılarının zəmanət şərtləri"));
        a.add(new Assumption("Kapitalın dəyəri", "9% (uzunmüddətli, tənzimlənən tarifli infrastruktur), kənd təsərrüfatında 10% (güzəştli aqrar kreditlər nəzərə alınmaqla), turizm və emalda 12%", "Ekspert qiymətləndirməsi"));
        a.add(new Assumption("Aktiv istehlakçı", "150 kVt-a qədər qurğusu olan fiziki şəxs il sonunda şəbəkəyə verdiyi artıq enerjiyə görə ödəniş alır", "Azərbaycanın aktiv istehlakçı qaydaları"));
        return new SimulationSpec(UseCase.GUNES, "MWp", mwp, capex, mwhPerMwp, "MWh/MWp", SOLAR_TARIFF, "₼/MWh",
                0.015 * capex + rent, 0, 0, new double[]{1}, Math.max(0.02, y.sdKwh() / y.kwhPerKwp()), 0.03, 0,
                shocks, 0.005, UseCase.GUNES.horizon(), 0.09, 0, 1, 0.02, 0.05, (int) Math.max(2, Math.round(mwp * 0.4)), a);
    }

    // ------------------------------------------------------------------ aqroturizm

    private SimulationSpec tourism(Plot p, double rooms) {
        double occupancy = 0.45, adr = 110, fb = 1.35;
        double nightsPerRoom = 365 * occupancy;
        double capex = rooms * 65_000 + 180_000;
        double rent = p.getAnnualRentPerHa().doubleValue() * Math.min(p.getAreaHa().doubleValue(), Math.max(2, rooms * 0.4));
        List<Shock> shocks = List.of(new Shock("Zəif turizm mövsümü", 0.1, 0.3), new Shock("Giriş yolunda təmir", 0.04, 0.25));
        List<Assumption> a = new ArrayList<>();
        a.add(new Assumption("Otaq sayı", Math.round(rooms) + " otaq", "Sahənin ölçüsünə görə"));
        a.add(new Assumption("Doluluq", Math.round(occupancy * 100) + "% (ildə " + Math.round(nightsPerRoom) + " gecə/otaq)", REF));
        a.add(new Assumption("Orta qiymət", Math.round(adr) + " ₼/gecə + yemək və turlar +" + Math.round((fb - 1) * 100) + "%", REF));
        a.add(new Assumption("İnvestisiya", "65 000 ₼/otaq + 180 000 ₼ ümumi obyektlər", REF));
        a.add(new Assumption("Xərclər", "gəlirin 42%-i + 45 000 ₼/il sabit + icarə", REF));
        a.add(new Assumption("Bazar", "Qarabağa turist axını artır: illik qiymət artımı 3% götürülüb", "Ekspert qiymətləndirməsi"));
        return new SimulationSpec(UseCase.AQROTURIZM, "otaq", rooms, capex, nightsPerRoom, "gecə/otaq", adr * fb, "₼/gecə",
                45_000 + rent, 0, 0.42, new double[]{0.55, 0.8, 1}, 0.15, 0.08, 0.03, shocks, 0, UseCase.AQROTURIZM.horizon(),
                DISCOUNT, 0, 0.95, 0.08, 0.1, (int) Math.round(rooms * 0.8 + 3), a);
    }

    // ------------------------------------------------------------------ soyuducu anbar

    private SimulationSpec storage(Plot p, double tons) {
        double throughput = 1.6 * 0.8;
        double capex = tons * 420 + 250_000;
        double rent = p.getAnnualRentPerHa().doubleValue() * Math.min(p.getAreaHa().doubleValue(), Math.max(1, tons / 1000.0));
        List<Shock> shocks = List.of(new Shock("Regionda məhsul qıtlığı", 0.08, 0.35), new Shock("Uzunmüddətli enerji kəsintisi", 0.03, 0.1));
        List<Assumption> a = new ArrayList<>();
        a.add(new Assumption("Tutum", Math.round(tons) + " t", "Sahənin ölçüsünə görə"));
        a.add(new Assumption("Dövriyyə", "ildə 1.6 dövr, 80% yüklənmə (" + Math.round(tons * throughput) + " t/il)", REF));
        a.add(new Assumption("Gəlir", "150 ₼/t: saxlama, çeşidləmə, qablaşdırma və mövsümi qiymət fərqi", REF));
        a.add(new Assumption("İnvestisiya", "420 ₼/t tutum + 250 000 ₼ avadanlıq", REF));
        a.add(new Assumption("Xərclər", "22 ₼/t tutum (enerji) + 70 000 ₼/il sabit + icarə", REF));
        return new SimulationSpec(UseCase.SOYUDUCU, "t", tons, capex, throughput, "t/t", 150, "₼/t",
                70_000 + rent, 22, 0, new double[]{0.6, 0.85, 1}, 0.12, 0.15, 0.02, shocks, 0, UseCase.SOYUDUCU.horizon(),
                DISCOUNT, 0, 0.95, 0.05, 0.08, (int) Math.round(8 + tons / 400), a);
    }

    private static double jobsPerHa(PlotCategory c) {
        return switch (c) {
            case EKINCILIK -> 0.05;
            case HEYVANDARLIQ -> 0.15;
            case BAGCILIQ -> 0.35;
            case UZUMCULUK -> 0.3;
            case ISTIXANA -> 6;
            case ARICILIQ -> 0.1;
        };
    }

    private static String shocksText(List<Shock> s) {
        return String.join(", ", s.stream().map(x -> x.name().toLowerCase(Locale.ROOT) + " " + Math.round(x.probability() * 100)
                + "% ehtimal, -" + Math.round(x.impact() * 100) + "% məhsul").toList());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static String fmt1(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    static String fmt2(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}
