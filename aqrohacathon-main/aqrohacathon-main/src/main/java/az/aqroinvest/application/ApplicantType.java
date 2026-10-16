package az.aqroinvest.application;

public enum ApplicantType {
    INDIVIDUAL("Fiziki şəxs / fərdi sahibkar"),
    LEGAL("Hüquqi şəxs"),
    FOREIGN("Xarici vətəndaş");

    private final String label;

    ApplicantType(String label) { this.label = label; }

    public String label() { return label; }
}
