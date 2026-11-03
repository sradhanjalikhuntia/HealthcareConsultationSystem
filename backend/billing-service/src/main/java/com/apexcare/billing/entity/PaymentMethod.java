package com.apexcare.billing.entity;

/**
 * Payment methods accepted by the hospital billing system.
 *
 * <p>Note: {@link #INSURANCE} refers to direct cashless settlement by an
 * insurance provider against a submitted claim — the claim lifecycle itself
 * is tracked separately via {@code InsuranceClaim}.
 */
public enum PaymentMethod {

    CASH,
    CARD,
    UPI,
    NET_BANKING,
    WALLET,
    CHEQUE,
    DEMAND_DRAFT,
    INSURANCE;

    /** Whether the payment is captured digitally (has a gateway transaction reference). */
    public boolean isDigital() {
        return this == CARD
                || this == UPI
                || this == NET_BANKING
                || this == WALLET;
    }

    /** Whether the method requires manual reconciliation (cash handling / cheque clearing). */
    public boolean requiresManualReconciliation() {
        return this == CASH
                || this == CHEQUE
                || this == DEMAND_DRAFT;
    }

    /** Whether the method settles immediately (vs. async clearing). */
    public boolean isInstantSettlement() {
        return this == CASH
                || this == CARD
                || this == UPI
                || this == WALLET;
    }
}
