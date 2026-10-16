package az.aqroinvest.plot;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PlotSpecifications {
    private PlotSpecifications() {}

    static Specification<Plot> of(PlotDtos.PlotFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.category() != null && !f.category().isEmpty()) p.add(root.get("category").in(f.category()));
            if (f.district() != null && !f.district().isEmpty()) p.add(root.get("district").in(f.district()));
            if (f.minArea() != null) p.add(cb.greaterThanOrEqualTo(root.get("areaHa"), f.minArea()));
            if (f.maxArea() != null) p.add(cb.lessThanOrEqualTo(root.get("areaHa"), f.maxArea()));
            if (f.maxRent() != null) p.add(cb.lessThanOrEqualTo(root.get("annualRentPerHa"), f.maxRent()));
            if (Boolean.TRUE.equals(f.irrigation())) p.add(cb.isTrue(root.get("irrigation")));
            if (Boolean.TRUE.equals(f.electricity())) p.add(cb.isTrue(root.get("electricity")));
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("village"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
