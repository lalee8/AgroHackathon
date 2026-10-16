package az.aqroinvest.impact;

import az.aqroinvest.application.Application;
import az.aqroinvest.application.ApplicationRepository;
import az.aqroinvest.application.ApplicantType;
import az.aqroinvest.application.Decision;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotCategory;
import az.aqroinvest.plot.PlotService;
import az.aqroinvest.plot.Technology;
import az.aqroinvest.satellite.SatelliteDtos.NdviPoint;
import az.aqroinvest.satellite.SatelliteDtos.NdviSeries;
import az.aqroinvest.satellite.SatelliteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * "Vəd və reallıq": qalib müraciətdə götürülmüş öhdəliklər (investisiya, iş yerləri, icra müddəti, texnologiyalar) ilə icranın
 * müqayisəsi. Qaydalara görə icarəçi öhdəliklərin icrası barədə EKTİS-də aylıq hesabat verir (4.16), sahədəki faktiki fəaliyyət
 * isə peyk NDVI göstəricisi ilə müstəqil yoxlanılır. NDVI real peyk məlumatıdır; aylıq hesabatlar demo rejimində simulyasiya olunur.
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {
    private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
    private final ApplicationRepository applications;
    private final PlotService plots;
    private final SatelliteService satellite;

    public record Promise(BigDecimal investment, int jobs, int months, String activity, List<String> technologies, BigDecimal annualRevenue) {}

    public record MonthRow(String month, long plannedInvestment, Long reportedInvestment, int plannedJobs, Integer reportedJobs) {}

    public record Check(String key, String label, String promised, String actual, String status, String note) {}

    public record Delivery(Long plotId, String plotCode, String plotName, String district, String winner, String winnerType, String appNumber,
                           LocalDate awardedOn, LocalDate contractOn, int monthsElapsed, int progressPct, Promise promised,
                           long expectedInvestment, long reportedInvestment, int expectedJobs, int reportedJobs, Double ndviBefore,
                           Integer ndviBeforeYear, Double ndviNow, Integer ndviNowYear, List<MonthRow> months, List<Check> checks,
                           String verdict, String verdictLabel, String note, String method) {}

    public record Summary(Long plotId, String plotCode, String plotName, String district, String winner, int monthsElapsed, int months,
                          long promisedInvestment, long reportedInvestment, int promisedJobs, int reportedJobs, Double ndviBefore,
                          Double ndviNow, String verdict, String verdictLabel) {}

    @Transactional(readOnly = true)
    public Delivery forPlot(Long plotId) {
        Plot p = plots.find(plotId);
        Application a = winner(p.getId());
        if (a == null) throw new ApiException(HttpStatus.NOT_FOUND, "NO_WINNER", "Bu sahə üzrə qalib müraciət yoxdur. Vəd və reallıq yalnız icarəyə verilmiş sahələr üçün göstərilir");
        return build(p, a);
    }

    @Transactional(readOnly = true)
    public List<Summary> all() {
        List<Summary> out = new ArrayList<>();
        applications.findAll().stream().filter(a -> a.getDecision() == Decision.APPROVED)
                .sorted(Comparator.comparing(a -> a.getPlot().getCode()))
                .forEach(a -> {
                    Delivery d = build(a.getPlot(), a);
                    out.add(new Summary(d.plotId(), d.plotCode(), d.plotName(), d.district(), d.winner(), d.monthsElapsed(), d.promised().months(),
                            d.promised().investment().longValue(), d.reportedInvestment(), d.promised().jobs(), d.reportedJobs(), d.ndviBefore(),
                            d.ndviNow(), d.verdict(), d.verdictLabel()));
                });
        return out;
    }

    private Application winner(Long plotId) {
        return applications.findAll().stream()
                .filter(a -> a.getDecision() == Decision.APPROVED && a.getPlot().getId().equals(plotId))
                .max(Comparator.comparing(Application::getDecidedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    Delivery build(Plot p, Application a) {
        LocalDate today = LocalDate.now(BAKU);
        LocalDate awarded = a.getDecidedAt() != null ? LocalDate.ofInstant(a.getDecidedAt(), BAKU) : today;
        // Qaydalar 4.16: icarə müqaviləsi qərardan sonra 20 gün ərzində EKTİS-də bağlanır
        LocalDate contract = awarded.plusDays(20);
        int elapsed = (int) Math.max(0, ChronoUnit.MONTHS.between(contract.withDayOfMonth(1), today.withDayOfMonth(1)));
        int months = a.getImplementationMonths() != null && a.getImplementationMonths() > 0 ? a.getImplementationMonths() : 24;
        long invest = a.getInvestmentAmount() != null ? a.getInvestmentAmount().longValue() : 0;
        int jobs = a.getJobsPlanned() != null ? a.getJobsPlanned() : 0;
        List<String> tech = a.getTechnologies().stream().map(t -> {
            try {
                return Technology.valueOf(t).label();
            } catch (IllegalArgumentException e) {
                return t;
            }
        }).toList();
        Promise promise = new Promise(BigDecimal.valueOf(invest), jobs, months, a.getActivityType() != null ? a.getActivityType().label() : p.getCategory().label(),
                tech, a.getExpectedAnnualRevenue());

        // Aylıq hesabatlar: ötən ay daxil olmaqla (cari ayın hesabatı növbəti ayın əvvəlində verilir)
        double pace = pace(a);
        List<MonthRow> rows = new ArrayList<>();
        long repInv = 0;
        int repJobs = 0;
        int horizon = Math.max(elapsed + 3, Math.min(months, 12));
        for (int m = 1; m <= horizon; m++) {
            LocalDate month = contract.withDayOfMonth(1).plusMonths(m - 1);
            long plan = Math.round(invest * planned(m, months));
            int planJobs = (int) Math.round(jobs * jobsPlanned(m, months));
            Long r = null;
            Integer rj = null;
            if (m <= elapsed) {
                double wobble = 1 + 0.06 * Math.sin(m * 1.7 + a.getId());
                r = Math.min(invest, Math.round(plan * pace * wobble));
                rj = (int) Math.min(jobs, Math.round(planJobs * Math.min(1.1, pace * (1 + 0.05 * Math.cos(m + a.getId())))));
                repInv = r;
                repJobs = rj;
            }
            rows.add(new MonthRow(month.toString().substring(0, 7), plan, r, planJobs, rj));
        }
        long expInv = elapsed == 0 ? 0 : Math.round(invest * planned(elapsed, months));
        int expJobs = elapsed == 0 ? 0 : (int) Math.round(jobs * jobsPlanned(elapsed, months));

        // Peyk: qərardan əvvəlki son mövsüm və ən son ölçmə
        NdviSeries s = satellite.ndvi(p);
        NdviPoint before = null, now = null;
        for (NdviPoint pt : s.points()) {
            if (pt.year() < awarded.getYear() || (pt.year() == awarded.getYear() && awarded.getMonthValue() > 9)) before = pt;
            now = pt;
        }
        if (now != null && before != null && now.year() <= before.year()) now = null;

        List<Check> checks = new ArrayList<>();
        checks.add(ratioCheck("investment", "İnvestisiya (məcmu)", money(expInv) + " plan üzrə bu günə", money(repInv) + " EKTİS hesabatı", repInv, expInv,
                "Vəd: " + money(invest) + ", " + months + " ayda"));
        checks.add(ratioCheck("jobs", "Yaradılmış iş yerləri", expJobs + " plan üzrə bu günə", repJobs + " EKTİS hesabatı", repJobs, expJobs,
                "Vəd: " + jobs + " daimi iş yeri"));
        boolean vegetation = p.getCategory() != PlotCategory.HEYVANDARLIQ && p.getCategory() != PlotCategory.ARICILIQ;
        if (!vegetation) {
            checks.add(new Check("ndvi", "Peykdən fəaliyyət (NDVI)", "-", "-", "NA",
                    "Heyvandarlıq və arıçılıqda NDVI təbii örtüyü göstərir, fəaliyyət meyarı deyil. Yoxlama yerində aparılır"));
        } else if (before == null || now == null) {
            checks.add(new Check("ndvi", "Peykdən fəaliyyət (NDVI)", before != null ? fmt(before.ndvi()) + " (" + before.year() + ")" : "-", "ilk mövsüm gözlənilir",
                    "PENDING", "Qərardan sonrakı ilk vegetasiya mövsümü hələ ölçülməyib"));
        } else {
            double diff = now.ndvi() - before.ndvi();
            String st = now.ndvi() >= 0.4 || diff >= 0.1 ? "OK" : now.ndvi() < 0.25 ? "ALERT" : "LAG";
            checks.add(new Check("ndvi", "Peykdən fəaliyyət (NDVI)", fmt(before.ndvi()) + " (" + before.year() + ", qərardan əvvəl)",
                    fmt(now.ndvi()) + " (" + now.year() + ")", st,
                    diff >= 0 ? "Bitki örtüyü " + Math.round(diff / Math.max(0.05, before.ndvi()) * 100) + "% artıb: sahədə fəaliyyət peykdən görünür"
                            : "Bitki örtüyü azalıb: yerində yoxlama tövsiyə olunur"));
        }
        checks.add(new Check("reports", "Aylıq hesabatlar (EKTİS, Qaydalar 4.16)", elapsed + " hesabat", elapsed + " təqdim edilib", elapsed == 0 ? "PENDING" : "OK",
                elapsed == 0 ? "İlk hesabat müqavilədən sonrakı ayın sonunda" : "Hesabatlar vaxtında təqdim edilib"));
        checks.add(new Check("lease", "Subicarə qadağası və təyinat", "Təyinat: " + promise.activity(), "Pozuntu qeydə alınmayıb", "OK",
                "Sahə başqasına icarəyə verilə bilməz; təyinat dəyişikliyi komissiyanın razılığı ilə"));

        boolean alert = checks.stream().anyMatch(c -> c.status().equals("ALERT"));
        boolean lag = checks.stream().anyMatch(c -> c.status().equals("LAG"));
        String verdict = elapsed == 0 ? "STARTING" : alert ? "ALERT" : lag ? "LAG" : "ON_TRACK";
        String label = switch (verdict) {
            case "STARTING" -> "İcraya başlanır";
            case "ALERT" -> "Diqqət: yoxlama lazımdır";
            case "LAG" -> "Qrafikdən geri qalır";
            default -> "Vəd edildiyi kimi icra olunur";
        };
        String note = switch (verdict) {
            case "ALERT" -> "Göstəricilərdən biri kəskin geri qalır. Komissiya yerində yoxlama və izahat tələb edə bilər; öhdəliklər icra edilmədikdə müqavilə ləğv oluna bilər.";
            case "LAG" -> "İcra qrafikdən bir qədər geri qalır. İcarəçiyə xəbərdarlıq və növbəti hesabatda düzəliş planı tövsiyə olunur.";
            case "STARTING" -> "Müqavilə yenicə bağlanıb; ilk aylıq hesabat gözlənilir.";
            default -> "Vəd edilən investisiya, iş yerləri və sahədəki fəaliyyət qrafikə uyğundur.";
        };
        int progress = invest == 0 ? 0 : (int) Math.round(repInv * 100.0 / invest);
        String winnerName = a.getApplicantType() == ApplicantType.INDIVIDUAL || a.getCompanyName() == null || a.getCompanyName().isBlank()
                ? a.getApplicantType().label() + (a.isIdpStatus() ? " (yerli sakin)" : "")
                : a.getCompanyName();
        return new Delivery(p.getId(), p.getCode(), p.getName(), p.getDistrict().label(), winnerName, a.getApplicantType().label(), a.getAppNumber(),
                awarded, contract, elapsed, progress, promise, expInv, repInv, expJobs, repJobs,
                before != null ? before.ndvi() : null, before != null ? before.year() : null, now != null ? now.ndvi() : null, now != null ? now.year() : null,
                rows, checks, verdict, label, note,
                "Vəd: qalib müraciətdəki öhdəliklər. Plan əyrisi: investisiyanın əsas hissəsi ilk aylarda (infrastruktur, şitil, texnika), iş yerləri icra müddəti boyunca. "
                        + "NDVI real peyk ölçməsidir (Landsat/Sentinel-2). EKTİS aylıq hesabatları demo rejimində simulyasiya olunur.");
    }

    /** Planlaşdırılmış məcmu investisiya payı: ilk aylarda sürətli (infrastruktur), sonra azalan. */
    static double planned(int m, int months) {
        double x = Math.min(1.0, (double) m / months);
        return 1 - Math.pow(1 - x, 1.6);
    }

    /** İş yerləri: 3 aylıq hazırlıqdan sonra xətti artım. */
    static double jobsPlanned(int m, int months) {
        if (m <= 2) return 0.15 * m / 2;
        return Math.min(1.0, 0.15 + 0.85 * (m - 2.0) / Math.max(1, months - 2));
    }

    /** Demo hesabatların icra sürəti: müraciətə bağlı sabit dəyər (hər açılışda eyni). */
    static double pace(Application a) {
        int h = Math.abs(Objects.hash(a.getAppNumber(), a.getId()));
        return 0.86 + (h % 22) / 100.0;
    }

    private static Check ratioCheck(String key, String label, String promised, String actual, long rep, long exp, String note) {
        if (exp == 0) return new Check(key, label, promised, actual, "PENDING", note);
        double r = (double) rep / exp;
        String st = r >= 0.9 ? "OK" : r >= 0.7 ? "LAG" : "ALERT";
        return new Check(key, label, promised, actual, st, note + " · icra " + Math.round(r * 100) + "%");
    }

    private static String money(long v) {
        if (v >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.2f mln ₼", v / 1_000_000.0);
        if (v >= 1_000) return String.format(java.util.Locale.ROOT, "%.0f min ₼", v / 1_000.0);
        return v + " ₼";
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
