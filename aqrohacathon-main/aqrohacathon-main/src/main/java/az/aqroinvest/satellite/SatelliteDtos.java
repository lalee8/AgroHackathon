package az.aqroinvest.satellite;

import java.util.List;

public final class SatelliteDtos {
    private SatelliteDtos() {}

    /** Şəklin coğrafi çərçivəsi (EPSG:4326, xətti piksel xəritələnməsi). */
    public record Window(double minLng, double minLat, double maxLng, double maxLat, int width, int height) {
        public String bbox() {
            return String.format(java.util.Locale.ROOT, "%.6f,%.6f,%.6f,%.6f", minLng, minLat, maxLng, maxLat);
        }

        public String hash() {
            return Integer.toHexString(bbox().hashCode() & 0x7fffffff);
        }
    }

    /** Epoxa: canlı görüntüdə çəkiliş tarixi, bulud payı və sahənin NDVI-si də verilir. */
    public record EpochView(String key, String period, String source, String url, String date, boolean live, Double ndvi, Double cloud) {}

    /** Son Sentinel-2 görüntüsünün metaməlumatı (diskdə saxlanılır, periodik yenilənir). */
    public record LiveMeta(long sceneId, String date, double cloud, Double ndvi, long fetchedAt) {}

    public record ImageryView(Long plotId, String plotCode, Window window, List<List<Double>> polygon,
                              List<EpochView> epochs, String occupationNote, String attribution) {}

    public record NdviPoint(int year, double ndvi, String source) {}

    public record NdviSeries(Long plotId, String plotCode, List<NdviPoint> points, Double baseline1990,
                             Double occupationMean, Double latest, Integer latestYear, String trend,
                             String narrative, String method) {}

    public record MonitoringRow(Long plotId, String code, String name, String districtLabel, String category,
                                String categoryLabel, String plotStatus, List<NdviPoint> points, Double latest,
                                Integer latestYear, Double previous, Double occupationMean, String status,
                                String statusLabel, String note) {}
}
