package az.aqroinvest.advisor;

import java.util.Locale;

public final class Money {
    private Money() {}

    public static String fmt(double v) {
        double a = Math.abs(v);
        String s = a >= 1e6 ? String.format(Locale.ROOT, "%.1f mln ₼", a / 1e6) : a >= 1e4 ? String.format(Locale.ROOT, "%.0f min ₼", a / 1e3)
                : String.format(Locale.ROOT, "%.0f ₼", a);
        return (v < 0 ? "-" : "") + s.replace(".0 mln", " mln");
    }
}
