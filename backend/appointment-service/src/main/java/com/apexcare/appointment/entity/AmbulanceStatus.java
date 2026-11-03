package com.apexcare.appointment.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum AmbulanceStatus {
    EN_ROUTE,
    ARRIVED,
    TRIAGED,
    ADMITTED,
    DIVERTED,
    COMPLETED;

    private static final Map<AmbulanceStatus, Set<AmbulanceStatus>> TRANSITIONS = Map.of(
            EN_ROUTE, EnumSet.of(ARRIVED, DIVERTED),
            ARRIVED, EnumSet.of(TRIAGED, ADMITTED),
            TRIAGED, EnumSet.of(ADMITTED),
            ADMITTED, EnumSet.of(COMPLETED),
            DIVERTED, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(AmbulanceStatus.class));

    public void requireTransitionTo(AmbulanceStatus target) {
        if (target == null || (target != this && !TRANSITIONS.getOrDefault(this, Set.of()).contains(target))) {
            throw new IllegalStateException("Invalid ambulance status transition: " + this + " -> " + target);
        }
    }
}
