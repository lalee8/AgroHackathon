package az.aqroinvest.plot;

public enum UsageType {
    LEASE("Uzunmüddətli icarə"),
    USUFRUCT("İstifadəyə vermə");

    private final String label;

    UsageType(String label) { this.label = label; }

    public String label() { return label; }
}
