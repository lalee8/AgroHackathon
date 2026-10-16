package az.aqroinvest.plan;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.economics.LandPotentialService;
import az.aqroinvest.economics.UseCase;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;

/**
 * Fəaliyyət planı: "bu torpaqda nə, nə vaxt, niyə və nə ilə etmək olar". Sahənin uyğunluq qiymətləndirməsi
 * (LandPotentialService), real iqlimi (NASA POWER), aqronomik təqvim və normativlər (resources/agro/calendar.json,
 * mənbələri ilə) və müsabiqə Qaydalarının minimum tələbləri (4.9) bir plana yığılır.
 */
@Service
@RequiredArgsConstructor
public class PlanService {
    private static final int[] DAYS = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    private final PlotService plots;
    private final LandPotentialService potential;
    private final ClimateService climate;
    private final ObjectMapper mapper;
    private JsonNode calendar;

    public record Need(String key, String icon, String label, String value, String note) {}

    public record Check(boolean ok, String text) {}

    public record Water(String unit, double needMinM3Ha, double needMaxM3Ha, double[] needMm, double[] rainMm, double[] irrigationMm,
                        double irrigationM3Total, double rainAnnualMm, String method, String note) {}

    public record OptionRef(String useCase, String label, String icon, int suitability, String verdict, boolean permitted) {}

    public record Plan(Map<String, Object> plot, String zone, String zoneLabel, String useCase, String useLabel, List<OptionRef> options,
                       Map<String, Object> fit, JsonNode use, List<Map<String, Object>> items, JsonNode item, List<JsonNode> calendar,
                       JsonNode climate, Water water, List<Need> needs, List<JsonNode> risks, List<Check> checks, List<String> rules,
                       JsonNode first12, List<String> sources, String disclaimer) {}

    @Transactional(readOnly = true)
    public Plan plan(Long plotId, String useParam, String itemParam) {
        Plot p = plots.find(plotId);
        JsonNode cal = calendar();
        List<LandPotentialService.Option> opts = potential.options(p);
        UseCase use;
        try {
            use = useParam == null || useParam.isBlank() ? opts.get(0).useCase() : UseCase.valueOf(useParam);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("UNKNOWN_USE", "Naməlum istiqamət: " + useParam);
        }
        LandPotentialService.Option opt = potential.option(p, use);
        JsonNode u = cal.path(use.name());
        if (u.isMissingNode()) throw ApiException.notFound("Bu istiqamət üçün plan");

        String zone = p.getAltitudeM() != null && p.getAltitudeM() >= 600 ? "mountain" : "lowland";
        List<Map<String, Object>> items = new ArrayList<>();
        JsonNode item = null;
        for (JsonNode it : u.path("items")) {
            boolean suitable = it.path("zoneSuitability").path(zone).path("suitable").asBoolean(true);
            items.add(Map.of("id", it.path("id").asText(), "name", it.path("name").asText(), "suitable", suitable,
                    "note", it.path("zoneSuitability").path(zone).path("note").asText("")));
            if (itemParam != null && itemParam.equals(it.path("id").asText())) item = it;
        }
        if (item == null) {
            for (JsonNode it : u.path("items")) if (it.path("zoneSuitability").path(zone).path("suitable").asBoolean(true)) { item = it; break; }
            if (item == null) item = u.path("items").path(0);
        }

        List<JsonNode> cl = new ArrayList<>();
        for (JsonNode a : item.path("calendar")) {
            String z = a.path("zone").asText("all");
            if (!z.equals("all") && !z.equals(zone)) continue;
            ObjectNode c = a.deepCopy();
            c.put("kind", kind(a.path("activity").asText()));
            cl.add(c);
        }
        List<JsonNode> risks = new ArrayList<>();
        for (JsonNode r : item.path("climateRisks")) {
            String z = r.path("zone").asText("all");
            if (z.equals("all") || z.equals(zone)) risks.add(r);
        }

        JsonNode clim = climate.climate(p);
        double ha = p.getAreaHa() == null ? 0 : p.getAreaHa().doubleValue();
        Water water = water(item, zone, clim, ha);
        List<Need> needs = needs(item, zone, ha, opt, water);
        List<Check> checks = checks(p, item, zone, use, clim);

        Map<String, Object> plot = new LinkedHashMap<>();
        plot.put("id", p.getId());
        plot.put("code", p.getCode());
        plot.put("name", p.getName());
        plot.put("district", p.getDistrict().label());
        plot.put("village", p.getVillage());
        plot.put("areaHa", ha);
        plot.put("altitudeM", p.getAltitudeM());
        plot.put("slopePercent", p.getSlopePercent());
        plot.put("irrigation", p.isIrrigation());
        plot.put("waterSource", p.getWaterSource());
        plot.put("soilQuality", p.getSoilQuality());
        plot.put("soilType", p.getSoilType());
        plot.put("demining", p.getDeminingStatus() == null ? null : p.getDeminingStatus().label());
        plot.put("designation", p.getDesignation() == null ? null : p.getDesignation().label());
        plot.put("lat", p.getLatitude());
        plot.put("lng", p.getLongitude());

        Map<String, Object> fit = new LinkedHashMap<>();
        fit.put("suitability", opt.suitability());
        fit.put("verdict", opt.verdict());
        fit.put("permitted", opt.permitted());
        fit.put("reasons", opt.reasons());
        fit.put("capex", opt.capex());
        fit.put("annualNet", opt.annualNet());
        fit.put("paybackP50", opt.paybackP50());
        fit.put("probPayback", opt.probPayback());
        fit.put("jobs", opt.jobs());
        fit.put("horizon", opt.horizon());
        fit.put("scale", opt.scale());
        fit.put("unit", opt.unit());

        ObjectNode useInfo = mapper.createObjectNode();
        useInfo.put("name", u.path("name").asText());
        useInfo.put("summary", u.path("summary").asText());
        useInfo.set("zones", u.path("zones"));

        List<String> sources = new ArrayList<>();
        u.path("sources").forEach(s -> sources.add(s.isTextual() ? s.asText() : s.path("url").asText(s.toString())));
        sources.add("https://power.larc.nasa.gov (iqlim, 2001-2025)");
        sources.add("https://president.az/az/articles/view/67411 (investisiya müsabiqələri Qaydaları, 4.9)");

        return new Plan(plot, zone, zone.equals("mountain") ? "Dağətəyi və dağ zonası" : "Aran zonası", use.name(), use.label(),
                opts.stream().map(o -> new OptionRef(o.useCase().name(), o.label(), o.icon(), o.suitability(), o.verdict(), o.permitted())).toList(),
                fit, useInfo, items, item, cl, clim, water, needs, risks, checks, rules(use, item), u.path("first12Months"), sources,
                u.path("disclaimer").asText("Rəqəmlər təxminidir: torpaq analizi və aqronom məsləhəti ilə dəqiqləşdirilməlidir."));
    }

