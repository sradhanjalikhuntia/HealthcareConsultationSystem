package com.apexcare.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "pharmacy_inventory",
        uniqueConstraints = {
                // Same medicine + batch + strength cannot be double-entered
                @UniqueConstraint(
                        name = "uk_pharmacy_batch",
                        columnNames = {"medicine_name", "strength", "batch_number"})
        },
        indexes = {
                @Index(name = "idx_pharmacy_name", columnList = "medicine_name"),
                @Index(name = "idx_pharmacy_generic", columnList = "generic_name"),
                @Index(name = "idx_pharmacy_category", columnList = "category"),
                @Index(name = "idx_pharmacy_expiry", columnList = "expiry_date"),
                @Index(name = "idx_pharmacy_low_stock", columnList = "stock_quantity,reorder_threshold")
        }
)
public class PharmacyItem {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "medicine_name", nullable = false, length = 120, updatable = false)
    private String medicineName;

    @Column(name = "generic_name", nullable = false, length = 120, updatable = false)
    private String genericName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60, updatable = false)
    private MedicineCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "dosage_form", nullable = false, length = 32, updatable = false)
    private DosageForm dosageForm;

    /** e.g. "500mg", "10mg/5ml", "100IU" — free text, matching pharma labelling. */
    @Column(nullable = false, length = 32, updatable = false)
    private String strength;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @Column(name = "reorder_threshold", nullable = false)
    private Integer reorderThreshold = 20;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "batch_number", length = 64, updatable = false)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "manufacturer", length = 120, updatable = false)
    private String manufacturer;

    // ---- Audit ----

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private UUID createdBy;

    @Version
    private Long version;

    // =================================================================
    // Lifecycle
    // =================================================================

    @PrePersist
    void onCreate() {
        this.id = UUID.randomUUID();
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.stockQuantity == null)  this.stockQuantity = 0;
        if (this.reorderThreshold == null) this.reorderThreshold = 20;

        validateInputs();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        validateInputs();
    }

    private void validateInputs() {
        if (stockQuantity != null && stockQuantity < 0) {
            throw new IllegalStateException("stockQuantity cannot be negative");
        }
        if (reorderThreshold != null && reorderThreshold < 0) {
            throw new IllegalArgumentException("reorderThreshold cannot be negative");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("unitPrice must be zero or positive");
        }
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now().minusDays(1))) {
            // Allowing "expires today" but not-yet-expired; avoid accidentally
            // persisting expired stock as fresh inventory.
            throw new IllegalStateException(
                    "Cannot persist expired item (expiryDate=" + expiryDate + ")");
        }
    }

    // =================================================================
    // Domain methods
    // =================================================================

    /**
     * Deducts {@code quantity} from stock. Throws if insufficient stock.
     * Concurrency-safe via optimistic locking ({@link Version}).
     */
    public void dispense(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Dispense quantity must be positive");
        }
        if (this.stockQuantity < quantity) {
            throw new IllegalStateException(
                    "Insufficient stock: requested " + quantity
                            + ", available " + this.stockQuantity);
        }
        this.stockQuantity -= quantity;
    }

    /** Adds {@code quantity} to stock (batch replenishment). */
    public void replenish(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Replenish quantity must be positive");
        }
        this.stockQuantity += quantity;
    }

    /** Adjusts stock to an exact value (stock-take reconciliation). */
    public void adjustTo(int newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
        this.stockQuantity = newQuantity;
    }

    /** True if stock is at or below the reorder threshold. */
    public boolean isLowStock() {
        return this.stockQuantity != null
                && this.reorderThreshold != null
                && this.stockQuantity <= this.reorderThreshold;
    }

    /** True if the item is expired as of today. */
    public boolean isExpired() {
        return this.expiryDate != null && this.expiryDate.isBefore(LocalDate.now());
    }

    /** True if expiry is within {@code days} days (near-expiry alert). */
    public boolean isExpiringSoon(int days) {
        if (this.expiryDate == null) return false;
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return !this.expiryDate.isAfter(cutoff) && !isExpired();
    }

    // =================================================================
    // equals / hashCode
    // =================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PharmacyItem)) return false;
        PharmacyItem other = (PharmacyItem) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // =================================================================
    // Getters / setters
    // =================================================================

    public UUID getId() { return id; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public String getGenericName() { return genericName; }
    public void setGenericName(String genericName) { this.genericName = genericName; }

    public MedicineCategory getCategory() { return category; }
    public void setCategory(MedicineCategory category) { this.category = category; }

    public DosageForm getDosageForm() { return dosageForm; }
    public void setDosageForm(DosageForm dosageForm) { this.dosageForm = dosageForm; }

    public String getStrength() { return strength; }
    public void setStrength(String strength) { this.strength = strength; }

    /** Direct setter retained for admin correction; prefer {@link #dispense(int)} / {@link #replenish(int)}. */
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public Integer getReorderThreshold() { return reorderThreshold; }
    public void setReorderThreshold(Integer reorderThreshold) { this.reorderThreshold = reorderThreshold; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice == null ? null : unitPrice.setScale(2, RoundingMode.HALF_UP);
    }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Long getVersion() { return version; }
}
