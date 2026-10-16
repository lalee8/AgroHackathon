package az.aqroinvest.advisor;

import az.aqroinvest.market.MarketEnums.Risk;
import az.aqroinvest.market.ProjectSector;
import az.aqroinvest.plot.District;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * İstifadəçinin sərbəst mətnindən niyyətin çıxarılması (deterministik, AI-sız): büdcə, aylıq gəlir hədəfi,
 * istiqamətlər, rayonlar, risk iştahı, müddət və sualın növü. Bu, həm AI-a göndərilən konteksti, həm də
 * oflayn cavabı qurmaq üçün istifadə olunur.
 */
public record AdvisorIntent(Long budget, Long monthlyGoal, List<ProjectSector> sectors, List<District> districts, Risk risk,
                            Integer horizon, String term, boolean entrepreneur, boolean nonArable) {

    private static final Map<ProjectSector, String[]> SECTOR_WORDS = new LinkedHashMap<>();
    private static final Map<District, String[]> DISTRICT_WORDS = new LinkedHashMap<>();
    static final Map<String, String> GLOSSARY = new LinkedHashMap<>();
    /** Təxmini məzənnələr (AMB, 2026): xarici investorun büdcəsi manata çevrilir. */
    static final double USD_AZN = 1.7, EUR_AZN = 1.95;
    private static final Pattern NUMBER = Pattern.compile("(\\d{1,3}(?:[ ,.]\\d{3})+(?!\\d)|\\d+(?:[.,]\\d+)?)\\s*(mlrd|milyard|milyar|billion|млрд|mln|milyon|millions?|млн|m\\b|minlik|min|bin|thousand|тыс|k\\b|manat|azn|₼)?");

    static {
        SECTOR_WORDS.put(ProjectSector.EKINCILIK, new String[]{"taxıl", "buğda", "arpa", "qarğıdalı", "əkinçilik", "dənli", "wheat", "grain", "пшениц", "зерн", "buğday", "tahıl"});
        SECTOR_WORDS.put(ProjectSector.BAGCILIQ, new String[]{"bağ", "meyvə", "nar", "alma", "fındıq", "gilas", "armud", "qoz", "orchard", "fruit", "apple", "pomegranate", "hazelnut", "сад", "фрукт", "яблок", "гранат", "meyve", "elma"});
        SECTOR_WORDS.put(ProjectSector.UZUMCULUK, new String[]{"üzüm", "şərab", "vineyard", "wine", "grape", "виноград", "вин"});
        SECTOR_WORDS.put(ProjectSector.ISTIXANA, new String[]{"istixana", "pomidor", "xiyar", "tərəvəz", "greenhouse", "tomato", "vegetable", "теплиц", "овощ", "sera", "domates", "sebze"});
        SECTOR_WORDS.put(ProjectSector.HEYVANDARLIQ, new String[]{"heyvan", "ferma", "süd", "mal-qara", "qoyun", "inək", " ət ", "livestock", "dairy", "cattle", "sheep", "скот", "молоч", "hayvancılık", "süt "});
        SECTOR_WORDS.put(ProjectSector.ARICILIQ, new String[]{"arı", "bal ", "honey", "beekeep", "мёд", "пчел", "arıcılık"});
        SECTOR_WORDS.put(ProjectSector.ENERJI, new String[]{"günəş", "panel", "enerji", "ges", "elektrik", "yaşıl enerji", "solar", "photovoltaic", "renewable", "energy", "солнеч", "энерг", "güneş"});
        SECTOR_WORDS.put(ProjectSector.TURIZM, new String[]{"otel", "turizm", "qonaq evi", "mehmanxana", "restoran", "turist", "hotel", "tourism", "guest house", "resort", "отель", "туризм", "гостиниц"});
        SECTOR_WORDS.put(ProjectSector.EMAL, new String[]{"anbar", "soyuducu", "emal", "qida", "zavod", "konserv", "şirə", "qablaşdırma", "sənaye", "cold storage", "warehouse", "processing", "packaging", "factory", "холодил", "склад", "переработ", "soğuk hava", "depo", "fabrika"});
        SECTOR_WORDS.put(ProjectSector.AQROTEXNOLOGIYA, new String[]{" it ", " ıt ", "texnologiya", "proqram", "tətbiq", "dron", "sensor", "startap", "rəqəmsal", "agritech", "drone", "software", "startup", "технолог", "дрон"});

        DISTRICT_WORDS.put(District.AGDAM, new String[]{"ağdam", "aghdam", "agdam", "агдам"});
        DISTRICT_WORDS.put(District.FUZULI, new String[]{"füzuli", "fuzuli", "horadiz", "физул"});
        DISTRICT_WORDS.put(District.CEBRAYIL, new String[]{"cəbrayıl", "jabrayil", "cebrail", "xudafərin", "джебраил"});
        DISTRICT_WORDS.put(District.ZENGILAN, new String[]{"zəngilan", "zangilan", "zengilan", "зангилан"});
        DISTRICT_WORDS.put(District.QUBADLI, new String[]{"qubadlı", "gubadli", "kubadlı", "губадл"});
        DISTRICT_WORDS.put(District.LACIN, new String[]{"laçın", "lachin", "laçin", "лачин"});
        DISTRICT_WORDS.put(District.KELBECER, new String[]{"kəlbəcər", "kalbajar", "kelbecer", "кельбаджар"});
        DISTRICT_WORDS.put(District.XOCALI, new String[]{"xocalı", "khojaly", "hocalı", "ходжалы"});
        DISTRICT_WORDS.put(District.XOCAVEND, new String[]{"xocavənd", "khojavend", "hocavend", "ходжавенд"});
        DISTRICT_WORDS.put(District.TERTER, new String[]{"tərtər", "tartar", "terter", "тертер"});
        DISTRICT_WORDS.put(District.SUSA, new String[]{"şuşa", "shusha", "шуша"});

        GLOSSARY.put("bonitet", "Bonitet balı torpağın keyfiyyətidir (0-100). Pul dilində: 80 bal olan sahədə məhsul orta göstəricidən təxminən 4% çox, 40 bal olan sahədə təxminən 13% az olur. Eyni xərclə gəlir dəyişir.");
        GLOSSARY.put("ndvi", "NDVI peykdən ölçülən bitki örtüyü indeksidir (0-1). 0.2-dən aşağı boş torpaq, 0.4-dən yuxarı sıx bitki örtüyü deməkdir. İnvestor üçün: torpağın keçmişdə becərildiyini və bu gün istifadə olunduğunu müstəqil göstərir.");
        GLOSSARY.put("p10", "P10, P50, P90 simulyasiyanın pis, orta və yaxşı ssenariləridir. P10: 10 ssenaridən yalnız 1-i bundan pis olur. P50: tam orta. P90: 10 ssenaridən yalnız 1-i bundan yaxşı olur.");
        GLOSSARY.put("irr", "IRR (daxili gəlirlilik) pulunuzun illik orta \"faizi\"dir. Bankdakı depozit faizi ilə müqayisə edin: 15% IRR depozitdən xeyli yaxşıdır, 3% isə zəifdir.");
        GLOSSARY.put("npv", "Xalis bugünkü dəyər (NPV): gələcək gəlirlərin bugünkü pula çevrilmiş cəmi, investisiya çıxılmaqla. Müsbətdirsə, layihə kapitalın dəyərindən (məs. 12%) çox qazandırır.");
        GLOSSARY.put("geri qayıtma", "Geri qayıtma müddəti: qoyduğunuz pulun tam geri gəldiyi il. Bağlarda ilk 3 il məhsul olmadığı üçün bu müddət uzundur.");
        GLOSSARY.put("niyyət məktubu", "Niyyət məktubu alıcının gələcək məhsulu müəyyən həcm və qiymətlə almağa yazılı razılığıdır. Pul dilində: qiymət riski azalır, bank kreditə daha asan razılaşır.");
        GLOSSARY.put("aktiv istehlakçı", "Aktiv istehlakçı: 150 kVt-a qədər günəş paneli olan fiziki şəxs öz elektrikini istehsal edir, il sonunda şəbəkəyə verdiyi artıq enerjiyə görə ödəniş alır.");
        GLOSSARY.put("təyinat", "Torpağın təyinatı qanunla nə etməyə icazə verildiyini göstərir: əkin, çoxillik əkmə, otlaq, əkinə yararsız, sənaye. Əkin yerində günəş stansiyası və ya anbar üçün təyinat dəyişikliyi lazımdır.");
        GLOSSARY.put("şoran", "Şoran torpaq duzlu torpaqdır, əkin üçün səmərəsizdir. Belə torpaqda günəş stansiyası, ekstensiv otarma və ya torpaqsız istixana daha çox dəyər yaradır.");
        GLOSSARY.put("aqrovoltaika", "Aqrovoltaika: günəş panellərinin altında qoyun otarmaq və ya kölgə sevən bitki becərmək. Torpaq həm enerji, həm kənd təsərrüfatı gəliri verir.");
        GLOSSARY.put("bar verməyə", "\"Bar verməyə 3 il\" o deməkdir ki, ilk 3 il satış olmayacaq: pul yalnız xərclənir. Bu, investisiyanın geri qayıtmasını 3 il gecikdirir.");
    }

    public static AdvisorIntent parse(String raw) {
        String t = " " + (raw == null ? "" : raw).toLowerCase(Locale.forLanguageTag("az")).replace('İ', 'i').replace("i̇", "i") + " ";
        Long budget = null, monthly = null;
        Matcher m = NUMBER.matcher(t);
        while (m.find()) {
            double v;
            String num = m.group(1);
            // "3,000,000" və ya "500 000": rəqəm qrupları; "2,5" və ya "2.5": onluq kəsr
            num = num.matches("\\d{1,3}([ ,.]\\d{3})+") ? num.replaceAll("[ ,.]", "") : num.replace(',', '.');
            try { v = Double.parseDouble(num); } catch (NumberFormatException e) { continue; }
            String u = m.group(2) == null ? "" : m.group(2);
            String after = t.substring(m.end(), Math.min(t.length(), m.end() + 14));
            String before = t.substring(Math.max(0, m.start() - 14), m.start());
            if (u.startsWith("mlrd") || u.startsWith("milyar") || u.startsWith("billion") || u.startsWith("млрд")) v *= 1_000_000_000;
            else if (u.startsWith("ml") || u.startsWith("mi") && !u.startsWith("min") || u.equals("m") || u.equals("млн")) v *= 1_000_000;
            else if (u.startsWith("min") || u.equals("k") || u.equals("bin") || u.startsWith("thousand") || u.equals("тыс")) v *= 1_000;
            else if (u.isEmpty() && (v < 1000 || after.matches("^\\s*(ha|hektar|hectare|ton|t\\b|otaq|mwp|mw|kw|nəfər|il\\b).*"))) continue; // il, faiz, hektar və s.
            String cur = before.substring(Math.max(0, before.length() - 3)) + " " + u + after.substring(0, Math.min(after.length(), 10));
            if (cur.matches("(?s).*(\\$|usd|dollar|dolar|долл).*")) v *= USD_AZN;
            else if (cur.matches("(?s).*(€|eur|avro|euro|евро).*")) v *= EUR_AZN;
            String around = before + " " + t.substring(m.end(), Math.min(t.length(), m.end() + 22));
            if (around.matches("(?s).*(ayda|aylıq|per month|a month|monthly|/ay|в месяц|aylık).*")) monthly = Math.round(v);
            else if (budget == null || v > budget) budget = Math.round(v);
        }
        List<ProjectSector> sectors = new ArrayList<>();
        SECTOR_WORDS.forEach((s, words) -> { for (String w : words) if (t.contains(w)) { sectors.add(s); break; } });
        List<District> districts = new ArrayList<>();
        DISTRICT_WORDS.forEach((d, words) -> { for (String w : words) if (t.contains(w)) { districts.add(d); break; } });
        Risk risk = null;
        if (t.matches("(?s).*(risksiz|aşağı risk|az risk|təhlükəsiz|etibarlı|riskə getmək istəmir|zəmanət|low.risk|safe|conservative|низк\\S* риск|düşük risk|güvenli).*")) risk = Risk.LOW;
        else if (t.matches("(?s).*(yüksək gəlir|yüksək risk|riskə gedirəm|aqressiv|maksimum gəlir|high.return|high.risk|aggressive|высок\\S* (риск|доход)|yüksek getiri).*")) risk = Risk.HIGH;
        else if (t.contains("orta risk") || t.contains("medium risk") || t.contains("moderate")) risk = Risk.MID;
        Integer horizon = null;
        Matcher h = Pattern.compile("(\\d{1,2})\\s*(il|years?|yıl|лет|год)").matcher(t);
        if (h.find()) horizon = Integer.parseInt(h.group(1));
        else if (t.matches(".*(tez qayıt|qısa müddət|tez qaytar|tez gəlir).*")) horizon = 5;
        String term = null;
        for (String k : GLOSSARY.keySet()) if (t.contains(k) && t.matches(".*(nədir|nə deməkdir|izah|anlamıram|başa düşmürəm|nəyə görə|nə üçün).*")) { term = k; break; }
        boolean entrepreneur = t.matches("(?s).*(sahibkaram|ideyam var|layihəm var|investor tapmaq|investor axtarıram|investoru necə|pitch|təqdim etmək|i have an idea|my project|find an investor|ищу инвестора).*");
        boolean nonArable = t.matches(".*(şoran|yararsız|daşlı|eroziya|boş torpaq|əkilməyən).*");
        if (nonArable && sectors.isEmpty()) { /* mühərrik özü alternativləri təklif edəcək */ }
        return new AdvisorIntent(budget, monthly, sectors, districts, risk, horizon, term, entrepreneur, nonArable);
    }

    public String summary() {
        List<String> p = new ArrayList<>();
        if (budget != null) p.add("büdcə " + Money.fmt(budget));
        if (monthlyGoal != null) p.add("hədəf ayda " + Money.fmt(monthlyGoal));
        if (!sectors.isEmpty()) p.add("istiqamət: " + String.join(", ", sectors.stream().map(s -> s.label().toLowerCase(Locale.forLanguageTag("az"))).toList()));
        if (!districts.isEmpty()) p.add("rayon: " + String.join(", ", districts.stream().map(District::label).toList()));
        if (risk != null) p.add(risk.label().toLowerCase(Locale.forLanguageTag("az")));
        if (horizon != null) p.add(horizon + " il");
        return String.join(" · ", p);
    }
}
