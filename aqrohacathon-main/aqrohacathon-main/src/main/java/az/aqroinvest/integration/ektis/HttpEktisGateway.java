package az.aqroinvest.integration.ektis;

import az.aqroinvest.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/** EKTİS REST adapteri: API açarı, HMAC-SHA256 imza, idempotentlik açarı və zaman aşımı ilə. */
@Slf4j
@Component
public class HttpEktisGateway implements EktisGateway {
    private final RestClient client;
    private final AppProperties.Ektis cfg;

    public HttpEktisGateway(AppProperties props) {
        this.cfg = props.ektis();
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(2000);
        rf.setReadTimeout(4000);
        this.client = RestClient.builder()
                .baseUrl(cfg.baseUrl())
                .requestFactory(rf)
                .defaultHeader("X-API-Key", cfg.apiKey())
                .defaultHeader("X-Client-System", IntegrationEventPublisher.SOURCE)
                .build();
    }

    @Override
    public FarmerCheck checkFarmer(String taxId) {
        if (taxId == null || taxId.isBlank()) return FarmerCheck.unavailable("VÖEN/FİN göstərilməyib");
        try {
            JsonNode n = client.get().uri("/v1/farmers/{taxId}", taxId.trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(JsonNode.class);
            if (n == null) return FarmerCheck.unavailable("Boş cavab");
            return new FarmerCheck(true,
                    n.path("status").asText(),
                    n.hasNonNull("farmerId") ? n.get("farmerId").asText() : null,
                    n.hasNonNull("registeredLandHa") ? new BigDecimal(n.get("registeredLandHa").asText()) : null,
                    n.hasNonNull("subsidiesReceived") ? n.get("subsidiesReceived").asInt() : null,
                    n.path("message").asText(null));
        } catch (Exception e) {
            log.warn("EKTİS fermer yoxlaması alınmadı: {}", e.getMessage());
            return FarmerCheck.unavailable("EKTİS hazırda əlçatan deyil - yoxlama sonradan təkrarlanacaq");
        }
    }

    @Override
    public DeliveryResult deliver(IntegrationEvent event) {
        String body = event.getPayload();
        String ts = Instant.now().toString();
        try {
            JsonNode resp = client.post().uri("/v1/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Idempotency-Key", event.getEventId())
                    .header("X-Event-Type", event.getEventType())
                    .header("X-Timestamp", ts)
                    .header("X-Signature", "sha256=" + sign(ts + "." + body))
                    .body(body)
                    .retrieve().body(JsonNode.class);
            String ref = resp == null ? null : resp.path("reference").asText(null);
            return new DeliveryResult(true, ref, null);
        } catch (RestClientResponseException e) {
            return new DeliveryResult(false, null, "HTTP " + e.getStatusCode().value() + ": " + trim(e.getResponseBodyAsString()));
        } catch (Exception e) {
            return new DeliveryResult(false, null, e.getClass().getSimpleName() + ": " + trim(e.getMessage()));
        }
    }

    public String sign(String data) {
        return hmac(cfg.hmacSecret(), data);
    }

    public static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String trim(String s) {
        if (s == null) return null;
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
