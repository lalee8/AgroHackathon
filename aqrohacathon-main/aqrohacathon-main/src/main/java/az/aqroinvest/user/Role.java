package az.aqroinvest.user;

/** Portal rolları. EKTİS və ASAN Login rol modelinə uyğunlaşdırıla bilər. */
public enum Role {
    INVESTOR("İnvestor / sahibkar"),
    BUYER("Alıcı (emal, ixrac, pərakəndə)"),
    EXPERT("Komissiya eksperti"),
    ADMIN("Nazirlik operatoru");

    private final String label;

    Role(String label) { this.label = label; }

    public String label() { return label; }
}
