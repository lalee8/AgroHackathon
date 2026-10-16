package az.aqroinvest.plot;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Random;

/** Sahənin mərkəzi və sahəsinə əsasən təxmini sərhəd poliqonu (GeoJSON) qurur. */
public final class GeoUtil {
    private GeoUtil() {}

    public static String polygonFor(double lat, double lng, BigDecimal areaHa, long seed) {
        double sideMeters = Math.sqrt(areaHa.doubleValue() * 10_000);
        double halfLat = (sideMeters / 2) / 111_320.0;
        double halfLng = (sideMeters / 2) / (111_320.0 * Math.cos(Math.toRadians(lat)));
        Random r = new Random(seed);
        int vertices = 6 + r.nextInt(3);
        StringBuilder sb = new StringBuilder("[");
        double first0 = 0, first1 = 0;
        for (int i = 0; i < vertices; i++) {
            double angle = 2 * Math.PI * i / vertices + r.nextDouble() * 0.3;
            double radius = 0.85 + r.nextDouble() * 0.35;
            double x = lng + Math.cos(angle) * halfLng * radius * 1.25;
            double y = lat + Math.sin(angle) * halfLat * radius * 1.25;
            if (i == 0) { first0 = x; first1 = y; }
            sb.append(String.format(Locale.ROOT, "[%.6f,%.6f],", x, y));
        }
        sb.append(String.format(Locale.ROOT, "[%.6f,%.6f]]", first0, first1));
        return sb.toString();
    }
}
