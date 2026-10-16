package az.aqroinvest.notification;

import az.aqroinvest.live.LiveService;
import az.aqroinvest.telegram.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * İstifadəçi bildirişləri. Portal daxili bildirişlər bazada saxlanılır; e-poçt/SMS kanalı
 * (məs. ASAN Xidmət SMS şlüzü) üçün göndərmə burada simulyasiya olunur və jurnala yazılır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repository;
    private final LiveService live;
    private final TelegramService telegram;

    @Transactional
    public void notify(Long userId, String type, String title, String message, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        repository.save(n);
        live.toUser(userId, "notification", java.util.Map.of("id", n.getId(), "type", type, "title", title,
                "message", message == null ? "" : message, "link", link == null ? "" : link));
        telegram.notifyUser(userId, title, message, link);
        log.info("[BİLDİRİŞ → user#{}] {} - {}", userId, title, message);
    }
}
