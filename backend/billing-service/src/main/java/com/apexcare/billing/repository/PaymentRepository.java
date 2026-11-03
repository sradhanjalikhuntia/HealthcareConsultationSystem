package com.apexcare.billing.repository;

import com.apexcare.billing.entity.Payment;
import com.apexcare.billing.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // -----------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------

    List<Payment> findByBillIdOrderByPaymentDateDesc(UUID billId);

    Page<Payment> findByBillIdOrderByPaymentDateDesc(UUID billId, Pageable pageable);

    Page<Payment> findByPatientIdOrderByPaymentDateDesc(UUID patientId, Pageable pageable);

    Optional<Payment> findByTransactionReference(String transactionReference);

    boolean existsByTransactionReference(String transactionReference);

    Optional<Payment> findFirstByBillIdAndStatusOrderByPaymentDateDesc(
            UUID billId, PaymentStatus status);

    // -----------------------------------------------------------------
    // Status-based
    // -----------------------------------------------------------------

    Page<Payment> findByStatusOrderByPaymentDateDesc(PaymentStatus status, Pageable pageable);

    long countByStatus(PaymentStatus status);

    long countByBillIdAndStatus(UUID billId, PaymentStatus status);

    List<Payment> findByStatusAndPaymentDateBetween(
            PaymentStatus status, Instant from, Instant to);

    // -----------------------------------------------------------------
    // Aggregates
    // -----------------------------------------------------------------

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p "
            + "WHERE p.status = :status AND p.paymentDate BETWEEN :from AND :to")
    BigDecimal sumAmountByStatusBetween(@Param("status") PaymentStatus status,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    @Query("SELECT COALESCE(SUM(p.refundedAmount), 0) FROM Payment p "
            + "WHERE p.status = :status")
    BigDecimal sumRefundedAmountByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.billId = :billId AND p.status = :status")
    BigDecimal sumAmountByBillAndStatus(@Param("billId") UUID billId,
                                        @Param("status") PaymentStatus status);
}
