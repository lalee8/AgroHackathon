package az.aqroinvest.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);

    Optional<Evaluation> findByApplicationIdAndExpertId(Long applicationId, Long expertId);
}
