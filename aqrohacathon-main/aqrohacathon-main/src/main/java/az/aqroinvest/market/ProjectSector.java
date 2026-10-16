package az.aqroinvest.market;

import az.aqroinvest.economics.UseCase;

/** İnvestorun seçdiyi istiqamət ("xanalar"). Hər istiqamət simulyasiya modeli ilə əlaqələndirilib. */
public enum ProjectSector {
    EKINCILIK("Taxılçılıq", "wheat", UseCase.EKINCILIK),
    BAGCILIQ("Bağçılıq", "cherry", UseCase.BAGCILIQ),
    UZUMCULUK("Üzümçülük və şərabçılıq", "grape", UseCase.UZUMCULUK),
    ISTIXANA("İstixana", "warehouse", UseCase.ISTIXANA),
    HEYVANDARLIQ("Heyvandarlıq", "milk", UseCase.HEYVANDARLIQ),
    ARICILIQ("Arıçılıq", "hexagon", UseCase.ARICILIQ),
    ENERJI("Bərpa olunan enerji", "sun", UseCase.GUNES),
    TURIZM("Aqroturizm", "house", UseCase.AQROTURIZM),
    EMAL("Emal və soyuducu anbar", "snowflake", UseCase.SOYUDUCU),
    AQROTEXNOLOGIYA("Aqrotexnologiya və IT", "laptop", null);

    private final String label;
    private final String icon;
    private final UseCase useCase;

    ProjectSector(String label, String icon, UseCase useCase) {
        this.label = label;
        this.icon = icon;
        this.useCase = useCase;
    }

    public String label() { return label; }
    public String icon() { return icon; }
    public UseCase useCase() { return useCase; }
}
