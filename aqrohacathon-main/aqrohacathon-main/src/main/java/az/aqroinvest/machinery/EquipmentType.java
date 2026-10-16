package az.aqroinvest.machinery;

import az.aqroinvest.plot.PlotCategory;

import java.util.List;

/** Texnika növləri: hansı fəaliyyətə lazımdır və ilin hansı işində istifadə olunur. */
public enum EquipmentType {
    TRACTOR("Traktor", "tractor", "Şum, daşıma, aqreqatlar üçün əsas güc", List.of(PlotCategory.EKINCILIK, PlotCategory.BAGCILIQ, PlotCategory.UZUMCULUK, PlotCategory.HEYVANDARLIQ)),
    COMBINE("Taxıl kombaynı", "wheat", "Buğda və arpanın biçini", List.of(PlotCategory.EKINCILIK)),
    SEEDER("Toxumsəpən", "sprout", "Dəqiq səpin", List.of(PlotCategory.EKINCILIK)),
    CULTIVATOR("Kultivator / freza", "layers", "Torpağın becərilməsi, alaq", List.of(PlotCategory.EKINCILIK, PlotCategory.BAGCILIQ, PlotCategory.UZUMCULUK, PlotCategory.ISTIXANA)),
    IRRIGATION("Damcı suvarma sistemi", "droplets", "Nasos stansiyası və suvarma xətləri", List.of(PlotCategory.BAGCILIQ, PlotCategory.UZUMCULUK, PlotCategory.ISTIXANA)),
    SPRAYER("Çiləyici (pülverizator)", "leaf", "Bağ və üzümlükdə dərmanlama", List.of(PlotCategory.BAGCILIQ, PlotCategory.UZUMCULUK)),
    DRONE("Aqrodron", "navigation", "Dərmanlama və sahənin müşahidəsi", List.of(PlotCategory.EKINCILIK, PlotCategory.BAGCILIQ, PlotCategory.UZUMCULUK)),
    BALER("Ot biçən və presləyici", "truck", "Yem tədarükü (rulon)", List.of(PlotCategory.HEYVANDARLIQ)),
    MILKING("Mobil sağım və soyuducu tank", "milk", "Süd sağımı və saxlanması", List.of(PlotCategory.HEYVANDARLIQ)),
    HARVEST_PLATFORM("Meyvə yığım platforması", "cherry", "Bağda məhsul yığımı", List.of(PlotCategory.BAGCILIQ));

    private final String label;
    private final String icon;
    private final String purpose;
    private final List<PlotCategory> categories;

    EquipmentType(String label, String icon, String purpose, List<PlotCategory> categories) {
        this.label = label;
        this.icon = icon;
        this.purpose = purpose;
        this.categories = categories;
    }

    public String label() { return label; }
    public String icon() { return icon; }
    public String purpose() { return purpose; }
    public List<PlotCategory> categories() { return categories; }
}
