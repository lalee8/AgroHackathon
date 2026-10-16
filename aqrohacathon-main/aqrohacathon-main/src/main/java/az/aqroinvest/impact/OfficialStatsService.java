package az.aqroinvest.impact;

import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Keçirilmiş investisiya müsabiqələrinin rəsmi göstəriciləri və bazar həcmi (TAM / SAM / SOM).
 * Əsas rəqəmlər Kənd Təsərrüfatı Nazirliyinin hakatona təqdim etdiyi materialdandır (I-IV müsabiqə), bazar həcmi üçün
 * açıq rəsmi mənbələr istifadə olunur. Hər rəqəmin mənbəyi cavabda göstərilir. SOM portalın öz sahələrindən canlı hesablanır.
 */
@Service
@RequiredArgsConstructor
public class OfficialStatsService {
    private final PlotRepository plots;

    public record DistrictRow(String district, double leasedHa, double minInvestment) {}

    public record CompetitionRow(String no, String period, String districts, Double offeredHa, Integer applications, String result, String source) {}

    public record Fact(String key, String label, double value, String unit, String source, String url, String date) {}

    public record Market(String level, String title, String what, double ha, double perHa, double value, String formula, String source) {}

    public record Official(int competitions, int districts, double offeredHa, int participants, int winners, int contracts,
                           double leasedHa, double minInvestmentTotal, double avgInvestmentPerHa, double winRate,
                           List<DistrictRow> byDistrict, List<CompetitionRow> timeline, List<Fact> context, List<Market> market,
                           double portalHa, int portalPlots, String note) {}

    private static final String MINISTRY = "Kənd Təsərrüfatı Nazirliyi, hakaton üçün təqdim olunmuş material (I-IV müsabiqə), 2026";

    /** İcarəyə verilmiş sahə və qoyulması nəzərdə tutulan minimum investisiya, rayonlar üzrə (Nazirliyin materialı). */
    static final List<DistrictRow> DISTRICTS = List.of(
            new DistrictRow("Xocavənd", 3936, 80_158_892.3),
            new DistrictRow("Qubadlı", 4881, 69_574_110),
            new DistrictRow("Xocalı", 1739.5, 20_635_402),
            new DistrictRow("Laçın", 349.35, 11_160_171),
            new DistrictRow("Ağdam", 498.84, 10_462_103),
            new DistrictRow("Ağdərə", 1539.29, 8_813_285),
            new DistrictRow("Füzuli", 1593, 5_551_828),
            new DistrictRow("Zəngilan", 116.44, 4_165_417),
            new DistrictRow("Cəbrayıl", 600, 700_900));

    static final List<CompetitionRow> TIMELINE = List.of(
            new CompetitionRow("I", "1 sentyabr - 15 oktyabr 2025", "Xocalı, Xocavənd, Laçın", 6876.0, 68,
                    "43-45 sahibkar, təxminən 69-70 mln ₼ investisiya öhdəliyi", "Nazirliyin açıqlamaları (redaktor.az, bakivaxti.az, AZƏRTAC)"),
            new CompetitionRow("II", "15 dekabr 2025 - fevral 2026", "Xocavənd, Xocalı, Zəngilan və digər rayonlar", 13000.0, null,
                    "100 mln ₼-dan çox investisiya öhdəliyi", "Nazir, Xalq qəzeti, 17.06.2026"),
            new CompetitionRow("III", "30 aprel - 15 iyun 2026", "9 rayon", 21039.0, 193,
                    "Qaliblər 14 iyul 2026-da komissiya tərəfindən təsdiqlənib", "FED.az, baku.ws"),
            new CompetitionRow("IV", "26 may - 10 iyul 2026", "9 rayon", null, null,
                    "Müqavilələrin bağlanması davam edir", "FED.az, Nazirliyin materialı"));

