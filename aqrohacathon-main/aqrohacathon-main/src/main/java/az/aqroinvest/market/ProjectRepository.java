package az.aqroinvest.market;

import az.aqroinvest.market.MarketEnums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @Query("select p from Project p join fetch p.owner left join fetch p.plot where p.status = :status order by p.verified desc, p.createdAt desc")
    List<Project> findPublished(ProjectStatus status);

    @Query("select p from Project p join fetch p.owner left join fetch p.plot where p.owner.id = :ownerId order by p.createdAt desc")
    List<Project> findByOwner(Long ownerId);

    @Query("select p from Project p join fetch p.owner left join fetch p.plot where p.id = :id")
    Optional<Project> findFull(Long id);

    @Query("select p from Project p join fetch p.owner left join fetch p.plot where p.plot.id = :plotId and p.status = 'PUBLISHED'")
    List<Project> findPublishedByPlot(Long plotId);

    long countByStatus(ProjectStatus status);
}
