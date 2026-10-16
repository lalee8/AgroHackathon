package az.aqroinvest.plot;

import az.aqroinvest.common.ApiException;
import az.aqroinvest.plot.PlotDtos.PlotView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Paylaşım linki: /p/{id}. Telegram, WhatsApp, Facebook, LinkedIn kimi xidmətlər JavaScript işlətmir, ona görə sahənin adı,
 * ölçüsü, şərtləri və peyk şəkli Open Graph teqləri ilə serverdə yazılır. İnsan ziyarətçi dərhal sahə səhifəsinə yönləndirilir.
 */
@Tag(name = "Torpaq sahələri")
@RestController
@RequiredArgsConstructor
public class PlotShareController {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Asia/Baku"));
    private final PlotService plots;

    @Operation(summary = "Sahə üçün paylaşım səhifəsi (Open Graph önizləməsi, sonra sahə səhifəsinə yönləndirmə)")
    @GetMapping(value = "/p/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> share(@PathVariable String id, HttpServletRequest req) throws NoResourceFoundException {
        PlotView p;
        try {
            p = plots.get(Long.parseLong(id));
        } catch (NumberFormatException | ApiException e) {
            // Mövcud olmayan sahə: dizaynlı "Səhifə tapılmadı" səhifəsi
            throw new NoResourceFoundException(org.springframework.http.HttpMethod.GET, req.getRequestURI());
        }
        String origin = origin(req);
        String target = "/sahe.html?id=" + p.id();
        String title = p.code() + " · " + p.name() + " · " + num(p.areaHa()) + " ha";
        StringBuilder d = new StringBuilder();
        d.append(p.districtLabel()).append(" rayonu, ").append(p.categoryLabel()).append(". ");
        d.append("İcarə ").append(num(p.annualRentPerHa())).append(" ₼/ha, minimum investisiya ").append(money(p.minInvestment()));
        d.append(", ").append(p.minJobs()).append(" iş yeri. ");
        if (p.competition() != null && p.competition().open()) {
            d.append("Müsabiqəyə müraciət ").append(DAY.format(p.competition().deadlineAt())).append(" tarixinədək.");
        } else {
            d.append(p.statusLabel()).append(".");
        }
        String image = origin + "/api/v1/plots/" + p.id() + "/imagery/2025";
        String html = """
                <!doctype html>
                <html lang="az"><head><meta charset="utf-8">
                <title>%1$s - Əkinçi 2.0</title>
                <meta name="description" content="%2$s">
                <meta property="og:type" content="website">
                <meta property="og:site_name" content="Əkinçi 2.0">
                <meta property="og:title" content="%1$s">
                <meta property="og:description" content="%2$s">
                <meta property="og:url" content="%3$s">
                <meta property="og:image" content="%4$s">
                <meta property="og:image:alt" content="%5$s sahəsinin peyk görüntüsü (Sentinel-2, 2025)">
                <meta name="twitter:card" content="summary_large_image">
                <meta name="twitter:title" content="%1$s">
                <meta name="twitter:description" content="%2$s">
                <meta name="twitter:image" content="%4$s">
                <link rel="canonical" href="%6$s">
                <meta http-equiv="refresh" content="0;url=%7$s">
                </head><body><p><a href="%7$s">%1$s</a></p></body></html>
                """.formatted(esc(title), esc(d.toString()), esc(origin + "/p/" + p.id()), esc(image), esc(p.code()),
                esc(origin + target), esc(target));
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic()).body(html);
    }

    static String origin(HttpServletRequest req) {
        String proto = first(req.getHeader("X-Forwarded-Proto"));
        String host = first(req.getHeader("X-Forwarded-Host"));
        if (proto == null) proto = req.getScheme();
        if (host == null) host = req.getHeader("Host");
        if (host == null) host = req.getServerName() + ":" + req.getServerPort();
        return proto + "://" + host;
    }

    private static String first(String header) {
        if (header == null || header.isBlank()) return null;
        String v = header.split(",")[0].trim();
        return v.matches("[A-Za-z0-9.:\\-\\[\\]]+") ? v : null;
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s, "UTF-8");
    }

    private static String num(BigDecimal v) {
        if (v == null) return "-";
        return v.stripTrailingZeros().scale() <= 0 ? v.setScale(0, RoundingMode.HALF_UP).toPlainString() : v.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static String money(BigDecimal v) {
        if (v == null) return "-";
        double x = v.doubleValue();
        if (x >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1f mln ₼", x / 1_000_000);
        return String.format(java.util.Locale.ROOT, "%.0f min ₼", x / 1_000);
    }
}
