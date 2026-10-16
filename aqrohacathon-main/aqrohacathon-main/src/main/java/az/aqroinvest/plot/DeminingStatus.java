package az.aqroinvest.plot;

/** ANAMA tərəfindən minatəmizləmə statusu - azad edilmiş ərazilərdə investorun ilk sualı. */
public enum DeminingStatus {
    CLEARED("Minalardan tam təmizlənib (ANAMA sertifikatı)"),
    SURVEYED("Texniki sorğu aparılıb, risk aşkarlanmayıb"),
    IN_PROGRESS("Təmizləmə davam edir");

    private final String label;

    DeminingStatus(String label) { this.label = label; }

    public String label() { return label; }
}
