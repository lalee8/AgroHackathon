package az.aqroinvest.plot;

import az.aqroinvest.common.StringListConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Müsabiqəyə çıxarılan kənd təsərrüfatı təyinatlı torpaq sahəsi. */
@Entity
@Table(name = "plots")
@Getter
@Setter
@NoArgsConstructor
public class Plot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private District district;

    private String village;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlotCategory category;

    /** Hüquqi təyinat: əkin, çoxillik, otlaq, əkinə yararsız, sənaye. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LandDesignation designation = LandDesignation.EKIN;

    /** Mənzərəli yer (göl, çay, dağ mənzərəsi): aqroturizm üçün üstünlük. */
    private boolean scenic;

    @Column(name = "area_ha", nullable = false, precision = 10, scale = 2)
    private BigDecimal areaHa;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    /** Sahənin sərhədləri: GeoJSON koordinat massivi [[lng,lat],...] */
    @Column(columnDefinition = "TEXT")
    private String polygon;

    @Column(name = "soil_type")
    private String soilType;

    /** Torpağın bonitet balı (0-100). */
    @Column(name = "soil_quality")
    private Integer soilQuality;

    @Column(name = "water_source")
    private String waterSource;

    private boolean irrigation;

    @Column(name = "road_distance_km", precision = 6, scale = 2)
    private BigDecimal roadDistanceKm;

    private boolean electricity;

    private boolean gas;

    @Column(name = "altitude_m")
    private Integer altitudeM;

    @Column(name = "slope_percent", precision = 4, scale = 1)
    private BigDecimal slopePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "demining_status", nullable = false, length = 20)
    private DeminingStatus deminingStatus;

    @Column(name = "demining_certificate")
    private String deminingCertificate;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 20)
    private UsageType usageType;

    @Column(name = "lease_years", nullable = false)
    private int leaseYears;

    @Column(name = "annual_rent_per_ha", nullable = false, precision = 10, scale = 2)
    private BigDecimal annualRentPerHa;

    @Column(name = "min_investment", nullable = false, precision = 14, scale = 2)
    private BigDecimal minInvestment;

    @Column(name = "min_jobs", nullable = false)
    private int minJobs;

    @Convert(converter = StringListConverter.class)
    @Column(name = "recommended_crops", columnDefinition = "TEXT")
    private List<String> recommendedCrops = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String terms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlotStatus status = PlotStatus.AVAILABLE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
