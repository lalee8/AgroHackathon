package az.aqroinvest.economics;

import java.util.List;

/**
 * İnvestisiyanın sosial və ekoloji təsiri ("Böyük Qayıdış" baxımından): neçə iş yeri, neçə ailənin qayıdışına imkan,
 * yerli iqtisadiyyata əmək haqqı axını, istehsal olunan ərzaq, qənaət edilən təbii qaz və CO2. Bütün əmsallar təxminidir
 * və mənbələri {@link #SOURCES} siyahısında açıq göstərilir.
 *
 * @param directJobs   daimi birbaşa iş yerləri
 * @param indirectJobs təchizat, nəqliyyat, xidmət sahəsində dolayı iş yerləri
 * @param families     qayıdışına iş yeri ilə imkan yaranan ailələr
 * @param people       həmin ailələrdə təxmini insan sayı
 * @param wageFund     ildə yerli iqtisadiyyata əmək haqqı axını, ₼
 * @param foodTons     yetkin ildə istehsal olunan ərzaq, t/il
 * @param gasM3        günəş elektrik stansiyası hesabına ixraca qalan təbii qaz, m³/il
 * @param co2Tons      qarşısı alınan və ya udulan CO2, t/il
 * @param areaHa       yenidən istifadəyə qaytarılan torpaq, ha
 */
public record Impact(int directJobs, int indirectJobs, int families, int people, long wageFund, double foodTons,
                     double gasM3, double co2Tons, double areaHa) {

    public static final double FAMILY_PER_JOB = 0.8, PEOPLE_PER_FAMILY = 4;
    /** Qaradağ GES (230 MW): ildə ~500 mln kVts, ~110 mln m³ qaz qənaəti, ~200 min t CO2 azalması. */
    public static final double GAS_M3_PER_MWH = 220, CO2_T_PER_MWH = 0.4;
    /** Çoxillik bağ və üzümlüklərin karbon udması, t CO2/ha/il (IPCC diapazonunun aşağı hissəsi). */
    public static final double CO2_PERENNIAL_T_PER_HA = 2.5;

    public static final List<String> SOURCES = List.of(
            "İş yerləri: simulyasiya modelinin hər istiqamət üzrə iş yeri normaları (ha, MWp, otaq və ya ton tutum başına)",
            "Ailə: hər daimi iş yerinin 0.8 ailənin qayıdışına imkan verdiyi, ailədə orta hesabla 4 nəfər olduğu fərz edilir",
            "Qaz və CO2: Qaradağ GES (230 MW) üzrə açıqlanmış göstəricilər: ildə ~500 mln kVts, ~110 mln m³ qaz qənaəti, ~200 min t CO2 azalması",
            "Bağ və üzümlüklərin karbon udması: 2.5 t CO2/ha/il (IPCC diapazonunun aşağı hissəsi)",
            "Əmək haqqı: istiqamətlər üzrə təxmini orta aylıq əmək haqqı (kənd təsərrüfatı 550 ₼, turizm 700 ₼, emal 750 ₼, enerji 1000 ₼)");

    public static final Impact ZERO = new Impact(0, 0, 0, 0, 0, 0, 0, 0, 0);

    public static Impact of(SimulationSpec s, double areaHa) {
        UseCase u = s.useCase();
        int direct = s.jobs();
        double indirectMult = switch (u) {
            case GUNES -> 0.3;
            case AQROTURIZM -> 1.0;
            case SOYUDUCU -> 0.7;
            case ISTIXANA -> 0.6;
            default -> 0.5;
        };
        double wage = switch (u) {
            case GUNES -> 1000;
            case AQROTURIZM -> 700;
            case SOYUDUCU -> 750;
            default -> 550;
        };
        int families = (int) Math.round(direct * FAMILY_PER_JOB);
        double food = u.agro() ? s.scale() * s.yieldPerUnit() : 0;
        double mwh = u == UseCase.GUNES ? s.scale() * s.yieldPerUnit() : 0;
        double co2 = mwh * CO2_T_PER_MWH + (u == UseCase.BAGCILIQ || u == UseCase.UZUMCULUK ? s.scale() * CO2_PERENNIAL_T_PER_HA : 0);
        return new Impact(direct, (int) Math.round(direct * indirectMult), families, (int) Math.round(families * PEOPLE_PER_FAMILY),
                Math.round(direct * wage * 12), round1(food), Math.round(mwh * GAS_M3_PER_MWH), round1(co2), round1(areaHa));
    }

    public Impact plus(Impact o) {
        return new Impact(directJobs + o.directJobs, indirectJobs + o.indirectJobs, families + o.families, people + o.people,
                wageFund + o.wageFund, round1(foodTons + o.foodTons), gasM3 + o.gasM3, round1(co2Tons + o.co2Tons), round1(areaHa + o.areaHa));
    }

    /** Hər 1 mln ₼ investisiyaya düşən iş yeri (inkişaf layihələrinin əsas göstəricisi). */
    public static double jobsPerMillion(int jobs, double invest) {
        return invest <= 0 ? 0 : Math.round(jobs / (invest / 1e6) * 10) / 10.0;
    }

    /** Sahədə istifadə olunan torpaq: günəş üçün MWp × 1.5 ha, digər istiqamətlərdə sahə və ya miqyas. */
    public static double areaUsed(SimulationSpec s, double plotArea) {
        return switch (s.useCase()) {
            case GUNES -> Math.min(plotArea, s.scale() * ModelFactory.SOLAR_HA_PER_MWP);
            case AQROTURIZM -> Math.min(plotArea, Math.max(2, s.scale() * 0.4));
            case SOYUDUCU -> Math.min(plotArea, Math.max(1, s.scale() / 1000.0));
            default -> Math.min(plotArea, s.scale());
        };
    }

    private static double round1(double d) {
        return Math.round(d * 10) / 10.0;
    }
}
