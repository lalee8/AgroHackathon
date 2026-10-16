package az.aqroinvest.buyer;

import az.aqroinvest.buyer.BuyerEnums.DemandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BuyerDemandRepository extends JpaRepository<BuyerDemand, Long> {
    @Query("select d from BuyerDemand d join fetch d.buyer where d.status = :status order by d.volumeTons desc")
    List<BuyerDemand> findByStatusFull(DemandStatus status);

    @Query("select d from BuyerDemand d join fetch d.buyer order by d.createdAt desc")
    List<BuyerDemand> findAllFull();

    @Query("select d from BuyerDemand d join fetch d.buyer where d.buyer.id = :buyerId order by d.createdAt desc")
    List<BuyerDemand> findByBuyer(Long buyerId);

    @Query("select d from BuyerDemand d join fetch d.buyer where d.id = :id")
    Optional<BuyerDemand> findFull(Long id);

    long countByStatus(DemandStatus status);
}
