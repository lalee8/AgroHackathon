package az.aqroinvest.economics;

import az.aqroinvest.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Günəş enerjisi istehsalı: Avropa Komissiyasının PVGIS xidməti (JRC, PVGIS-SARAH2 radiasiya bazası, 2005-2020).
 * Sahənin koordinatı üçün 1 kWp optimal bucaqlı stasionar panelin illik istehsalı və illər arası dəyişkənliyi alınır.
 * Nəticə diskdə keşlənir, demo üçün sahələrin cavabları layihəyə daxil edilib.
 */
@Slf4j
@Service
public class SolarYieldService {
    public static final String SOURCE = "PVGIS 5.2, Avropa Komissiyası JRC (SARAH2 radiasiya bazası, 2005-2020)";
    static final double FALLBACK_KWH = 1380, FALLBACK_SD = 50;

    public record SolarYield(double kwhPerKwp, double sdKwh, double optimalTilt, List<Double> monthly, boolean measured, String source) {}

    private final AppProperties.Imagery cfg;
    private final ObjectMapper mapper;
    private final RestClient http;
    private final Path cacheDir;
    private final Map<String, SolarYield> memo = new ConcurrentHashMap<>();

    public SolarYieldService(AppProperties props, ObjectMapper mapper) throws IOException {
        this.cfg = props.imagery();
        this.mapper = mapper;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(5000);
        rf.setReadTimeout(15000);
        this.http = RestClient.builder().requestFactory(rf).build();
        this.cacheDir = Path.of(cfg.cacheDir()).toAbsolutePath().normalize();
        Files.createDirectories(cacheDir);
    }

    public SolarYield yield(double lat, double lng) {
        String name = String.format(Locale.ROOT, "pvgis_%.3f_%.3f.json", lat, lng);
        return memo.computeIfAbsent(name, n -> load(n, lat, lng));
    }

    private SolarYield load(String name, double lat, double lng) {
        byte[] raw = readCached(name);
        if (raw == null && cfg.enabled()) {
            try {
                String url = String.format(Locale.ROOT,
                        "https://re.jrc.ec.europa.eu/api/v5_2/PVcalc?lat=%.4f&lon=%.4f&peakpower=1&loss=14&optimalangles=1&outputformat=json", lat, lng);
                raw = http.get().uri(url).retrieve().body(byte[].class);
                if (raw != null && raw.length > 100) Files.write(cacheDir.resolve(name), raw);
            } catch (Exception e) {
                log.warn("PVGIS sorğusu alınmadı: {}", e.getMessage());
                raw = null;
            }
        }
        if (raw != null) {
            try {
                JsonNode d = mapper.readTree(raw);
                JsonNode tot = d.path("outputs").path("totals").path("fixed");
                List<Double> monthly = new ArrayList<>();
                for (JsonNode m : d.path("outputs").path("monthly").path("fixed")) monthly.add(m.path("E_m").asDouble());
                double tilt = d.path("inputs").path("mounting_system").path("fixed").path("slope").path("value").asDouble(30);
                if (tot.has("E_y")) return new SolarYield(tot.path("E_y").asDouble(), tot.path("SD_y").asDouble(FALLBACK_SD),
                        tilt, monthly, true, SOURCE);
            } catch (IOException e) {
                log.warn("PVGIS cavabı oxunmadı: {}", e.getMessage());
            }
        }
        return new SolarYield(FALLBACK_KWH, FALLBACK_SD, 32, List.of(), false,
                "Təxmini dəyər (Qarabağ üçün PVGIS ortası). PVGIS əlçatan olanda dəqiqləşdiriləcək");
    }

    private byte[] readCached(String name) {
        try {
            Path f = cacheDir.resolve(name);
            if (Files.exists(f)) return Files.readAllBytes(f);
            ClassPathResource res = new ClassPathResource("imagery/" + name);
            if (res.exists()) {
                try (InputStream in = res.getInputStream()) {
                    return in.readAllBytes();
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
