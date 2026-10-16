package az.aqroinvest.application;

import az.aqroinvest.plot.PlotCategory;
import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApplicationDtos {
    private ApplicationDtos() {}

    public record ApplicationRequest(
            @NotNull(message = "Müsabiqə seçilməlidir") Long competitionId,
            @NotNull(message = "Sahə seçilməlidir") Long plotId,
            @NotNull(message = "Müraciətçinin növü seçilməlidir") ApplicantType applicantType,
            @Size(max = 200) String companyName,
            @Pattern(regexp = "^$|^\\d{10}$", message = "VÖEN 10 rəqəmdən ibarət olmalıdır") String voen,
            @Pattern(regexp = "^$|^[A-Za-z0-9]{7}$", message = "FİN kod 7 simvoldan ibarət olmalıdır") String fin,
            @Size(max = 60) String citizenship,
            @Pattern(regexp = "^$|^[A-Za-z0-9 ]{5,20}$", message = "Pasport nömrəsi 5-20 hərf və rəqəmdən ibarət olmalıdır") String passportNo,
            @Pattern(regexp = "^$|^\\+?[0-9 ()-]{9,20}$", message = "Telefon nömrəsi yanlışdır") String contactPhone,
            @Email(message = "E-poçt formatı yanlışdır") @Size(max = 160) String contactEmail,
            @Size(max = 255) String legalAddress,
            PlotCategory activityType,
            @DecimalMin(value = "0", message = "Məbləğ mənfi ola bilməz") @DecimalMax("1000000000") BigDecimal investmentAmount,
            @Min(0) @Max(100) Integer ownFundsPercent,
            @Min(0) @Max(100000) Integer jobsPlanned,
            @Min(1) @Max(120) Integer implementationMonths,
            @Min(0) @Max(80) Integer experienceYears,
            List<String> technologies,
            @Size(max = 10000) String businessPlan,
            @DecimalMin("0") BigDecimal expectedAnnualRevenue,
            boolean idpStatus,
            boolean consentGiven) {
    }

    public record StatusChangeRequest(
            @NotNull(message = "Yeni status göstərilməlidir") ApplicationStatus status,
            @Size(max = 4000) String comment,
            Decision decision) {
    }

    public record RespondRequest(@NotBlank(message = "Cavab mətni tələb olunur") @Size(max = 4000) String comment) {}

    public record EvaluationRequest(
            @NotNull Map<String, @Min(0) @Max(10) Integer> scores,
            @Size(max = 4000) String comment) {
    }

    public record CompetitionRef(Long id, String code, String title, Instant deadlineAt, boolean open) {}

    public record PlotRef(Long id, String code, String name, String districtLabel, String categoryLabel,
                          BigDecimal areaHa, double latitude, double longitude, BigDecimal minInvestment, int minJobs) {}

    public record DocumentView(Long id, DocumentType type, String typeLabel, String name, String contentType,
                               long size, String sha256, Instant uploadedAt) {}

    public record HistoryView(ApplicationStatus fromStatus, String fromLabel, ApplicationStatus toStatus,
                              String toLabel, String comment, String actor, Instant createdAt) {}

    public record EvaluationView(Long id, String expert, @JsonRawValue String scores, int total, String comment,
                                 Instant createdAt) {}

    public record TechView(String code, String label) {}

    public record StatusOption(ApplicationStatus status, String label) {}

    public record ApplicationView(
            Long id, String appNumber, String trackingCode,
            ApplicationStatus status, String statusLabel, Decision decision, String decisionLabel, String decisionNote,
            CompetitionRef competition, PlotRef plot,
            String applicantName, String applicantEmail,
            ApplicantType applicantType, String applicantTypeLabel, String companyName, String voen, String fin,
            String citizenship, String passportNo,
            String contactPhone, String contactEmail, String legalAddress,
            PlotCategory activityType, String activityLabel,
            BigDecimal investmentAmount, Integer ownFundsPercent, Integer jobsPlanned, Integer implementationMonths,
            Integer experienceYears, List<TechView> technologies, String businessPlan, BigDecimal expectedAnnualRevenue,
            boolean idpStatus, boolean consentGiven,
            Integer autoScore, @JsonRawValue String scoreBreakdown, Integer expertAverage, Integer finalScore,
            String ektisFarmerStatus, String ektisReference,
            Instant submittedAt, Instant decidedAt, Instant createdAt, Instant updatedAt,
            boolean editable, List<String> missingRequirements, List<StatusOption> allowedTransitions,
            List<DocumentView> documents, List<HistoryView> history, List<EvaluationView> evaluations) {
    }

    public record ApplicationListItem(
            Long id, String appNumber, ApplicationStatus status, String statusLabel, Decision decision,
            String decisionLabel, String applicantName, String plotCode, String plotName, String districtLabel,
            String categoryLabel, String competitionCode, BigDecimal investmentAmount, Integer jobsPlanned,
            Integer autoScore, Integer expertAverage, Integer finalScore, Instant submittedAt, Instant updatedAt) {
    }

    public record TrackView(String appNumber, ApplicationStatus status, String statusLabel, Decision decision, String decisionLabel,
                            String applicant, String plot, String competition, Instant submittedAt, Instant updatedAt,
                            List<HistoryView> history) {
    }
}
