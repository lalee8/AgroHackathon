package az.aqroinvest.satellite;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.config.AppProperties;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotCategory;
import az.aqroinvest.plot.PlotStatus;
import az.aqroinvest.satellite.SatelliteDtos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

/**
 * Peyk arxivi xidməti - iki real, pulsuz mənbədən:
 *  • Landsat (USGS/NASA): GLS 1990/2000/2005/2010 epoxaları və Landsat 8/9 (2013+) - Esri ImageServer;
 *  • Sentinel-2 (Copernicus/ESA): EOX s2cloudless illik buludsuz mozaikaları.
 * Hər sahə üçün: (1) "zaman maşını" şəkilləri, (2) sahənin poliqonu üzrə orta NDVI illik seriyası.
 * Nəticələr diskdə keşlənir; seed sahələri üçün əvvəlcədən yüklənmiş paket classpath:/imagery altındadır
 * (demo internetsiz də işləsin).
 */
@Slf4j
@Service
public class SatelliteService {
    public static final String ATTRIBUTION = "Landsat: USGS/NASA (Esri Landsat ImageServer) · Sentinel-2: Copernicus/ESA, EOX s2cloudless (CC BY 4.0 / CC BY-NC-SA 4.0)";
    private static final int IMG_W = 640;
    private static final int IMG_H = 480;
    private static final String MOSAIC = "{\"mosaicMethod\":\"esriMosaicAttribute\",\"sortField\":\"CloudCover\",\"ascending\":true,\"mosaicOperation\":\"MT_FIRST\"}";

