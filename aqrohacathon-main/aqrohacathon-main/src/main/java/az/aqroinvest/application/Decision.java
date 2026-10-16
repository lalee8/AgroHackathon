package az.aqroinvest.application;

public enum Decision {
    APPROVED("Qalib elan edildi - təsdiqləndi"),
    REJECTED("İmtina edildi");

    private final String label;

    Decision(String label) { this.label = label; }

    public String label() { return label; }
}
