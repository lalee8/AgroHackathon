package az.aqroinvest.plan;

import az.aqroinvest.plot.Plot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sahənin iqlimi: NASA POWER klimatologiyası (2001-2025, aylıq orta temperatur, ekstremal minimum və maksimum,
 * yağıntı, şaxtalı günlər, günəş radiasiyası). Seed sahələri üçün məlumat layihəyə daxildir (resources/climate),
 * yeni sahə üçün ilk sorğuda NASA POWER-dən yüklənir və yaddaşda saxlanılır. İnternet yoxdursa plan iqlimsiz qurulur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClimateService {
    private static final String[] M = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    private final ObjectMapper mapper;
    private final Map<String, JsonNode> cache = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    public JsonNode climate(Plot p) {
        String key = p.getCode() + "@" + Math.round(p.getLatitude() * 1000) + "," + Math.round(p.getLongitude() * 1000);
        JsonNode hit = cache.get(key);
        if (hit != null) return hit;
        JsonNode c = bundled(p);
        if (c == null) c = fetch(p);
        if (c != null) cache.put(key, c);
        return c;
    }

    private JsonNode bundled(Plot p) {
        ClassPathResource r = new ClassPathResource("climate/" + p.getCode() + ".json");
        if (!r.exists()) return null;
        try (InputStream in = r.getInputStream()) {
            JsonNode n = mapper.readTree(in);
            // Koordinat dəyişibsə köhnə iqlim istifadə olunmur
            if (Math.abs(n.path("lat").asDouble() - p.getLatitude()) > 0.02 || Math.abs(n.path("lng").asDouble() - p.getLongitude()) > 0.02) return null;
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode fetch(Plot p) {
        String url = String.format(Locale.ROOT, "https://power.larc.nasa.gov/api/temporal/climatology/point?parameters=T2M,T2M_MAX,T2M_MIN,PRECTOTCORR,"
                + "ALLSKY_SFC_SW_DWN,FROST_DAYS,RH2M,WS2M&community=AG&longitude=%.4f&latitude=%.4f&format=JSON", p.getLongitude(), p.getLatitude());
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return null;
            JsonNode d = mapper.readTree(res.body());
            JsonNode par = d.path("properties").path("parameter");
            ObjectNode out = mapper.createObjectNode();
            out.put("code", p.getCode());
            out.put("lat", p.getLatitude());
            out.put("lng", p.getLongitude());
            out.put("elevation", d.path("geometry").path("coordinates").path(2).asDouble());
            out.put("period", d.path("header").path("range").asText(""));
            out.put("source", "NASA POWER (MERRA-2, CERES SYN1deg), climatology 2001-2025");
            ObjectNode months = out.putObject("months");
            ObjectNode annual = out.putObject("annual");
            par.fieldNames().forEachRemaining(k -> {
                var arr = months.putArray(k);
                for (String m : M) arr.add(Math.round(par.path(k).path(m).asDouble() * 100) / 100.0);
                annual.put(k, Math.round(par.path(k).path("ANN").asDouble() * 100) / 100.0);
            });
            return out;
        } catch (Exception e) {
            log.debug("NASA POWER əlçatan deyil ({}): {}", p.getCode(), e.getMessage());
            return null;
        }
    }
}
