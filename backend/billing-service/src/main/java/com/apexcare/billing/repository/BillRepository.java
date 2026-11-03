package com.apexcare.billing.repository;

import com.apexcare.billing.entity.Bill;
import com.apexcare.billing.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillRepository extends JpaRepository<Bill, UUID> {

    // -----------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------

    Page<Bill> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);

    Page<Bill> findByPatientId(UUID patientId, Pageable pageable);

    Page<Bill> findByPatientIdAndPaymentStatus(UUID patientId, PaymentStatus paymentStatus, Pageable pageable);

    List<Bill> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<Bill> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);

    Optional<Bill> findFirstByAppointmentIdOrderByCreatedAtDesc(UUID appointmentId);

    boolean existsByAppointmentId(UUID appointmentId);

    Optional<Bill> findFirstByPatientIdAndPaymentStatusOrderByCreatedAtDesc(UUID patientId, PaymentStatus paymentStatus);

    // -----------------------------------------------------------------
    // Status-based queries
    // -----------------------------------------------------------------

    Page<Bill> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);

    long countByPaymentStatus(PaymentStatus paymentStatus);

    long countByPatientIdAndPaymentStatus(UUID patientId, PaymentStatus paymentStatus);

    // -----------------------------------------------------------------
    // Aggregates — parameterized by status (no hardcoded enum literals)
    // -----------------------------------------------------------------

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b WHERE b.paymentStatus = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b")
    BigDecimal sumTotalBilledAmount();

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b WHERE b.patientId = :patientId")
    BigDecimal sumTotalAmountByPatient(@Param("patientId") UUID patientId);
}
