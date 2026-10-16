package az.aqroinvest.live;

import az.aqroinvest.user.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real vaxt yeniləmələri (Server-Sent Events). Brauzerin EventSource API-si başlıq göndərə bilmədiyi üçün
 * JWT URL-ə yazılmır: əvəzində 60 saniyəlik birdəfəlik "bilet" verilir (token loglara düşmür).
 * Hadisələr DB tranzaksiyası uğurla bitdikdən sonra göndərilir.
 */
@Slf4j
@Service
public class LiveService {
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final List<Client> clients = new CopyOnWriteArrayList<>();

    record Ticket(Long userId, Role role, Instant expiresAt) {}

    record Client(Long userId, Role role, SseEmitter emitter) {}

    public String issueTicket(Long userId, Role role) {
        byte[] b = new byte[24];
        random.nextBytes(b);
        String t = HexFormat.of().formatHex(b);
        tickets.put(t, new Ticket(userId, role, Instant.now().plusSeconds(60)));
        return t;
    }

    public SseEmitter connect(String ticket) {
        Ticket t = ticket == null ? null : tickets.remove(ticket);
        if (t == null || t.expiresAt().isBefore(Instant.now())) return null;
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Client c = new Client(t.userId(), t.role(), emitter);
        clients.add(c);
        emitter.onCompletion(() -> clients.remove(c));
        emitter.onTimeout(() -> clients.remove(c));
        emitter.onError(e -> clients.remove(c));
        try {
            emitter.send(SseEmitter.event().name("hello").data(Map.of("connectedAt", Instant.now().toString())));
        } catch (IOException e) {
            clients.remove(c);
        }
        return emitter;
    }

    public void toUser(Long userId, String event, Object data) {
        afterCommit(() -> clients.stream().filter(c -> c.userId().equals(userId)).forEach(c -> send(c, event, data)));
    }

    public void toRole(Role role, String event, Object data) {
        afterCommit(() -> clients.stream().filter(c -> c.role() == role).forEach(c -> send(c, event, data)));
    }

    private void send(Client c, String event, Object data) {
        try {
            c.emitter().send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            clients.remove(c);
        }
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    /** Proksi və yük balanslaşdırıcıların əlaqəni kəsməməsi üçün ürək döyüntüsü. */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(Instant.now()));
        for (Client c : clients) {
            try {
                c.emitter().send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                clients.remove(c);
            }
        }
    }

    public int connectedClients() {
        return clients.size();
    }
}
