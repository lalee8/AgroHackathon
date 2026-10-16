package az.aqroinvest.application;

import az.aqroinvest.common.StringListConverter;
import az.aqroinvest.competition.Competition;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotCategory;
import az.aqroinvest.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** İnvestisiya müsabiqəsinə elektron müraciət. */
@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Rəsmi müraciət nömrəsi, məs. AQR-2026-000042 (göndəriləndə yaranır). */
    @Column(name = "app_number", unique = true, length = 30)
    private String appNumber;

    /** Girişsiz status yoxlaması üçün qısa izləmə kodu. */
    @Column(name = "tracking_code", nullable = false, length = 12)
    private String trackingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id")
    private User applicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competition_id")
    private Competition competition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plot_id")
    private Plot plot;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicant_type", nullable = false, length = 10)
    private ApplicantType applicantType;

    @Column(name = "company_name")
    private String companyName;

    private String voen;

    private String fin;

    /** Xarici vətəndaş üçün vətəndaşlıq və pasport nömrəsi. */
    @Column(length = 60)
    private String citizenship;

    @Column(name = "passport_no", length = 20)
    private String passportNo;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "legal_address")
    private String legalAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", length = 30)
    private PlotCategory activityType;

    @Column(name = "investment_amount", precision = 14, scale = 2)
    private BigDecimal investmentAmount;

    @Column(name = "own_funds_percent")
    private Integer ownFundsPercent;

    @Column(name = "jobs_planned")
    private Integer jobsPlanned;

    @Column(name = "implementation_months")
    private Integer implementationMonths;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> technologies = new ArrayList<>();

    @Column(name = "business_plan", columnDefinition = "TEXT")
    private String businessPlan;

    @Column(name = "expected_annual_revenue", precision = 14, scale = 2)
    private BigDecimal expectedAnnualRevenue;

    /** Məcburi köçkün / Qarabağ əsilli sahibkar (sosial prioritet meyarı). */
    @Column(name = "idp_status")
    private boolean idpStatus;

    @Column(name = "consent_given")
    private boolean consentGiven;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Decision decision;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "auto_score")
    private Integer autoScore;

    @Column(name = "score_breakdown", columnDefinition = "TEXT")
    private String scoreBreakdown;

    /** EKTİS fermer reyestrində yoxlamanın nəticəsi. */
    @Column(name = "ektis_farmer_status", length = 30)
    private String ektisFarmerStatus;

    /** EKTİS tərəfindən qaytarılan istinad nömrəsi. */
    @Column(name = "ektis_reference", length = 40)
    private String ektisReference;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ApplicationStatusHistory> history = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("uploadedAt ASC")
    private List<ApplicationDocument> documents = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public String displayName() {
        return companyName != null && !companyName.isBlank() ? companyName : applicant.getFullName();
    }
}
