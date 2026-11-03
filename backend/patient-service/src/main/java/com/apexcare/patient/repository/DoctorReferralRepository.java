package com.apexcare.patient.repository;

import com.apexcare.patient.entity.DoctorReferral;
import com.apexcare.patient.entity.ReferralPriority;
import com.apexcare.patient.entity.ReferralStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DoctorReferralRepository extends JpaRepository<DoctorReferral, UUID> {

    List<DoctorReferral> findByReferredDoctorIdOrderByCreatedAtDesc(UUID doctorId);

    List<DoctorReferral> findByReferringDoctorIdOrderByCreatedAtDesc(UUID doctorId);

    List<DoctorReferral> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<DoctorReferral> findByReferredDoctorIdAndStatusOrderByCreatedAtDesc(
            UUID doctorId, ReferralStatus status);

    List<DoctorReferral> findByReferringDoctorIdAndStatusOrderByCreatedAtDesc(
            UUID doctorId, ReferralStatus status);

    Page<DoctorReferral> findByStatusOrderByCreatedAtDesc(ReferralStatus status, Pageable pageable);

    Page<DoctorReferral> findByStatusAndPriorityOrderByCreatedAtDesc(
            ReferralStatus status, ReferralPriority priority, Pageable pageable);

    long countByReferredDoctorIdAndStatus(UUID doctorId, ReferralStatus status);

    long countByReferringDoctorIdAndStatus(UUID doctorId, ReferralStatus status);
}
