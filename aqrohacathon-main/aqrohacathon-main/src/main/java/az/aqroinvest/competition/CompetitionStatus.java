package az.aqroinvest.competition;

/** Müsabiqənin həyat dövrü: Layihə → Təsdiq gözləyir → Elan edilib → Bağlanıb. */
public enum CompetitionStatus {
    DRAFT("Layihə"),
    PENDING_APPROVAL("Təsdiq gözləyir"),
    PUBLISHED("Elan edilib"),
    CLOSED("Bağlanıb");

    private final String label;

    CompetitionStatus(String label) { this.label = label; }

    public String label() { return label; }
}
