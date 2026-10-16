package az.aqroinvest.matching;

import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotCategory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Təxmini investisiya gəlirliliyi kalkulyatoru. İstinad göstəriciləri (məhsuldarlıq, qiymət, xərc)
 * kateqoriya üzrə orta göstəricilərdir və torpağın bonitet balı, suvarma imkanı ilə korreksiya olunur.
 * Nəticə qərar üçün deyil, investora ilkin təsəvvür yaratmaq üçündür.
 */
@Service
public class RoiService {

    public record YearRow(int year, long revenue, long costs, long net, long cumulative) {}

    public record RoiResult(String category, String product, double areaHa, long investment, double yieldPerHa,
                            double pricePerTon, long annualRevenueAtMaturity, long annualCostsAtMaturity,
                            long annualNetAtMaturity, Integer paybackYear, long tenYearNet, double roiPercent,
                            int jobsEstimate, List<YearRow> years, String disclaimer) {}

    public RoiResult calculate(Plot plot, PlotCategory category, Long investmentOverride) {
        PlotCategory c = category != null ? category : plot.getCategory();
        double area = plot.getAreaHa().doubleValue();
        double soil = plot.getSoilQuality() == null ? 60 : plot.getSoilQuality();
        double factor = (0.7 + 0.3 * soil / 100.0) * (plot.isIrrigation() ? 1.1 : (c == PlotCategory.ISTIXANA ? 0.85 : 0.8));
        // İstixana üçün sahənin hamısı deyil, ~15%-i örtülü istehsala ayrılır
        double productiveArea = c == PlotCategory.ISTIXANA ? area * 0.15 : area;
        double yield = c.yieldTonPerHa() * factor;
        long capex = Math.round(c.capexPerHa() * productiveArea);
        long investment = investmentOverride != null && investmentOverride > 0
                ? investmentOverride : Math.max(capex, plot.getMinInvestment().longValue());
        long revenueMature = Math.round(yield * c.pricePerTon() * productiveArea);
        long rent = Math.round(plot.getAnnualRentPerHa().doubleValue() * area);
        long opex = Math.round(c.opexPerHa() * productiveArea) + rent;

        List<YearRow> rows = new ArrayList<>();
        long cumulative = -investment;
        Integer payback = null;
        long tenYearNet = 0;
        for (int y = 1; y <= 10; y++) {
            double ramp;
            int start = c.yearsToFirstHarvest();
            if (y <= start) ramp = 0;
            else if (y == start + 1) ramp = 0.5;
            else if (y == start + 2) ramp = 0.8;
            else ramp = 1.0;
            long rev = Math.round(revenueMature * ramp);
            long cost = Math.round(opex * (ramp == 0 ? 0.45 : (0.6 + 0.4 * ramp)));
            long net = rev - cost;
            cumulative += net;
            tenYearNet += net;
            if (payback == null && cumulative >= 0) payback = y;
            rows.add(new YearRow(y, rev, cost, net, cumulative));
        }
        double roi = investment == 0 ? 0 : (tenYearNet - investment) * 100.0 / investment;
        int jobs = (int) Math.max(plot.getMinJobs(), Math.round(productiveArea * jobsPerHa(c)));
        return new RoiResult(c.label(), c.product(), area, investment, round1(yield), c.pricePerTon(),
                revenueMature, opex, revenueMature - opex, payback, tenYearNet, round1(roi), jobs, rows,
                "Hesablama orta sahə göstəricilərinə əsaslanan təxmini proqnozdur və hüquqi öhdəlik yaratmır.");
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

    private static double round1(double d) {
        return Math.round(d * 10) / 10.0;
    }
}
