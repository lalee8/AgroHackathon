package az.aqroinvest.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    List<Application> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

    Optional<Application> findByAppNumber(String appNumber);

    Optional<Application> findByAppNumberAndTrackingCode(String appNumber, String trackingCode);

    boolean existsByApplicantIdAndPlotIdAndCompetitionIdAndStatusNot(Long applicantId, Long plotId, Long competitionId,
                                                                     ApplicationStatus status);

    long countByStatus(ApplicationStatus status);

    long countByCompetitionIdAndStatusNot(Long competitionId, ApplicationStatus status);

    long countByStatusNot(ApplicationStatus status);

    boolean existsByPlotId(Long plotId);

    List<Application> findByCompetitionIdAndStatusNotOrderByAutoScoreDesc(Long competitionId, ApplicationStatus status);

    @Query("select a.status, count(a) from Application a group by a.status")
    List<Object[]> countByStatusGrouped();

    @Query("select a.plot.district, count(a), coalesce(sum(a.investmentAmount),0) from Application a where a.status <> az.aqroinvest.application.ApplicationStatus.DRAFT group by a.plot.district")
    List<Object[]> statsByDistrict();

    @Query("select a.activityType, count(a), coalesce(sum(a.investmentAmount),0) from Application a where a.status <> az.aqroinvest.application.ApplicationStatus.DRAFT group by a.activityType")
    List<Object[]> statsByActivity();

    @Query("select coalesce(sum(a.investmentAmount),0) from Application a where a.status <> az.aqroinvest.application.ApplicationStatus.DRAFT and a.status <> az.aqroinvest.application.ApplicationStatus.WITHDRAWN")
    BigDecimal totalProposedInvestment();

    @Query("select coalesce(sum(a.jobsPlanned),0) from Application a where a.status <> az.aqroinvest.application.ApplicationStatus.DRAFT and a.status <> az.aqroinvest.application.ApplicationStatus.WITHDRAWN")
    Long totalPlannedJobs();

    @Query("select coalesce(sum(a.investmentAmount),0) from Application a where a.decision = az.aqroinvest.application.Decision.APPROVED")
    BigDecimal approvedInvestment();

    @Query("select coalesce(sum(a.jobsPlanned),0) from Application a where a.decision = az.aqroinvest.application.Decision.APPROVED")
    Long approvedJobs();

    @Query("select a.submittedAt from Application a where a.submittedAt is not null and a.submittedAt >= :from")
    List<Instant> submittedSince(Instant from);

    @Query("select a.plot.id, count(a) from Application a where a.status <> az.aqroinvest.application.ApplicationStatus.DRAFT and a.status <> az.aqroinvest.application.ApplicationStatus.WITHDRAWN group by a.plot.id")
    List<Object[]> activeCountsByPlot();

    @Query("select count(a) > 0 from Application a where a.plot.id = :plotId and a.decision = az.aqroinvest.application.Decision.APPROVED")
    boolean existsApprovedForPlot(Long plotId);

    @Query(value = "select nextval('application_number_seq')", nativeQuery = true)
    Long nextApplicationNumber();
}
