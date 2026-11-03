package com.apexcare.billing.repository;

import com.apexcare.billing.entity.MedicineCategory;
import com.apexcare.billing.entity.PharmacyItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PharmacyItemRepository extends JpaRepository<PharmacyItem, UUID> {

    // -----------------------------------------------------------------
    // Category / name lookups
    // -----------------------------------------------------------------

    List<PharmacyItem> findByCategory(MedicineCategory category);

    Page<PharmacyItem> findByCategory(MedicineCategory category, Pageable pageable);

    Page<PharmacyItem> findAll(Pageable pageable);   // inherited, redeclared for clarity

    Optional<PharmacyItem> findFirstByMedicineNameAndStrengthAndBatchNumber(
            String medicineName, String strength, String batchNumber);

    boolean existsByMedicineNameAndStrengthAndBatchNumber(
            String medicineName, String strength, String batchNumber);

    @Query("""
            SELECT p FROM PharmacyItem p
            WHERE LOWER(p.medicineName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.genericName)  LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<PharmacyItem> searchByNameOrGeneric(@Param("q") String q, Pageable pageable);

    // -----------------------------------------------------------------
    // Stock health queries
    // -----------------------------------------------------------------

    /** Items where stockQuantity <= reorderThreshold. Bounded for safety. */
    @Query("SELECT p FROM PharmacyItem p WHERE p.stockQuantity <= p.reorderThreshold")
    List<PharmacyItem> findLowStockItems();

    @Query("SELECT p FROM PharmacyItem p WHERE p.stockQuantity <= p.reorderThreshold")
    Page<PharmacyItem> findLowStockItems(Pageable pageable);

    @Query("SELECT p FROM PharmacyItem p WHERE p.stockQuantity = 0")
    List<PharmacyItem> findOutOfStockItems();

    // -----------------------------------------------------------------
    // Expiry queries
    // -----------------------------------------------------------------

    /**
     * Items expiring on or before the given date.
     * Excludes items already expired (compare with today for that).
     */
    List<PharmacyItem> findByExpiryDateBetweenOrderByExpiryDateAsc(
            LocalDate from, LocalDate to);

    /** Items already expired as of the given reference date. */
    List<PharmacyItem> findByExpiryDateBeforeOrderByExpiryDateAsc(LocalDate referenceDate);

    // -----------------------------------------------------------------
    // Aggregates
    // -----------------------------------------------------------------

    long countByCategory(MedicineCategory category);

    @Query("SELECT COUNT(p) FROM PharmacyItem p WHERE p.stockQuantity <= p.reorderThreshold")
    long countLowStockItems();

    @Query("SELECT COUNT(p) FROM PharmacyItem p WHERE p.stockQuantity = 0")
    long countOutOfStockItems();

    @Query("SELECT COUNT(p) FROM PharmacyItem p WHERE p.expiryDate < :today")
    long countExpired(@Param("today") LocalDate today);
}
