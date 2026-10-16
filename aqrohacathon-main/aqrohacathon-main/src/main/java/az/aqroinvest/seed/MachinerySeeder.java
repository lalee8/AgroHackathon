package az.aqroinvest.seed;

import az.aqroinvest.config.AppProperties;
import az.aqroinvest.machinery.Equipment;
import az.aqroinvest.machinery.EquipmentRepository;
import az.aqroinvest.machinery.EquipmentRequest;
import az.aqroinvest.machinery.EquipmentRequestRepository;
import az.aqroinvest.machinery.EquipmentType;
import az.aqroinvest.plot.District;
import az.aqroinvest.plot.PlotRepository;
import az.aqroinvest.user.Role;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import az.aqroinvest.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Texnika icarəsi üçün demo elanlar: texnika sahibləri (aqroservis şirkətləri və fərdi sahibkarlar) və bir neçə sorğu. */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class MachinerySeeder implements CommandLineRunner {
    private final AppProperties props;
    private final UserRepository users;
    private final PlotRepository plots;
    private final EquipmentRepository equipment;
    private final EquipmentRequestRepository requests;
    private final PasswordEncoder encoder;

    private record E(String owner, EquipmentType type, String title, District d, int price, Equipment.PriceUnit unit, int from, int to,
                     int year, String desc) {}

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed().enabled() || plots.count() == 0 || equipment.count() > 0) return;
        String pw = encoder.encode("Texnika123!");
        User a = owner("aqroservis@example.az", "Qarabağ Aqroservis MMC (Elşən Məmmədli)", "+994 50 410 70 25", pw);
        User f = owner("fuzuli.texnika@example.az", "Füzuli Texnika Parkı (Rövşən Əsgərov)", "+994 55 620 13 48", pw);
        User d = owner("aqrodron@example.az", "AqroDron Xidmət (Kənan Abdullayev)", "+994 70 333 81 19", pw);
        var H = Equipment.PriceUnit.HA;
        var D = Equipment.PriceUnit.DAY;
        var M = Equipment.PriceUnit.MONTH;
        List<E> list = List.of(
                new E(a.getEmail(), EquipmentType.TRACTOR, "John Deere 6155M traktor (155 at gücü)", District.AGDAM, 180, D, 3, 11, 2022, "Operatorla birlikdə. Şum, kultivasiya, daşıma aqreqatları ayrıca."),
                new E(f.getEmail(), EquipmentType.COMBINE, "Claas Lexion 760 taxıl kombaynı", District.FUZULI, 75, H, 6, 8, 2021, "Biçin, operator və yanacaq daxil. Minimum 50 ha."),
                new E(f.getEmail(), EquipmentType.SEEDER, "Kverneland 6 m dəqiq toxumsəpən", District.FUZULI, 25, H, 3, 10, 2023, "Payızlıq və yazlıq səpin üçün."),
                new E(a.getEmail(), EquipmentType.IRRIGATION, "Mobil damcı suvarma dəsti (nasos + 20 ha xətt)", District.CEBRAYIL, 950, M, 4, 10, 2024, "Quraşdırma və təlim daxildir. Bağ və üzümlük üçün."),
                new E(a.getEmail(), EquipmentType.SPRAYER, "Pülverizator 2000 L (bağ üçün, ventilyatorlu)", District.AGDAM, 35, H, 3, 9, 2022, "Traktora qoşulur, operator daxil."),
                new E(d.getEmail(), EquipmentType.DRONE, "DJI Agras T40 aqrodron", District.XOCAVEND, 18, H, 3, 10, 2024, "Dərmanlama və NDVI çəkilişi. Pilot sertifikatlıdır."),
                new E(f.getEmail(), EquipmentType.BALER, "Ot biçən və rulon presləyici dəst", District.LACIN, 30, H, 5, 9, 2020, "Yem tədarükü üçün, operatorla."),
                new E(d.getEmail(), EquipmentType.MILKING, "Mobil sağım aparatı və 2 t soyuducu tank", District.KELBECER, 400, M, 4, 10, 2023, "Yay otlaqları üçün, generatorla."),
                new E(a.getEmail(), EquipmentType.HARVEST_PLATFORM, "Meyvə yığım platforması (4 nəfərlik)", District.ZENGILAN, 150, D, 8, 11, 2022, "Nar və alma yığımında məhsuldarlığı 2 dəfə artırır."),
                new E(f.getEmail(), EquipmentType.CULTIVATOR, "Torpaq frezi və kultivator", District.QUBADLI, 28, H, 3, 11, 2021, "Bağ cərgəarası və istixana torpağı üçün."));
        Instant now = Instant.now();
        int i = 0;
        for (E x : list) {
            Equipment e = new Equipment();
            e.setOwner(users.findByEmailIgnoreCase(x.owner()).orElseThrow());
            e.setType(x.type());
            e.setTitle(x.title());
            e.setDistrict(x.d());
            e.setPrice(BigDecimal.valueOf(x.price()));
            e.setPriceUnit(x.unit());
            e.setSeasonFrom(x.from());
            e.setSeasonTo(x.to());
            e.setSuitable(new java.util.ArrayList<>(x.type().categories().stream().map(Enum::name).toList()));
            e.setDescription(x.desc());
            e.setMadeYear(x.year());
            e.setCreatedAt(now.minus(20 - i++, ChronoUnit.DAYS));
            equipment.save(e);
        }
        // Demo investorun bir sorğusu qəbul edilib, biri gözləyir: kabinetdə və texnika sahibinin panelində görünür
        users.findByEmailIgnoreCase("investor@aqroinvest.az").ifPresent(inv -> {
            var all = equipment.findByActiveTrueOrderByCreatedAtDesc();
            request(all, EquipmentType.SPRAYER, inv, "AGD-004", 60, EquipmentRequest.Status.ACCEPTED);
            request(all, EquipmentType.IRRIGATION, inv, "AGD-004", 6, EquipmentRequest.Status.PENDING);
        });
        log.info("Texnika icarəsi demo məlumatları: {} elan", equipment.count());
    }

    private void request(List<Equipment> all, EquipmentType t, User who, String plotCode, int qty, EquipmentRequest.Status st) {
        Equipment e = all.stream().filter(x -> x.getType() == t).findFirst().orElse(null);
        if (e == null) return;
        EquipmentRequest q = new EquipmentRequest();
        q.setEquipment(e);
        q.setRequester(who);
        q.setPlot(plots.findByCode(plotCode).orElse(null));
        q.setStartDate(LocalDate.now().plusDays(20));
        q.setQuantity(BigDecimal.valueOf(qty));
        q.setTotalPrice(e.getPrice().multiply(BigDecimal.valueOf(qty)));
        q.setStatus(st);
        q.setMessage("Kəngərli bağı üçün mövsüm qabağı");
        if (st != EquipmentRequest.Status.PENDING) q.setDecidedAt(Instant.now());
        requests.save(q);
    }

    private User owner(String email, String name, String phone, String pw) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFullName(name);
            u.setPhone(phone);
            u.setRole(Role.INVESTOR);
            u.setUserType(UserType.INVESTOR);
            u.setPasswordHash(pw);
            return users.save(u);
        });
    }
}
