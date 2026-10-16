package az.aqroinvest.plot;

/** Fəaliyyət istiqamətləri. Hər istiqamət üçün ROI kalkulyatorunda istifadə olunan istinad göstəriciləri. */
public enum PlotCategory {
    //            etiket                    məhsul             məhsuldarlıq t/ha  qiymət AZN/t  xərc AZN/ha/il  ilk yatırım AZN/ha  bar verməyə qədər il
    // Bağ: damcı suvarma, dolu toru, şpalera və tinglər daxil; yığım işçi qüvvəsi illik xərcin ən böyük hissəsidir.
    // İstixana: isidilən çoxbölməli plyonka istixanası (hidroponika), ~60 ₼/m². Bal: topdansatış qiyməti, ~15 arı ailəsi/ha.
    EKINCILIK("Əkinçilik", "Buğda / arpa", 4.2, 430, 1250, 2000, 0),
    HEYVANDARLIQ("Heyvandarlıq", "Süd və ət", 3.5, 1500, 3200, 8500, 1),
    BAGCILIQ("Bağçılıq", "Nar / alma / fındıq", 18.0, 900, 7800, 18000, 3),
    UZUMCULUK("Üzümçülük", "Texniki və süfrə üzümü", 11.0, 750, 4200, 13000, 3),
    ISTIXANA("İstixana təsərrüfatı", "Pomidor / xiyar", 200.0, 1100, 115000, 600000, 0),
    ARICILIQ("Arıçılıq", "Bal və arı məhsulları", 0.3, 15000, 2200, 5500, 1);

    private final String label;
    private final String product;
    private final double yieldTonPerHa;
    private final double pricePerTon;
    private final double opexPerHa;
    private final double capexPerHa;
    private final int yearsToFirstHarvest;

    PlotCategory(String label, String product, double yieldTonPerHa, double pricePerTon,
                 double opexPerHa, double capexPerHa, int yearsToFirstHarvest) {
        this.label = label;
        this.product = product;
        this.yieldTonPerHa = yieldTonPerHa;
        this.pricePerTon = pricePerTon;
        this.opexPerHa = opexPerHa;
        this.capexPerHa = capexPerHa;
        this.yearsToFirstHarvest = yearsToFirstHarvest;
    }

    public String label() { return label; }
    public String product() { return product; }
    public double yieldTonPerHa() { return yieldTonPerHa; }
    public double pricePerTon() { return pricePerTon; }
    public double opexPerHa() { return opexPerHa; }
    public double capexPerHa() { return capexPerHa; }
    public int yearsToFirstHarvest() { return yearsToFirstHarvest; }
}
