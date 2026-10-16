package az.aqroinvest.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valyuta məzənnələri: Azərbaycan Respublikası Mərkəzi Bankının (CBAR) rəsmi gündəlik bülleteni. Xarici investor
 * rəqəmləri dollar və ya avroda görə bilsin. Bülleten gündə bir dəfə alınır; əlçatan olmasa son məlum məzənnə qalır.
 */
@Slf4j
@RestController
@Tag(name = "Soraq kitabçaları")
public class CurrencyService {
    private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
    private static final Pattern VALUTE = Pattern.compile("<Valute Code=\"([A-Z]{3})\">.*?<Nominal>([^<]*)</Nominal>.*?<Value>([^<]*)</Value>", Pattern.DOTALL);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile Map<String, Object> cached;
    private volatile LocalDate cachedFor;
    private volatile Instant lastTry = Instant.EPOCH;

    @Operation(summary = "AZN məzənnələri (1 vahid xarici valyuta neçə manatdır), Mərkəzi Bankın bülleteni")
    @GetMapping("/api/v1/reference/rates")
    public ResponseEntity<Map<String, Object>> rates() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()).body(current());
    }

    synchronized Map<String, Object> current() {
        LocalDate today = LocalDate.now(BAKU);
        if (cached != null && today.equals(cachedFor)) return cached;
        if (cached == null || Duration.between(lastTry, Instant.now()).toMinutes() >= 30) {
            lastTry = Instant.now();
            Map<String, Object> fresh = fetch(today);
            if (fresh != null) {
                cached = fresh;
                cachedFor = today;
                return cached;
            }
        }
        if (cached == null) {
            // Son məlum rəsmi məzənnə (15.10.2026)
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", "2026-10-15");
            r.put("source", "Mərkəzi Bank (son məlum məzənnə)");
            r.put("rates", Map.of("USD", 1.7, "EUR", 1.9067, "RUB", 0.020148, "TRY", 0.0344));
            cached = r;
        }
        return cached;
    }

    private Map<String, Object> fetch(LocalDate day) {
        String url = "https://cbar.az/currencies/" + day.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ".xml";
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return null;
            Map<String, Double> rates = new LinkedHashMap<>();
            Matcher m = VALUTE.matcher(res.body());
            while (m.find()) {
                String code = m.group(1);
                if (!code.equals("USD") && !code.equals("EUR") && !code.equals("RUB") && !code.equals("TRY") && !code.equals("GBP")) continue;
                double nominal = Double.parseDouble(m.group(2).replaceAll("[^0-9.]", "").isEmpty() ? "1" : m.group(2).replaceAll("[^0-9.]", ""));
                rates.put(code, Math.round(Double.parseDouble(m.group(3).trim()) / nominal * 1e6) / 1e6);
            }
            if (!rates.containsKey("USD")) return null;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", day.toString());
            r.put("source", "Azərbaycan Respublikası Mərkəzi Bankı (cbar.az)");
            r.put("rates", rates);
            return r;
        } catch (Exception e) {
            log.debug("CBAR məzənnəsi alınmadı: {}", e.getMessage());
            return null;
        }
    }
}
