package com.apexcare.billing.service;

import com.apexcare.billing.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lightweight audit-log service for the billing bounded context.
 *
 * <p>Records significant state-changing operations to the application log.
 * In a future iteration this can be wired to a dedicated audit_logs table
 * or a message-broker topic without changing any caller.</p>
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    /**
     * Records an audit event.
     *
     * @param principal    the authenticated user performing the action (may be null for system calls)
     * @param action       short verb describing the operation (e.g. "CREATE_BILL", "PAYMENT_RECEIVED")
     * @param resourceType the type of resource being acted on (e.g. "BILL", "PHARMACY_ITEM")
     * @param resourceId   the resource's ID as a string
     * @param details      human-readable description of the change
     * @param extra        optional extra context (reserved for future use; may be null)
     */
    public void record(JwtPrincipal principal,
                       String action,
                       String resourceType,
                       String resourceId,
                       String details,
                       Object extra) {

        String actorId   = principal != null ? String.valueOf(principal.userId()) : "SYSTEM";
        String actorName = principal != null ? principal.username() : "system";
        String actorRole = principal != null ? principal.role()     : "SYSTEM";

        log.info("[AUDIT] action={} resource={}/{} actor={} ({}, {}) details=\"{}\"",
                action, resourceType, resourceId,
                actorId, actorName, actorRole,
                details);

        // Extra context is accepted for future structured logging / audit table integration.
        if (extra != null) {
            log.debug("[AUDIT] extra context for action={}: {}", action, extra);
        }
    }
}
