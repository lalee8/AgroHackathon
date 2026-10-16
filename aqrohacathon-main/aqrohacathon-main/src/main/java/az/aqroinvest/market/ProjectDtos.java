package az.aqroinvest.market;

import az.aqroinvest.market.MarketEnums.*;
import az.aqroinvest.plot.District;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record PlotRef(Long id, String code, String name, String districtLabel, String designationLabel) {}

    public record Match(int score, List<String> reasons) {}

    public record ProjectCard(Long id, String code, String title, String tagline, ProjectSector sector, String sectorLabel,
                              String sectorIcon, Stage stage, String stageLabel, District district, String districtLabel,
                              PlotRef plot, BigDecimal capex, BigDecimal ownFunds, BigDecimal askAmount, DealType dealType,
                              String dealLabel, String offer, int jobs, boolean verified, ProjectStatus status,
                              String statusLabel, String ownerName, long interestCount, int credibility,
                              String credibilityLabel, double probPayback, Integer paybackP50, String risk, String riskLabel,
                              Match match, Instant createdAt) {}

    public record Contact(String name, String email, String phone) {}

    public record InterestView(Long id, Long projectId, String projectCode, String projectTitle, String investorName,
                               BigDecimal amount, String message, InterestStatus status, String statusLabel, String reply,
                               Contact contact, Instant createdAt) {}

    public record ProjectDetail(ProjectCard card, String problem, String solution, String market, String team,
                                BigDecimal scale, BigDecimal equityPercent, BigDecimal interestPercent,
                                BigDecimal revenueSharePercent, BigDecimal claimedRevenue, BigDecimal claimedProfit,
                                BigDecimal claimedYield, BigDecimal claimedPrice, int views,
                                ProjectAnalysisService.Analysis analysis, boolean owner, InterestView myInterest,
                                List<InterestView> interests, Contact ownerContact) {}

    public record ProjectRequest(
            @NotNull(message = "İstiqamət seçilməlidir") ProjectSector sector,
            Long plotId,
            District district,
            @NotBlank(message = "Layihənin adı tələb olunur") @Size(max = 160) String title,
            @Size(max = 240) String tagline,
            @Size(max = 4000) String problem,
            @Size(max = 4000) String solution,
            @Size(max = 4000) String market,
            @Size(max = 4000) String team,
            Stage stage,
            @DecimalMin("0") BigDecimal scale,
            @NotNull(message = "İnvestisiya məbləği tələb olunur") @DecimalMin(value = "1000", message = "İnvestisiya ən azı 1 000 ₼ olmalıdır") BigDecimal capex,
            @DecimalMin("0") BigDecimal ownFunds,
            @NotNull(message = "İnvestordan istənilən məbləğ tələb olunur") @DecimalMin(value = "1000", message = "Məbləğ ən azı 1 000 ₼ olmalıdır") BigDecimal askAmount,
            @NotNull(message = "Təklifin növü seçilməlidir") DealType dealType,
            @DecimalMin("0.5") @DecimalMax("100") BigDecimal equityPercent,
            @DecimalMin("0") @DecimalMax("60") BigDecimal interestPercent,
            @DecimalMin("0.5") @DecimalMax("80") BigDecimal revenueSharePercent,
            @DecimalMin("0") BigDecimal claimedRevenue,
            @DecimalMin("0") BigDecimal claimedProfit,
            @DecimalMin("0") BigDecimal claimedYield,
            @DecimalMin("0") BigDecimal claimedPrice,
            @Min(0) Integer jobs,
            Boolean publish) {}

    public record InterestRequest(@NotNull @DecimalMin(value = "1000", message = "Məbləğ ən azı 1 000 ₼ olmalıdır") BigDecimal amount,
                                  @Size(max = 2000) String message) {}

    public record RespondRequest(boolean accept, @Size(max = 2000) String reply) {}

    public record ProfileView(List<String> sectors, BigDecimal ticketMin, BigDecimal ticketMax, Risk risk, Integer horizonYears) {}
}
