package com.apexcare.patient.repository;

import com.apexcare.patient.entity.News2RiskLevel;
import com.apexcare.patient.entity.PatientVitals;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientVitalsRepository extends JpaRepository<PatientVitals, UUID> {

    Page<PatientVitals> findByPatientIdOrderByRecordedAtDesc(UUID patientId, Pageable pageable);

    List<PatientVitals> findByPatientIdOrderByRecordedAtDesc(UUID patientId);

    Optional<PatientVitals> findFirstByPatientIdOrderByRecordedAtDesc(UUID patientId);

    Page<PatientVitals> findByPatientIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            UUID patientId, Instant from, Instant to, Pageable pageable);

    List<PatientVitals> findByPatientIdAndNews2ScoreGreaterThanEqualOrderByRecordedAtDesc(
            UUID patientId, int minScore);

    List<PatientVitals> findByRiskLevelAndRecordedAtAfterOrderByRecordedAtDesc(
            News2RiskLevel riskLevel, Instant since);

    @Query("""
            SELECT v FROM PatientVitals v
            WHERE v.patientId = :patientId
              AND v.recordedAt = (
                    SELECT MAX(v2.recordedAt) FROM PatientVitals v2
                    WHERE v2.patientId = :patientId
              )
            """)
    Optional<PatientVitals> findLatestByPatientId(@Param("patientId") UUID patientId);
}