    // ------------------------------------------------------------------ hesablamalar

    /** Bitkinin illik su tələbi aylara temperatur üzrə bölünür, effektiv yağıntı (80%) çıxılır, qalan suvarma ehtiyacıdır. */
    static Water water(JsonNode item, String zone, JsonNode clim, double ha) {
        JsonNode w = item.path("waterNeed").path(zone);
        if (w.isMissingNode() || w.isNull()) w = item.path("waterNeed").path(zone.equals("mountain") ? "lowland" : "mountain");
        String unit = w.path("unit").asText("");
        String method = item.path("waterNeed").path("irrigationType").asText("");
        if (!unit.startsWith("m3/ha") || w.path("min").isNull() || w.path("max").isNull() || clim == null) {
            return new Water(unit, w.path("min").asDouble(0), w.path("max").asDouble(0), null, rain(clim), null, 0,
                    clim == null ? 0 : Arrays.stream(rain(clim)).sum(), method, item.path("waterNeed").path("note").asText(""));
        }
        double min = w.path("min").asDouble(), max = w.path("max").asDouble();
        double annualMm = (min + max) / 2 / 10.0;
        JsonNode t = clim.path("months").path("T2M");
        double[] weight = new double[12];
        double sum = 0;
        for (int m = 0; m < 12; m++) {
            weight[m] = Math.max(0, t.path(m).asDouble() - 8);
            sum += weight[m];
        }
        double[] need = new double[12], rainMm = rain(clim), irr = new double[12];
        double irrTotal = 0;
        for (int m = 0; m < 12; m++) {
            need[m] = sum == 0 ? 0 : Math.round(annualMm * weight[m] / sum);
            irr[m] = Math.max(0, Math.round(need[m] - 0.8 * rainMm[m]));
            irrTotal += irr[m];
        }
        return new Water(unit, min, max, need, rainMm, irr, Math.round(irrTotal * 10 * ha), Math.round(Arrays.stream(rainMm).sum()), method,
                item.path("waterNeed").path("note").asText(""));
    }