    private final AppProperties.Imagery cfg;
    private final ObjectMapper mapper;
    private final RestClient http;
    private final Path cacheDir;
    private final ExecutorService pool = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "imagery");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, NdviSeries> ndviMemo = new ConcurrentHashMap<>();

    public SatelliteService(AppProperties props, ObjectMapper mapper) throws IOException {
        this.cfg = props.imagery();
        this.mapper = mapper;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(6000);
        rf.setReadTimeout(20000);
        this.http = RestClient.builder().requestFactory(rf)
                .defaultHeader("User-Agent", "AqroInvest-Qarabag/1.0 (hackathon prototype)").build();
        this.cacheDir = Path.of(cfg.cacheDir()).toAbsolutePath().normalize();
        Files.createDirectories(cacheDir);
    }

    // ------------------------------------------------------------------ geometry

    /** Sahənin ətrafı ilə birlikdə göstərilən pəncərə (4:3, metr nisbəti qorunur). */
    public Window window(Plot p) {
        double side = Math.sqrt(p.getAreaHa().doubleValue() * 10_000);
        double widthM = Math.max(2600, side * 2.8);
        double heightM = widthM * IMG_H / IMG_W;
        double dLat = heightM / 2 / 111_320.0;
        double dLng = widthM / 2 / (111_320.0 * Math.cos(Math.toRadians(p.getLatitude())));
        return new Window(p.getLongitude() - dLng, p.getLatitude() - dLat, p.getLongitude() + dLng, p.getLatitude() + dLat, IMG_W, IMG_H);
    }

    /** NDVI üçün sahəyə sıx çərçivə (~30 m piksel). */
    Window tightWindow(List<double[]> poly, double lat) {
        double minLng = Double.MAX_VALUE, minLat = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        for (double[] pt : poly) {
            minLng = Math.min(minLng, pt[0]); maxLng = Math.max(maxLng, pt[0]);
            minLat = Math.min(minLat, pt[1]); maxLat = Math.max(maxLat, pt[1]);
        }
        double padLng = (maxLng - minLng) * 0.05, padLat = (maxLat - minLat) * 0.05;
        minLng -= padLng; maxLng += padLng; minLat -= padLat; maxLat += padLat;
        double wM = (maxLng - minLng) * 111_320.0 * Math.cos(Math.toRadians(lat));
        double hM = (maxLat - minLat) * 111_320.0;
        int w = (int) Math.max(8, Math.min(160, Math.round(wM / 30)));
        int h = (int) Math.max(8, Math.min(160, Math.round(hM / 30)));
        return new Window(minLng, minLat, maxLng, maxLat, w, h);
    }

    public List<double[]> polygon(Plot p) {
        List<double[]> pts = new ArrayList<>();
        if (p.getPolygon() == null) return pts;
        String[] nums = p.getPolygon().replace("[", " ").replace("]", " ").trim().split("[ ,]+");
        for (int i = 0; i + 1 < nums.length; i += 2) pts.add(new double[]{Double.parseDouble(nums[i]), Double.parseDouble(nums[i + 1])});
        return pts;
    }

    static boolean inside(double x, double y, List<double[]> poly) {
        boolean in = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            double xi = poly.get(i)[0], yi = poly.get(i)[1], xj = poly.get(j)[0], yj = poly.get(j)[1];
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) in = !in;
        }
        return in;
    }

    // ------------------------------------------------------------------ imagery

    public ImageryView imagery(Plot p) {
        Window w = window(p);
        List<EpochView> epochs = new ArrayList<>();
        for (ImageryEpoch e : ImageryEpoch.values()) {
            String url = "/api/v1/plots/" + p.getId() + "/imagery/" + e.key();
            if (e != ImageryEpoch.LIVE) {
                epochs.add(new EpochView(e.key(), e.period(), e.sourceLabel(), url, null, false, null, null));
                continue;
            }
            LiveMeta m = live(p);
            if (m != null) epochs.add(new EpochView(e.key(), e.period(), e.sourceLabel(), url + "?v=" + m.sceneId(), m.date(), true, m.ndvi(), m.cloud()));
        }
        List<List<Double>> poly = polygon(p).stream().map(a -> List.of(a[0], a[1])).toList();
        return new ImageryView(p.getId(), p.getCode(), w, poly, epochs,
                p.getDistrict().label() + " rayonu 1990-cı illərin əvvəlindən 2020-ci ilə qədər işğal altında olub. "
                        + "Şəkillər eyni ərazinin 35 il ərzindəki dəyişməsini, sonuncu isə bu günün peyk görüntüsünü göstərir.", ATTRIBUTION);
    }

    public byte[] image(Plot p, ImageryEpoch epoch) {
        if (epoch == ImageryEpoch.LIVE) return liveImage(p);
        Window w = window(p);
        String name = p.getCode() + "_" + epoch.key() + "_" + w.hash() + ".jpg";
        byte[] cached = readCached(name);
        if (cached != null) return cached;
        if (!cfg.enabled()) throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "IMAGERY_DISABLED", "Peyk arxivi söndürülüb");
        String url = epoch.source() == ImageryEpoch.Source.LANDSAT
                ? cfg.landsatUrl() + "/exportImage?" + query(Map.of(
                "bbox", w.bbox(), "bboxSR", "4326", "imageSR", "4326", "size", w.width() + "," + w.height(),
                "format", "jpg", "f", "image", "time", millis(epoch.from()) + "," + millis(epoch.to()),
                "renderingRule", "{\"rasterFunction\":\"Natural Color with DRA\"}", "mosaicRule", MOSAIC))
                : cfg.sentinelWms() + "?" + query(Map.of(
                "service", "WMS", "request", "GetMap", "version", "1.1.1", "layers", epoch.layer(), "styles", "",
                "srs", "EPSG:4326", "bbox", w.bbox(), "width", String.valueOf(w.width()), "height", String.valueOf(w.height()),
                "format", "image/jpeg"));
        byte[] bytes = fetch(url);
        if (bytes == null || bytes.length < 1500 || !(bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "IMAGERY_UNAVAILABLE", "Peyk şəkli hazırda əlçatan deyil");
        }
        writeCache(name, bytes);
        return bytes;
    }

    // ------------------------------------------------------------------ son görüntü (Sentinel-2, avtomatik yenilənir)

    private final Map<String, Object> liveLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> liveFailedAt = new ConcurrentHashMap<>();

    /**
     * Sahənin pəncərəsini tam əhatə edən ən son az buludlu (≤10%) Sentinel-2 səhnəsi. Metaməlumat diskdə saxlanılır və
     * {@code live-refresh-hours} keçəndə yenidən yoxlanılır; yeni səhnə varsa şəkil və NDVI yenilənir. İnternet yoxdursa
     * son saxlanmış görüntü (seed sahələri üçün paketdə olan) qaytarılır.
     */
    public LiveMeta live(Plot p) {
        Window w = window(p);
        String base = p.getCode() + "_live_" + w.hash();
        synchronized (liveLocks.computeIfAbsent(base, k -> new Object())) {
            LiveMeta cached = readLiveMeta(base);
            boolean fresh = cached != null && System.currentTimeMillis() - cached.fetchedAt() < cfg.refreshHours() * 3_600_000L;
            if (fresh || !cfg.enabled() || cfg.sentinelLiveUrl() == null) return cached;
            // Xidmət əlçatmazdırsa hər səhifə açılışında gözləməmək üçün 30 dəqiqə yenidən cəhd edilmir
            Long failed = liveFailedAt.get(base);
            if (failed != null && System.currentTimeMillis() - failed < 30 * 60_000L) return cached;
            LiveMeta next = refreshLive(p, w, base, cached);
            if (next == null) liveFailedAt.put(base, System.currentTimeMillis()); else liveFailedAt.remove(base);
            return next != null ? next : cached;
        }
    }

    private LiveMeta refreshLive(Plot p, Window w, String base, LiveMeta cached) {
        try {
            // Əvvəl pəncərəni tam əhatə edən səhnə; sahə iki peyk zolağının sərhədindədirsə, eyni günün qonşu səhnələri birləşdirilir
            List<long[]> scenes = scenes(w, "esriSpatialRelWithin", 1);
            if (scenes.isEmpty()) {
                List<long[]> around = scenes(w, "esriSpatialRelIntersects", 6);
                if (!around.isEmpty()) {
                    String day = Instant.ofEpochMilli(around.get(0)[1]).atZone(ZoneOffset.UTC).toLocalDate().toString();
                    scenes = around.stream().filter(x -> Instant.ofEpochMilli(x[1]).atZone(ZoneOffset.UTC).toLocalDate().toString().equals(day)).toList();
                }
            }
            if (scenes.isEmpty()) return null;
            long id = scenes.get(0)[0];
            String date = Instant.ofEpochMilli(scenes.get(0)[1]).atZone(ZoneOffset.UTC).toLocalDate().toString();
            double cloud = scenes.get(0)[2] / 10.0;
            String ids = String.join(",", scenes.stream().map(x -> String.valueOf(x[0])).toList());
            if (cached != null && cached.sceneId() == id && readCached(base + ".jpg") != null) {
                LiveMeta same = new LiveMeta(id, cached.date(), cached.cloud(), cached.ndvi(), System.currentTimeMillis());
                writeLiveMeta(base, same);
                return same;
            }
            String lock = "{\"mosaicMethod\":\"esriMosaicLockRaster\",\"lockRasterIds\":[" + ids + "]}";
            byte[] jpg = fetch(cfg.sentinelLiveUrl() + "/exportImage?" + query(Map.of(
                    "bbox", w.bbox(), "bboxSR", "4326", "imageSR", "4326", "size", w.width() + "," + w.height(),
                    "format", "jpg", "f", "image", "renderingRule", "{\"rasterFunction\":\"Natural Color with DRA\"}", "mosaicRule", lock)));
            if (jpg == null || jpg.length < 1500 || !(jpg[0] == (byte) 0xFF && jpg[1] == (byte) 0xD8)) return null;
            List<double[]> poly = polygon(p);
            Double ndvi = poly.size() >= 3 ? liveNdvi(poly, p.getLatitude(), lock) : null;
            writeCache(base + ".jpg", jpg);
            LiveMeta m = new LiveMeta(id, date, cloud, ndvi, System.currentTimeMillis());
            writeLiveMeta(base, m);
            log.info("Son peyk görüntüsü yeniləndi: {} · {} · NDVI {}", p.getCode(), date, ndvi);
            return m;
        } catch (Exception e) {
            log.warn("Son peyk görüntüsü alınmadı ({}): {}", p.getCode(), e.getMessage());
            return null;
        }
    }

    /** Az buludlu (≤10%) son səhnələr: [objectid, çəkiliş vaxtı (ms), bulud payı ×1000]. */
    private List<long[]> scenes(Window w, String rel, int n) throws IOException {
        byte[] body = fetch(cfg.sentinelLiveUrl() + "/query?" + query(Map.of(
                "geometry", w.bbox(), "geometryType", "esriGeometryEnvelope", "inSR", "4326",
                "spatialRel", rel, "where", "cloudcover <= 0.1 AND category = 1",
                "outFields", "objectid,acquisitiondate,cloudcover", "orderByFields", "acquisitiondate DESC",
                "resultRecordCount", String.valueOf(n), "returnGeometry", "false", "f", "json")));
        List<long[]> out = new ArrayList<>();
        if (body == null) return out;
        for (var f : mapper.readTree(body).path("features")) {
            var a = f.path("attributes");
            out.add(new long[]{a.path("objectid").asLong(), a.path("acquisitiondate").asLong(), Math.round(a.path("cloudcover").asDouble() * 1000)});
        }
        return out;
    }

    /** Sentinel-2 (10 m) NDVI: sahənin poliqonu daxilindəki piksellərin ortası. */
    private Double liveNdvi(List<double[]> poly, double lat, String lock) {
        Window t = tightWindow(poly, lat);
        double wM = (t.maxLng() - t.minLng()) * 111_320.0 * Math.cos(Math.toRadians(lat));
        double hM = (t.maxLat() - t.minLat()) * 111_320.0;
        Window w = new Window(t.minLng(), t.minLat(), t.maxLng(), t.maxLat(),
                (int) Math.max(8, Math.min(220, Math.round(wM / 10))), (int) Math.max(8, Math.min(220, Math.round(hM / 10))));
        byte[] bytes = fetch(cfg.sentinelLiveUrl() + "/exportImage?" + query(Map.of(
                "bbox", w.bbox(), "bboxSR", "4326", "imageSR", "4326", "size", w.width() + "," + w.height(),
                "format", "tiff", "pixelType", "F32", "f", "image", "renderingRule", "{\"rasterFunction\":\"NDVI Raw\"}", "mosaicRule", lock)));
        Double v = rasterMean(bytes, w, poly);
        return v == null ? null : Math.round(v * 1000) / 1000.0;
    }

    public byte[] liveImage(Plot p) {
        Window w = window(p);
        String base = p.getCode() + "_live_" + w.hash();
        live(p);
        byte[] bytes = readCached(base + ".jpg");
        if (bytes == null) throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "IMAGERY_UNAVAILABLE", "Son peyk görüntüsü hazırda əlçatan deyil");
        return bytes;
    }

    private LiveMeta readLiveMeta(String base) {
        byte[] b = readCached(base + ".json");
        if (b == null) return null;
        try {
            return mapper.readValue(b, LiveMeta.class);
        } catch (IOException e) {
            return null;
        }
    }

    private void writeLiveMeta(String base, LiveMeta m) {
        try {
            writeCache(base + ".json", mapper.writeValueAsBytes(m));
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ NDVI

    /** Bir il üçün bir və ya bir neçə zaman pəncərəsi; nəticə pəncərələr üzrə maksimum ("pik yaşıllıq"). */
    record Period(int year, List<String[]> windows, String source) {
        Period(int year, String from, String to, String source) {
            this(year, List.<String[]>of(new String[]{from, to}), source);
        }
    }

    List<Period> periods() {
        List<Period> list = new ArrayList<>(List.of(
                new Period(1990, "1986-01-01", "1994-01-01", "Landsat GLS 1990"),
                new Period(2000, "1999-01-01", "2003-06-01", "Landsat GLS 2000"),
                new Period(2005, "2004-01-01", "2007-06-01", "Landsat GLS 2005"),
                new Period(2010, "2008-01-01", "2012-06-01", "Landsat GLS 2010")));
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        int last = now.getMonthValue() >= 10 ? now.getYear() : now.getYear() - 1;
        for (int y = 2013; y <= last; y++) {
            list.add(new Period(y, List.of(new String[]{y + "-04-15", y + "-07-01"}, new String[]{y + "-07-01", y + "-10-01"}),
                    "Landsat 8/9 (aprel-sentyabr, pik yaşıllıq)"));
        }
        return list;
    }

    public NdviSeries ndvi(Plot p) {
        List<double[]> poly = polygon(p);
        Window tw = tightWindow(poly, p.getLatitude());
        String key = p.getCode() + "_" + tw.hash();
        NdviSeries memo = ndviMemo.get(key);
        if (memo != null) return memo;
        String name = "ndvi_" + key + ".json";
        List<NdviPoint> points = null;
        byte[] cached = readCached(name);
        if (cached != null) {
            try {
                points = mapper.readValue(cached, new TypeReference<>() {});
            } catch (IOException ignored) {
            }
        }
        if (points == null) {
            if (!cfg.enabled()) return summarize(p, List.of());
            List<Future<NdviPoint>> futures = new ArrayList<>();
            for (Period per : periods()) futures.add(pool.submit(() -> fetchNdvi(per, tw, poly)));
            points = new ArrayList<>();
            for (Future<NdviPoint> f : futures) {
                try {
                    NdviPoint np = f.get(40, TimeUnit.SECONDS);
                    if (np != null) points.add(np);
                } catch (Exception e) {
                    log.debug("NDVI alınmadı: {}", e.getMessage());
                }
            }
            points.sort(Comparator.comparingInt(NdviPoint::year));
            if (points.size() >= 3) {
                try {
                    writeCache(name, mapper.writeValueAsBytes(points));
                } catch (IOException ignored) {
                }
            }
        }
        NdviSeries s = summarize(p, points);
        if (!points.isEmpty()) ndviMemo.put(key, s);
        return s;
    }

    private NdviPoint fetchNdvi(Period per, Window w, List<double[]> poly) {
        Double best = null;
        for (String[] win : per.windows()) {
            Double v = meanNdvi(win[0], win[1], w, poly);
            if (v != null && (best == null || v > best)) best = v;
        }
        return best == null ? null : new NdviPoint(per.year(), Math.round(best * 1000) / 1000.0, per.source());
    }

    private Double meanNdvi(String from, String to, Window w, List<double[]> poly) {
        String url = cfg.landsatUrl() + "/exportImage?" + query(Map.of(
                "bbox", w.bbox(), "bboxSR", "4326", "imageSR", "4326", "size", w.width() + "," + w.height(),
                "format", "tiff", "pixelType", "F32", "f", "image", "time", millis(from) + "," + millis(to),
                "renderingRule", "{\"rasterFunction\":\"NDVI Raw\"}", "mosaicRule", MOSAIC));
        return rasterMean(fetch(url), w, poly);
    }

    /** F32 TIFF rasterinin poliqon daxilindəki orta qiyməti (0 və etibarsız piksellər atılır). */
    private Double rasterMean(byte[] bytes, Window w, List<double[]> poly) {
        if (bytes == null || bytes.length < 100) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) return null;
            Raster r = img.getRaster();
            double sumIn = 0, sumAll = 0;
            int nIn = 0, nAll = 0;
            for (int y = 0; y < r.getHeight(); y++) {
                for (int x = 0; x < r.getWidth(); x++) {
                    float v = r.getSampleFloat(x, y, 0);
                    if (Float.isNaN(v) || v < -1 || v > 1 || v == 0f) continue;
                    double lng = w.minLng() + (x + 0.5) / r.getWidth() * (w.maxLng() - w.minLng());
                    double lat = w.maxLat() - (y + 0.5) / r.getHeight() * (w.maxLat() - w.minLat());
                    sumAll += v;
                    nAll++;
                    if (inside(lng, lat, poly)) {
                        sumIn += v;
                        nIn++;
                    }
                }
            }
            if (nIn >= 3) return sumIn / nIn;
            return nAll > 0 ? sumAll / nAll : null;
        } catch (IOException e) {
            return null;
        }
    }

    NdviSeries summarize(Plot p, List<NdviPoint> pts) {
        String method = "Sahənin poliqonu daxilindəki Landsat piksellərinin orta NDVI dəyəri (Normalized Difference Vegetation Index: "
                + "-1..1; 0.2-dən aşağı - çılpaq və ya boş torpaq, 0.2-0.4 - seyrək bitki örtüyü, 0.4-dən yuxarı - sıx bitki örtüyü). "
                + "1990-2010: Landsat 5/7 GLS arxivi; 2013-dən: Landsat 8/9, hər il yaz (aprel-iyun) və yay (iyul-sentyabr) dövrlərinin "
                + "ən az buludlu səhnələrindən pik yaşıllıq. Sensorlar fərqli olduğu üçün müqayisə yalnız eyni sensor ailəsi daxilində aparılır.";
        if (pts.isEmpty()) {
            return new NdviSeries(p.getId(), p.getCode(), pts, null, null, null, null, "UNKNOWN",
                    "Peyk məlumatı hazırda əlçatan deyil.", method);
        }
        Double base = pts.stream().filter(x -> x.year() == 1990).map(NdviPoint::ndvi).findFirst().orElse(null);
        Double occGls = avg(pts, 1995, 2012);
        Double occLate = avg(pts, 2013, 2019);
        Double after = avg(pts, 2021, 3000);
        NdviPoint last = pts.get(pts.size() - 1);
        String trend = "STABLE";
        if (occLate != null && after != null) {
            if (after - occLate > 0.05) trend = "RECOVERING";
            else if (occLate - after > 0.05) trend = "DECLINING";
        }
        StringBuilder sb = new StringBuilder();
        if (base != null) {
            sb.append(String.format(Locale.ROOT, "1990-cı ildə sahədə bitki örtüyü indeksi (NDVI) %.2f idi - %s. ", base, cls(base)));
            if (occGls != null) {
                sb.append(String.format(Locale.ROOT, "İşğal illərində (2000-2010) eyni sensorlarla orta göstərici %.2f olub", occGls));
                sb.append(base - occGls > 0.04 ? " - bitki örtüyü azalıb, torpaq əsasən istifadəsiz qalıb. " : ". ");
            }
        }
        if (occLate != null && after != null) {
            sb.append(String.format(Locale.ROOT, "Landsat 8/9 məlumatına görə işğalın son illərində (2013-2019) orta NDVI %.2f, azad edildikdən sonra (2021-%d) isə %.2f olub",
                    occLate, last.year(), after));
            sb.append(switch (trend) {
                case "RECOVERING" -> " - torpaqda canlanma müşahidə olunur.";
                case "DECLINING" -> " - bitki örtüyü azalıb.";
                default -> " - əhəmiyyətli dəyişiklik yoxdur.";
            });
        }
        return new NdviSeries(p.getId(), p.getCode(), pts, base, occGls != null ? occGls : occLate, last.ndvi(), last.year(), trend,
                sb.toString().trim(), method);
    }

    private static Double avg(List<NdviPoint> pts, int from, int to) {
        OptionalDouble d = pts.stream().filter(x -> x.year() >= from && x.year() <= to).mapToDouble(NdviPoint::ndvi).average();
        return d.isPresent() ? Math.round(d.getAsDouble() * 1000) / 1000.0 : null;
    }

    static String cls(double v) {
        if (v < 0.2) return "çılpaq və ya boş torpaq";
        if (v < 0.4) return "seyrək bitki örtüyü";
        return "sıx bitki örtüyü";
    }

    /** Peykdən nəzarət: icarəyə verilmiş və müsabiqədəki sahələrin vəziyyəti. */
    public MonitoringRow monitoring(Plot p) {
        NdviSeries s = ndvi(p);
        List<NdviPoint> pts = s.points();
        Double prev = pts.size() >= 2 ? pts.get(pts.size() - 2).ndvi() : null;
        boolean vegetationBased = p.getCategory() != PlotCategory.HEYVANDARLIQ && p.getCategory() != PlotCategory.ARICILIQ;
        String status, label, note;
        if (pts.isEmpty()) {
            status = "NO_DATA"; label = "Məlumat yoxdur"; note = "Peyk məlumatı əlçatan deyil";
        } else if (!vegetationBased) {
            status = "NOT_APPLICABLE"; label = "Təbii örtük";
            note = "Otlaq və arıçılıq sahələrində NDVI təbii bitki örtüyünü göstərir; fəaliyyət meyarı kimi istifadə olunmur";
        } else if (p.getStatus() == PlotStatus.AWARDED && s.latest() < 0.25) {
            status = "ALERT"; label = "Fəaliyyət əlaməti zəifdir";
            note = "Son mövsümdə bitki örtüyü zəifdir. Yerində yoxlama tövsiyə olunur";
        } else if (p.getStatus() == PlotStatus.AWARDED) {
            status = "ACTIVE"; label = "Becərilir";
            note = "Bitki örtüyü təyinata uyğun fəaliyyəti göstərir";
        } else {
            status = "BASELINE"; label = "İlkin vəziyyət";
            note = "İcarəyə verilməyib; göstərici gələcək müqayisə üçün baza kimi saxlanılır";
        }
        return new MonitoringRow(p.getId(), p.getCode(), p.getName(), p.getDistrict().label(), p.getCategory().name(),
                p.getCategory().label(), p.getStatus().name(), pts, s.latest(), s.latestYear(), prev, s.occupationMean(),
                status, label, note);
    }

    // ------------------------------------------------------------------ io

    private byte[] fetch(String url) {
        try {
            return http.get().uri(URI.create(url)).retrieve().body(byte[].class);
        } catch (Exception e) {
            log.warn("Peyk sorğusu alınmadı: {}", e.getMessage());
            return null;
        }
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

    private void writeCache(String name, byte[] bytes) {
        try {
            Files.write(cacheDir.resolve(name), bytes);
        } catch (IOException e) {
            log.warn("Keş yazıla bilmədi: {}", e.getMessage());
        }
    }

    private static String query(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private static long millis(String date) {
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public Path cacheDir() {
        return cacheDir;
    }

    public Instant now() {
        return Instant.now();
    }
}
