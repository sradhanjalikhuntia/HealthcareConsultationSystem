package com.apexcare.billing.dto;

import com.apexcare.billing.exception.InvalidRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AddPharmacyItemRequest(
        String medicineName,
        String genericName,
        String category,
        String dosageForm,
        String strength,
        Integer stockQuantity,
        Integer reorderThreshold,
        BigDecimal unitPrice,
        String batchNumber,
        LocalDate expiryDate,
        String manufacturer
) {
    public AddPharmacyItemRequest {
        if (medicineName == null || medicineName.isBlank()) {
            throw new InvalidRequestException("medicineName is required");
        }
        if (genericName == null || genericName.isBlank()) {
            throw new InvalidRequestException("genericName is required");
        }
        if (category == null || category.isBlank()) {
            throw new InvalidRequestException("category is required");
        }
        if (dosageForm == null || dosageForm.isBlank()) {
            throw new InvalidRequestException("dosageForm is required");
        }
        if (strength == null || strength.isBlank()) {
            throw new InvalidRequestException("strength is required");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new InvalidRequestException("unitPrice must be zero or positive");
        }
        if (stockQuantity != null && stockQuantity < 0) {
            throw new InvalidRequestException("stockQuantity cannot be negative");
        }
        if (reorderThreshold != null && reorderThreshold < 0) {
            throw new InvalidRequestException("reorderThreshold cannot be negative");
        }
    }
}

