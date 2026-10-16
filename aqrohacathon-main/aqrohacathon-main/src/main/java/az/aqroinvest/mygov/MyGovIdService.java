package az.aqroinvest.mygov;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.security.AuthDtos.AuthResponse;
import az.aqroinvest.security.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mygov ID ilə giriş (OIDC "authorization code" axınının prototipi).
 *
 * Axın: portal sessiya (state) açır → istifadəçi QR kodu telefonda skan edib və ya "digər üsullar" ilə təsdiqləyir →
 * portal təsdiqi sorğulayır və birdəfəlik kodu öz tokeninə dəyişir. Real mygov ID-yə qoşulmaq üçün Rəqəmsal İnkişaf
 * və Nəqliyyat Nazirliyinin verdiyi client_id/secret lazımdır; o vaxta qədər identifikasiya tərəfi test mühitində
 * simulyasiya olunur (test vətəndaşları, real şifrə istənilmir). Portalın tərəfi (sessiya, kod mübadiləsi, FİN üzrə
 * hesab) dəyişmir: real adapter yalnız {@link #approve} hissəsini əvəz edir.
 */
@Service
public class MyGovIdService {
    public enum Status { PENDING, APPROVED, DENIED, USED, EXPIRED }

    /** Test mühitinin vətəndaşları. Birincisi demo investorun, ikincisi demo sahibkarın hesabına uyğun gəlir. */
    public record Citizen(String id, String fin, String fullName, String phone, String note) {}

    static final List<Citizen> CITIZENS = List.of(
            new Citizen("c1", "5ZK8M2P", "Kamran Əhmədov", "+994 50 321 45 67", "Mövcud investor hesabı"),
            new Citizen("c2", "3HN7QW1", "Tural Babayev", "+994 55 410 22 11", "Mövcud sahibkar hesabı"),
            new Citizen("c3", "7RQ4TZ5", "Nigar Quliyeva", "+994 51 233 80 14", "Yeni istifadəçi: ilk giriş"));

    private static final long TTL_MS = 5 * 60_000L;
    private static final SecureRandom RND = new SecureRandom();

    private static final class Session {
        final long createdAt = System.currentTimeMillis();
        volatile Status status = Status.PENDING;
        volatile Citizen citizen;
        volatile String code;
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> codes = new ConcurrentHashMap<>();
    private final AuthService auth;

    public MyGovIdService(AuthService auth) {
        this.auth = auth;
    }

    public Map<String, Object> start() {
        cleanup();
        if (sessions.size() > 5000) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_SESSIONS", "Çox sayda açıq sessiya");
        String state = token(24);
        sessions.put(state, new Session());
        return Map.of("state", state, "expiresAt", Instant.now().plusMillis(TTL_MS).toString());
    }

    public Map<String, Object> status(String state) {
        Session s = session(state);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", s.status.name());
        if (s.status == Status.APPROVED) m.put("code", s.code);
        return m;
    }

    /** Test mühiti: vətəndaşın təsdiqi (real mygov ID-də bu addımı mygov tətbiqi edir). */
    public Map<String, Object> approve(String state, String citizenId, boolean allow) {
        Session s = session(state);
        if (s.status != Status.PENDING) throw ApiException.badRequest("ALREADY_DECIDED", "Bu sorğu artıq cavablandırılıb");
        if (!allow) {
            s.status = Status.DENIED;
            return Map.of("status", s.status.name());
        }
        Citizen c = CITIZENS.stream().filter(x -> x.id().equals(citizenId)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_CITIZEN", "Test vətəndaşı tapılmadı"));
        s.citizen = c;
        s.code = token(24);
        s.status = Status.APPROVED;
        codes.put(s.code, state);
        return Map.of("status", s.status.name());
    }

    /** Birdəfəlik kod → portalın tokeni. Kod ikinci dəfə işləmir. */
    public AuthResponse exchange(String code) {
        String state = code == null ? null : codes.remove(code);
        Session s = state == null ? null : sessions.get(state);
        if (s == null || s.status != Status.APPROVED || expired(s)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CODE", "Kod etibarsızdır və ya vaxtı keçib");
        }
        s.status = Status.USED;
        Citizen c = s.citizen;
        return auth.loginWithMyGov(c.fin(), c.fullName(), c.phone());
    }

    public List<Map<String, String>> citizens() {
        return CITIZENS.stream().map(c -> Map.of("id", c.id(), "fullName", c.fullName(),
                "fin", c.fin().substring(0, 2) + "***" + c.fin().substring(5), "note", c.note())).toList();
    }

    private Session session(String state) {
        Session s = state == null ? null : sessions.get(state);
        if (s == null) throw ApiException.notFound("Sessiya");
        if (expired(s) && s.status == Status.PENDING) s.status = Status.EXPIRED;
        return s;
    }

    private static boolean expired(Session s) {
        return System.currentTimeMillis() - s.createdAt > TTL_MS;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> now - e.getValue().createdAt > 2 * TTL_MS);
        codes.values().removeIf(st -> !sessions.containsKey(st));
    }

    private static String token(int bytes) {
        byte[] b = new byte[bytes];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
