package az.aqroinvest.integration.ektis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Domen hadisəsini biznes əməliyyatı ilə EYNİ tranzaksiyada outbox cədvəlinə yazır.
 * Beləliklə "müraciət yadda saxlanıldı, amma EKTİS-ə xəbər getmədi" vəziyyəti mümkün deyil.
 */
@Component
@RequiredArgsConstructor
public class IntegrationEventPublisher {
    public static final String SCHEMA_VERSION = "1.0";
    public static final String SOURCE = "aqroinvest-qarabag";

    private final IntegrationEventRepository repository;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher springEvents;

    /** Outbox-a yeni hadisə yazıldı (commit-dən sonra dərhal göndərmək üçün siqnal). */
    public record OutboxPending(Long id) {}

    @Transactional
    public IntegrationEvent publish(String eventType, String aggregateType, Object aggregateId, Object data) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("aggregate", Map.of("type", aggregateType, "id", String.valueOf(aggregateId)));
        envelope.put("data", data);

        IntegrationEvent e = new IntegrationEvent();
        e.setEventId(eventId);
        e.setEventType(eventType);
        e.setAggregateType(aggregateType);
        e.setAggregateId(String.valueOf(aggregateId));
        try {
            e.setPayload(mapper.writeValueAsString(envelope));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Hadisə serializasiya edilə bilmədi", ex);
        }
        e.setStatus(IntegrationEventStatus.PENDING);
        IntegrationEvent saved = repository.save(e);
        springEvents.publishEvent(new OutboxPending(saved.getId()));
        return saved;
    }
}
