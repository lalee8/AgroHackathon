package az.aqroinvest.watch;

import az.aqroinvest.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Sevimli sahələr", description = "Sahəni izləmək: müsabiqəyə çıxanda bildiriş")
@RestController
@RequiredArgsConstructor
public class WatchController {
    private final WatchService watches;
    private final CurrentUser currentUser;

    @Operation(summary = "Mənim sevimli sahələrim")
    @GetMapping("/api/v1/me/watches")
    public List<WatchService.WatchView> mine() {
        return watches.mine(currentUser.require().id());
    }

    @Operation(summary = "Sahəni sevimlilərə əlavə et (müsabiqəyə çıxanda bildiriş)")
    @PostMapping("/api/v1/plots/{id}/watch")
    public Map<String, Object> watch(@PathVariable Long id) {
        return watches.watch(currentUser.require().id(), id, true);
    }

    @Operation(summary = "Sahəni sevimlilərdən çıxar")
    @DeleteMapping("/api/v1/plots/{id}/watch")
    public Map<String, Object> unwatch(@PathVariable Long id) {
        return watches.watch(currentUser.require().id(), id, false);
    }
}
