package az.aqroinvest.buyer;

import az.aqroinvest.buyer.BuyerEnums.*;
import az.aqroinvest.plot.District;
import az.aqroinvest.plot.PlotCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public final class BuyerDtos {
    private BuyerDtos() {}

    public record DemandView(Long id, String code, String company, BuyerType buyerType, String buyerTypeLabel, Product product,
                             String productLabel, PlotCategory category, String categoryLabel, BigDecimal volumeTons,
                             BigDecimal priceMin, BigDecimal priceMax, int contractYears, District district, String districtLabel,
                             double latitude, double longitude, int radiusKm, String quality, String season,
                             DemandStatus status, String statusLabel, Instant verifiedAt, Instant createdAt,
                             long lettersSigned, BigDecimal contractedTons) {}

    public record DemandMatch(DemandView demand, double distanceKm, double plotProductionTons, int coveragePercent, int score) {}

    public record LetterView(Long id, Long demandId, String demandCode, String company, String productLabel,
                             String requesterName, Long plotId, String plotCode, Long applicationId, String appNumber,
                             Long projectId, String projectCode, BigDecimal volumeTons, BigDecimal price, LoiStatus status,
                             String statusLabel, String message, String buyerNote, Instant signedAt, Instant createdAt) {}

    public record DemandRequest(
            @NotBlank(message = "Şirkətin adı tələb olunur") @Size(max = 160) String company,
            @NotNull(message = "Alıcının növü seçilməlidir") BuyerType buyerType,
            @NotNull(message = "Məhsul seçilməlidir") Product product,
            @NotNull @DecimalMin(value = "1", message = "Həcm ən azı 1 ton olmalıdır") BigDecimal volumeTons,
            @DecimalMin("0") BigDecimal priceMin,
            @DecimalMin("0") BigDecimal priceMax,
            @Min(1) @Max(15) int contractYears,
            @NotNull(message = "Qəbul məntəqəsinin rayonu seçilməlidir") District district,
            @Min(10) @Max(500) int radiusKm,
            @Size(max = 2000) String quality,
            @Size(max = 80) String season) {}

    public record LetterRequest(Long plotId, Long applicationId, Long projectId,
                                @NotNull @DecimalMin(value = "1", message = "Həcm ən azı 1 ton olmalıdır") BigDecimal volumeTons,
                                @Size(max = 2000) String message) {}

    public record SignRequest(BigDecimal price, @Size(max = 2000) String note) {}
}
