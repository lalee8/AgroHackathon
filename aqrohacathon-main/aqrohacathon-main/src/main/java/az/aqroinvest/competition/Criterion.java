package az.aqroinvest.competition;

import java.util.List;

/**
 * Qiymətləndirmə meyarı və onun çəkisi (cəmi 100). Adlar investisiya müsabiqələri Qaydalarının 4.10-cu bəndinə uyğundur;
 * Qaydalara görə çəkiləri komissiya hər layihə istiqaməti üçün ayrıca müəyyən edir, ona görə müsabiqədə dəyişdirilə bilər.
 */
public record Criterion(String key, String label, int weight) {

    public static List<Criterion> defaults() {
        return List.of(
                new Criterion("investment", "İqtisadi səmərəlilik: investisiya və hektara düşən əlavə dəyər (4.10.2)", 25),
                new Criterion("jobs", "Məşğulluq və yerli resurslardan istifadə (4.9.3)", 20),
                new Criterion("businessPlan", "Layihənin 4.9 tələblərinə uyğunluğu və təcrübə (4.10.1, 4.10.4)", 15),
                new Criterion("innovation", "İdarəetmə potensialı, innovasiya və texnologiya (4.10.6)", 15),
                new Criterion("ownFunds", "Maliyyə riskləri: öz və cəlb edilmiş vəsait (4.10.5)", 10),
                new Criterion("documents", "Vergi öhdəlikləri və sənədlərin tamlığı (4.10.3, 2.4.6)", 10),
                new Criterion("social", "Yerli müraciətçi və sosial təsir (4.10.7)", 5));
    }
}
