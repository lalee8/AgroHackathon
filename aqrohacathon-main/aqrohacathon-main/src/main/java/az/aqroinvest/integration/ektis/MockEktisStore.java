package az.aqroinvest.integration.ektis;

import az.aqroinvest.live.LiveService;
import az.aqroinvest.user.Role;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** EKTİS simulyatorunun "qəbul etdiyi" hadisələrin yaddaşdakı jurnalı (nümayiş üçün). */
@Component
@RequiredArgsConstructor
public class MockEktisStore {
    private final LiveService live;
    private static final int MAX = 300;
    private final Deque<Received> received = new ArrayDeque<>();
    private final Map<String, String> idempotency = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong(1000);
    private final AtomicLong rejected = new AtomicLong();

    public String register(String eventId, String eventType, String aggregateId, JsonNode payload, boolean signatureValid) {
        Received r;
        synchronized (this) {
            String existing = idempotency.get(eventId);
            if (existing != null) return existing;
            String ref = "EKTIS-" + java.time.Year.now() + "-" + counter.incrementAndGet();
            idempotency.put(eventId, ref);
            r = new Received(ref, eventId, eventType, aggregateId, Instant.now(), payload, signatureValid);
            received.addFirst(r);
            while (received.size() > MAX) received.removeLast();
        }
        live.toRole(Role.ADMIN, "ektis", r);
        return r.reference();
    }

    public void rejected(String reason, String eventType) {
        live.toRole(Role.ADMIN, "ektis-rejected", java.util.Map.of("reason", reason, "eventType", eventType == null ? "" : eventType,
                "at", Instant.now().toString()));
    }

    public boolean seen(String eventId) {
        return idempotency.containsKey(eventId);
    }

    public void markRejected() {
        rejected.incrementAndGet();
    }

    public synchronized List<Received> list() {
        return new ArrayList<>(received);
    }

    public long rejectedCount() {
        return rejected.get();
    }

    public record Received(String reference, String eventId, String eventType, String aggregateId, Instant receivedAt,
                           JsonNode payload, boolean signatureValid) {}
}
