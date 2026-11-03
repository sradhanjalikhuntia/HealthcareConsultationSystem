package com.apexcare.billing.repository;

import com.apexcare.billing.entity.PatientAdmissionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientAdmissionRecordRepository extends JpaRepository<PatientAdmissionRecord, UUID> {
    List<PatientAdmissionRecord> findByPatientIdOrderByDischargedAtDesc(UUID patientId);
    List<PatientAdmissionRecord> findByPatientIdAndBilledFalseOrderByDischargedAtDesc(UUID patientId);
    List<PatientAdmissionRecord> findByBillId(UUID billId);
    Optional<PatientAdmissionRecord> findByAdmissionId(UUID admissionId);
}

