package az.aqroinvest.user;

/**
 * Müraciət edən tərəfin hüquqi statusu. Torpağı yalnız dövlət verir (müsabiqə və dövlət qurumlarından ibarət komissiya),
 * müraciət edənlər isə üç növdür. Sahibkar, investor, yerli sakin və alıcı bu statuslardan biri ilə qeydiyyatdan keçir.
 */
public enum LegalType {
    INDIVIDUAL("Fiziki şəxs"),
    COMPANY("Hüquqi şəxs"),
    FOREIGN("Xarici vətəndaş");

    private final String label;

    LegalType(String label) { this.label = label; }

    public String label() { return label; }
}
