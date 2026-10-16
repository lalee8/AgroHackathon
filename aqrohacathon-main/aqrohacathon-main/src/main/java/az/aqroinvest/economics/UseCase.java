package az.aqroinvest.economics;

import az.aqroinvest.plot.PlotCategory;

/**
 * Torpaqdan istifadə ssenariləri ("xanalar"). Altı kənd təsərrüfatı istiqaməti kataloqdakı kateqoriyalarla eynidir,
 * üçü isə torpağın təyinatına görə açılan alternativ imkanlardır: günəş enerjisi, aqroturizm, soyuducu anbar və emal.
 */
public enum UseCase {
    EKINCILIK("Taxılçılıq", "wheat", "ha", "Buğda, arpa, qarğıdalı", 10),
    HEYVANDARLIQ("Heyvandarlıq", "beef", "ha", "Süd və ət ferması", 10),
    BAGCILIQ("Meyvə bağı", "apple", "ha", "Nar, alma, fındıq", 15),
    UZUMCULUK("Üzümçülük", "grape", "ha", "Texniki və süfrə üzümü", 15),
    ISTIXANA("İstixana", "warehouse", "ha", "Pomidor, xiyar (ixrac)", 15),
    ARICILIQ("Arıçılıq", "hexagon", "ha", "Bal və arı məhsulları", 10),
    GUNES("Günəş elektrik stansiyası", "sun", "MWp", "Şəbəkəyə elektrik satışı, panellər altında otarma", 25),
    AQROTURIZM("Aqroturizm və eko-otel", "house", "otaq", "Qonaq evi, yerli mətbəx, bağ turu", 15),
    SOYUDUCU("Soyuducu anbar və qablaşdırma", "warehouse", "t", "Meyvə-tərəvəzin saxlanması, çeşidlənməsi, ixraca hazırlığı", 15);

    private final String label;
    private final String icon;
    private final String unit;
    private final String hint;
    private final int horizon;

    UseCase(String label, String icon, String unit, String hint, int horizon) {
        this.label = label;
        this.icon = icon;
        this.unit = unit;
        this.hint = hint;
        this.horizon = horizon;
    }

    public String label() { return label; }
    public String icon() { return icon; }
    public String unit() { return unit; }
    public String hint() { return hint; }
    /** Standart simulyasiya üfüqü: birillik bitkilər və ferma 10 il, 25 il bar verən bağ və üzümlüklər, 15-20 il xidmət edən istixana 15 il, infrastruktur daha uzun. */
    public int horizon() { return horizon; }

    public PlotCategory category() {
        return switch (this) {
            case EKINCILIK -> PlotCategory.EKINCILIK;
            case HEYVANDARLIQ -> PlotCategory.HEYVANDARLIQ;
            case BAGCILIQ -> PlotCategory.BAGCILIQ;
            case UZUMCULUK -> PlotCategory.UZUMCULUK;
            case ISTIXANA -> PlotCategory.ISTIXANA;
            case ARICILIQ -> PlotCategory.ARICILIQ;
            default -> null;
        };
    }

    public boolean agro() {
        return category() != null;
    }

    public static UseCase of(PlotCategory c) {
        return UseCase.valueOf(c.name());
    }
}
