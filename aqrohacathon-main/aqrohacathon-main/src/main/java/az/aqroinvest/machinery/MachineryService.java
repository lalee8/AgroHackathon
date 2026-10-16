package az.aqroinvest.machinery;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.economics.ModelFactory;
import az.aqroinvest.economics.MonteCarloService;
import az.aqroinvest.economics.SimulationSpec;
import az.aqroinvest.economics.UseCase;
import az.aqroinvest.notification.NotificationService;
import az.aqroinvest.plot.District;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotCategory;
import az.aqroinvest.plot.PlotRepository;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Texnika icarəsi bazarı: texnika sahibi elan edir, sahibkar və ya investor sahəsi üçün icarəyə götürür. Platforma
 * sahənin istiqamətinə görə lazım olan texnikanı göstərir və "texnikanı almaq" ilə "icarəyə götürmək" variantlarını
 * eyni Monte-Carlo modelində müqayisə edir: icarədə ilkin investisiya azalır, illik xərc artır.
 */
@Service
@RequiredArgsConstructor
public class MachineryService {
    private final EquipmentRepository equipment;
    private final EquipmentRequestRepository requests;
    private final PlotRepository plots;
    private final UserRepository users;
    private final NotificationService notifications;
    private final ModelFactory models;
    private final MonteCarloService mc;

    // ------------------------------------------------------------------ DTO

    public record EquipmentView(Long id, String type, String typeLabel, String icon, String purpose, String title,
                                String district, String districtLabel, double lat, double lng, BigDecimal price,
                                String priceUnit, String priceLabel, Integer seasonFrom, Integer seasonTo,
                                List<String> suitable, String description, Integer madeYear, String ownerName,
                                Long ownerId, long acceptedRequests, Double distanceKm) {}

    public record CreateRequest(@NotNull EquipmentType type, @NotBlank @Size(max = 160) String title, @NotNull District district,
                                @NotNull @DecimalMin("1") @DecimalMax("100000") BigDecimal price, @NotNull Equipment.PriceUnit priceUnit,
                                @Min(1) @Max(12) Integer seasonFrom, @Min(1) @Max(12) Integer seasonTo, List<String> suitable,
                                @Size(max = 600) String description, @Min(1980) @Max(2030) Integer madeYear) {}

    public record RentRequest(Long plotId, @NotNull LocalDate startDate,
                              @NotNull @DecimalMin(value = "0.5", message = "Miqdar müsbət olmalıdır") @DecimalMax("5000") BigDecimal quantity,
                              @Size(max = 500) String message) {}

    public record RequestView(Long id, Long equipmentId, String equipmentTitle, String equipmentType, String typeLabel,
                              String ownerName, String requesterName, String plotCode, String plotName, LocalDate startDate,
                              BigDecimal quantity, String unitLabel, BigDecimal totalPrice, String status, String statusLabel,
                              String message, Instant createdAt, Instant decidedAt) {}

    public record Need(String type, String label, String icon, String purpose) {}

    public record Option(String mode, long capex, Double irrPct, Double roiPct, Integer paybackP50, long npvP50, long annualNetMature,
                         double probPayback) {}

    public record Compare(String useCase, String useLabel, double scale, String unit, long machineryCapex, long rentPerYear,
                          Option buy, Option rent, List<String> notes) {}

    public record PlotMachinery(Long plotId, String plotCode, String useCase, String useLabel, List<Need> needed,
                                List<EquipmentView> listings, Compare compare) {}

    // ------------------------------------------------------------------ elanlar

    @Transactional(readOnly = true)
    public List<EquipmentView> list(EquipmentType type, District district, PlotCategory category) {
        return equipment.findByActiveTrueOrderByCreatedAtDesc().stream()
                .filter(e -> type == null || e.getType() == type)
                .filter(e -> district == null || e.getDistrict() == district)
                .filter(e -> category == null || e.getSuitable().contains(category.name()))
                .map(e -> view(e, null)).toList();
    }

    @Transactional(readOnly = true)
    public EquipmentView get(Long id) {
        return view(find(id), null);
    }

