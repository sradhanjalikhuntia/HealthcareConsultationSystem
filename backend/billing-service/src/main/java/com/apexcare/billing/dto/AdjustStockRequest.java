package com.apexcare.billing.dto;

import com.apexcare.billing.exception.InvalidRequestException;

import java.util.UUID;

public record AdjustStockRequest(
        UUID itemId,
        Integer newQuantity,
        String reason
) {
    public AdjustStockRequest {
        if (itemId == null) {
            throw new InvalidRequestException("itemId is required");
        }
        if (newQuantity == null || newQuantity < 0) {
            throw new InvalidRequestException("newQuantity must be zero or positive");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("reason is required for stock adjustments");
        }
    }
}

