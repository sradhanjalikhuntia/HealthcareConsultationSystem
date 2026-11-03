package com.apexcare.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "patient_dispense_records",
        indexes = {
                @Index(name = "idx_dispense_patient", columnList = "patient_id"),
                @Index(name = "idx_dispense_billed", columnList = "billed"),
                @Index(name = "idx_dispense_created", columnList = "dispensed_at")
        }
)
public class PatientDispenseRecord {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "patient_name", length = 120)
    private String patientName;

    @Column(name = "pharmacy_item_id", nullable = false, updatable = false)
    private UUID pharmacyItemId;

    @Column(name = "medicine_name", nullable = false, length = 120, updatable = false)
    private String medicineName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "dispensed_at", nullable = false, updatable = false)
    private Instant dispensedAt;

    private UUID dispensedBy;

    @Column(nullable = false)
    private Boolean billed = false;

    @Column(name = "bill_id")
    private UUID billId;

    @PrePersist
    void onCreate() {
        if (this.id == null) this.id = UUID.randomUUID();
        if (this.dispensedAt == null) this.dispensedAt = Instant.now();
        if (this.billed == null) this.billed = false;
        if (this.totalAmount == null && this.unitPrice != null && this.quantity != null) {
            this.totalAmount = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
        }
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public UUID getPharmacyItemId() { return pharmacyItemId; }
    public void setPharmacyItemId(UUID pharmacyItemId) { this.pharmacyItemId = pharmacyItemId; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Instant getDispensedAt() { return dispensedAt; }
    public void setDispensedAt(Instant dispensedAt) { this.dispensedAt = dispensedAt; }

    public UUID getDispensedBy() { return dispensedBy; }
    public void setDispensedBy(UUID dispensedBy) { this.dispensedBy = dispensedBy; }

    public Boolean getBilled() { return billed; }
    public void setBilled(Boolean billed) { this.billed = billed; }

    public UUID getBillId() { return billId; }
    public void setBillId(UUID billId) { this.billId = billId; }
}

