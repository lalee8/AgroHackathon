package az.aqroinvest.plot;

import az.aqroinvest.application.ApplicationRepository;
import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.competition.Competition;
import az.aqroinvest.competition.CompetitionRepository;
import az.aqroinvest.competition.CompetitionStatus;
import az.aqroinvest.integration.ektis.IntegrationEventPublisher;
import az.aqroinvest.plot.PlotDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlotService {
    private final PlotRepository plots;
    private final CompetitionRepository competitions;
    private final ApplicationRepository applications;
    private final AuditService audit;
    private final IntegrationEventPublisher events;

    @Transactional(readOnly = true)
    public List<PlotView> search(PlotFilter filter) {
        List<Plot> list = plots.findAll(PlotSpecifications.of(filter), sortOf(filter.sort()));
        Map<Long, ActiveCompetition> active = activeCompetitionsByPlot();
        Map<Long, Long> counts = applicationCounts();
        List<PlotView> views = list.stream().map(p -> toView(p, active.get(p.getId()), counts.getOrDefault(p.getId(), 0L))).toList();
        if (Boolean.TRUE.equals(filter.openOnly())) {
            views = views.stream().filter(v -> v.competition() != null && v.competition().open()).toList();
        }
        if ("deadline".equals(filter.sort())) {
            // Əvvəl müraciəti açıq olanlar (son tarixi ən yaxın olan birinci), sonra qalanları
            views = views.stream().sorted(Comparator
                    .comparing((PlotView v) -> v.competition() == null || !v.competition().open())
                    .thenComparing(v -> v.competition() == null ? null : v.competition().deadlineAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(PlotView::code)).toList();
        } else if ("infra".equals(filter.sort())) {
            views = views.stream().sorted(Comparator.comparingInt(PlotView::infrastructureScore).reversed()).toList();
        }
        return views;
    }

    @Transactional(readOnly = true)
    public PlotView get(Long id) {
        Plot p = find(id);
        return toView(p, activeCompetitionsByPlot().get(id), applicationCounts().getOrDefault(id, 0L));
    }

    @Transactional(readOnly = true)
    public List<PlotView> byIds(List<Long> ids) {
        Map<Long, ActiveCompetition> active = activeCompetitionsByPlot();
        Map<Long, Long> counts = applicationCounts();
        return plots.findAllById(ids).stream()
                .sorted(Comparator.comparingInt(p -> ids.indexOf(p.getId())))
                .map(p -> toView(p, active.get(p.getId()), counts.getOrDefault(p.getId(), 0L))).toList();
    }

    public Plot find(Long id) {
        return plots.findById(id).orElseThrow(() -> ApiException.notFound("Torpaq sahəsi"));
    }

    @Transactional
    public PlotView create(PlotRequest req) {
        if (plots.existsByCode(req.code().trim())) {
            throw ApiException.conflict("CODE_TAKEN", "Bu kodla sahə artıq mövcuddur");
        }
        Plot p = new Plot();
        apply(p, req);
        plots.save(p);
        audit.log("PLOT_CREATED", "Plot", p.getId(), p.getCode() + " - " + p.getName());
        events.publish("PLOT_CREATED", "Plot", p.getId(), Map.of("code", p.getCode(), "name", p.getName(),
                "district", p.getDistrict().name(), "areaHa", p.getAreaHa(), "category", p.getCategory().name()));
        return get(p.getId());
    }

    @Transactional
    public PlotView update(Long id, PlotRequest req) {
        Plot p = find(id);
        plots.findByCode(req.code().trim()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.conflict("CODE_TAKEN", "Bu kodla sahə artıq mövcuddur");
        });
        apply(p, req);
        audit.log("PLOT_UPDATED", "Plot", p.getId(), p.getCode());
        events.publish("PLOT_UPDATED", "Plot", p.getId(), Map.of("code", p.getCode(), "status", p.getStatus().name()));
        return get(p.getId());
    }

    @Transactional
    public void delete(Long id) {
        Plot p = find(id);
        if (applications.existsByPlotId(id)) {
            throw ApiException.conflict("PLOT_HAS_APPLICATIONS", "Bu sahəyə müraciətlər var - silmək mümkün deyil");
        }
        if (competitions.existsWithPlot(id)) {
            throw ApiException.conflict("PLOT_IN_COMPETITION", "Sahə müsabiqəyə daxil edilib - əvvəlcə müsabiqədən çıxarın");
        }
        plots.delete(p);
        audit.log("PLOT_DELETED", "Plot", id, p.getCode());
    }

    private void apply(Plot p, PlotRequest r) {
        p.setCode(r.code().trim().toUpperCase(Locale.ROOT));
        p.setName(r.name().trim());
        p.setDistrict(r.district());
        p.setVillage(r.village());
        p.setCategory(r.category());
        p.setDesignation(r.designation() != null ? r.designation() : LandDesignation.defaultFor(r.category()));
        if (r.scenic() != null) p.setScenic(r.scenic());
        p.setAreaHa(r.areaHa());
        p.setLatitude(r.latitude());
        p.setLongitude(r.longitude());
        p.setPolygon(r.polygon() != null && r.polygon().trim().startsWith("[")
                ? r.polygon().trim()
                : GeoUtil.polygonFor(r.latitude(), r.longitude(), r.areaHa(), r.code().hashCode()));
        p.setSoilType(r.soilType());
        p.setSoilQuality(r.soilQuality());
        p.setWaterSource(r.waterSource());
        p.setIrrigation(r.irrigation());
        p.setRoadDistanceKm(r.roadDistanceKm());
        p.setElectricity(r.electricity());
        p.setGas(r.gas());
        p.setAltitudeM(r.altitudeM());
        p.setSlopePercent(r.slopePercent());
        p.setDeminingStatus(r.deminingStatus());
        p.setDeminingCertificate(r.deminingCertificate());
        p.setUsageType(r.usageType());
        p.setLeaseYears(r.leaseYears());
        p.setAnnualRentPerHa(r.annualRentPerHa());
        p.setMinInvestment(r.minInvestment());
        p.setMinJobs(r.minJobs());
        p.setRecommendedCrops(r.recommendedCrops() == null ? new ArrayList<>() : new ArrayList<>(r.recommendedCrops()));
        p.setDescription(r.description());
        p.setTerms(r.terms());
        if (r.status() != null) p.setStatus(r.status());
    }

    /** Sahənin infrastruktur indeksi (0-100): su, enerji, yol, torpaq keyfiyyəti, relyef. */
    public static int infrastructureScore(Plot p) {
        double s = 0;
        if (p.isIrrigation()) s += 25;
        if (p.isElectricity()) s += 15;
        if (p.isGas()) s += 5;
        double road = p.getRoadDistanceKm() == null ? 10 : p.getRoadDistanceKm().doubleValue();
        s += Math.max(0, 20 - road * 2);
        s += (p.getSoilQuality() == null ? 50 : p.getSoilQuality()) * 0.25;
        double slope = p.getSlopePercent() == null ? 5 : p.getSlopePercent().doubleValue();
        s += Math.max(0, 10 - slope);
        return (int) Math.min(100, Math.round(s));
    }

    public PlotView toView(Plot p, ActiveCompetition comp, long appCount) {
        BigDecimal rentTotal = p.getAnnualRentPerHa().multiply(p.getAreaHa()).setScale(0, RoundingMode.HALF_UP);
        return new PlotView(p.getId(), p.getCode(), p.getName(),
                p.getDistrict(), p.getDistrict().label(), p.getDistrict().region(), p.getVillage(),
                p.getCategory(), p.getCategory().label(),
                p.getDesignation(), p.getDesignation().label(), p.getDesignation().description(), p.isScenic(),
                p.getAreaHa(), p.getLatitude(), p.getLongitude(), p.getPolygon(),
                p.getSoilType(), p.getSoilQuality(), p.getWaterSource(), p.isIrrigation(),
                p.getRoadDistanceKm(), p.isElectricity(), p.isGas(), p.getAltitudeM(), p.getSlopePercent(),
                p.getDeminingStatus(), p.getDeminingStatus().label(), p.getDeminingCertificate(),
                p.getUsageType(), p.getUsageType().label(), p.getLeaseYears(), p.getAnnualRentPerHa(),
                rentTotal, p.getMinInvestment(), p.getMinJobs(),
                p.getRecommendedCrops(), p.getDescription(), p.getTerms(),
                p.getStatus(), p.getStatus().label(),
                comp, appCount, infrastructureScore(p), p.getUpdatedAt());
    }

    public Map<Long, ActiveCompetition> activeCompetitionsByPlot() {
        Map<Long, ActiveCompetition> map = new HashMap<>();
        for (Competition c : competitions.findByStatusOrderByDeadlineAtAsc(CompetitionStatus.PUBLISHED)) {
            ActiveCompetition ac = new ActiveCompetition(c.getId(), c.getCode(), c.getTitle(), c.getDeadlineAt(),
                    c.isAcceptingApplications());
            c.getPlots().forEach(p -> map.putIfAbsent(p.getId(), ac));
        }
        return map;
    }

    public Map<Long, Long> applicationCounts() {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : applications.activeCountsByPlot()) map.put((Long) row[0], (Long) row[1]);
        return map;
    }

    private static Sort sortOf(String sort) {
        if (sort == null) return Sort.by("code");
        return switch (sort) {
            case "area_desc" -> Sort.by(Sort.Direction.DESC, "areaHa");
            case "area_asc" -> Sort.by(Sort.Direction.ASC, "areaHa");
            case "rent_asc" -> Sort.by(Sort.Direction.ASC, "annualRentPerHa");
            case "quality" -> Sort.by(Sort.Direction.DESC, "soilQuality");
            case "investment_asc" -> Sort.by(Sort.Direction.ASC, "minInvestment");
            default -> Sort.by("code");
        };
    }
}
