package az.aqroinvest.buyer;

import az.aqroinvest.buyer.BuyerEnums.*;
import az.aqroinvest.plot.District;
import az.aqroinvest.plot.PlotCategory;
import az.aqroinvest.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Alıcının (emal müəssisəsi, ixracatçı, şəbəkə) məhsul tələbi. Nazirlik yoxladıqdan sonra "təsdiqlənmiş tələb" olur:
 * bu, zəmanət deyil, alıcının real ehtiyacının və ödəmə qabiliyyətinin yoxlandığını göstərir.
 */
@Entity
@Table(name = "buyer_demands")
@Getter
@Setter
@NoArgsConstructor
public class BuyerDemand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id")
    private User buyer;

    @Column(nullable = false, length = 160)
    private String company;

    @Enumerated(EnumType.STRING)
    @Column(name = "buyer_type", nullable = false, length = 20)
    private BuyerType buyerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlotCategory category;

    @Column(name = "volume_tons", nullable = false, precision = 12, scale = 2)
    private BigDecimal volumeTons;

    @Column(name = "price_min", precision = 12, scale = 2)
    private BigDecimal priceMin;

    @Column(name = "price_max", precision = 12, scale = 2)
    private BigDecimal priceMax;

    @Column(name = "contract_years", nullable = false)
    private int contractYears;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private District district;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "radius_km", nullable = false)
    private int radiusKm;

    @Column(columnDefinition = "TEXT")
    private String quality;

    @Column(length = 80)
    private String season;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemandStatus status = DemandStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
