package az.aqroinvest.competition;

import az.aqroinvest.plot.Plot;
import az.aqroinvest.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "competitions")
@Getter
@Setter
@NoArgsConstructor
public class Competition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompetitionStatus status = CompetitionStatus.DRAFT;

    /** Qiymətləndirmə meyarlarının çəkiləri (JSON). */
    @Column(columnDefinition = "TEXT")
    private String criteria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @ManyToMany
    @JoinTable(name = "competition_plots",
            joinColumns = @JoinColumn(name = "competition_id"),
            inverseJoinColumns = @JoinColumn(name = "plot_id"))
    @OrderBy("code ASC")
    private Set<Plot> plots = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /** Müraciət qəbulu açıqdırmı? */
    public boolean isAcceptingApplications() {
        Instant now = Instant.now();
        return status == CompetitionStatus.PUBLISHED && !now.isBefore(startAt) && now.isBefore(deadlineAt);
    }
}
