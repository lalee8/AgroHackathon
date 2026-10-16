package az.aqroinvest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Statik fayllar üçün keş siyasəti. Spring Security standart olaraq hər cavaba "no-store" yazır və brauzer hər keçiddə
 * CSS, Chart.js və ikonları yenidən yükləyir. Burada başlıq əvvəlcədən qoyulur (Security mövcud başlığı dəyişmir):
 * <ul>
 *   <li>vendor kitabxanaları: 7 gün (versiyası dəyişmir);</li>
 *   <li>şəkillər və ikonlar: 1 saat;</li>
 *   <li>HTML, CSS, JS və tərcümə lüğətləri: "no-cache", yəni hər dəfə Last-Modified ilə yoxlanır, dəyişməyibsə 304 gəlir.
 *       Yeni deploy-dan sonra köhnə JS işləmir.</li>
 * </ul>
 * İnkişaf rejimində (spring.web.resources.cache.period=0) resurs işləyicisi başlığı yenidən "no-store" edir, bu normaldır.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class StaticCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String policy = policy(req.getMethod(), req.getRequestURI());
        if (policy != null) res.setHeader("Cache-Control", policy);
        chain.doFilter(req, res);
    }

    static String policy(String method, String uri) {
        if (!"GET".equals(method) && !"HEAD".equals(method)) return null;
        if (uri.startsWith("/vendor/")) return "public, max-age=604800";
        if (uri.startsWith("/img/")) return "public, max-age=3600";
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/i18n/") || uri.endsWith(".html") || uri.equals("/")) return "no-cache";
        return null;
    }
}
