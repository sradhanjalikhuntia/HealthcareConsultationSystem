package com.apexcare.billing.dto;

import com.apexcare.billing.entity.PharmacyItem;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PharmacyItemResponse {

    private UUID id;
    private String medicineName;
    private String genericName;
    private String category;
    private String dosageForm;
    private String strength;

    private Integer stockQuantity;
    private Integer reorderThreshold;
    private BigDecimal unitPrice;

    private String batchNumber;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    private String manufacturer;

    // Computed convenience fields (not stored on entity)
    private Boolean lowStock;
    private Boolean expired;
    private Boolean expiringSoon;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    public static PharmacyItemResponse from(PharmacyItem i) {
        if (i == null) return null;
        PharmacyItemResponse r = new PharmacyItemResponse();
        r.id = i.getId();
        r.medicineName = i.getMedicineName();
        r.genericName = i.getGenericName();
        r.category = i.getCategory() == null ? null : i.getCategory().name();
        r.dosageForm = i.getDosageForm() == null ? null : i.getDosageForm().name();
        r.strength = i.getStrength();
        r.stockQuantity = i.getStockQuantity();
        r.reorderThreshold = i.getReorderThreshold();
        r.unitPrice = i.getUnitPrice();
        r.batchNumber = i.getBatchNumber();
        r.expiryDate = i.getExpiryDate();
        r.manufacturer = i.getManufacturer();
        r.createdAt = i.getCreatedAt();
        r.updatedAt = i.getUpdatedAt();
        r.lowStock = i.isLowStock();
        r.expired = i.isExpired();
        r.expiringSoon = i.isExpiringSoon(30);
        return r;
    }

    // ---- getters ----
    public UUID getId() { return id; }
    public String getMedicineName() { return medicineName; }
    public String getGenericName() { return genericName; }
    public String getCategory() { return category; }
    public String getDosageForm() { return dosageForm; }
    public String getStrength() { return strength; }
    public Integer getStockQuantity() { return stockQuantity; }
    public Integer getReorderThreshold() { return reorderThreshold; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getBatchNumber() { return batchNumber; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public String getManufacturer() { return manufacturer; }
    public Boolean getLowStock() { return lowStock; }
    public Boolean getExpired() { return expired; }
    public Boolean getExpiringSoon() { return expiringSoon; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
