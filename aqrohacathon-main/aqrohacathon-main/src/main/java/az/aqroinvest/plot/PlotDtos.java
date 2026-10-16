package az.aqroinvest.plot;

import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PlotDtos {
    private PlotDtos() {}

    public record ActiveCompetition(Long id, String code, String title, Instant deadlineAt, boolean open) {}

    public record PlotView(
            Long id, String code, String name,
            District district, String districtLabel, String region, String village,
            PlotCategory category, String categoryLabel,
            LandDesignation designation, String designationLabel, String designationDescription, boolean scenic,
            BigDecimal areaHa, double latitude, double longitude,
            @JsonRawValue String polygon,
            String soilType, Integer soilQuality, String waterSource, boolean irrigation,
            BigDecimal roadDistanceKm, boolean electricity, boolean gas, Integer altitudeM, BigDecimal slopePercent,
            DeminingStatus deminingStatus, String deminingLabel, String deminingCertificate,
            UsageType usageType, String usageLabel, int leaseYears, BigDecimal annualRentPerHa,
            BigDecimal annualRentTotal, BigDecimal minInvestment, int minJobs,
            List<String> recommendedCrops, String description, String terms,
            PlotStatus status, String statusLabel,
            ActiveCompetition competition, long applicationCount, int infrastructureScore,
            Instant updatedAt) {
    }

    public record PlotRequest(
            @NotBlank(message = "Kod tələb olunur") @Size(max = 20) String code,
            @NotBlank(message = "Ad tələb olunur") @Size(max = 160) String name,
            @NotNull(message = "Rayon seçilməlidir") District district,
            @Size(max = 80) String village,
            @NotNull(message = "Təyinat seçilməlidir") PlotCategory category,
            @NotNull @DecimalMin(value = "0.1", message = "Sahə 0.1 ha-dan az ola bilməz") BigDecimal areaHa,
            @NotNull @DecimalMin("38.5") @DecimalMax("40.6") Double latitude,
            @NotNull @DecimalMin("45.5") @DecimalMax("47.8") Double longitude,
            String polygon,
            @Size(max = 80) String soilType,
            @Min(0) @Max(100) Integer soilQuality,
            @Size(max = 120) String waterSource,
            boolean irrigation,
            @DecimalMin("0") BigDecimal roadDistanceKm,
            boolean electricity,
            boolean gas,
            Integer altitudeM,
            BigDecimal slopePercent,
            @NotNull DeminingStatus deminingStatus,
            @Size(max = 40) String deminingCertificate,
            @NotNull UsageType usageType,
            @Min(1) @Max(99) int leaseYears,
            @NotNull @DecimalMin("0") BigDecimal annualRentPerHa,
            @NotNull @DecimalMin("0") BigDecimal minInvestment,
            @Min(0) int minJobs,
            List<String> recommendedCrops,
            String description,
            String terms,
            PlotStatus status,
            LandDesignation designation,
            Boolean scenic) {
    }

    public record PlotFilter(
            List<PlotCategory> category, List<District> district, BigDecimal minArea, BigDecimal maxArea,
            BigDecimal maxRent, Boolean irrigation, Boolean electricity, PlotStatus status, String q,
            Boolean openOnly, String sort) {
    }
}