    private static double[] rain(JsonNode clim) {
        double[] r = new double[12];
        if (clim == null) return r;
        JsonNode pr = clim.path("months").path("PRECTOTCORR");
        for (int m = 0; m < 12; m++) r[m] = Math.round(pr.path(m).asDouble() * DAYS[m]);
        return r;
    }

    private static List<Need> needs(JsonNode item, String zone, double ha, LandPotentialService.Option opt, Water water) {
        List<Need> out = new ArrayList<>();
        JsonNode d = zoneOr(item.path("plantingDensity"), zone);
        String du = d == null ? "" : d.path("unit").asText("");
        if (d != null && d.has("min") && du.startsWith("ha/MVt")) {
            // Günəş stansiyası: sahəyə sığan güc və illik istehsal
            double capMin = ha / d.path("max").asDouble(), capMax = ha / d.path("min").asDouble();
            out.add(new Need("density", "sun", "Quraşdırıla bilən güc", range(capMin, capMax) + " MVt", range(d.path("min").asDouble(), d.path("max").asDouble()) + " " + du));
            JsonNode y = zoneOr(item.path("typicalYield"), zone);
            if (y != null && y.has("min")) out.add(new Need("yield", "zap", "İllik istehsal", range(capMin * y.path("min").asDouble() / 1000, capMax * y.path("max").asDouble() / 1000) + " mln kVt.saat",
                    range(y.path("min").asDouble(), y.path("max").asDouble()) + " " + y.path("unit").asText()));
        } else if (d != null && d.has("min") && du.contains("/ha")) {
            String unit = du.substring(0, du.indexOf("/ha")).trim();
            out.add(new Need("density", "sprout", unit.contains("arı") ? "Arı ailəsi" : unit.contains("qoyun") || unit.contains("inək") ? "Heyvan sayı" : "Əkin materialı",
                    range(d.path("min").asDouble() * ha, d.path("max").asDouble() * ha) + " " + unit,
                    range(d.path("min").asDouble(), d.path("max").asDouble()) + " " + du + (d.has("spacing") ? ", sxem " + d.path("spacing").asText() : "")));
        } else if (d != null && d.has("min")) {
            out.add(new Need("density", "sprout", "Norma", range(d.path("min").asDouble(), d.path("max").asDouble()) + " " + d.path("unit").asText(""), null));
        }
        if (water.irrigationM3Total() > 0) {
            out.add(new Need("water", "droplets", "Suvarma suyu", fmt(water.irrigationM3Total()) + " m³/il",
                    "Yağıntı çıxıldıqdan sonra, " + fmt(ha) + " ha üçün; " + water.method()));
        } else if (water.unit() != null && !water.unit().isBlank() && water.needMaxM3Ha() > 0) {
            out.add(new Need("water", "droplets", "Su", range(water.needMinM3Ha(), water.needMaxM3Ha()) + " " + water.unit(), water.method()));
        }
        JsonNode y = zoneOr(item.path("typicalYield"), zone);
        if (y != null && y.has("min") && !du.startsWith("ha/MVt")) {
            String unit = y.path("unit").asText("");
            String total = unit.startsWith("t/ha") ? range(y.path("min").asDouble() * ha, y.path("max").asDouble() * ha) + " t/il" : null;
            out.add(new Need("yield", "wheat", "Gözlənilən məhsul (tam güc)", total != null ? total : range(y.path("min").asDouble(), y.path("max").asDouble()) + " " + unit,
                    total != null ? range(y.path("min").asDouble(), y.path("max").asDouble()) + " " + unit + (y.has("note") ? ", " + y.path("note").asText() : "") : y.path("note").asText(null)));
        }
        JsonNode l = item.path("labor");
        if (l.path("permanentPer10ha").isNumber()) {
            out.add(new Need("labor", "users", "İşçi qüvvəsi", Math.max(1, Math.round(l.path("permanentPer10ha").asDouble() * ha / 10)) + " daimi, pikdə +"
                    + Math.round(l.path("seasonalPeakPer10ha").asDouble() * ha / 10) + " mövsümi", l.path("note").asText(null)));
        } else if (l.has("note")) {
            out.add(new Need("labor", "users", "İşçi qüvvəsi", null, l.path("note").asText()));
        }
        int first = item.path("yearsToFirstYield").path("value").asInt(0), full = item.path("yearsToFullYield").path("value").asInt(0);
        if (first > 0) out.add(new Need("time", "hourglass", "İlk məhsul / tam güc", first + "-ci il" + (full > 0 ? " / " + full + "-ci il" : ""),
                item.path("yearsToFirstYield").path("note").asText(null)));
        if (item.path("machinery").isArray() && !item.path("machinery").isEmpty()) {
            List<String> m = new ArrayList<>();
            item.path("machinery").forEach(x -> m.add(x.asText()));
            out.add(new Need("machinery", "tractor", "Texnika", m.size() + " növ", String.join(", ", m)));
        }
        out.add(new Need("capex", "coins", "İnvestisiya (simulyasiya)", fmt(opt.capex()) + " ₼",
                "Orta ssenaridə pul " + (opt.paybackP50() == null ? opt.horizon() + " ildən gec" : opt.paybackP50() + "-ci ildə") + " qayıdır, " + opt.jobs() + " iş yeri"));
        return out;
    }

