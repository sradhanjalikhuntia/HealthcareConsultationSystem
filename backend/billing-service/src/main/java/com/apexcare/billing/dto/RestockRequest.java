package com.apexcare.billing.dto;

import com.apexcare.billing.exception.InvalidRequestException;

import java.util.UUID;

public record RestockRequest(
        UUID itemId,
        Integer quantity
) {
    public RestockRequest {
        if (itemId == null) {
            throw new InvalidRequestException("itemId is required");
        }
        if (quantity == null || quantity <= 0) {
            throw new InvalidRequestException("quantity must be greater than zero");
        }
    }
}

