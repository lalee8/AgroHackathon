package az.aqroinvest.application;

import java.util.EnumSet;
import java.util.Set;

/**
 * Müraciətin statusları və icazə verilən keçidlər (sonlu avtomat).
 * Texniki tapşırıqdakı statuslar: "Qəbul edilib", "Baxılır", "Əlavə məlumat tələb olunur", "Qərar verilib".
 */
public enum ApplicationStatus {
    DRAFT("Qaralama"),
    SUBMITTED("Qəbul edilib"),
    UNDER_REVIEW("Baxılır"),
    INFO_REQUESTED("Əlavə məlumat tələb olunur"),
    DECIDED("Qərar verilib"),
    WITHDRAWN("Geri çağırılıb");

    private final String label;

    ApplicationStatus(String label) { this.label = label; }

    public String label() { return label; }

    /** Admin/operator tərəfindən icazə verilən keçidlər. */
    public Set<ApplicationStatus> adminTransitions() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(UNDER_REVIEW, INFO_REQUESTED);
            case UNDER_REVIEW -> EnumSet.of(INFO_REQUESTED, DECIDED);
            case INFO_REQUESTED -> EnumSet.of(UNDER_REVIEW, DECIDED);
            default -> EnumSet.noneOf(ApplicationStatus.class);
        };
    }

    public boolean canAdminMoveTo(ApplicationStatus target) {
        return adminTransitions().contains(target);
    }

    public boolean isFinal() {
        return this == DECIDED || this == WITHDRAWN;
    }

    /** İnvestor müraciəti hələ redaktə edə bilərmi? */
    public boolean isEditableByApplicant() {
        return this == DRAFT || this == INFO_REQUESTED;
    }
}
