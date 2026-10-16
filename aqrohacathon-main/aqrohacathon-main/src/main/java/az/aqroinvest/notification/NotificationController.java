package az.aqroinvest.notification;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Bildirişlər")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationRepository repository;
    private final CurrentUser currentUser;

    @Operation(summary = "Son 30 bildiriş və oxunmamışların sayı")
    @GetMapping
    public Map<String, Object> list() {
        Long uid = currentUser.require().id();
        List<Notification> items = repository.findTop30ByUserIdOrderByCreatedAtDesc(uid);
        return Map.of("unread", repository.countByUserIdAndReadFalse(uid), "items", items);
    }

    @Operation(summary = "Bildirişi oxunmuş et")
    @PostMapping("/{id}/read")
    @Transactional
    public void read(@PathVariable Long id) {
        Notification n = repository.findById(id).orElseThrow(() -> ApiException.notFound("Bildiriş"));
        if (!n.getUserId().equals(currentUser.require().id())) throw ApiException.notFound("Bildiriş");
        n.setRead(true);
    }

    @Operation(summary = "Hamısını oxunmuş et")
    @PostMapping("/read-all")
    @Transactional
    public Map<String, Integer> readAll() {
        return Map.of("updated", repository.markAllRead(currentUser.require().id()));
    }
}
