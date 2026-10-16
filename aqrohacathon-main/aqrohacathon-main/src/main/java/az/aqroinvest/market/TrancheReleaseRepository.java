package az.aqroinvest.market;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrancheReleaseRepository extends JpaRepository<TrancheRelease, Long> {
    List<TrancheRelease> findByInterestIdOrderBySeq(Long interestId);

    boolean existsByInterestIdAndSeq(Long interestId, int seq);
}
