package com.apexcare.billing.repository;

import com.apexcare.billing.entity.LabTestOrder;
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
public interface LabTestOrderRepository extends JpaRepository<LabTestOrder, UUID> {

    // -----------------------------------------------------------------
    // Patient / doctor lookups
    // -----------------------------------------------------------------

    List<LabTestOrder> findByPatientIdOrderByOrderedAtDesc(UUID patientId);

    List<LabTestOrder> findByPatientIdAndBilledFalseOrderByOrderedAtDesc(UUID patientId);

    List<LabTestOrder> findByBillId(UUID billId);

    Page<LabTestOrder> findByPatientIdOrderByOrderedAtDesc(UUID patientId, Pageable pageable);

    Page<LabTestOrder> findByDoctorIdOrderByOrderedAtDesc(UUID doctorId, Pageable pageable);

    Optional<LabTestOrder> findFirstByAppointmentIdOrderByOrderedAtDesc(UUID appointmentId);

    boolean existsByAppointmentId(UUID appointmentId);

    // -----------------------------------------------------------------
    // Status / category / priority
    // -----------------------------------------------------------------

    Page<LabTestOrder> findByStatusOrderByOrderedAtDesc(String status, Pageable pageable);

    Page<LabTestOrder> findByCategoryOrderByOrderedAtDesc(String category, Pageable pageable);

    Page<LabTestOrder> findByStatusAndCategoryOrderByOrderedAtDesc(
            String status, String category, Pageable pageable);

    Page<LabTestOrder> findByPriorityOrderByOrderedAtDesc(String priority, Pageable pageable);

    long countByStatus(String status);

    long countByPatientIdAndStatus(UUID patientId, String status);

    long countByDoctorIdAndStatus(UUID doctorId, String status);

    // -----------------------------------------------------------------
    // Critical-value dashboard
    // -----------------------------------------------------------------

    List<LabTestOrder> findByAbnormalFlagInAndStatusOrderByResultReportedAtDesc(
            List<String> abnormalFlags, String status);

    Page<LabTestOrder> findByAbnormalFlagOrderByResultReportedAtDesc(
            String abnormalFlag, Pageable pageable);

    // -----------------------------------------------------------------
    // Sample / date filters
    // -----------------------------------------------------------------

    Optional<LabTestOrder> findBySampleId(String sampleId);

    Page<LabTestOrder> findByOrderedAtBetweenOrderByOrderedAtDesc(
            Instant from, Instant to, Pageable pageable);

    // -----------------------------------------------------------------
    // Aggregates
    // -----------------------------------------------------------------

    @Query("SELECT COUNT(l) FROM LabTestOrder l WHERE l.status = :status")
    long countActiveByStatus(@Param("status") String status);

    @Query("SELECT COUNT(l) FROM LabTestOrder l "
            + "WHERE l.status = 'COMPLETED' "
            + "AND l.abnormalFlag IN ('CRITICAL_HIGH','CRITICAL_LOW') "
            + "AND l.resultReportedAt >= :since")
    long countCriticalResultsSince(@Param("since") Instant since);
}
