package com.apexcare.appointment.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status of an appointment.
 *
 * <p>Terminal states ({@link #COMPLETED}, {@link #CANCELLED}, {@link #NO_SHOW})
 * cannot be transitioned out of. Use {@link #canTransitionTo(AppointmentStatus)}
 * before changing status.
 */
public enum AppointmentStatus {

    SCHEDULED,
    CONFIRMED,
    CHECKED_IN,
    CALLED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED_TRANSITIONS = Map.of(
            SCHEDULED, EnumSet.of(CONFIRMED, CHECKED_IN, CALLED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW),
            CONFIRMED, EnumSet.of(CHECKED_IN, CALLED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW),
            CHECKED_IN, EnumSet.of(CALLED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW),
            CALLED, EnumSet.of(IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW),
            IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(AppointmentStatus.class),
            CANCELLED, EnumSet.noneOf(AppointmentStatus.class),
            NO_SHOW,   EnumSet.noneOf(AppointmentStatus.class)
    );

    /** Whether the current status can legally transition to {@code target}. */
    public boolean canTransitionTo(AppointmentStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** @throws IllegalStateException if the transition is not allowed. */
    public void requireTransitionTo(AppointmentStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid appointment status transition: " + this + " → " + target);
        }
    }

    /** Whether this status is final and cannot be changed further. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW;
    }

    /** Whether the appointment is still active (not in a terminal state). */
    public boolean isActive() {
        return !isTerminal();
    }
}
