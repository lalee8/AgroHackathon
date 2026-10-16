package az.aqroinvest.mygov;

import az.aqroinvest.security.AuthDtos.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "mygov ID ilə giriş", description = "Dövlət identifikasiyası ilə giriş (QR və ya digər üsullar). Test mühitində identifikasiya tərəfi simulyasiya olunur")
@RestController
@RequiredArgsConstructor
public class MyGovIdController {
    private final MyGovIdService mygov;

    public record ApproveRequest(String state, String citizenId, Boolean allow) {}

    public record ExchangeRequest(String code) {}

    @Operation(summary = "Giriş sessiyası açmaq (state)")
    @PostMapping("/api/v1/auth/mygov/start")
    public Map<String, Object> start() {
        return mygov.start();
    }

    @Operation(summary = "Sessiyanın vəziyyəti: təsdiqlənibsə birdəfəlik kod")
    @GetMapping("/api/v1/auth/mygov/status")
    public Map<String, Object> status(@RequestParam String state) {
        return mygov.status(state);
    }

    @Operation(summary = "Birdəfəlik kodu portalın tokeninə dəyişmək")
    @PostMapping("/api/v1/auth/mygov/exchange")
    public AuthResponse exchange(@RequestBody ExchangeRequest req) {
        return mygov.exchange(req.code());
    }

    @Operation(summary = "Test mühiti: test vətəndaşları (FİN maskalanıb)")
    @GetMapping("/api/v1/mock/mygov/citizens")
    public List<Map<String, String>> citizens() {
        return mygov.citizens();
    }

    @Operation(summary = "Test mühiti: vətəndaşın təsdiqi (real axında mygov tətbiqi edir)")
    @PostMapping("/api/v1/mock/mygov/approve")
    public Map<String, Object> approve(@RequestBody ApproveRequest req) {
        return mygov.approve(req.state(), req.citizenId(), req.allow() == null || req.allow());
    }
}
