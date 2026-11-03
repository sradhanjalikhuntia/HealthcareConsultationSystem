package com.apexcare.billing.repository;

import com.apexcare.billing.entity.InsuranceClaim;
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
public interface InsuranceClaimRepository extends JpaRepository<InsuranceClaim, UUID> {

    // -----------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------

    List<InsuranceClaim> findByPatientIdOrderBySubmittedAtDesc(UUID patientId);

    Page<InsuranceClaim> findByPatientIdOrderBySubmittedAtDesc(UUID patientId, Pageable pageable);

    Optional<InsuranceClaim> findByClaimNumber(String claimNumber);

    Optional<InsuranceClaim> findFirstByBillIdOrderBySubmittedAtDesc(UUID billId);

    boolean existsByBillIdAndStatusNot(UUID billId, String status);

    // -----------------------------------------------------------------
    // Status-based
    // -----------------------------------------------------------------

    Page<InsuranceClaim> findByStatusOrderBySubmittedAtDesc(String status, Pageable pageable);

    long countByStatus(String status);

    long countByPatientIdAndStatus(UUID patientId, String status);

    // -----------------------------------------------------------------
    // Provider + date filters
    // -----------------------------------------------------------------

    Page<InsuranceClaim> findByProviderNameOrderBySubmittedAtDesc(String providerName, Pageable pageable);

    Page<InsuranceClaim> findBySubmittedAtBetweenOrderBySubmittedAtDesc(
            Instant from, Instant to, Pageable pageable);

    // -----------------------------------------------------------------
    // Aggregates for dashboard
    // -----------------------------------------------------------------

    @Query("SELECT COALESCE(SUM(c.claimedAmount), 0) FROM InsuranceClaim c WHERE c.status = :status")
    BigDecimal sumClaimedAmountByStatus(@Param("status") String status);

    @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM InsuranceClaim c WHERE c.status = :status")
    BigDecimal sumApprovedAmountByStatus(@Param("status") String status);

    @Query("SELECT COALESCE(SUM(c.claimedAmount), 0) FROM InsuranceClaim c")
    BigDecimal sumTotalClaimedAmount();
}
