package com.apexcare.patient.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.apexcare.patient.entity.Patient;
import com.apexcare.patient.entity.PatientStatus;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Page<Patient> findByStatus(PatientStatus status, Pageable pageable);

    @Query("""
            SELECT p FROM Patient p
            WHERE (:status IS NULL OR p.status = :status)
              AND (
                    CAST(:q AS string) IS NULL
                    OR LOWER(p.firstName)       LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%')
                    OR LOWER(p.lastName)        LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%')
                    OR LOWER(p.email)           LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%')
                    OR p.phone                  LIKE CONCAT('%', CAST(:q AS string), '%')
              )
            """)
    Page<Patient> search(@Param("q") String q,
                         @Param("status") PatientStatus status,
                         Pageable pageable);

    long countByStatus(PatientStatus status);

    boolean existsByUniquePatientId(String uniquePatientId);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Patient> findByUniquePatientId(String uniquePatientId);

    Optional<Patient> findTopByUniquePatientIdStartingWithOrderByUniquePatientIdDesc(String prefix);

    List<Patient> findByUniquePatientIdIsNull();
}
