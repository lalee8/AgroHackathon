package az.aqroinvest.plot;

/** İşğaldan azad edilmiş rayonlar (Qarabağ və Şərqi Zəngəzur iqtisadi rayonları). */
public enum District {
    AGDAM("Ağdam", "Qarabağ", 39.993, 46.930),
    FUZULI("Füzuli", "Qarabağ", 39.600, 47.145),
    XOCAVEND("Xocavənd", "Qarabağ", 39.795, 47.112),
    XOCALI("Xocalı", "Qarabağ", 39.913, 46.790),
    SUSA("Şuşa", "Qarabağ", 39.758, 46.749),
    TERTER("Tərtər", "Qarabağ", 40.343, 46.932),
    CEBRAYIL("Cəbrayıl", "Şərqi Zəngəzur", 39.399, 47.028),
    ZENGILAN("Zəngilan", "Şərqi Zəngəzur", 39.086, 46.652),
    QUBADLI("Qubadlı", "Şərqi Zəngəzur", 39.344, 46.580),
    LACIN("Laçın", "Şərqi Zəngəzur", 39.640, 46.547),
    KELBECER("Kəlbəcər", "Şərqi Zəngəzur", 40.105, 46.038);

    private final String label;
    private final String region;
    private final double lat;
    private final double lng;

    District(String label, String region, double lat, double lng) {
        this.label = label;
        this.region = region;
        this.lat = lat;
        this.lng = lng;
    }

    public String label() { return label; }
    public String region() { return region; }
    public double lat() { return lat; }
    public double lng() { return lng; }
}
