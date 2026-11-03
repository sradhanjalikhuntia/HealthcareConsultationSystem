package com.apexcare.patient.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable   // Telemetry is append-only; corrections go as new records
@Table(name = "patient_vitals_telemetry", indexes = {
        @Index(name = "idx_vitals_patient_time", columnList = "patientId,recordedAt"),
        @Index(name = "idx_vitals_news2", columnList = "news2Score"),
        @Index(name = "idx_vitals_risk", columnList = "riskLevel")
})
public class PatientVitals {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID patientId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(nullable = false, updatable = false)
    private UUID recordedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private VitalsSource source;

    // ---- Raw vitals ----

    @Min(0) @Max(300)
    @Column(updatable = false)
    private Integer heartRate;

    @Min(20) @Max(300)
    @Column(updatable = false)
    private Integer systolicBp;

    @Min(0) @Max(200)
    @Column(updatable = false)
    private Integer diastolicBp;

    @Min(0) @Max(100)
    @Column(updatable = false)
    private Integer spO2;

    @Min(0) @Max(80)
    @Column(updatable = false)
    private Integer respiratoryRate;

    @DecimalMin("25.0") @DecimalMax("45.0")
    @Column(precision = 4, scale = 1, updatable = false)
    private BigDecimal temperature;

    @DecimalMin("0.5") @DecimalMax("500.0")
    @Column(precision = 5, scale = 2, updatable = false)
    private BigDecimal weight;

    /**
     * Which SpO2 scale to apply. Scale 1 = default;
     * Scale 2 = for hypercapnic respiratory failure (target 88-92%).
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16, updatable = false)
    private Spo2Scale spo2Scale = Spo2Scale.SCALE_1;

    /**
     * ACVPU consciousness level.
     * Alert = 0; any Confusion/Voice/Pain/Unresponsive = +3.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private ConsciousnessLevel consciousness = ConsciousnessLevel.ALERT;

    /**
     * Whether the patient is receiving supplemental oxygen.
     * On oxygen = +2 to NEWS2.
     */
    @Column(nullable = false, updatable = false)
    private boolean onSupplementalOxygen = false;

    // ---- Computed (server-side only) ----

