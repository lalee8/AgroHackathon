package az.aqroinvest.buyer;

import az.aqroinvest.application.Application;
import az.aqroinvest.buyer.BuyerEnums.LoiStatus;
import az.aqroinvest.market.Project;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Alıcının niyyət məktubu: gələcək məhsulu müəyyən həcm və qiymətlə almağa razılıq. Müraciətə və ya layihəyə əlavə olunur. */
@Entity
@Table(name = "letters_of_intent")
@Getter
@Setter
@NoArgsConstructor
public class LetterOfIntent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_id")
    private BuyerDemand demand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plot_id")
    private Plot plot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "volume_tons", nullable = false, precision = 12, scale = 2)
    private BigDecimal volumeTons;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoiStatus status = LoiStatus.REQUESTED;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "buyer_note", columnDefinition = "TEXT")
    private String buyerNote;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
