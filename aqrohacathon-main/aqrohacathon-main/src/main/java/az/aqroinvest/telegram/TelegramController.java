package az.aqroinvest.telegram;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.security.CurrentUser;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "Telegram bildirişləri")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
public class TelegramController {
    private final TelegramService telegram;
    private final UserRepository users;
    private final CurrentUser currentUser;

    @Operation(summary = "Telegram kanalının vəziyyəti")
    @GetMapping("/status")
    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        User u = users.findById(currentUser.require().id()).orElseThrow();
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", telegram.enabled());
        m.put("linked", u.getTelegramChatId() != null);
        m.put("bot", telegram.enabled() ? telegram.botUsername() : null);
        m.put("error", telegram.lastError());
        return m;
    }

    @Operation(summary = "Botu hesaba qoşmaq üçün birdəfəlik deep-link (10 dəqiqə)")
    @PostMapping("/link")
    public Map<String, String> link() {
        if (!telegram.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TELEGRAM_DISABLED", "Telegram botu konfiqurasiya olunmayıb");
        String url = telegram.createLink(currentUser.require().id());
        if (url == null) throw new ApiException(HttpStatus.BAD_GATEWAY, "TELEGRAM_UNAVAILABLE", "Telegram API əlçatan deyil");
        return Map.of("url", url);
    }

    @Operation(summary = "Test mesajı göndər")
    @PostMapping("/test")
    @Transactional(readOnly = true)
    public Map<String, Boolean> test() {
        User u = users.findById(currentUser.require().id()).orElseThrow();
        if (u.getTelegramChatId() == null) throw ApiException.badRequest("NOT_LINKED", "Telegram qoşulmayıb");
        telegram.notifyUser(u.getId(), "Test bildirişi", "Telegram kanalı işləyir.", "/kabinet.html");
        return Map.of("sent", true);
    }

    @Operation(summary = "Telegram-ı ayır")
    @PostMapping("/unlink")
    @Transactional
    public Map<String, Boolean> unlink() {
        users.findById(currentUser.require().id()).ifPresent(u -> u.setTelegramChatId(null));
        return Map.of("linked", false);
    }
}
