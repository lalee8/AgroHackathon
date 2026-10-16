package az.aqroinvest.integration.ektis;

import az.aqroinvest.application.ApplicationRepository;
import az.aqroinvest.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Outbox-dakı hadisələri fon rejimində EKTİS-ə ötürür.
 * Uğursuz cəhdlər eksponensial gecikmə ilə təkrarlanır (5s, 10s, 20s, ...), limitdən sonra DEAD olur
 * və admin panelindən əl ilə yenidən göndərilə bilər.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor {
    private final IntegrationEventRepository repository;
    private final ApplicationRepository applications;
    private final EktisGateway gateway;
    private final AppProperties props;
    private final TransactionTemplate tx;
    private final ReentrantLock lock = new ReentrantLock();
    private final ExecutorService immediate = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "outbox-immediate");
        t.setDaemon(true);
        return t;
    });

    /** Hadisə commit olunan kimi göndərilir (növbəti planlı dövrü gözləmədən). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPending(IntegrationEventPublisher.OutboxPending e) {
        immediate.execute(() -> {
            try {
                Thread.sleep(150);
                processDue();
            } catch (Exception ignored) {
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.ektis.outbox-interval-ms}", initialDelay = 8000)
    public void scheduled() {
        processDue();
    }

    /** @return emal olunan hadisələrin sayı */
    public int processDue() {
        if (!lock.tryLock()) return 0;
        try {
            List<IntegrationEvent> due = repository.findDue(Instant.now(), PageRequest.of(0, 25));
            for (IntegrationEvent e : due) {
                EktisGateway.DeliveryResult r = gateway.deliver(e);
                tx.executeWithoutResult(s -> apply(e.getId(), r));
            }
            return due.size();
        } finally {
            lock.unlock();
        }
    }

    private void apply(Long id, EktisGateway.DeliveryResult r) {
        IntegrationEvent e = repository.findById(id).orElseThrow();
        e.setAttempts(e.getAttempts() + 1);
        if (r.success()) {
            e.setStatus(IntegrationEventStatus.SENT);
            e.setSentAt(Instant.now());
            e.setExternalRef(r.externalRef());
            e.setLastError(null);
            if ("Application".equals(e.getAggregateType()) && "APPLICATION_SUBMITTED".equals(e.getEventType())) {
                applications.findById(Long.valueOf(e.getAggregateId())).ifPresent(a -> {
                    if (a.getEktisReference() == null) a.setEktisReference(r.externalRef());
                });
            }
        } else {
            e.setLastError(r.error());
            if (e.getAttempts() >= props.ektis().maxAttempts()) {
                e.setStatus(IntegrationEventStatus.DEAD);
                log.error("EKTİS hadisəsi {} {} cəhddən sonra çatdırılmadı: {}", e.getEventId(), e.getAttempts(), r.error());
            } else {
                e.setStatus(IntegrationEventStatus.FAILED);
                long delay = (long) (5 * Math.pow(2, e.getAttempts() - 1));
                e.setNextAttemptAt(Instant.now().plusSeconds(delay));
            }
        }
    }
}
