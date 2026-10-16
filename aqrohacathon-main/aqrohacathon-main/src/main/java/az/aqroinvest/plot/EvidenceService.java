package az.aqroinvest.plot;

import az.aqroinvest.satellite.SatelliteDtos;
import az.aqroinvest.satellite.SatelliteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AgroEvidence: sahə haqqında hər faktın mənbəyi, yoxlanma vəziyyəti, yenilənmə tarixi və çatışmayan məlumatlar.
 * Prototipdə dövlət reyestrlərindən (kadastr, ANAMA, meliorasiya) gələcək məlumatlar demo kimi açıq işarələnir:
 * yalnız real ölçülən mənbələr (peyk, PVGIS, portalın öz müsabiqə məlumatı) "təsdiqlənib" sayılır.
 */
@Service
@RequiredArgsConstructor
public class EvidenceService {
    private final PlotService plots;
    private final SatelliteService satellite;

    /** Mənbənin növü: REAL - ölçülən və ya rəsmi açıq məlumat; PORTAL - platformanın öz qeydi; DEMO - pilotda reyestrlə əvəz olunacaq. */
    public enum Kind { REAL, PORTAL, DEMO }

    public record Fact(String key, String label, String value, String source, Kind kind, String updated) {}

    public record Gap(String label, String why) {}

    public record Evidence(Long plotId, String code, int completeness, int realShare, List<Fact> facts, List<Gap> gaps, String note) {}

    @Transactional(readOnly = true)
    public Evidence evidence(Long id) {
        Plot p = plots.find(id);
        String upd = date(p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt());
        List<Fact> f = new ArrayList<>();
        List<Gap> gaps = new ArrayList<>();
        f.add(new Fact("area", "Sahə və sərhəd", p.getAreaHa().stripTrailingZeros().toPlainString() + " ha, poliqon " + (p.getPolygon() == null ? "yoxdur" : "var"),
                "Dövlət Torpaq Kadastrı (pilotda inteqrasiya; indi nümunə sərhəd)", Kind.DEMO, upd));
        f.add(new Fact("demining", "Minatəmizləmə", p.getDeminingStatus() == null ? "məlumat yoxdur" : p.getDeminingStatus().label()
                + (p.getDeminingCertificate() != null ? " · sertifikat " + p.getDeminingCertificate() : ""), "ANAMA (pilotda inteqrasiya; indi nümunə qeyd)", Kind.DEMO, upd));
        if (p.getDeminingCertificate() == null) gaps.add(new Gap("Minatəmizləmə sertifikatının nömrəsi", "ANAMA ilə inteqrasiyadan sonra avtomatik doldurulacaq"));
        if (p.getSoilQuality() != null) {
            f.add(new Fact("soil", "Torpaq keyfiyyəti (bonitet)", p.getSoilQuality() + "/100" + (p.getSoilType() != null ? " · " + p.getSoilType() : ""),
                    "Aqrokimya xəritəsi (regional orta göstərici, sahə analizi deyil)", Kind.DEMO, upd));
        } else gaps.add(new Gap("Torpaq keyfiyyəti", "Bonitet balı daxil edilməyib"));
        gaps.add(new Gap("Torpağın laboratoriya analizi", "Sahədən nümunə götürülməyib: humus, pH və duzluluq investor qərarından əvvəl yoxlanmalıdır"));
        f.add(new Fact("water", "Suvarma", p.isIrrigation() ? "var" + (p.getWaterSource() != null ? " · " + p.getWaterSource() : "") : "yoxdur",
                "Meliorasiya və Su Təsərrüfatı ASC (pilotda inteqrasiya; indi nümunə qeyd)", Kind.DEMO, upd));
        if (p.isIrrigation() && p.getWaterSource() == null) gaps.add(new Gap("Su mənbəyi", "Suvarma var, amma mənbə göstərilməyib"));
        f.add(new Fact("infra", "Elektrik, qaz, yol", String.format(Locale.ROOT, "elektrik %s, qaz %s, asfalt yola %s km", p.isElectricity() ? "var" : "yoxdur",
                p.isGas() ? "var" : "yoxdur", p.getRoadDistanceKm() == null ? "?" : p.getRoadDistanceKm().stripTrailingZeros().toPlainString()),
                "Azərişıq, Azəriqaz, AAYDA (pilotda inteqrasiya; indi nümunə qeyd)", Kind.DEMO, upd));
        if (p.getRoadDistanceKm() == null) gaps.add(new Gap("Yola məsafə", "Göstərilməyib"));
        if (p.getAltitudeM() != null || p.getSlopePercent() != null) {
            f.add(new Fact("relief", "Relyef", (p.getAltitudeM() == null ? "" : p.getAltitudeM() + " m hündürlük") + (p.getSlopePercent() == null ? "" : ", maillik "
                    + p.getSlopePercent().stripTrailingZeros().toPlainString() + "%"), "Açıq rəqəmsal relyef modeli (SRTM) əsasında", Kind.REAL, upd));
        }
        SatelliteDtos.NdviSeries n = satellite.ndvi(p);
        if (n.latest() != null) {
            f.add(new Fact("ndvi", "Bitki örtüyü (NDVI)", String.format(Locale.ROOT, "%.2f (%d), 1990-dan bəri %d il", n.latest(), n.latestYear(), n.points().size()),
                    "Landsat 5/7/8/9 (USGS/NASA), sahənin poliqonu üzrə hesablanıb", Kind.REAL, String.valueOf(n.latestYear())));
        } else gaps.add(new Gap("Bitki örtüyü tarixçəsi", "Peyk məlumatı hələ hesablanmayıb"));
        SatelliteDtos.LiveMeta live = satellite.live(p);
        if (live != null) {
            f.add(new Fact("live", "Son peyk görüntüsü", "Sentinel-2, bulud " + live.cloud() + "%" + (live.ndvi() != null ? String.format(Locale.ROOT, ", NDVI %.2f", live.ndvi()) : ""),
                    "Copernicus Sentinel-2 L2A (AWS açıq məlumatı), avtomatik yenilənir", Kind.REAL, live.date()));
        }
        f.add(new Fact("terms", "İcarə şərtləri", (p.getLeaseYears() > 0 ? "ilk 5 il, " + p.getLeaseYears() + " ilədək uzadılır, " : "") + p.getAnnualRentPerHa().stripTrailingZeros().toPlainString()
                + " ₼/ha/il, minimum investisiya " + String.format(Locale.ROOT, "%,d", p.getMinInvestment().longValue()).replace(',', ' ') + " ₼", "Portal: müsabiqə şərtləri (Qaydalar 4.16; icarə haqqı nümunədir)", Kind.PORTAL, upd));
        int total = f.size() + gaps.size();
        int completeness = Math.round(100f * f.size() / Math.max(1, total));
        long real = f.stream().filter(x -> x.kind() == Kind.REAL).count();
        int realShare = Math.round(100f * real / Math.max(1, f.size()));
        return new Evidence(p.getId(), p.getCode(), completeness, realShare, f, gaps,
                "Uyğunluq balı yalnız bu faktlardan hesablanır. \"Demo\" işarəli məlumat pilotda müvafiq dövlət reyestrindən avtomatik gələcək; o vaxta qədər qərar üçün yerində yoxlama tövsiyə olunur.");
    }

    private static String date(Instant t) {
        return t == null ? null : LocalDate.ofInstant(t, ZoneOffset.UTC).toString();
    }
}
