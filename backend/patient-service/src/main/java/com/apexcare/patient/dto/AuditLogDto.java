package com.apexcare.patient.dto;

import com.apexcare.patient.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(UUID id, UUID actorId, String actorUsername, String actorRole,
                          String action, String resourceType, String resourceId,
                          String details, String ipAddress, Instant eventTimestamp) {
    public static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(log.getId(), log.getActorId(), log.getActorUsername(),
                log.getActorRole() == null ? null : log.getActorRole().name(),
                log.getAction() == null ? null : log.getAction().name(),
                log.getResourceType() == null ? null : log.getResourceType().name(),
                log.getResourceId(), log.getDetails(), log.getIpAddress(),
                log.getEventTimestamp());
    }
}
