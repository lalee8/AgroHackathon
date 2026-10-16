package az.aqroinvest.common;

/** Şəxsi məlumatların maskalanması ("Fərdi məlumatlar haqqında" Qanuna uyğun minimal göstərmə prinsipi). */
public final class Masking {
    private Masking() {}

    public static String fin(String fin) {
        if (fin == null || fin.length() < 3) return fin;
        return fin.substring(0, 2) + "*".repeat(fin.length() - 3) + fin.substring(fin.length() - 1);
    }

    public static String phone(String phone) {
        if (phone == null || phone.length() < 6) return phone;
        return phone.substring(0, phone.length() - 6) + "***" + phone.substring(phone.length() - 3);
    }

    public static String email(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String visible = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return visible + "***" + email.substring(at);
    }
}
