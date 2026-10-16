package az.aqroinvest.satellite;

import az.aqroinvest.config.AppProperties;
import az.aqroinvest.plot.Plot;
import az.aqroinvest.plot.PlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Son peyk görüntülərini fonda yeniləyir: Sentinel-2 hər 5 gündən bir eyni yeri yenidən çəkir, server bütün sahələr
 * üçün yeni az buludlu səhnə olub-olmadığını mütəmadi yoxlayır. İstifadəçi səhifəni açanda gözləmir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveImageryRefresher {
    private final SatelliteService satellite;
    private final PlotRepository plots;
    private final AppProperties props;

    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT6H")
    @Transactional(readOnly = true)
    public void refresh() {
        if (props.imagery() == null || !props.imagery().enabled()) return;
        int n = 0;
        for (Plot p : plots.findAll()) {
            try {
                if (satellite.live(p) != null) n++;
            } catch (Exception e) {
                log.debug("Son görüntü yenilənmədi ({}): {}", p.getCode(), e.getMessage());
            }
        }
        log.info("Son peyk görüntüləri yoxlanıldı: {} sahə", n);
    }
}
