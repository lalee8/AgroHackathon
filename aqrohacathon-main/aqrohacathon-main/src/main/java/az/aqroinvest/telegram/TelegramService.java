package az.aqroinvest.telegram;

import az.aqroinvest.config.AppProperties;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Telegram bildiriş kanalı (istəyə bağlı). İstifadəçi kabinetdə QR/deep-link vasitəsilə botu öz hesabına qoşur
 * (/start &lt;birdəfəlik kod&gt;), sonra hər bildiriş həm də Telegram-a göndərilir.
 * Webhook əvəzinə long polling istifadə olunur: server internetə açıq olmasa da (NAT arxasında) işləyir.
 */
@Slf4j
@Service
public class TelegramService {
    private final AppProperties props;
    private final UserRepository users;
    private final TransactionTemplate tx;
    private final RestClient http;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, LinkCode> codes = new ConcurrentHashMap<>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "telegram");
        t.setDaemon(true);
        return t;
    });
    private volatile String botUsername;
    private volatile long offset = 0;
    private volatile String lastError;

    record LinkCode(Long userId, Instant expiresAt) {}

    public TelegramService(AppProperties props, UserRepository users, TransactionTemplate tx) {
        this.props = props;
        this.users = users;
        this.tx = tx;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5000);
        rf.setReadTimeout(10000);
        this.http = RestClient.builder().requestFactory(rf).build();
    }

    public boolean enabled() {
        return props.telegram() != null && props.telegram().enabled();
    }

    private String url(String method) {
        return props.telegram().apiUrl() + "/bot" + props.telegram().botToken() + "/" + method;
    }

    public String botUsername() {
        if (!enabled()) return null;
        if (botUsername == null) {
            try {
                JsonNode n = http.get().uri(url("getMe")).retrieve().body(JsonNode.class);
                botUsername = n == null ? null : n.path("result").path("username").asText(null);
                lastError = null;
            } catch (Exception e) {
                lastError = "Telegram API əlçatan deyil və ya token yanlışdır";
                log.warn("Telegram getMe alınmadı: {}", e.getMessage());
            }
        }
        return botUsername;
    }

    public String lastError() {
        return lastError;
    }

    /** Birdəfəlik qoşulma kodu (10 dəqiqə) və deep-link. */
    public String createLink(Long userId) {
        String bot = botUsername();
        if (bot == null) return null;
        byte[] b = new byte[9];
        random.nextBytes(b);
        String code = HexFormat.of().formatHex(b);
        codes.put(code, new LinkCode(userId, Instant.now().plusSeconds(600)));
        return "https://t.me/" + bot + "?start=" + code;
    }

    public void send(Long chatId, String html) {
        if (!enabled() || chatId == null) return;
        Runnable r = () -> sender.execute(() -> {
            try {
                http.post().uri(url("sendMessage")).contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("chat_id", chatId, "text", html, "parse_mode", "HTML", "disable_web_page_preview", true))
                        .retrieve().toBodilessEntity();
            } catch (Exception e) {
                log.warn("Telegram mesajı göndərilmədi: {}", e.getMessage());
            }
        });
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

    public void notifyUser(Long userId, String title, String message, String link) {
        if (!enabled()) return;
        users.findById(userId).map(User::getTelegramChatId).ifPresent(chatId -> {
            String abs = link == null || link.isBlank() ? null : props.baseUrl() + link;
            String text = "🔔 <b>" + esc(title) + "</b>" + (message == null || message.isBlank() ? "" : "\n" + esc(message))
                    + (abs == null ? "" : "\n\n" + esc(abs)) + "\n\n<i>Əkinçi 2.0</i>";
            send(chatId, text);
        });
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 5000)
    public void poll() {
        if (!enabled()) return;
        codes.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(Instant.now()));
        JsonNode resp;
        try {
            resp = http.get().uri(url("getUpdates") + "?timeout=0&allowed_updates=%5B%22message%22%5D&offset=" + offset)
                    .retrieve().body(JsonNode.class);
        } catch (Exception e) {
            lastError = "Telegram API əlçatan deyil";
            return;
        }
        if (resp == null || !resp.path("ok").asBoolean()) return;
        for (JsonNode u : resp.path("result")) {
            offset = Math.max(offset, u.path("update_id").asLong() + 1);
            JsonNode msg = u.path("message");
            long chatId = msg.path("chat").path("id").asLong();
            String text = msg.path("text").asText("").trim();
            if (text.startsWith("/start")) handleStart(chatId, text.length() > 6 ? text.substring(6).trim() : "");
            else if (text.startsWith("/stop")) handleStop(chatId);
        }
    }

    private void handleStart(long chatId, String code) {
        LinkCode lc = codes.remove(code);
        if (lc == null || lc.expiresAt().isBefore(Instant.now())) {
            send(chatId, "Salam! Bildirişləri qoşmaq üçün Əkinçi 2.0 portalında <b>Kabinetim</b> bölməsindəki QR kodu skan edin.");
            return;
        }
        String name = tx.execute(s -> users.findById(lc.userId()).map(u -> {
            users.findByTelegramChatId(chatId).filter(o -> !o.getId().equals(u.getId())).ifPresent(o -> o.setTelegramChatId(null));
            u.setTelegramChatId(chatId);
            return u.getFullName();
        }).orElse(null));
        if (name != null) {
            send(chatId, "✅ <b>" + esc(name) + "</b>, bildirişlər qoşuldu.\nMüraciətinizin statusu dəyişən kimi burada xəbər alacaqsınız.\n\nAyırmaq üçün: /stop");
        }
    }

    private void handleStop(long chatId) {
        tx.executeWithoutResult(s -> users.findByTelegramChatId(chatId).ifPresent(u -> u.setTelegramChatId(null)));
        send(chatId, "Bildirişlər dayandırıldı.");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
