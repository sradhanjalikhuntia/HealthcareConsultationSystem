package com.apexcare.patient.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable   // Hibernate-level protection: no updates allowed
@Table(name = "hipaa_audit_logs", indexes = {
        @Index(name = "idx_audit_actor", columnList = "actor_id"),
        @Index(name = "idx_audit_resource", columnList = "resource_type,resource_id"),
        @Index(name = "idx_audit_timestamp", columnList = "event_timestamp"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_actor_timestamp", columnList = "actor_id,event_timestamp")
})
public class AuditLog {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "actor_id", updatable = false, nullable = false)
    private UUID actorId;

    @Column(name = "actor_username", updatable = false, nullable = false, length = 80)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", updatable = false, nullable = false, length = 32)
    private Role actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", updatable = false, nullable = false, length = 64)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", updatable = false, nullable = false, length = 64)
    private ResourceType resourceType;

    @Column(name = "resource_id", updatable = false, length = 64)
    private String resourceId;

    @Column(name = "details", updatable = false, length = 2000)
    private String details;

    /**
     * Client IP address (IPv4 or IPv6).
     * If the request came via proxy, this holds the original client IP
     * extracted from X-Forwarded-For (first entry only).
     */
    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "event_timestamp", updatable = false, nullable = false)
    private Instant eventTimestamp;

    /** JPA needs a no-arg constructor; keep it protected to discourage direct instantiation. */
    protected AuditLog() {}

    // ---------------------------------------------------------------
    // Factory method — the ONLY way to create an audit entry
    // ---------------------------------------------------------------
    public static AuditLog record(
            UUID actorId,
            String actorUsername,
            Role actorRole,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            String details,
            String ipAddress
    ) {
        if (actorId == null || action == null || resourceType == null) {
            throw new IllegalArgumentException(
                    "actorId, action, and resourceType are mandatory for an audit entry");
        }

        AuditLog entry = new AuditLog();
        entry.id = UUID.randomUUID();
        entry.actorId = actorId;
        entry.actorUsername = actorUsername;
        entry.actorRole = actorRole;
        entry.action = action;
        entry.resourceType = resourceType;
        entry.resourceId = resourceId;
        entry.details = details;
        entry.ipAddress = ipAddress;
        entry.eventTimestamp = Instant.now();
        return entry;
    }

    // ---------------------------------------------------------------
    // Getters only — no public setters (immutability)
    // ---------------------------------------------------------------

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getActorUsername() { return actorUsername; }
    public Role getActorRole() { return actorRole; }
    public AuditAction getAction() { return action; }
    public ResourceType getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public Instant getEventTimestamp() { return eventTimestamp; }

    // ---------------------------------------------------------------
    // equals / hashCode (ID-based)
    // ---------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLog)) return false;
        AuditLog other = (AuditLog) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
