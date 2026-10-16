package az.aqroinvest.commission;

/**
 * Müsabiqə komissiyasında təmsil olunan dövlət qurumları. Qaydalara görə (Prezidentin 27.11.2024 tarixli Fərmanı ilə
 * təsdiq edilmiş investisiya müsabiqələri Qaydaları, 3.1) komissiya 9 nəfərdir: Kənd Təsərrüfatı Nazirliyi 2 (sədri
 * Nazirlik təyin edir), İqtisadiyyat Nazirliyi 2, Ekologiya və Təbii Sərvətlər Nazirliyi, Rəqəmsal İnkişaf və Nəqliyyat
 * Nazirliyi, Şəhərsalma və Arxitektura Dövlət Komitəsi, Su Ehtiyatları Dövlət Agentliyi və torpağın yerləşdiyi ərazi
 * üzrə Prezidentin xüsusi nümayəndəliyi 1 nəfər.
 */
public enum Institution {
    KTN("Kənd Təsərrüfatı Nazirliyi", "KTN"),
    IQT("İqtisadiyyat Nazirliyi", "İN"),
    ETSN("Ekologiya və Təbii Sərvətlər Nazirliyi", "ETSN"),
    RINN("Rəqəmsal İnkişaf və Nəqliyyat Nazirliyi", "RİNN"),
    SADK("Şəhərsalma və Arxitektura Dövlət Komitəsi", "ŞADK"),
    SEDA("Su Ehtiyatları Dövlət Agentliyi", "SEDA"),
    XN("Prezidentin xüsusi nümayəndəliyi", "XN");

    private final String label;
    private final String abbr;

    Institution(String label, String abbr) {
        this.label = label;
        this.abbr = abbr;
    }

    public String label() { return label; }

    public String abbr() { return abbr; }
}
