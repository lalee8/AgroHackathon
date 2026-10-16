package az.aqroinvest.live;

import az.aqroinvest.security.AuthPrincipal;
import az.aqroinvest.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Tag(name = "Real vaxt yeniləmələri (SSE)")
@RestController
@RequestMapping("/api/v1/live")
@RequiredArgsConstructor
public class LiveController {
    private final LiveService live;
    private final CurrentUser currentUser;

    @Operation(summary = "SSE bağlantısı üçün 60 saniyəlik birdəfəlik bilet")
    @PostMapping("/ticket")
    public Map<String, String> ticket() {
        AuthPrincipal me = currentUser.require();
        return Map.of("ticket", live.issueTicket(me.id(), me.role()));
    }

    @Operation(summary = "Server-Sent Events axını (bildirişlər, statuslar, EKTİS hadisələri)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String ticket, HttpServletResponse response) {
        SseEmitter e = live.connect(ticket);
        if (e == null) {
            response.setStatus(401);
            return null;
        }
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return e;
    }
}
