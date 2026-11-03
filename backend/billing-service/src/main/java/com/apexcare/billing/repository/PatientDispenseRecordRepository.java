package com.apexcare.billing.repository;

import com.apexcare.billing.entity.PatientDispenseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientDispenseRecordRepository extends JpaRepository<PatientDispenseRecord, UUID> {
    List<PatientDispenseRecord> findByPatientIdOrderByDispensedAtDesc(UUID patientId);
    List<PatientDispenseRecord> findByPatientIdAndBilledFalseOrderByDispensedAtDesc(UUID patientId);
    List<PatientDispenseRecord> findByBillId(UUID billId);
}

