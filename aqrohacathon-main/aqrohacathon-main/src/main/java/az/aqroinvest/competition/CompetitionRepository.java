package az.aqroinvest.competition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
    Optional<Competition> findByCode(String code);

    List<Competition> findByStatusOrderByDeadlineAtAsc(CompetitionStatus status);

    List<Competition> findAllByOrderByCreatedAtDesc();

    long countByStatus(CompetitionStatus status);

    @Query("select count(c) > 0 from Competition c join c.plots p where p.id = :plotId")
    boolean existsWithPlot(Long plotId);

    @Query("select c from Competition c join c.plots p where p.id = :plotId and c.status = az.aqroinvest.competition.CompetitionStatus.PUBLISHED")
    List<Competition> findPublishedByPlot(Long plotId);
}
