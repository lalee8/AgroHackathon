package az.aqroinvest.audit;

import az.aqroinvest.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repository;
    private final CurrentUser currentUser;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, Object entityId, String details) {
        AuditLog log = new AuditLog();
        currentUser.optional().ifPresent(p -> {
            log.setActorId(p.id());
            log.setActorEmail(p.email());
        });
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : String.valueOf(entityId));
        log.setDetails(details);
        log.setIpAddress(clientIp());
        repository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAs(Long actorId, String actorEmail, String action, String entityType, Object entityId, String details) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setActorEmail(actorEmail);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : String.valueOf(entityId));
        log.setDetails(details);
        log.setIpAddress(clientIp());
        repository.save(log);
    }

    public static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        HttpServletRequest req = attrs.getRequest();
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
