package com.apexcare.patient.service;

import com.apexcare.patient.entity.AuditAction;
import com.apexcare.patient.entity.AuditLog;
import com.apexcare.patient.entity.ResourceType;
import com.apexcare.patient.entity.Role;
import com.apexcare.patient.repository.AuditLogRepository;
import com.apexcare.patient.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    /** Reserved actor id used when no real user is available (system jobs, startup). */
    public static final UUID SYSTEM_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    // -----------------------------------------------------------------
    // Primary API — preferred
    // -----------------------------------------------------------------
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(JwtPrincipal principal,
                       AuditAction action,
                       ResourceType resourceType,
                       String resourceId,
                       String details,
                       String ipAddress) {
        UUID actorId = principal != null ? principal.userId() : SYSTEM_ACTOR_ID;
        String username = principal != null ? principal.username() : "system";
        Role role = principal != null ? principal.role() : Role.SYSTEM;

        save(actorId, username, role, action, resourceType, resourceId, details, ipAddress);
    }

    // -----------------------------------------------------------------
    // Low-level API — for system events with no principal
    // -----------------------------------------------------------------
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystem(AuditAction action,
                             ResourceType resourceType,
                             String resourceId,
                             String details) {
        save(SYSTEM_ACTOR_ID, "system", Role.SYSTEM, action, resourceType, resourceId, details, null);
    }

    public Page<AuditLog> getLogs(Pageable pageable) {
        return repository.findAllByOrderByEventTimestampDesc(pageable);
    }

    // -----------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------
    private void save(UUID actorId,
                      String username,
                      Role role,
                      AuditAction action,
                      ResourceType resourceType,
                      String resourceId,
                      String details,
                      String ipAddress) {
        try {
            AuditLog entry = AuditLog.record(
                    actorId,
                    username,
                    role,
                    action,
                    resourceType,
                    resourceId,
                    details,
                    normalizeIp(ipAddress)
            );
            repository.save(entry);
        } catch (Exception ex) {
            // Audit failures must never break the main business flow.
            // Log loudly so ops can investigate — but do not rethrow.
            log.error("Failed to write audit entry action={} resource={}:{}",
                    action, resourceType, resourceId, ex);
        }
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) return null;
        // X-Forwarded-For may contain a chain: "client, proxy1, proxy2" — keep the first.
        int comma = ip.indexOf(',');
        String first = (comma > 0 ? ip.substring(0, comma) : ip).trim();
        return first.length() > 45 ? first.substring(0, 45) : first;   // IPv6 max length
    }
}
