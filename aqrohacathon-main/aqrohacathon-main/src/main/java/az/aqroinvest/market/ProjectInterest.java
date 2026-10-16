package az.aqroinvest.market;

import az.aqroinvest.market.MarketEnums.InterestStatus;
import az.aqroinvest.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** İnvestorun layihəyə maraq bildirməsi. Əlaqə məlumatları yalnız sahibkar razılıq verdikdən sonra açılır. */
@Entity
@Table(name = "project_interests")
@Getter
@Setter
@NoArgsConstructor
public class ProjectInterest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investor_id")
    private User investor;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterestStatus status = InterestStatus.NEW;

    @Column(columnDefinition = "TEXT")
    private String reply;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Sahibkarın razılaşmanı qəbul etdiyi an: mərhələli maliyyələşmənin başlanğıcı. */
    @Column(name = "accepted_at")
    private Instant acceptedAt;

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
