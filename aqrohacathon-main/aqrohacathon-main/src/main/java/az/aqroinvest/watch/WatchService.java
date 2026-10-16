package az.aqroinvest.watch;

import az.aqroinvest.notification.NotificationService;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import az.aqroinvest.plot.PlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Sevimli sahələr və "müsabiqəyə çıxanda xəbər ver" bildirişi (portal, Telegram, e-poçt kanalları ilə). */
@Service
@RequiredArgsConstructor
public class WatchService {
    private final PlotWatchRepository watches;
    private final PlotRepository plots;
    private final PlotService plotService;
    private final NotificationService notifications;

    public record WatchView(Long plotId, String code, String name, String district, String category, String categoryLabel, String status,
                            String statusLabel, java.math.BigDecimal areaHa, Instant since) {}

    @Transactional(readOnly = true)
    public List<WatchView> mine(Long userId) {
        List<PlotWatch> ws = watches.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Plot> ps = plots.findAllById(ws.stream().map(PlotWatch::getPlotId).toList()).stream()
                .collect(Collectors.toMap(Plot::getId, p -> p));
        List<WatchView> out = new ArrayList<>();
        for (PlotWatch w : ws) {
            Plot p = ps.get(w.getPlotId());
            if (p == null) continue;
            out.add(new WatchView(p.getId(), p.getCode(), p.getName(), p.getDistrict().label(), p.getCategory().name(), p.getCategory().label(),
                    p.getStatus().name(), p.getStatus().label(), p.getAreaHa(), w.getCreatedAt()));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> watch(Long userId, Long plotId, boolean on) {
        plotService.find(plotId);
        Optional<PlotWatch> cur = watches.findByUserIdAndPlotId(userId, plotId);
        if (on && cur.isEmpty()) {
            PlotWatch w = new PlotWatch();
            w.setUserId(userId);
            w.setPlotId(plotId);
            w.setCreatedAt(Instant.now());
            watches.save(w);
        } else if (!on) {
            cur.ifPresent(watches::delete);
        }
        return Map.of("plotId", plotId, "watching", on, "watchers", watches.countByPlotId(plotId));
    }

    /** Müsabiqə dərc olunanda həmin sahələri izləyənlərə ayrıca bildiriş. */
    @Transactional
    public int notifyCompetition(Collection<Plot> competitionPlots, String title, Long competitionId) {
        Map<Long, Plot> byId = competitionPlots.stream().collect(Collectors.toMap(Plot::getId, p -> p, (a, b) -> a));
        int n = 0;
        for (PlotWatch w : watches.findByPlotIdIn(byId.keySet())) {
            Plot p = byId.get(w.getPlotId());
            notifications.notify(w.getUserId(), "WATCH", "Sevimli sahəniz müsabiqəyə çıxdı: " + p.getCode(),
                    p.getName() + " (" + p.getDistrict().label() + ") \"" + title + "\" müsabiqəsinə daxil edilib. Müraciət edə bilərsiniz.",
                    "/sahe.html?id=" + p.getId());
            n++;
        }
        return n;
    }
}
