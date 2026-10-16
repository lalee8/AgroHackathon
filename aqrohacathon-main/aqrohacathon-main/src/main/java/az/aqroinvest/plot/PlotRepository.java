package az.aqroinvest.plot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PlotRepository extends JpaRepository<Plot, Long>, JpaSpecificationExecutor<Plot> {
    Optional<Plot> findByCode(String code);

    boolean existsByCode(String code);

    long countByStatus(PlotStatus status);

    @Query("select coalesce(sum(p.areaHa), 0) from Plot p")
    BigDecimal totalArea();

    @Query("select p.district, count(p), coalesce(sum(p.areaHa),0) from Plot p group by p.district")
    List<Object[]> statsByDistrict();

    @Query("select p.category, count(p), coalesce(sum(p.areaHa),0) from Plot p group by p.category")
    List<Object[]> statsByCategory();
}
