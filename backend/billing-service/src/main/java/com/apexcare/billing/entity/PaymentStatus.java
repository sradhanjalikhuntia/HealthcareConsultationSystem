package com.apexcare.billing.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for both {@code Bill} and {@code Payment}.
 *
 * <p><b>Bill usage:</b> {@code PENDING → PAID → REFUNDED}.
 * {@code FAILED} is not used at the bill level — if a payment attempt fails,
 * the bill remains {@code PENDING}.
 *
 * <p><b>Payment usage:</b> {@code PENDING → PAID} on success,
 * {@code PENDING → FAILED} on failure, {@code PAID → REFUNDED} on refund.
 *
 * <p>State transitions are enforced by {@link #requireTransitionTo} —
 * illegal jumps (e.g. {@code FAILED → PAID}) throw immediately.
 */
public enum PaymentStatus {

    PENDING,
    PAID,
    FAILED,
    REFUNDED,
    CANCELLED;

    /**
     * Allowed transitions.
     *
     * <p>Note that {@code PAID → FAILED} is deliberately NOT allowed — once a
     * payment succeeds, a gateway failure notification later is a refund/void
     * scenario, not a failure. Use {@code refund()} for that case.
     */
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING,   EnumSet.of(PAID, FAILED, CANCELLED),
            PAID,      EnumSet.of(REFUNDED),
            FAILED,    EnumSet.noneOf(PaymentStatus.class),
            REFUNDED,  EnumSet.noneOf(PaymentStatus.class),
            CANCELLED, EnumSet.noneOf(PaymentStatus.class)
    );

    public boolean canTransitionTo(PaymentStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void requireTransitionTo(PaymentStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid payment status transition: " + this + " → " + target);
        }
    }

    /** Terminal states cannot transition further. */
    public boolean isTerminal() {
        return this == FAILED || this == REFUNDED || this == CANCELLED;
    }

    /** Whether the payment has been captured successfully. */
    public boolean isSuccessful() {
        return this == PAID;
    }

    /** Whether the bill/payment is still awaiting completion. */
    public boolean isPending() {
        return this == PENDING;
    }
}
