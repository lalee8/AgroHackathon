package az.aqroinvest.plot;

/** İnnovasiya meyarı üzrə qiymətləndirilən müasir aqrotexnologiyalar. */
public enum Technology {
    DRIP_IRRIGATION("Damcı suvarma", 3),
    SOLAR("Günəş enerjisi panelləri", 3),
    IOT_SENSORS("Ağıllı sensorlar (IoT) və torpaq monitorinqi", 3),
    DRONES("Dron ilə monitorinq və çiləmə", 2),
    COLD_STORAGE("Soyuducu anbar / logistika", 2),
    ORGANIC("Üzvi (orqanik) sertifikatlı istehsal", 2),
    PROCESSING("Emal müəssisəsi (əlavə dəyər zənciri)", 3),
    CLIMATE_CONTROL("İqlim nəzarətli istixana", 2),
    PRECISION_AG("GPS / peyk əsaslı dəqiq əkinçilik", 2),
    WATER_RECYCLING("Su təkrar istifadəsi / yağış suyu toplanması", 2);

    private final String label;
    private final int points;

    Technology(String label, int points) {
        this.label = label;
        this.points = points;
    }

    public String label() { return label; }

    public int points() { return points; }

    public static Technology parse(String code) {
        try {
            return Technology.valueOf(code);
        } catch (Exception e) {
            return null;
        }
    }
}
