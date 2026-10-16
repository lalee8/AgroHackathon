package az.aqroinvest.user;

/**
 * Portal istifadəçisinin növü (INVESTOR rolu daxilində). Qeydiyyat formaları fərqlidir: investor büdcə, istiqamət və
 * risk iştahını, sahibkar isə rayonunu, ideyasını və lazım olan investisiyanı yazır. Hüquqlar eynidir. Hüquqi status
 * (fiziki, hüquqi şəxs, xarici vətəndaş) ayrıca {@link LegalType}-dadır.
 */
public enum UserType {
    INVESTOR("İnvestor"),
    ENTREPRENEUR("Sahibkar");

    private final String label;

    UserType(String label) { this.label = label; }

    public String label() { return label; }
}
