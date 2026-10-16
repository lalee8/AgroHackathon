package az.aqroinvest.plot;

public enum PlotStatus {
    AVAILABLE("Müsabiqəyə hazır"),
    IN_COMPETITION("Müsabiqədə"),
    AWARDED("İcarəyə verilib");

    private final String label;

    PlotStatus(String label) { this.label = label; }

    public String label() { return label; }
}
