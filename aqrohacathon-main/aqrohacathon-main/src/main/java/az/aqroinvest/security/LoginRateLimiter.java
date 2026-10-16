package az.aqroinvest.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP ünvanı üzrə sürüşən pəncərə ilə giriş cəhdlərinin məhdudlaşdırılması (brute-force qarşısı).
 * Klaster mühitində Redis əsaslı limiterlə əvəz oluna bilər.
 */
@Component
public class LoginRateLimiter {
    private static final long WINDOW_SECONDS = 60;
    private final int maxAttempts;

    public LoginRateLimiter(az.aqroinvest.config.AppProperties props) {
        this.maxAttempts = props.security() == null ? 20 : props.security().loginLimit();
    }

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        if (key == null) return true;
        Instant now = Instant.now();
        Deque<Instant> q = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minusSeconds(WINDOW_SECONDS))) q.pollFirst();
            if (q.size() >= maxAttempts) return false;
            q.addLast(now);
            return true;
        }
    }
}
