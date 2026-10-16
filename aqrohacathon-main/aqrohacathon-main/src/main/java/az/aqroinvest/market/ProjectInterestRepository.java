package az.aqroinvest.market;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectInterestRepository extends JpaRepository<ProjectInterest, Long> {
    @Query("select i from ProjectInterest i join fetch i.investor join fetch i.project p join fetch p.owner where p.id = :projectId order by i.createdAt desc")
    List<ProjectInterest> findByProject(Long projectId);

    @Query("select i from ProjectInterest i join fetch i.investor join fetch i.project p join fetch p.owner where i.investor.id = :investorId order by i.createdAt desc")
    List<ProjectInterest> findByInvestor(Long investorId);

    @Query("select i from ProjectInterest i join fetch i.investor join fetch i.project p join fetch p.owner where p.owner.id = :ownerId order by i.createdAt desc")
    List<ProjectInterest> findByOwner(Long ownerId);

    Optional<ProjectInterest> findByProjectIdAndInvestorId(Long projectId, Long investorId);

    @Query("select i from ProjectInterest i join fetch i.investor join fetch i.project p join fetch p.owner left join fetch p.plot where i.id = :id")
    Optional<ProjectInterest> findFull(Long id);

    long countByProjectId(Long projectId);
}