    @Transactional
    public EquipmentView create(Long ownerId, CreateRequest r) {
        User owner = users.findById(ownerId).orElseThrow(() -> ApiException.notFound("İstifadəçi"));
        Equipment e = new Equipment();
        e.setOwner(owner);
        e.setType(r.type());
        e.setTitle(r.title().trim());
        e.setDistrict(r.district());
        e.setPrice(r.price().setScale(2, RoundingMode.HALF_UP));
        e.setPriceUnit(r.priceUnit());
        e.setSeasonFrom(r.seasonFrom());
        e.setSeasonTo(r.seasonTo());
        List<String> suit = r.suitable() == null || r.suitable().isEmpty()
                ? r.type().categories().stream().map(Enum::name).toList()
                : r.suitable().stream().filter(s -> Arrays.stream(PlotCategory.values()).anyMatch(c -> c.name().equals(s))).distinct().toList();
        e.setSuitable(new ArrayList<>(suit));
        e.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        e.setMadeYear(r.madeYear());
        return view(equipment.save(e), null);
    }

    @Transactional(readOnly = true)
    public List<EquipmentView> mine(Long ownerId) {
        return equipment.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(e -> view(e, null)).toList();
    }

    // ------------------------------------------------------------------ icarə sorğuları

    @Transactional
    public RequestView rent(Long equipmentId, Long requesterId, RentRequest r) {
        Equipment e = find(equipmentId);
        if (!e.isActive()) throw ApiException.badRequest("NOT_AVAILABLE", "Bu texnika hazırda icarəyə verilmir");
        if (e.getOwner().getId().equals(requesterId)) throw ApiException.badRequest("OWN_EQUIPMENT", "Öz texnikanızı icarəyə götürə bilməzsiniz");
        if (r.startDate().isBefore(LocalDate.now())) throw ApiException.badRequest("PAST_DATE", "Başlama tarixi keçmişdə ola bilməz");
        User u = users.findById(requesterId).orElseThrow(() -> ApiException.notFound("İstifadəçi"));
        EquipmentRequest q = new EquipmentRequest();
        q.setEquipment(e);
        q.setRequester(u);
        if (r.plotId() != null) q.setPlot(plots.findById(r.plotId()).orElseThrow(() -> ApiException.notFound("Sahə")));
        q.setStartDate(r.startDate());
        q.setQuantity(r.quantity().setScale(2, RoundingMode.HALF_UP));
        q.setMessage(r.message() == null || r.message().isBlank() ? null : r.message().trim());
        q.setTotalPrice(e.getPrice().multiply(r.quantity()).setScale(2, RoundingMode.HALF_UP));
        requests.save(q);
        notifications.notify(e.getOwner().getId(), "EQUIPMENT_REQUEST", "Texnika üçün icarə sorğusu",
                u.getFullName() + ": " + e.getTitle() + ", " + q.getQuantity().stripTrailingZeros().toPlainString() + " " + unit(e.getPriceUnit())
                        + (q.getPlot() != null ? " (" + q.getPlot().getCode() + ")" : ""), "/texnika.html#mine");
        return requestView(q);
    }

    @Transactional(readOnly = true)
    public List<RequestView> myRequests(Long requesterId) {
        return requests.findByRequesterIdOrderByCreatedAtDesc(requesterId).stream().map(this::requestView).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestView> incoming(Long ownerId) {
        return requests.findByEquipmentOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::requestView).toList();
    }

    @Transactional
    public RequestView decide(Long requestId, Long ownerId, boolean accept) {
        EquipmentRequest q = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Sorğu"));
        if (!q.getEquipment().getOwner().getId().equals(ownerId)) throw ApiException.notFound("Sorğu");
        if (q.getStatus() != EquipmentRequest.Status.PENDING) throw ApiException.badRequest("ALREADY_DECIDED", "Sorğu artıq cavablandırılıb");
        q.setStatus(accept ? EquipmentRequest.Status.ACCEPTED : EquipmentRequest.Status.DECLINED);
        q.setDecidedAt(Instant.now());
        notifications.notify(q.getRequester().getId(), "EQUIPMENT_DECISION", accept ? "İcarə sorğunuz qəbul edildi" : "İcarə sorğunuz rədd edildi",
                q.getEquipment().getTitle() + (accept ? ": sahibi ilə əlaqə açıldı" : ""), "/texnika.html#mine");
        return requestView(q);
    }

