package com.apexcare.patient.repository;

import com.apexcare.patient.entity.AdmissionRecord;
import com.apexcare.patient.entity.AdmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdmissionRecordRepository extends JpaRepository<AdmissionRecord, UUID> {

    List<AdmissionRecord> findByPatientIdOrderByAdmittedAtDesc(UUID patientId);

    Optional<AdmissionRecord> findByBedIdAndStatus(UUID bedId, AdmissionStatus status);

    List<AdmissionRecord> findByPatientIdAndStatus(UUID patientId, AdmissionStatus status);

    boolean existsByBedIdAndStatus(UUID bedId, AdmissionStatus status);

    boolean existsByPatientIdAndStatus(UUID patientId, AdmissionStatus status);

    long countByStatus(AdmissionStatus status);
}
