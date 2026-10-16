package az.aqroinvest.watch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlotWatchRepository extends JpaRepository<PlotWatch, Long> {
    List<PlotWatch> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PlotWatch> findByUserIdAndPlotId(Long userId, Long plotId);

    List<PlotWatch> findByPlotIdIn(Collection<Long> plotIds);

    long countByPlotId(Long plotId);
}