    @Transactional
    public RequestView cancel(Long requestId, Long requesterId) {
        EquipmentRequest q = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Sorğu"));
        if (!q.getRequester().getId().equals(requesterId)) throw ApiException.notFound("Sorğu");
        if (q.getStatus() != EquipmentRequest.Status.PENDING) throw ApiException.badRequest("ALREADY_DECIDED", "Sorğu artıq cavablandırılıb");
        q.setStatus(EquipmentRequest.Status.CANCELLED);
        q.setDecidedAt(Instant.now());
        return requestView(q);
    }

    // ------------------------------------------------------------------ sahə üçün texnika və al/icarə müqayisəsi

    /** Texnikanın kapitaldakı payı, icarə xərci (₼/ha/il) və alınan texnikanın illik baxım xərci (texnika dəyərinin %-i). */
    private record Econ(double machineryShare, double rentPerHa, double maintenance) {}

    private static final Map<PlotCategory, Econ> ECON = Map.of(
            PlotCategory.EKINCILIK, new Econ(0.5, 150, 0.07),
            PlotCategory.HEYVANDARLIQ, new Econ(0.12, 110, 0.07),
            PlotCategory.BAGCILIQ, new Econ(0.08, 160, 0.07),
            PlotCategory.UZUMCULUK, new Econ(0.1, 150, 0.07));

    @Transactional(readOnly = true)
    public PlotMachinery forPlot(Long plotId, UseCase use) {
        Plot p = plots.findById(plotId).orElseThrow(() -> ApiException.notFound("Sahə"));
        UseCase u = use != null ? use : UseCase.valueOf(p.getCategory().name());
        PlotCategory cat = u.category();
        List<Need> needed = cat == null ? List.of() : Arrays.stream(EquipmentType.values()).filter(t -> t.categories().contains(cat))
                .map(t -> new Need(t.name(), t.label(), t.icon(), t.purpose())).toList();
        List<EquipmentView> near = cat == null ? List.of() : equipment.findByActiveTrueOrderByCreatedAtDesc().stream()
                .filter(e -> e.getSuitable().contains(cat.name()))
                .map(e -> view(e, km(p.getLatitude(), p.getLongitude(), e.getDistrict().lat(), e.getDistrict().lng())))
                .sorted(Comparator.comparingDouble(EquipmentView::distanceKm)).limit(8).toList();
        return new PlotMachinery(p.getId(), p.getCode(), u.name(), u.label(), needed, near, compare(p, u));
    }

