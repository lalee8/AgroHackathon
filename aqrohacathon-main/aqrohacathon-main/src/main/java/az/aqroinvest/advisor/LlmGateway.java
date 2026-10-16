package az.aqroinvest.advisor;

import az.aqroinvest.config.AppProperties;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.BadRequestException;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * Dil modelinə vahid giriş: Google Gemini (REST, SSE axını) və ya Anthropic Claude (rəsmi Java SDK). Hansının işləyəcəyini
 * {@link AppProperties.Ai#active()} seçir. Xəta və ya imtina halında çağıran tərəf oflayn cavaba keçir, ona görə bu sinif
 * heç vaxt yarımçıq vəziyyətdə "uğurlu" qaytarmır.
 */
@Slf4j
@Service
public class LlmGateway {
    public enum Outcome { COMPLETED, REFUSED }

    /** Model çağırışı alınmadı. emitted: istifadəçiyə artıq mətn göndərilibmi (təkrar cəhd etmək olmaz). */
    public static class LlmException extends RuntimeException {
        final int status;
        final boolean emitted;

        LlmException(int status, String message, boolean emitted) {
            super(message);
            this.status = status;
            this.emitted = emitted;
        }
    }

    private static final Set<String> BLOCKED = Set.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION", "IMAGE_SAFETY");

    private final AppProperties.Ai cfg;
    private final ObjectMapper mapper;
    private volatile AnthropicClient claude;

    public LlmGateway(AppProperties props, ObjectMapper mapper) {
        this.cfg = props.ai();
        this.mapper = mapper;
    }

    /** "gemini", "claude" və ya null (oflayn). */
    public String provider() {
        return cfg == null ? null : cfg.active();
    }

    public boolean enabled() {
        return provider() != null;
    }

    public String model() {
        String p = provider();
        if ("gemini".equals(p)) return cfg.gemini().model();
        if ("claude".equals(p)) return cfg.model();
        return "offline";
    }

    public static String label(String provider) {
        return switch (provider == null ? "offline" : provider) {
            case "gemini", "claude", "ai" -> "Əkinçi AI";
            default -> "Oflayn rejim";
        };
    }

    public String vendor() {
        String p = provider();
        return "gemini".equals(p) ? "Google Gemini" : "claude".equals(p) ? "Anthropic Claude" : "Qaydalara əsaslanan oflayn rejim";
    }

    /**
     * Cavabı hissə-hissə yazır. history rolları "user" / "assistant" olmalı və istifadəçi ilə başlamalıdır.
     * @throws LlmException model əlçatmazdırsa (çağıran oflayn rejimə keçir)
     */
    public Outcome stream(String system, List<AdvisorService.Message> history, Consumer<String> onText) {
        String p = provider();
        if ("gemini".equals(p)) return gemini(system, history, onText);
        if ("claude".equals(p)) return claude(system, history, onText);
        throw new LlmException(0, "AI açarı təyin edilməyib", false);
    }

    /** Tam mətn (axınsız istifadə üçün). Alınmasa null. */
    public String complete(String system, String user) {
        if (!enabled()) return null;
        StringBuilder sb = new StringBuilder();
        try {
            Outcome o = stream(system, List.of(new AdvisorService.Message("user", user)), sb::append);
            return o == Outcome.COMPLETED && !sb.isEmpty() ? sb.toString().trim() : null;
        } catch (RuntimeException e) {
            log.warn("AI cavabı alınmadı: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ Gemini

    /** Kvotası bitmiş (429) modellər bir müddət atlanır ki, hər sualda əvvəlcə uğursuz sorğu gözlənilməsin. */
    private final java.util.Map<String, java.time.Instant> cooldown = new java.util.concurrent.ConcurrentHashMap<>();

    private Outcome gemini(String system, List<AdvisorService.Message> history, Consumer<String> onText) {
        AppProperties.Ai.Gemini g = cfg.gemini();
        LinkedHashSet<String> models = new LinkedHashSet<>();
        models.add(g.model());
        if (g.fallbackModel() != null && !g.fallbackModel().isBlank()) models.add(g.fallbackModel().trim());
        boolean[] emitted = {false};
        Consumer<String> out = t -> {
            emitted[0] = true;
            onText.accept(t);
        };
        LlmException last = null;
        java.time.Instant now = java.time.Instant.now();
        List<String> order = new ArrayList<>(models.stream().filter(m -> cooldown.getOrDefault(m, java.time.Instant.EPOCH).isBefore(now)).toList());
        if (order.isEmpty()) order.addAll(models);
        for (String model : order) {
            boolean thinking = g.thinking() != null && !g.thinking().isBlank() && !"none".equalsIgnoreCase(g.thinking());
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    return geminiOnce(g, model, thinking, system, history, out, emitted);
                } catch (LlmException e) {
                    last = e;
                    if (e.emitted) throw e;
                    // Bəzi modellər düşünmə səviyyəsini dəstəkləmir: parametrsiz təkrarla
                    if (e.status == 400 && thinking && e.getMessage().toLowerCase(Locale.ROOT).contains("think")) {
                        thinking = false;
                        continue;
                    }
                    if (e.status == 400 || e.status == 401 || e.status == 403) throw e;
                    if (e.status == 429) cooldown.put(model, java.time.Instant.now().plus(java.time.Duration.ofMinutes(20)));
                    log.info("Gemini {} əlçatan deyil ({}), növbəti model sınanır", model, e.status);
                    break;
                }
            }
        }
        throw last == null ? new LlmException(0, "Gemini modeli yoxdur", false) : last;
    }

    private Outcome geminiOnce(AppProperties.Ai.Gemini g, String model, boolean thinking, String system,
                               List<AdvisorService.Message> history, Consumer<String> out, boolean[] emitted) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        ArrayNode contents = body.putArray("contents");
        for (AdvisorService.Message m : history) {
            ObjectNode c = contents.addObject();
            c.put("role", "assistant".equals(m.role()) ? "model" : "user");
            c.putArray("parts").addObject().put("text", m.content());
        }
        ObjectNode gen = body.putObject("generationConfig");
        gen.put("maxOutputTokens", Math.max(256, cfg.maxTokens()));
        gen.put("temperature", 0.4);
        if (thinking) gen.putObject("thinkingConfig").put("thinkingLevel", g.thinking().trim().toLowerCase(Locale.ROOT));

        HttpURLConnection c = null;
        try {
            String base = g.baseUrl() == null || g.baseUrl().isBlank() ? "https://generativelanguage.googleapis.com/v1beta" : g.baseUrl().replaceAll("/+$", "");
            c = (HttpURLConnection) URI.create(base + "/models/" + model + ":streamGenerateContent?alt=sse").toURL().openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(60000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "text/event-stream");
            c.setRequestProperty("x-goog-api-key", g.apiKey().trim());
            try (OutputStream os = c.getOutputStream()) {
                os.write(mapper.writeValueAsBytes(body));
            }
            int code = c.getResponseCode();
            if (code != 200) throw new LlmException(code, errorMessage(c), false);
            String finish = null, block = null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String json = line.substring(5).trim();
                    if (json.isEmpty() || "[DONE]".equals(json)) continue;
                    JsonNode d = mapper.readTree(json);
                    JsonNode fb = d.path("promptFeedback").path("blockReason");
                    if (fb.isTextual()) block = fb.asText();
                    for (JsonNode cand : d.path("candidates")) {
                        for (JsonNode part : cand.path("content").path("parts")) {
                            if (part.path("thought").asBoolean(false)) continue;
                            String t = part.path("text").asText("");
                            if (!t.isEmpty()) out.accept(AdvisorService.clean(t));
                        }
                        if (cand.path("finishReason").isTextual()) finish = cand.path("finishReason").asText();
                    }
                }
            }
            if (block != null || (finish != null && BLOCKED.contains(finish))) {
                log.info("Gemini cavabı məhdudlaşdırıldı: {}", block != null ? block : finish);
                return Outcome.REFUSED;
            }
            if (!emitted[0]) throw new LlmException(502, "Gemini boş cavab qaytardı", false);
            return Outcome.COMPLETED;
        } catch (LlmException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new LlmException(503, "Gemini ilə əlaqə: " + e.getClass().getSimpleName() + " " + e.getMessage(), emitted[0]);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String errorMessage(HttpURLConnection c) {
        try (InputStream es = c.getErrorStream()) {
            if (es == null) return "HTTP " + c.getResponseCode();
            String raw = new String(es.readNBytes(8192), StandardCharsets.UTF_8);
            JsonNode n = mapper.readTree(raw);
            String msg = n.path("error").path("message").asText("");
            return "HTTP " + c.getResponseCode() + (msg.isBlank() ? "" : ": " + msg);
        } catch (Exception e) {
            return "HTTP xətası";
        }
    }

    // ------------------------------------------------------------------ Claude

    private AnthropicClient claudeClient() {
        if (claude == null) {
            synchronized (this) {
                if (claude == null) {
                    AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder().apiKey(cfg.apiKey());
                    if (cfg.baseUrl() != null && !cfg.baseUrl().isBlank()) b.baseUrl(cfg.baseUrl());
                    claude = b.build();
                }
            }
        }
        return claude;
    }

    private Outcome claude(String system, List<AdvisorService.Message> history, Consumer<String> onText) {
        boolean[] emitted = {false};
        Consumer<String> out = t -> {
            emitted[0] = true;
            onText.accept(t);
        };
        try {
            return claudeOnce(system, history, out, cfg.fallbacks());
        } catch (BadRequestException e) {
            // Server tərəfi ehtiyat model parametri qəbul edilmədisə, onsuz təkrarla
            if (cfg.fallbacks() && !emitted[0]) {
                try {
                    return claudeOnce(system, history, out, false);
                } catch (RuntimeException ex) {
                    throw new LlmException(400, ex.getMessage(), emitted[0]);
                }
            }
            throw new LlmException(400, e.getMessage(), emitted[0]);
        } catch (RuntimeException e) {
            throw new LlmException(503, e.getMessage(), emitted[0]);
        }
    }

    private Outcome claudeOnce(String system, List<AdvisorService.Message> history, Consumer<String> onText, boolean fallbacks) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(cfg.model())
                .maxTokens(cfg.maxTokens())
                .system(system)
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(cfg.effort())).build());
        if (fallbacks) {
            // İmtina halında sorğu server tərəfində tövsiyə olunan modelə yönləndirilir
            b.addBeta("server-side-fallback-2026-07-01").putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        for (AdvisorService.Message m : history) {
            if ("assistant".equals(m.role())) b.addAssistantMessage(m.content());
            else b.addUserMessage(m.content());
        }
        BetaStopReason[] stop = new BetaStopReason[1];
        try (StreamResponse<BetaRawMessageStreamEvent> s = claudeClient().beta().messages().createStreaming(b.build())) {
            s.stream().forEach(ev -> {
                ev.contentBlockDelta().flatMap(d -> d.delta().text()).ifPresent(t -> onText.accept(AdvisorService.clean(t.text())));
                ev.messageDelta().flatMap(d -> d.delta().stopReason()).ifPresent(r -> stop[0] = r);
            });
        }
        return BetaStopReason.REFUSAL.equals(stop[0]) ? Outcome.REFUSED : Outcome.COMPLETED;
    }
}
