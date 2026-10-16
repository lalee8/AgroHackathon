package az.aqroinvest;

import az.aqroinvest.advisor.AdvisorService;
import az.aqroinvest.advisor.LlmGateway;
import az.aqroinvest.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Gemini axını saxta serverlə: ehtiyat modelə keçid, düşüncə hissələrinin atılması, bloklanmış cavab. */
class LlmGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            calls.add(path + " key=" + ex.getRequestHeaders().getFirst("x-goog-api-key"));
            bodies.add(mapper.readTree(ex.getRequestBody().readAllBytes()));
            String body;
            int code = 200;
            if (path.contains("busy-model")) {
                code = 503;
                body = "{\"error\":{\"code\":503,\"message\":\"high demand\",\"status\":\"UNAVAILABLE\"}}";
            } else if (path.contains("safety-model")) {
                body = "data: {\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"SAFETY\"}]}\n\n";
            } else {
                body = "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"gizli düşüncə\",\"thought\":true}]}}]}\n\n"
                        + "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Günəş stansiyası — 15 ildə \"}]}}]}\n\n"
                        + "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"qayıdır.\"}]},\"finishReason\":\"STOP\"}]}\n\n";
            }
            byte[] raw = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", code == 200 ? "text/event-stream" : "application/json");
            ex.sendResponseHeaders(code, raw.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(raw);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LlmGateway gateway(String model, String fallback) {
        AppProperties.Ai ai = new AppProperties.Ai("auto", "", "claude-test", "low", 1000, false, 20, "",
                new AppProperties.Ai.Gemini("test-key", model, fallback, "low", base));
        return new LlmGateway(new AppProperties(null, null, null, null, null, null, null, null, ai), mapper);
    }

    @Test
    @DisplayName("503 verən modeldən ehtiyat modelə keçir, düşüncə hissəsi və uzun tire istifadəçiyə çatmır")
    void fallsBackAndStreams() {
        LlmGateway g = gateway("busy-model", "ok-model");
        assertThat(g.provider()).isEqualTo("gemini");
        StringBuilder sb = new StringBuilder();
        LlmGateway.Outcome o = g.stream("sistem", List.of(new AdvisorService.Message("user", "salam"),
                new AdvisorService.Message("assistant", "buyurun"), new AdvisorService.Message("user", "günəş?")), sb::append);
        assertThat(o).isEqualTo(LlmGateway.Outcome.COMPLETED);
        assertThat(sb.toString()).isEqualTo("Günəş stansiyası - 15 ildə qayıdır.").doesNotContain("düşüncə");
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0)).contains("busy-model:streamGenerateContent");
        assertThat(calls.get(1)).contains("ok-model:streamGenerateContent").endsWith("key=test-key");
        JsonNode req = bodies.get(1);
        assertThat(req.path("systemInstruction").path("parts").get(0).path("text").asText()).isEqualTo("sistem");
        assertThat(req.path("contents").get(1).path("role").asText()).isEqualTo("model");
        assertThat(req.path("generationConfig").path("thinkingConfig").path("thinkingLevel").asText()).isEqualTo("low");
    }

    @Test
    @DisplayName("Təhlükəsizlik filtri cavabı kəsərsə REFUSED qaytarılır (məsləhətçi oflayn cavaba keçir)")
    void safetyBlockIsRefusal() {
        LlmGateway.Outcome o = gateway("safety-model", null).stream("s", List.of(new AdvisorService.Message("user", "x")), t -> {});
        assertThat(o).isEqualTo(LlmGateway.Outcome.REFUSED);
        assertThat(gateway("busy-model", null).complete("s", "x")).isNull();
    }
}
