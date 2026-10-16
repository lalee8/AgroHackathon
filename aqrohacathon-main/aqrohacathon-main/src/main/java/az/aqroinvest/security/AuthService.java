package az.aqroinvest.security;

import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.config.AppProperties;
import az.aqroinvest.security.AuthDtos.*;
import az.aqroinvest.user.Role;
import az.aqroinvest.user.UserType;
import az.aqroinvest.user.LegalType;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final LoginRateLimiter rateLimiter;
    private final AppProperties props;
    private final az.aqroinvest.market.InvestorProfileRepository investorProfiles;
    private final az.aqroinvest.market.EntrepreneurProfileRepository entrepreneurProfiles;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Bu e-poçt ilə artıq hesab mövcuddur");
        }
        User u = new User();
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPhone(blankToNull(req.phone()));
        u.setFin(req.fin() == null || req.fin().isBlank() ? null : req.fin().toUpperCase(Locale.ROOT));
        u.setPasswordHash(encoder.encode(req.password()));
        // Qeydiyyatla yalnız investor, sahibkar və ya alıcı hesabı yaradıla bilər, əməkdaş rolları yox
        u.setRole("BUYER".equalsIgnoreCase(req.accountType()) ? Role.BUYER : Role.INVESTOR);
        if (u.getRole() == Role.INVESTOR) u.setUserType("ENTREPRENEUR".equalsIgnoreCase(req.accountType()) ? UserType.ENTREPRENEUR : UserType.INVESTOR);
        u.setLastLoginAt(Instant.now());
        applyLegal(u, req.legal(), false);
        users.save(u);
        saveTypeProfile(u, req.investor(), req.entrepreneur());
        audit.logAs(u.getId(), u.getEmail(), "USER_REGISTERED", "User", u.getId(), "Yeni hesab: " + label(u) + ", " + u.getLegalType().label());
        return toResponse(u);
    }

    /** Hesab növünün seçilməsi (mygov ID ilə ilk girişdən sonra) və ya dəyişdirilməsi. */
    @Transactional
    public UserView setAccountType(Long userId, AccountTypeUpdate req) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("İstifadəçi"));
        if (u.getRole() != Role.INVESTOR) throw ApiException.badRequest("NOT_ALLOWED", "Bu hesab üçün növ seçilmir");
        u.setUserType("ENTREPRENEUR".equalsIgnoreCase(req.accountType()) ? UserType.ENTREPRENEUR : UserType.INVESTOR);
        if (req.legal() != null) applyLegal(u, req.legal(), u.getMygovSub() != null);
        saveTypeProfile(u, req.investor(), req.entrepreneur());
        audit.logAs(u.getId(), u.getEmail(), "ACCOUNT_TYPE_SET", "User", u.getId(), label(u) + (u.getLegalType() == null ? "" : ", " + u.getLegalType().label()));
        return toView(u);
    }

    /**
     * Hüquqi status üzrə tələblər: fiziki şəxs FİN, hüquqi şəxs şirkətin adı və VÖEN, xarici vətəndaş vətəndaşlıq və
     * pasport nömrəsi göstərir. mygov ID yalnız Azərbaycan vətəndaşları üçündür, ona görə mygov hesabı xarici ola bilməz.
     */
    void applyLegal(User u, LegalFields l, boolean viaMygov) {
        LegalType t = l != null && enumOk(LegalType.class, l.legalType()) ? LegalType.valueOf(l.legalType()) : LegalType.INDIVIDUAL;
        java.util.Map<String, String> err = new java.util.LinkedHashMap<>();
        if (t == LegalType.FOREIGN && viaMygov) err.put("legal.legalType", "mygov ID ilə daxil olan hesab xarici vətəndaş ola bilməz");
        String voen = l == null ? null : blankToNull(l.voen());
        String company = l == null ? null : blankToNull(l.companyName());
        switch (t) {
            case INDIVIDUAL -> {
                if (u.getFin() == null) err.put("fin", "Fiziki şəxs üçün FİN kod tələb olunur");
            }
            case COMPANY -> {
                if (company == null) err.put("legal.companyName", "Şirkətin adı tələb olunur");
                if (voen == null) err.put("legal.voen", "Hüquqi şəxs üçün VÖEN tələb olunur");
            }
            case FOREIGN -> {
                if (l == null || blankToNull(l.citizenship()) == null) err.put("legal.citizenship", "Vətəndaşlıq tələb olunur");
                if (l == null || blankToNull(l.passportNo()) == null) err.put("legal.passportNo", "Pasport nömrəsi tələb olunur");
            }
        }
        if (!err.isEmpty()) throw ApiException.fields("Hüquqi status üzrə məlumat natamamdır", err);
        u.setLegalType(t);
        u.setVoen(voen);
        u.setCompanyName(t == LegalType.INDIVIDUAL ? null : company);
        u.setCompanyForm(t == LegalType.COMPANY && l != null ? blankToNull(l.companyForm()) : null);
        u.setRepPosition(t == LegalType.COMPANY && l != null ? blankToNull(l.repPosition()) : null);
        u.setCitizenship(t == LegalType.FOREIGN ? blankToNull(l.citizenship()) : null);
        u.setPassportNo(t == LegalType.FOREIGN ? l.passportNo().replace(" ", "").toUpperCase(Locale.ROOT) : null);
        if (t == LegalType.FOREIGN && !viaMygov) u.setFin(null);
        boolean local = t == LegalType.INDIVIDUAL && l != null && Boolean.TRUE.equals(l.localResident());
        u.setLocalResident(local);
        u.setHomeDistrict(local && enumOk(az.aqroinvest.plot.District.class, l.homeDistrict()) ? az.aqroinvest.plot.District.valueOf(l.homeDistrict()) : null);
        if (l != null && blankToNull(l.prefLang()) != null) u.setPrefLang(l.prefLang());
    }

    private void saveTypeProfile(User u, InvestorFields inv, EntrepreneurFields ent) {
        if (u.getUserType() == UserType.INVESTOR && inv != null) {
            var p = investorProfiles.findById(u.getId()).orElseGet(() -> {
                var n = new az.aqroinvest.market.InvestorProfile();
                n.setUserId(u.getId());
                return n;
            });
            p.setTicketMin(inv.ticketMin());
            p.setTicketMax(inv.ticketMax());
            if (inv.ticketMin() != null && inv.ticketMax() != null && inv.ticketMax().compareTo(inv.ticketMin()) < 0) {
                throw ApiException.badRequest("BAD_RANGE", "Maksimum məbləğ minimumdan az ola bilməz");
            }
            p.setSectors(new java.util.ArrayList<>(inv.sectors() == null ? java.util.List.of()
                    : inv.sectors().stream().filter(x -> enumOk(az.aqroinvest.market.ProjectSector.class, x)).distinct().toList()));
            if (enumOk(az.aqroinvest.market.MarketEnums.Risk.class, inv.risk())) p.setRisk(az.aqroinvest.market.MarketEnums.Risk.valueOf(inv.risk()));
            investorProfiles.save(p);
        }
        if (u.getUserType() == UserType.ENTREPRENEUR && ent != null) {
            var p = entrepreneurProfiles.findById(u.getId()).orElseGet(() -> {
                var n = new az.aqroinvest.market.EntrepreneurProfile();
                n.setUserId(u.getId());
                return n;
            });
            p.setDistrict(enumOk(az.aqroinvest.plot.District.class, ent.district()) ? az.aqroinvest.plot.District.valueOf(ent.district()) : null);
            p.setSector(enumOk(az.aqroinvest.market.ProjectSector.class, ent.sector()) ? az.aqroinvest.market.ProjectSector.valueOf(ent.sector()) : null);
            p.setLandStatus(enumOk(az.aqroinvest.market.EntrepreneurProfile.LandStatus.class, ent.landStatus())
                    ? az.aqroinvest.market.EntrepreneurProfile.LandStatus.valueOf(ent.landStatus()) : null);
            p.setLandHa(ent.landHa());
            p.setNeedAmount(ent.needAmount());
            p.setIdea(blankToNull(ent.idea()));
            entrepreneurProfiles.save(p);
        }
    }

    private static <E extends Enum<E>> boolean enumOk(Class<E> type, String v) {
        if (v == null || v.isBlank()) return false;
        try {
            Enum.valueOf(type, v);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * mygov ID ilə giriş: dövlət identifikasiyasından gələn FİN-ə görə hesab tapılır (və ya yaradılır). Şifrə tələb
     * olunmur; yeni hesabda növ (investor/sahibkar) sonra seçilir.
     */
    @Transactional
    public AuthResponse loginWithMyGov(String fin, String fullName, String phone) {
        String sub = "mygov:" + fin.toUpperCase(Locale.ROOT);
        User u = users.findByMygovSub(sub).or(() -> users.findFirstByFinIgnoreCase(fin)).orElse(null);
        boolean created = false;
        if (u == null) {
            u = new User();
            u.setEmail(fin.toLowerCase(Locale.ROOT) + "@mygov-id.local");
            u.setFullName(fullName);
            u.setPhone(phone);
            u.setFin(fin.toUpperCase(Locale.ROOT));
            u.setRole(Role.INVESTOR);
            u.setLegalType(LegalType.INDIVIDUAL);
            u.setPasswordHash(encoder.encode(java.util.UUID.randomUUID().toString()));
            created = true;
        }
        if (!u.isEnabled()) throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Hesab deaktiv edilib");
        u.setMygovSub(sub);
        if (u.getFin() == null) u.setFin(fin.toUpperCase(Locale.ROOT));
        u.setLastLoginAt(Instant.now());
        users.save(u);
        audit.logAs(u.getId(), u.getEmail(), created ? "USER_REGISTERED_MYGOV" : "LOGIN_MYGOV", "User", u.getId(), "mygov ID");
        return toResponse(u);
    }

    private static String label(User u) {
        return u.getRole() == Role.INVESTOR && u.getUserType() != null ? u.getUserType().label() : u.getRole().label();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest req) {
        if (!rateLimiter.tryAcquire(AuditService.clientIp())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Çox sayda cəhd. Bir dəqiqə sonra yenidən yoxlayın.");
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        User u = users.findByEmailIgnoreCase(email).orElse(null);
        if (u == null) {
            // İstifadəçinin mövcudluğunu açıqlamamaq üçün eyni mesaj
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "E-poçt və ya şifrə yanlışdır");
        }
        if (!u.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Hesab deaktiv edilib");
        }
        if (u.isLocked()) {
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Hesab müvəqqəti bloklanıb. " + props.security().lockMinutes() + " dəqiqə sonra yenidən cəhd edin.");
        }
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            u.setFailedLogins(u.getFailedLogins() + 1);
            if (u.getFailedLogins() >= props.security().maxFailedLogins()) {
                u.setLockedUntil(Instant.now().plus(props.security().lockMinutes(), ChronoUnit.MINUTES));
                u.setFailedLogins(0);
                audit.logAs(u.getId(), u.getEmail(), "ACCOUNT_LOCKED", "User", u.getId(), "Ardıcıl uğursuz giriş cəhdləri");
            }
            users.save(u);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "E-poçt və ya şifrə yanlışdır");
        }
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        u.setLastLoginAt(Instant.now());
        users.save(u);
        audit.logAs(u.getId(), u.getEmail(), "LOGIN", "User", u.getId(), null);
        return toResponse(u);
    }

    private AuthResponse toResponse(User u) {
        JwtService.IssuedToken token = jwt.issue(u);
        return new AuthResponse(token.value(), token.expiresAt(), toView(u));
    }

    public static UserView toView(User u) {
        LegalType t = u.getLegalType();
        return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getFin(), u.getRole(),
                label(u), u.getCreatedAt(), u.getUserType() == null ? null : u.getUserType().name(), u.getMygovSub() != null,
                t == null ? null : t.name(), t == null ? null : t.label(), u.getCompanyName(), u.getCompanyForm(), u.getVoen(),
                u.getRepPosition(), u.getCitizenship(), u.getPassportNo(), u.isLocalResident(),
                u.getHomeDistrict() == null ? null : u.getHomeDistrict().name(), u.getHomeDistrict() == null ? null : u.getHomeDistrict().label(),
                u.getPrefLang(), displayName(u));
    }

    /** Hüquqi şəxsdə şirkətin adı və nümayəndə, digərlərində ad və soyad. */
    public static String displayName(User u) {
        if (u.getCompanyName() != null && u.getLegalType() != LegalType.INDIVIDUAL) return u.getCompanyName() + " (" + u.getFullName() + ")";
        return u.getFullName();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
