package com.apexcare.patient.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status of a patient record.
 *
 * <p>Values chosen to support:
 * <ul>
 *   <li>Clinical workflows (admissions, appointments, alerts)</li>
 *   <li>Billing and communication (do not contact deceased patients)</li>
 *   <li>Compliance audits (HIPAA, GDPR retention)</li>
 *   <li>Data quality (duplicate-record merges)</li>
 * </ul>
 *
 * <p><b>State machine:</b> use {@link #canTransitionTo(PatientStatus)} before
 * changing a patient's status. {@link #DECEASED} and {@link #MERGED} are
 * terminal — no further transitions are allowed.
 */
public enum PatientStatus {

    /** Currently under care or eligible for appointments. Default state. */
    ACTIVE,

    /** No recent visits, but the patient could return. Not currently under care. */
    DORMANT,

    /** Discharged after treatment. Still a patient; may return. */
    DISCHARGED,

    /** Transferred to another facility. Records may follow. */
    TRANSFERRED,

    /** Duplicate record merged into another patient. Terminal state. */
    MERGED,

    /** Patient is deceased. Terminal state. No further communications allowed. */
    DECEASED,

    /** Administratively deactivated (account closed by request, GDPR erasure, etc.). */
    INACTIVE;

    // ---------------------------------------------------------------
    // Allowed transitions
    // ---------------------------------------------------------------

    private static final Map<PatientStatus, Set<PatientStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<PatientStatus, Set<PatientStatus>> m = new java.util.EnumMap<>(PatientStatus.class);

        m.put(ACTIVE, EnumSet.of(DORMANT, DISCHARGED, TRANSFERRED, MERGED, DECEASED, INACTIVE));
        m.put(DORMANT, EnumSet.of(ACTIVE, MERGED, DECEASED, INACTIVE));
        m.put(DISCHARGED, EnumSet.of(ACTIVE, MERGED, DECEASED));         // readmission
        m.put(TRANSFERRED, EnumSet.of(MERGED, DECEASED));
        m.put(INACTIVE, EnumSet.of(ACTIVE));                             // reactivation
        m.put(MERGED, EnumSet.noneOf(PatientStatus.class));              // terminal
        m.put(DECEASED, EnumSet.noneOf(PatientStatus.class));            // terminal

        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(m);
    }

    /**
     * Whether the current status can legally transition to {@code target}.
     * A status may transition to itself (no-op), which is treated as allowed.
     */
    public boolean canTransitionTo(PatientStatus target) {
        if (target == null) return false;
        if (this == target) return true;
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * @throws IllegalStateException if the transition is not allowed.
     */
    public void requireTransitionTo(PatientStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid patient status transition: " + this + " → " + target);
        }
    }

    // ---------------------------------------------------------------
    // Predicates used by business logic
    // ---------------------------------------------------------------

    /** Whether this patient may receive appointments, reminders, and non-clinical comms. */
    public boolean isReceivable() {
        return this == ACTIVE || this == DORMANT;
    }

    /** Whether new appointments can be booked. */
    public boolean isBookable() {
        return this == ACTIVE;
    }

    /** Whether clinical data (vitals, notes, referrals) can still be added. */
    public boolean isClinicallyEditable() {
        return this != DECEASED && this != MERGED;
    }

    /** Whether the record is frozen and cannot be modified further. */
    public boolean isTerminal() {
        return this == DECEASED || this == MERGED;
    }

    /** Whether the patient is currently under active care. */
    public boolean isUnderCare() {
        return this == ACTIVE;
    }
}
