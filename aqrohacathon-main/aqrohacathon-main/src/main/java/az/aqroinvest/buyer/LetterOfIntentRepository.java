package az.aqroinvest.buyer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LetterOfIntentRepository extends JpaRepository<LetterOfIntent, Long> {
    String FETCH = "select l from LetterOfIntent l join fetch l.demand d join fetch d.buyer join fetch l.requester left join fetch l.plot left join fetch l.application left join fetch l.project ";

    @Query(FETCH + "where l.requester.id = :userId order by l.createdAt desc")
    List<LetterOfIntent> findByRequester(Long userId);

    @Query(FETCH + "where d.buyer.id = :buyerId order by l.createdAt desc")
    List<LetterOfIntent> findByBuyer(Long buyerId);

    @Query(FETCH + "where l.application.id = :applicationId order by l.createdAt desc")
    List<LetterOfIntent> findByApplication(Long applicationId);

    @Query(FETCH + "where l.project.id = :projectId order by l.createdAt desc")
    List<LetterOfIntent> findByProject(Long projectId);

    @Query(FETCH + "where l.id = :id")
    java.util.Optional<LetterOfIntent> findFull(Long id);
}