    Compare compare(Plot p, UseCase u) {
        PlotCategory cat = u.category();
        Econ econ = cat == null ? null : ECON.get(cat);
        if (econ == null) return null;
        SimulationSpec buy = models.build(p, u, ModelFactory.Overrides.none());
        double machinery = buy.capex() * econ.machineryShare();
        double perHaMachinery = machinery / Math.max(1, buy.scale());
        // İcarədə: texnika alınmır (ilkin investisiya azalır), əvəzinə hər il icarə haqqı ödənilir, baxım xərci isə düşür
        double extraOpexPerHa = econ.rentPerHa() - econ.maintenance() * perHaMachinery;
        SimulationSpec rent = new SimulationSpec(buy.useCase(), buy.unit(), buy.scale(), buy.capex() - machinery, buy.yieldPerUnit(),
                buy.yieldUnit(), buy.price(), buy.priceUnit(), buy.opexFixed(), buy.opexPerUnit() + extraOpexPerHa, buy.opexRevenueShare(),
                buy.ramp(), buy.yieldSigma(), buy.priceSigma(), buy.priceDrift(), buy.shocks(), buy.degradation(), buy.horizon(),
                buy.discountRate(), buy.offtakeShare(), buy.offtakePriceFactor(), buy.capexOverrunMean(), buy.capexOverrunSigma(),
                buy.jobs(), buy.assumptions());
        long seed = 31L * p.getId() + u.ordinal();
        MonteCarloService.Summary a = mc.simulate(buy, 600, seed).summary(), b = mc.simulate(rent, 600, seed).summary();
        List<String> notes = List.of(
                String.format(Locale.ROOT, "Texnika ilkin investisiyanın təxminən %d%%-idir (%s)", Math.round(econ.machineryShare() * 100), cat.label().toLowerCase(Locale.forLanguageTag("az"))),
                String.format(Locale.ROOT, "İcarə haqqı: %d ₼/ha/il; alınan texnikanın baxımı: dəyərinin %d%%-i/il", Math.round(econ.rentPerHa()), Math.round(econ.maintenance() * 100)),
                "Hər iki variant eyni 600 gələcək ssenaridə hesablanıb: fərq yalnız texnikanın alınması və ya icarəsindədir");
        return new Compare(u.name(), u.label(), buy.scale(), buy.unit(), Math.round(machinery), Math.round(econ.rentPerHa() * buy.scale()),
                option("BUY", buy, a), option("RENT", rent, b), notes);
    }

    private static Option option(String mode, SimulationSpec s, MonteCarloService.Summary m) {
        // ROI: yetkin ildə illik xalis gəlirin ilkin investisiyaya nisbəti
        double roi = s.capex() > 0 ? Math.round(m.annualNetMature() / s.capex() * 1000) / 10.0 : 0;
        return new Option(mode, Math.round(s.capex()), m.irrP50(), roi, m.paybackP50(), m.npvP50(),
                m.annualNetMature(), m.probPayback());
    }

    // ------------------------------------------------------------------ köməkçilər

    private Equipment find(Long id) {
        return equipment.findById(id).orElseThrow(() -> ApiException.notFound("Texnika"));
    }

    private EquipmentView view(Equipment e, Double km) {
        return new EquipmentView(e.getId(), e.getType().name(), e.getType().label(), e.getType().icon(), e.getType().purpose(), e.getTitle(),
                e.getDistrict().name(), e.getDistrict().label(), e.getDistrict().lat(), e.getDistrict().lng(), e.getPrice(),
                e.getPriceUnit().name(), priceLabel(e), e.getSeasonFrom(), e.getSeasonTo(), e.getSuitable(), e.getDescription(),
                e.getMadeYear(), e.getOwner().getFullName(), e.getOwner().getId(),
                requests.countByEquipmentIdAndStatus(e.getId(), EquipmentRequest.Status.ACCEPTED), km == null ? null : Math.round(km * 10) / 10.0);
    }

    private RequestView requestView(EquipmentRequest q) {
        Equipment e = q.getEquipment();
        return new RequestView(q.getId(), e.getId(), e.getTitle(), e.getType().name(), e.getType().label(), e.getOwner().getFullName(),
                q.getRequester().getFullName(), q.getPlot() == null ? null : q.getPlot().getCode(), q.getPlot() == null ? null : q.getPlot().getName(),
                q.getStartDate(), q.getQuantity(), unit(e.getPriceUnit()), q.getTotalPrice(), q.getStatus().name(), statusLabel(q.getStatus()),
                q.getMessage(), q.getCreatedAt(), q.getDecidedAt());
    }

    static String unit(Equipment.PriceUnit u) {
        return switch (u) {
            case DAY -> "gün";
            case HA -> "ha";
            case MONTH -> "ay";
        };
    }

    private static String priceLabel(Equipment e) {
        return e.getPrice().stripTrailingZeros().toPlainString() + " ₼/" + unit(e.getPriceUnit());
    }

    private static String statusLabel(EquipmentRequest.Status s) {
        return switch (s) {
            case PENDING -> "Cavab gözlənilir";
            case ACCEPTED -> "Qəbul edildi";
            case DECLINED -> "Rədd edildi";
            case CANCELLED -> "Ləğv edildi";
        };
    }

    static double km(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371, dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
