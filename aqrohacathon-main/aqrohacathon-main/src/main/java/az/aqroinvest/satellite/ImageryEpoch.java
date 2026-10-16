package az.aqroinvest.satellite;

/** "Torpağın zaman maşını" üçün peyk şəkli epoxaları. */
public enum ImageryEpoch {
    Y1990("1990", "İşğaldan əvvəl", "Landsat 5 · GLS 1990 arxivi (USGS/NASA)", Source.LANDSAT, "1986-01-01", "1994-01-01", null),
    Y2000("2000", "İşğal dövrü", "Landsat 7 · GLS 2000 arxivi (USGS/NASA)", Source.LANDSAT, "1999-01-01", "2003-06-01", null),
    Y2010("2010", "İşğal dövrü", "Landsat · GLS 2010 arxivi (USGS/NASA)", Source.LANDSAT, "2008-01-01", "2012-06-01", null),
    Y2018("2018", "İşğal dövrü", "Sentinel-2 · buludsuz illik mozaika (Copernicus/ESA, EOX)", Source.SENTINEL, null, null, "s2cloudless-2018"),
    Y2025("2025", "Azadlıqdan sonra · bərpa", "Sentinel-2 · 2025 buludsuz illik mozaika (Copernicus/ESA, EOX)", Source.SENTINEL, null, null, "s2cloudless-2025"),
    /** Ən son buludsuz Sentinel-2 görüntüsü: hər 5 gündən bir yeni çəkiliş, server özü yeniləyir. */
    LIVE("live", "Bu gün · son peyk görüntüsü", "Sentinel-2 L2A · son buludsuz çəkiliş (Copernicus/ESA, AWS açıq məlumatı)", Source.SENTINEL_LIVE, null, null, null);

    public enum Source { LANDSAT, SENTINEL, SENTINEL_LIVE }

    private final String key;
    private final String period;
    private final String sourceLabel;
    private final Source source;
    private final String from;
    private final String to;
    private final String layer;

    ImageryEpoch(String key, String period, String sourceLabel, Source source, String from, String to, String layer) {
        this.key = key;
        this.period = period;
        this.sourceLabel = sourceLabel;
        this.source = source;
        this.from = from;
        this.to = to;
        this.layer = layer;
    }

    public String key() { return key; }
    public String period() { return period; }
    public String sourceLabel() { return sourceLabel; }
    public Source source() { return source; }
    public String from() { return from; }
    public String to() { return to; }
    public String layer() { return layer; }

    public static ImageryEpoch of(String key) {
        for (ImageryEpoch e : values()) if (e.key.equals(key)) return e;
        throw new IllegalArgumentException("Naməlum epoxa: " + key);
    }
}
