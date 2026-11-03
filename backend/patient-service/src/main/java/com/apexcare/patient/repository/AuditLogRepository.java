package com.apexcare.patient.repository;

import com.apexcare.patient.entity.AuditAction;
import com.apexcare.patient.entity.AuditLog;
import com.apexcare.patient.entity.ResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByOrderByEventTimestampDesc(Pageable pageable);

    Page<AuditLog> findByActorIdOrderByEventTimestampDesc(UUID actorId, Pageable pageable);

    Page<AuditLog> findByResourceTypeAndResourceIdOrderByEventTimestampDesc(
            ResourceType resourceType, String resourceId, Pageable pageable);

    Page<AuditLog> findByActionOrderByEventTimestampDesc(AuditAction action, Pageable pageable);

    Page<AuditLog> findByEventTimestampBetweenOrderByEventTimestampDesc(
            Instant from, Instant to, Pageable pageable);
}
