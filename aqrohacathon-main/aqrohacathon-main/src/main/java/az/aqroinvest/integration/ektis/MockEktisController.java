package az.aqroinvest.integration.ektis;

import az.aqroinvest.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * EKTİS SİMULYATORU - real dövlət sisteminə qoşulmadan inteqrasiyanı nümayiş etdirmək üçün.
 * Xarici sistem kimi davranır: API açarını və HMAC imzanı yoxlayır, idempotentliyi təmin edir,
 * və retry mexanizmini göstərmək üçün sorğuların bir hissəsinə 503 qaytarır.
 */
@Tag(name = "EKTİS simulyatoru (mock)", description = "Real EKTİS API-sini təqlid edən test xidməti")
@RestController
@RequestMapping("/mock/ektis/v1")
@RequiredArgsConstructor
public class MockEktisController {
    private final AppProperties props;
    private final MockEktisStore store;
    private final ObjectMapper mapper;

    @Operation(summary = "Fermer reyestrində VÖEN/FİN üzrə axtarış")
    @GetMapping("/farmers/{taxId}")
    public ResponseEntity<?> farmer(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                    @PathVariable String taxId) {
        if (!validKey(apiKey)) return unauthorized();
        int h = Math.abs(taxId.trim().toUpperCase().hashCode());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taxId", taxId);
        if (h % 10 < 6) {
            body.put("status", "REGISTERED");
            body.put("farmerId", "FRM-" + (100000 + h % 900000));
            body.put("registeredLandHa", (h % 4800) / 100.0 + 1);
            body.put("subsidiesReceived", h % 5);
            body.put("message", "Fermer EKTİS reyestrində qeydiyyatdadır");
        } else {
            body.put("status", "NOT_REGISTERED");
            body.put("farmerId", null);
            body.put("message", "Reyestrdə qeyd tapılmadı - qalib elan olunduqda avtomatik qeydiyyat təklif ediləcək");
        }
        body.put("checkedAt", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Portal hadisələrinin qəbulu (imza + idempotentlik yoxlaması ilə)")
    @PostMapping("/events")
    public ResponseEntity<?> receive(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                     @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey,
                                     @RequestHeader(value = "X-Timestamp", required = false) String ts,
                                     @RequestHeader(value = "X-Signature", required = false) String signature,
                                     @RequestBody String body) {
        if (!validKey(apiKey)) return unauthorized();
        if (ts == null || signature == null || idemKey == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "İmza, vaxt damğası və idempotentlik açarı tələb olunur"));
        }
        String expected = "sha256=" + HttpEktisGateway.hmac(props.ektis().hmacSecret(), ts + "." + body);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            store.markRejected();
            store.rejected("401 - HMAC imzası etibarsızdır", null);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "HMAC imzası etibarsızdır"));
        }
        try {
            if (Duration.between(Instant.parse(ts), Instant.now()).abs().toMinutes() > 5) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Vaxt damğası köhnədir (replay qorunması)"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vaxt damğası formatı yanlışdır"));
        }
        boolean duplicate = store.seen(idemKey);
        String type = "UNKNOWN";
        String aggId = null;
        JsonNode node = null;
        try {
            node = mapper.readTree(body);
            type = node.path("eventType").asText(type);
            aggId = node.path("aggregate").path("id").asText(null);
        } catch (Exception ignored) {
        }
        if (!duplicate && ThreadLocalRandom.current().nextDouble() < props.ektis().mock().failureRate()) {
            store.rejected("503 - simulyasiya edilmiş nasazlıq, portal təkrar göndərəcək", type);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "EKTİS müvəqqəti əlçatmazdır (simulyasiya edilmiş nasazlıq)"));
        }
        String ref = store.register(idemKey, type, aggId, node, true);
        return ResponseEntity.status(duplicate ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(Map.of("reference", ref, "duplicate", duplicate, "receivedAt", Instant.now().toString()));
    }

    private boolean validKey(String key) {
        return key != null && MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),
                props.ektis().apiKey().getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Etibarsız API açarı"));
    }
}