    @Column(updatable = false)
    private Integer news2Score;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, updatable = false)
    private News2RiskLevel riskLevel;

    @Column(length = 500, updatable = false)
    private String clinicalAlert;

    /**
     * True if any single parameter scored 3 — NHS protocol requires
     * urgent review even if total score is low.
     */
    @Column(updatable = false)
    private boolean singleParamCritical;

    // ---------------------------------------------------------------

    @PrePersist
    void onCreate() {
        this.id = UUID.randomUUID();
        this.recordedAt = Instant.now();   // server-authoritative
        if (this.consciousness == null) this.consciousness = ConsciousnessLevel.ALERT;
        if (this.spo2Scale == null) this.spo2Scale = Spo2Scale.SCALE_1;
        calculateNews2();
    }

    /**
     * Compute NEWS2 per the Royal College of Physicians (2017) algorithm.
     * Package-private so tests can invoke directly; not exposed via API.
     */
    void calculateNews2() {
        int score = 0;
        boolean single3 = false;

        // --- Respiratory rate ---
        if (respiratoryRate != null) {
            int r;
            if (respiratoryRate <= 8 || respiratoryRate >= 25) r = 3;
            else if (respiratoryRate >= 21) r = 2;
            else if (respiratoryRate <= 11) r = 1;
            else r = 0;
            score += r;
            if (r == 3) single3 = true;
        }

        // --- SpO2 ---
        if (spO2 != null) {
            int r;
            if (spo2Scale == Spo2Scale.SCALE_2) {
                // Scale 2 (hypercapnic target 88-92%)
                if (spO2 <= 83) r = 3;
                else if (spO2 <= 85) r = 2;
                else if (spO2 <= 87) r = 1;
                else if (spO2 <= 92) r = 0;
                else if (spO2 <= 94) r = 1;
                else if (spO2 <= 96) r = 2;
                else r = 3;
            } else {
                // Scale 1 (default)
                if (spO2 <= 91) r = 3;
                else if (spO2 <= 93) r = 2;
                else if (spO2 <= 95) r = 1;
                else r = 0;
            }
            score += r;
            if (r == 3) single3 = true;
        }

        // --- Supplemental oxygen ---
        if (onSupplementalOxygen) {
            score += 2;
        }

        // --- Systolic BP ---
        if (systolicBp != null) {
            int r;
            if (systolicBp <= 90 || systolicBp >= 220) r = 3;
            else if (systolicBp <= 100) r = 2;
            else if (systolicBp <= 110) r = 1;
            else r = 0;
            score += r;
            if (r == 3) single3 = true;
        }

        // --- Heart rate ---
        if (heartRate != null) {
            int r;
            if (heartRate <= 40 || heartRate >= 131) r = 3;
            else if (heartRate >= 111) r = 2;
            else if (heartRate <= 50 || heartRate >= 91) r = 1;
            else r = 0;
            score += r;
            if (r == 3) single3 = true;
        }

        // --- Consciousness (ACVPU) ---
        if (consciousness != ConsciousnessLevel.ALERT) {
            score += 3;
            single3 = true;
        }

        // --- Temperature ---
        if (temperature != null) {
            int r;
            double t = temperature.doubleValue();
            if (t <= 35.0) r = 3;
            else if (t >= 39.1) r = 2;
            else if (t <= 36.0 || t >= 38.1) r = 1;
            else r = 0;
            score += r;
            if (r == 3) single3 = true;
        }

        this.news2Score = score;
        this.singleParamCritical = single3;

        // NHS NEWS2 escalation bands
        if (score >= 7 || single3) {
            this.riskLevel = News2RiskLevel.HIGH_EMERGENCY;
            this.clinicalAlert = "CRITICAL: High risk of deterioration. "
                    + "Continuous monitoring and emergency clinical review required.";
        } else if (score >= 5) {
            this.riskLevel = News2RiskLevel.MEDIUM;
            this.clinicalAlert = "URGENT: Medium risk. Hourly monitoring and urgent "
                    + "clinical review required.";
        } else {
            this.riskLevel = News2RiskLevel.LOW;
            this.clinicalAlert = "STABLE: Routine ward monitoring.";
        }
    }

    // ---- getters only for computed fields ----

    public Integer getNews2Score() { return news2Score; }
    public News2RiskLevel getRiskLevel() { return riskLevel; }
    public String getClinicalAlert() { return clinicalAlert; }
    public boolean isSingleParamCritical() { return singleParamCritical; }

    // ---- getters/setters for raw fields ----

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }
    public Instant getRecordedAt() { return recordedAt; }
    public UUID getRecordedBy() { return recordedBy; }
    public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }
    public VitalsSource getSource() { return source; }
    public void setSource(VitalsSource source) { this.source = source; }
    public Integer getHeartRate() { return heartRate; }
    public void setHeartRate(Integer heartRate) { this.heartRate = heartRate; }
    public Integer getSystolicBp() { return systolicBp; }
    public void setSystolicBp(Integer systolicBp) { this.systolicBp = systolicBp; }
    public Integer getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(Integer diastolicBp) { this.diastolicBp = diastolicBp; }
    public Integer getSpO2() { return spO2; }
    public void setSpO2(Integer spO2) { this.spO2 = spO2; }
    public Integer getRespiratoryRate() { return respiratoryRate; }
    public void setRespiratoryRate(Integer respiratoryRate) { this.respiratoryRate = respiratoryRate; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public Spo2Scale getSpo2Scale() { return spo2Scale; }
    public void setSpo2Scale(Spo2Scale spo2Scale) { this.spo2Scale = spo2Scale; }
    public ConsciousnessLevel getConsciousness() { return consciousness; }
    public void setConsciousness(ConsciousnessLevel consciousness) { this.consciousness = consciousness; }
    public boolean isOnSupplementalOxygen() { return onSupplementalOxygen; }
    public void setOnSupplementalOxygen(boolean onSupplementalOxygen) { this.onSupplementalOxygen = onSupplementalOxygen; }

    // ---- equals / hashCode ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PatientVitals)) return false;
        PatientVitals other = (PatientVitals) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
