package az.aqroinvest.competition;

import az.aqroinvest.plot.District;
import az.aqroinvest.plot.PlotCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CompetitionDtos {
    private CompetitionDtos() {}

    public record PlotSummary(Long id, String code, String name, District district, String districtLabel,
                              PlotCategory category, String categoryLabel, BigDecimal areaHa,
                              BigDecimal minInvestment, String status) {}

    public record CompetitionView(Long id, String code, String title, String description,
                                  Instant startAt, Instant deadlineAt, CompetitionStatus status, String statusLabel,
                                  boolean open, List<Criterion> criteria, List<PlotSummary> plots,
                                  int plotCount, BigDecimal totalAreaHa, long applicationCount,
                                  String createdBy, String approvedBy, Instant approvedAt, String reviewNote,
                                  Instant createdAt) {}

    public record CompetitionRequest(
            @NotBlank(message = "Başlıq tələb olunur") @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull(message = "Başlama tarixi tələb olunur") Instant startAt,
            @NotNull(message = "Son müraciət tarixi tələb olunur") @Future(message = "Son tarix gələcəkdə olmalıdır") Instant deadlineAt,
            @NotEmpty(message = "Ən azı bir sahə seçilməlidir") List<Long> plotIds,
            List<Criterion> criteria) {}

    public record ReviewRequest(@Size(max = 2000) String note) {}
}
