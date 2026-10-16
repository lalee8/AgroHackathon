package az.aqroinvest.economics;

import java.util.List;

/**
 * Monte-Carlo modelinin parametrləri. Hər ssenari üçün gözlənilən dəyərlər və onların ətrafındakı qeyri-müəyyənlik.
 *
 * @param scale            miqyas (ha, MWp, otaq və ya ton tutum)
 * @param yieldPerUnit     vahiddən illik məhsul (t/ha, MWh/MWp, otaq-gecə/otaq, t/t)
 * @param price            məhsul vahidinin qiyməti (AZN)
 * @param ramp             ilk illərdə tam gücə çıxma payı (0-1)
 * @param offtakeShare     alıcı ilə müqavilə üzrə satılan həcm payı (qiymət riski yoxdur)
 * @param offtakePriceFactor müqavilə qiymətinin bazar qiymətinə nisbəti
 */
public record SimulationSpec(
        UseCase useCase, String unit, double scale, double capex,
        double yieldPerUnit, String yieldUnit, double price, String priceUnit,
        double opexFixed, double opexPerUnit, double opexRevenueShare,
        double[] ramp, double yieldSigma, double priceSigma, double priceDrift,
        List<Shock> shocks, double degradation, int horizon, double discountRate,
        double offtakeShare, double offtakePriceFactor,
        double capexOverrunMean, double capexOverrunSigma, int jobs,
        List<Assumption> assumptions) {

    /** Təsadüfi hadisə: hər il {@code probability} ehtimalı ilə məhsulu {@code impact} qədər azaldır. */
    public record Shock(String name, double probability, double impact) {}

    /** Fərziyyə və onun mənbəyi (şəffaflıq üçün istifadəçiyə göstərilir). */
    public record Assumption(String label, String value, String source) {}

    public double expectedAnnualRevenue() {
        return scale * yieldPerUnit * price;
    }

    public SimulationSpec withOfftake(double share, double factor) {
        return new SimulationSpec(useCase, unit, scale, capex, yieldPerUnit, yieldUnit, price, priceUnit, opexFixed,
                opexPerUnit, opexRevenueShare, ramp, yieldSigma, priceSigma, priceDrift, shocks, degradation, horizon,
                discountRate, share, factor, capexOverrunMean, capexOverrunSigma, jobs, assumptions);
    }

    public SimulationSpec withHorizon(int h) {
        return new SimulationSpec(useCase, unit, scale, capex, yieldPerUnit, yieldUnit, price, priceUnit, opexFixed,
                opexPerUnit, opexRevenueShare, ramp, yieldSigma, priceSigma, priceDrift, shocks, degradation, h,
                discountRate, offtakeShare, offtakePriceFactor, capexOverrunMean, capexOverrunSigma, jobs, assumptions);
    }
}
