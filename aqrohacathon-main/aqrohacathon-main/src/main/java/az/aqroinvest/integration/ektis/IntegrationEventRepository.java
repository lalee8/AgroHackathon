package az.aqroinvest.integration.ektis;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, Long> {

    @Query("select e from IntegrationEvent e where e.status in (az.aqroinvest.integration.ektis.IntegrationEventStatus.PENDING, az.aqroinvest.integration.ektis.IntegrationEventStatus.FAILED) and e.nextAttemptAt <= :now order by e.id asc")
    List<IntegrationEvent> findDue(Instant now, Pageable pageable);

    Page<IntegrationEvent> findAllByOrderByIdDesc(Pageable pageable);

    long countByStatus(IntegrationEventStatus status);
}