    private static List<Check> checks(Plot p, JsonNode item, String zone, UseCase use, JsonNode clim) {
        List<Check> c = new ArrayList<>();
        double ha = p.getAreaHa() == null ? 0 : p.getAreaHa().doubleValue();
        c.add(new Check(ha >= 5, "Sahə " + fmt(ha) + " ha: Qaydalara görə müsabiqə sahəsi ən azı 5 ha olmalıdır"));
        JsonNode soil = item.path("soil");
        if (p.getAltitudeM() != null && soil.path("altitude").has("maxM")) {
            int a = p.getAltitudeM(), lo = soil.path("altitude").path("minM").asInt(0), hi = soil.path("altitude").path("maxM").asInt(5000);
            c.add(new Check(a >= lo && a <= hi, "Hündürlük " + a + " m: bu bitki üçün " + lo + "-" + hi + " m uyğundur"));
        }
        if (p.getSlopePercent() != null && soil.has("maxSlopePercent")) {
            double s = p.getSlopePercent().doubleValue();
            int max = soil.path("maxSlopePercent").asInt();
            c.add(new Check(s <= max, "Maillik " + fmt(s) + "%: ən çox " + max + "% tövsiyə olunur"));
        }
        boolean needsWater = !item.path("waterNeed").path("irrigationType").asText("").toLowerCase(Locale.ROOT).startsWith("suvarma yoxdur");
        if (needsWater && List.of(UseCase.BAGCILIQ, UseCase.UZUMCULUK, UseCase.ISTIXANA, UseCase.EKINCILIK).contains(use)) {
            c.add(new Check(p.isIrrigation(), p.isIrrigation() ? "Suvarma var" + (p.getWaterSource() != null ? ": " + p.getWaterSource() : "")
                    : "Suvarma yoxdur: quyu, hovuz və ya kanal layihəsi lazımdır (Su Ehtiyatları Dövlət Agentliyi)"));
        }
        if (p.getDeminingStatus() != null) {
            boolean clear = p.getDeminingStatus() != az.aqroinvest.plot.DeminingStatus.IN_PROGRESS;
            c.add(new Check(clear, "Minatəmizləmə: " + p.getDeminingStatus().label()));
        }
        if (clim != null) {
            double frost = clim.path("annual").path("FROST_DAYS").asDouble(0);
            double tmin = clim.path("annual").path("T2M_MIN").asDouble(0);
            String note = item.path("zoneSuitability").path(zone).path("note").asText("");
            c.add(new Check(frost < 90, String.format(Locale.ROOT, "İldə %.0f şaxtalı gün, ekstremal minimum %.0f°C (NASA POWER, 2001-2025)", frost, tmin)
                    + (note.isBlank() ? "" : ". " + note)));
        }
        if (List.of(UseCase.GUNES, UseCase.AQROTURIZM, UseCase.SOYUDUCU).contains(use)) {
            c.add(new Check(false, "Kənd təsərrüfatı icarəsi tikintiyə icazə verməyə bilər: infrastruktur torpağı 1304 nömrəli Fərmanla ayrılır (Qaydalar 4.20)"));
        }
        return c;
    }