    static final List<Fact> CONTEXT = List.of(
            new Fact("arable", "Azad edilmiş ərazilərdə əkinə yararlı torpaq", 160_000, "ha", "Kənd təsərrüfatı naziri, peyk əsaslı qiymətləndirmə",
                    "https://banker.az/qarabagda-sakinl%C9%99r%C9%99-pay-torpaqlarinin-n%C9%99-vaxt-veril%C9%99c%C9%99yi-aciqlanib/", "2023-12-12"),
            new Fact("mapped", "Müəyyən edilib xəritələşdirilmiş əkin sahəsi", 128_800, "ha", "Böyük Qayıdış I Dövlət Proqramı, 5.1.4",
                    "https://azertag.az/store/files/2022/noyabr/DP_boyuk_qayidish.pdf", "2022-11"),
            new Fact("demined", "Minalardan təmizlənmiş ərazi (2020-dən)", 305_383.7, "ha", "ANAMA (APA araşdırması)",
                    "https://en.apa.az/incident/the-war-beneath-the-ground-why-does-azerbaijans-mine-problem-still-persist-research-527065", "2026-09-29"),
            new Fact("contaminated", "Mina ilə çirklənmiş ərazi (təxmini)", 1_167_000, "ha", "ANAMA", null, "2026"),
            new Fact("grain", "2025/2026 təsərrüfat ilində taxıl əkini", 49_900, "ha", "Kənd təsərrüfatı naziri, Zəngilan müşavirəsi",
                    "https://modern.az/az/iqtisadiyyat/635995/azad-edilmis-erazilerde-499-min-hektarda-taxil-ekilib/", "2026-06-12"));

    @Transactional(readOnly = true)
    public Official official() {
        double leased = DISTRICTS.stream().mapToDouble(DistrictRow::leasedHa).sum();
        double minInv = DISTRICTS.stream().mapToDouble(DistrictRow::minInvestment).sum();
        double perHa = minInv / leased;
        int participants = 521, winners = 212;
        double winRate = (double) winners / participants;
        double offered = 42_905;
        List<Plot> all = plots.findAll();
        double portalHa = all.stream().map(Plot::getAreaHa).filter(java.util.Objects::nonNull).mapToDouble(BigDecimal::doubleValue).sum();
        List<Market> market = List.of(
                new Market("TAM", "Ümumi bazar", "Azad edilmiş ərazilərdə bütün əkinə yararlı torpaq", 160_000, perHa, 160_000 * perHa,
                        "160 000 ha × orta investisiya/ha", "Nazir (2023) + Nazirliyin müsabiqə göstəriciləri"),
                new Market("SAM", "Xidmət edilə bilən bazar", "Dövlətin investisiya müsabiqələrinə çıxardığı torpaq (I-IV)", offered, perHa, offered * perHa,
                        "42 905 ha × orta investisiya/ha", MINISTRY),
                new Market("SOM", "Real əldə ediləcək pay", "Portaldakı sahələr × qalib olma ehtimalı", portalHa, perHa, portalHa * perHa * winRate,
                        String.format(java.util.Locale.ROOT, "%.0f ha × orta investisiya/ha × %.1f%% (212 qalib / 521 iştirakçı)", portalHa, winRate * 100),
                        "Portalın öz sahələri, Nazirliyin müsabiqə göstəriciləri"));
        return new Official(4, 9, offered, participants, winners, 141, round2(leased), round2(minInv), Math.round(perHa), round4(winRate),
                DISTRICTS, TIMELINE, CONTEXT, market, round2(portalHa), all.size(),
                "Orta investisiya/ha = rayonlar üzrə minimum investisiya öhdəliklərinin cəmi (" + Math.round(minInv / 1_000_000) + " mln ₼) / icarəyə verilmiş sahə ("
                        + Math.round(leased) + " ha). Mənbə: " + MINISTRY + ".");
    }

    private static double round2(double v) { return Math.round(v * 100) / 100.0; }

    private static double round4(double v) { return Math.round(v * 10000) / 10000.0; }
}
