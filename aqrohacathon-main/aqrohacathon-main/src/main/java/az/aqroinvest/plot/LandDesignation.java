package az.aqroinvest.plot;

/**
 * Torpağın hüquqi təyinatı (Torpaq Məcəlləsinə uyğun sadələşdirilmiş təsnifat). Kateqoriya "nə əkmək olar" sualına,
 * təyinat isə "bu torpaqda qanunla nə etmək olar" sualına cavab verir. Ən yaxşı istifadə mühərriki hər iki göstəricini nəzərə alır.
 */
public enum LandDesignation {
    EKIN("Əkin yeri", "Kənd təsərrüfatı təyinatlı torpaq: əkin üçün yararlı. Ərzaq təhlükəsizliyi səbəbindən başqa məqsədə istifadəsi məhduddur."),
    COXILLIK("Çoxillik əkmələr", "Kənd təsərrüfatı təyinatlı torpaq: bağ, üzümlük, giləmeyvə plantasiyaları."),
    OTLAQ("Otlaq və biçənək", "Kənd təsərrüfatı təyinatlı torpaq: heyvandarlıq üçün otlaq və biçənək."),
    YARARSIZ("Əkinə yararsız torpaq", "Şoranlaşmış, daşlı və ya eroziyaya uğramış torpaq. Əkin üçün səmərəsizdir, alternativ istifadə (günəş enerjisi, ekstensiv otarma, torpaqsız istixana) üçün namizəddir."),
    SENAYE("Sənaye və digər təyinatlı", "Sənaye, nəqliyyat, rabitə və digər təyinatlı torpaq: emal, anbar, logistika obyektləri üçün.");

    private final String label;
    private final String description;

    LandDesignation(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }
    public String description() { return description; }

    /** Köhnə məlumatlar üçün kateqoriyadan təyinatın ilkin müəyyən edilməsi. */
    public static LandDesignation defaultFor(PlotCategory c) {
        return switch (c) {
            case BAGCILIQ, UZUMCULUK -> COXILLIK;
            case HEYVANDARLIQ, ARICILIQ -> OTLAQ;
            default -> EKIN;
        };
    }
}