    /** Qaydaların 4.9-cu bəndi üzrə minimum tələblər (istiqamət və bitkiyə görə). */
    static List<String> rules(UseCase use, JsonNode item) {
        String id = item.path("id").asText();
        List<String> r = new ArrayList<>();
        switch (use) {
            case BAGCILIQ -> {
                if (id.startsWith("apple")) r.add("Alma: hektara ən azı 2 250 ting, şpaler sistemi");
                else if (id.equals("pomegranate")) r.add("Nar: hektara ən azı 650 ting");
                else if (id.equals("hazelnut")) r.add("Fındıq: hektara ən azı 500 ting");
                r.add("Müasir suvarma (Nazirliyin 413 nömrəli Fərmanla təsdiq edilmiş siyahısından), sertifikatlı və ya xəstəlikdən təmiz tinglər");
                r.add("Dolu riski olan ərazidə dolu əleyhinə avadanlıq; texnikanın izlənməsi, iqlim məlumatı, suvarmanın idarəsi, inteqrir mübarizə");
            }
            case UZUMCULUK -> {
                r.add("Süfrə üzümü hektara ən azı 2 500, şərab üzümü 3 000 ting, şpaler sistemi");
                r.add("Müasir suvarma, sertifikatlı tinglər, dolu əleyhinə avadanlıq");
            }
            case HEYVANDARLIQ -> {
                r.add(id.startsWith("sheep") ? "Ən azı 500 baş qoyun və ya 200 baş keçi" : "Ən azı 80 baş damazlıq inək və ya 50 baş camış, 100% süni mayalanma");
                r.add("Mexanikləşdirilmiş tövlə, karantin, baytarlıq otağı, anbar, yem sexi, ventilyasiya");
                r.add("Heyvanların elektron identifikasiyası və izlənməsi, yem bazası və texnika");
            }
            case ARICILIQ -> r.add("Ən azı 150 arı ailəsi");
            case EKINCILIK, ISTIXANA -> {
                r.add("Zonanın ixtisaslaşmasına uyğun bitkilər, sertifikatlı toxum, növbəli əkin");
                r.add("Müasir suvarma sistemləri, resursqoruyan və ağıllı aqrotexnologiyalar");
            }
            default -> r.add("Bu fəaliyyət kənd təsərrüfatı icarəsi ilə məhdudlaşa bilər: infrastruktur torpağının ayrılması ayrıca qaydada aparılır (Qaydalar 4.20)");
        }
        r.add("Hamı üçün: aqrar sığorta, kredit ehtiyacı cədvəli, risklərin qiymətləndirilməsi, yerli işçi qüvvəsi, torpaq münbitliyinin qorunması (4.9.1-4.9.8)");
        return r;
    }

    private static JsonNode zoneOr(JsonNode n, String zone) {
        if (n == null || n.isMissingNode()) return null;
        JsonNode z = n.path(zone);
        if (z.isObject()) return z;
        z = n.path(zone.equals("mountain") ? "lowland" : "mountain");
        return z.isObject() ? z : null;
    }

    static String kind(String activity) {
        String a = activity.toLowerCase(Locale.ROOT);
        if (a.matches(".*(yığım|biçin|biçilmə|məhsul|satış|bal çəkimi|ballama|qırxım|quzulama|doğum|tədarük|istehsal|hesablaşma).*")) return "harvest";
        if (a.matches(".*(əkin|səpin|salınma|ting|şitil|pətəklərin yerləşdirilməsi|köçürmə|alınması).*")) return "plant";
        if (a.matches(".*(suvarma|su ).*")) return "water";
        if (a.matches(".*(budama|çiləmə|gübrə|mübarizə|qulluq|dərman|peyvənd|yuyulma|seyrəltmə|baxım|nəzarət|müalicə|azot|fertiq|peyin|tozlanma|pöhrə|qışa hazırla|qorunma|örtü|yem|otarma|sağım|xəstəli|herbisid|alaq|dezinfeksiya|gənə|formalaşdır|təmir|profilaktika|yoxla|baxış|stres|iqlim idarə|kölgələmə|sərinləşdirmə|qar təmizlən|sığorta|mayalanma|cütləşdir|damazlıq|rasion|qışlama|ailələrin artırılması).*")) return "care";
        if (a.matches(".*(hazırlıq|hazırlanması|şum|üzləmə|qalıq|torpaq|quraşdırıl|tikinti|montaj|layihə|icazə|analiz|hasar|planlaşdırma|sınaq|istismara qəbul|dolu torunun açılması).*")) return "prep";
        return "other";
    }

    private static String range(double a, double b) {
        return Math.round(a) == Math.round(b) ? fmt(a) : fmt(a) + "-" + fmt(b);
    }

    private static String fmt(double v) {
        if (Math.abs(v) >= 100) return String.format(Locale.ROOT, "%,.0f", v).replace(',', ' ');
        if (v == Math.rint(v)) return String.format(Locale.ROOT, "%.0f", v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private JsonNode calendar() {
        if (calendar == null) {
            try (InputStream in = new ClassPathResource("agro/calendar.json").getInputStream()) {
                calendar = mapper.readTree(in);
            } catch (Exception e) {
                throw new IllegalStateException("agro/calendar.json oxunmadı", e);
            }
        }
        return calendar;
    }
}
