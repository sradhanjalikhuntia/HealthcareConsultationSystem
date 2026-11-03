package com.apexcare.appointment.repository;

import com.apexcare.appointment.entity.EmergencyAlert;
import com.apexcare.appointment.entity.EmergencyCodeType;
import com.apexcare.appointment.entity.EmergencySeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmergencyAlertRepository extends JpaRepository<EmergencyAlert, UUID> {

    // -----------------------------------------------------------------
    // Active alerts — most common query for the ward dashboard
    // -----------------------------------------------------------------

    List<EmergencyAlert> findByActiveOrderByTriggeredAtDesc(boolean active);

    Page<EmergencyAlert> findByActiveOrderByTriggeredAtDesc(boolean active, Pageable pageable);

    List<EmergencyAlert> findByActiveTrueOrderByTriggeredAtDesc();

    // -----------------------------------------------------------------
    // Full history / audit view
    // -----------------------------------------------------------------

    List<EmergencyAlert> findAllByOrderByTriggeredAtDesc();

    Page<EmergencyAlert> findAllByOrderByTriggeredAtDesc(Pageable pageable);

    // -----------------------------------------------------------------
    // Filters
    // -----------------------------------------------------------------

    List<EmergencyAlert> findByCodeTypeOrderByTriggeredAtDesc(EmergencyCodeType codeType);

    List<EmergencyAlert> findBySeverityOrderByTriggeredAtDesc(EmergencySeverity severity);

    List<EmergencyAlert> findByActiveAndCodeTypeOrderByTriggeredAtDesc(
            boolean active, EmergencyCodeType codeType);

    List<EmergencyAlert> findByTriggeredAtBetweenOrderByTriggeredAtDesc(
            Instant from, Instant to);

    List<EmergencyAlert> findByTriggeredByOrderByTriggeredAtDesc(UUID triggeredBy);

    // -----------------------------------------------------------------
    // Existence / count helpers
    // -----------------------------------------------------------------

    boolean existsByActiveTrueAndCodeType(EmergencyCodeType codeType);

    long countByActiveTrue();

    long countByCodeType(EmergencyCodeType codeType);

    long countByActiveTrueAndSeverity(EmergencySeverity severity);
}
