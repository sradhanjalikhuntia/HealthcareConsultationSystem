package com.apexcare.billing.dto;

import com.apexcare.billing.exception.InvalidRequestException;

import java.util.UUID;

public record DispenseRequest(
        UUID itemId,
        Integer quantity,
        UUID patientId,
        String patientName,
        Boolean autoAddToBill
) {
    public DispenseRequest {
        if (itemId == null) {
            throw new InvalidRequestException("itemId is required");
        }
        if (quantity == null || quantity <= 0) {
            throw new InvalidRequestException("quantity must be greater than zero");
        }
    }
}

