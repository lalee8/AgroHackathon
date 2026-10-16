package az.aqroinvest.market;

import az.aqroinvest.economics.UseCase;
import az.aqroinvest.market.MarketEnums.*;
import az.aqroinvest.plot.District;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Sahibkarın investor axtardığı layihə: ideya, rəqəmlərlə ifadə olunmuş biznes modeli və investora təklif. */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plot_id")
    private Plot plot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectSector sector;

    @Enumerated(EnumType.STRING)
    @Column(name = "use_case", length = 30)
    private UseCase useCase;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private District district;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 240)
    private String tagline;

    @Column(columnDefinition = "TEXT")
    private String problem;

    @Column(columnDefinition = "TEXT")
    private String solution;

    @Column(columnDefinition = "TEXT")
    private String market;

    @Column(columnDefinition = "TEXT")
    private String team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Stage stage = Stage.PLAN;

    @Column(precision = 14, scale = 2)
    private BigDecimal scale;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal capex;

    @Column(name = "own_funds", nullable = false, precision = 16, scale = 2)
    private BigDecimal ownFunds = BigDecimal.ZERO;

    @Column(name = "ask_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal askAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "deal_type", nullable = false, length = 20)
    private DealType dealType;

    @Column(name = "equity_percent", precision = 5, scale = 2)
    private BigDecimal equityPercent;

    @Column(name = "interest_percent", precision = 5, scale = 2)
    private BigDecimal interestPercent;

    @Column(name = "revenue_share_percent", precision = 5, scale = 2)
    private BigDecimal revenueSharePercent;

    @Column(name = "claimed_revenue", precision = 16, scale = 2)
    private BigDecimal claimedRevenue;

    @Column(name = "claimed_profit", precision = 16, scale = 2)
    private BigDecimal claimedProfit;

    @Column(name = "claimed_yield", precision = 12, scale = 3)
    private BigDecimal claimedYield;

    @Column(name = "claimed_price", precision = 12, scale = 2)
    private BigDecimal claimedPrice;

    private int jobs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.DRAFT;

    private boolean verified;

    private int views;

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
