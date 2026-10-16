package az.aqroinvest.advisor;

import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Tag(name = "AI investisiya məsləhətçisi", description = "Platforma məlumatına əsaslanan söhbət: kəndli dilini investor dilinə çevirir")
@RestController
@RequestMapping("/api/v1/advisor")
public class AdvisorController {
    private final AdvisorService advisor;
    private final int perMinute;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "advisor");
        t.setDaemon(true);
        return t;
    });

    public AdvisorController(AdvisorService advisor, az.aqroinvest.config.AppProperties props) {
        this.advisor = advisor;
        this.perMinute = props.ai() == null ? 20 : Math.max(1, props.ai().requestsPerMinute());
    }

    public record ChatRequest(@NotEmpty @Size(max = 30) List<AdvisorService.Message> messages, String lang) {}

    @Operation(summary = "Məsləhətçinin rejimi: AI və ya oflayn (platforma məlumatından qaydalarla)")
    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean on = advisor.llmEnabled();
        return Map.of("llm", on, "mode", on ? "ai" : "offline", "label", on ? "Əkinçi AI" : "Əkinçi AI (oflayn rejim)");
    }

    @Operation(summary = "Söhbət (tam cavab)")
    @PostMapping("/chat")
    public AdvisorService.Reply chat(@RequestBody ChatRequest req) {
        limit();
        return advisor.reply(sanitize(req.messages()));
    }

    @Operation(summary = "Söhbət (axın): meta (kartlar), delta (mətn hissələri), done")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest req, HttpServletResponse response) {
        limit();
        List<AdvisorService.Message> history = sanitize(req.messages());
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        SseEmitter emitter = new SseEmitter(180_000L);
        pool.execute(() -> {
            try {
                AdvisorGuard.Verdict verdict = advisor.verdict(history);
                // Kənar sualda platforma konteksti (simulyasiyalar) ümumiyyətlə hesablanmır: imtina dərhal gəlir
                AdvisorContextService.Context ctx = verdict == AdvisorGuard.Verdict.OFF_TOPIC ? null : advisor.context(history);
                // Kənar və ya qeyri-müəyyən sualda kartlar göstərilmir
                boolean onTopic = verdict == AdvisorGuard.Verdict.ON_TOPIC;
                emitter.send(SseEmitter.event().name("meta").data(Map.of("intent", onTopic ? ctx.intent().summary() : "", "cards", onTopic ? advisor.cards(ctx) : List.of(),
                        "llm", advisor.llmEnabled(),
                        "mode", advisor.llmEnabled() ? "ai" : "offline"), MediaType.APPLICATION_JSON));
                String lang = req.lang() == null ? null : req.lang().toLowerCase(java.util.Locale.ROOT);
                String mode = advisor.stream(history, ctx, t -> {
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(Map.of("t", t), MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }, lang);
                emitter.send(SseEmitter.event().name("done").data(Map.of("mode", AdvisorService.publicMode(mode)), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (UncheckedIOException e) {
                // İstifadəçi səhifəni bağlayıb və ya yeni sual verib: xəta deyil, axını sakitcə bağla
                log.debug("Məsləhətçi axını kəsildi: {}", e.getMessage());
                emitter.complete();
            } catch (Exception e) {
                log.warn("Məsləhətçi axını xətası: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private static final class UncheckedIOException extends RuntimeException {
        UncheckedIOException(IOException e) { super(e); }
    }

    /** Söhbət tarixçəsi: istifadəçi ilə başlayır, rollar növbələşir, son 12 mesaj, hər biri 2000 simvola qədər. */
    static List<AdvisorService.Message> sanitize(List<AdvisorService.Message> in) {
        if (in == null) throw ApiException.badRequest("NO_QUESTION", "Sual yazın");
        List<AdvisorService.Message> out = new ArrayList<>();
        for (AdvisorService.Message m : in) {
            if (m == null || m.content() == null || m.content().isBlank()) continue;
            String role = "assistant".equals(m.role()) ? "assistant" : "user";
            String text = m.content().length() > 2000 ? m.content().substring(0, 2000) : m.content();
            if (out.isEmpty() && role.equals("assistant")) continue;
            if (!out.isEmpty() && out.get(out.size() - 1).role().equals(role)) {
                AdvisorService.Message prev = out.remove(out.size() - 1);
                out.add(new AdvisorService.Message(role, prev.content() + "\n" + text));
            } else out.add(new AdvisorService.Message(role, text));
        }
        while (out.size() > 12) out.remove(0);
        while (!out.isEmpty() && !out.get(0).role().equals("user")) out.remove(0);
        if (out.isEmpty() || !out.get(out.size() - 1).role().equals("user")) throw ApiException.badRequest("NO_QUESTION", "Sual yazın");
        return out;
    }

    private void limit() {
        String ip = AuditService.clientIp();
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(ip == null ? "?" : ip, k -> new ConcurrentLinkedDeque<>());
        while (!q.isEmpty() && q.peekFirst() < now - 60_000) q.pollFirst();
        if (q.size() >= perMinute) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Çox sayda sual. Bir dəqiqə sonra yenidən yoxlayın.");
        q.addLast(now);
    }
}
