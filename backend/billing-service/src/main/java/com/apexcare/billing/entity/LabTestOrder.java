package com.apexcare.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "lab_test_orders",
        indexes = {
                @Index(name = "idx_lab_patient", columnList = "patient_id"),
                @Index(name = "idx_lab_doctor", columnList = "doctor_id"),
                @Index(name = "idx_lab_appointment", columnList = "appointment_id"),
                @Index(name = "idx_lab_status", columnList = "status"),
                @Index(name = "idx_lab_category", columnList = "category"),
                @Index(name = "idx_lab_ordered_at", columnList = "ordered_at"),
                @Index(name = "idx_lab_status_ordered", columnList = "status,ordered_at"),
                @Index(name = "idx_lab_abnormal", columnList = "abnormal_flag")
        }
)
public class LabTestOrder {

    // Valid values for each String field — enforced at persist time
    public static final List<String> VALID_CATEGORIES = List.of(
            "BLOOD", "URINE", "RADIOLOGY", "BIOCHEMISTRY",
            "PATHOLOGY", "MICROBIOLOGY", "IMMUNOLOGY", "HISTOPATHOLOGY");

    public static final List<String> VALID_STATUSES = List.of(
            "ORDERED", "SAMPLE_COLLECTED", "IN_ANALYSIS", "COMPLETED", "CANCELLED");

    public static final List<String> VALID_PRIORITIES = List.of(
            "ROUTINE", "URGENT", "STAT");

    public static final List<String> VALID_ABNORMAL_FLAGS = List.of(
            "NORMAL", "HIGH", "LOW", "CRITICAL_HIGH", "CRITICAL_LOW", "ABNORMAL");

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "unique_patient_id", length = 32, updatable = false)
    private String uniquePatientId;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

    @Column(name = "appointment_id", updatable = false)
    private UUID appointmentId;

    @Column(name = "test_name", nullable = false, length = 200, updatable = false)
    private String testName;

    @Column(nullable = false, length = 32, updatable = false)
    private String category;

    @Column(nullable = false, length = 32)
    private String status = "ORDERED";

    @Column(name = "priority", length = 16, updatable = false)
    private String priority = "ROUTINE";

    @Column(name = "sample_id", length = 64, updatable = false)
    private String sampleId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "sample_collected_at")
    private Instant sampleCollectedAt;

    private UUID sampleCollectedBy;

    @Column(name = "result_summary", length = 4000)
    private String resultSummary;

    @Column(name = "reference_range", length = 255)
    private String referenceRange;

    @Column(name = "abnormal_flag", length = 16)
    private String abnormalFlag;

    @Column(name = "result_file_url", length = 500)
    private String resultFileUrl;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "result_reported_at")
    private Instant resultReportedAt;

    private UUID resultReportedBy;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "ordered_at", nullable = false, updatable = false)
    private Instant orderedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private UUID orderedBy;
    @Column(name = "test_price", precision = 10, scale = 2)
    private BigDecimal testPrice;
    @Column(name = "billed")
    private Boolean billed = false;
    @Column(name = "bill_id")
    private UUID billId;

    @Version
    private Long version;

    // =================================================================
    // Lifecycle
    // =================================================================

    @PrePersist
    void onCreate() {
        this.id = UUID.randomUUID();
        Instant now = Instant.now();
        this.orderedAt = now;
        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null)   this.status = "ORDERED";
        if (this.priority == null) this.priority = "ROUTINE";
        if (this.testPrice == null) this.testPrice = determineTestPrice(this.category);
        if (this.billed == null) this.billed = false;

        validate();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        validate();
    }

    private void validate() {
        if (!VALID_CATEGORIES.contains(category)) {
            throw new IllegalStateException(
                    "Invalid category: " + category + ". Allowed: " + VALID_CATEGORIES);
        }
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalStateException(
                    "Invalid status: " + status + ". Allowed: " + VALID_STATUSES);
        }
        if (priority != null && !VALID_PRIORITIES.contains(priority)) {
            throw new IllegalStateException(
                    "Invalid priority: " + priority + ". Allowed: " + VALID_PRIORITIES);
        }
        if (abnormalFlag != null && !VALID_ABNORMAL_FLAGS.contains(abnormalFlag)) {
            throw new IllegalStateException(
                    "Invalid abnormalFlag: " + abnormalFlag
                            + ". Allowed: " + VALID_ABNORMAL_FLAGS);
        }

        // Result fields only meaningful once COMPLETED
        boolean hasResult = resultSummary != null
                || abnormalFlag != null
                || resultReportedAt != null;
        if (hasResult && !"COMPLETED".equals(status)) {
            throw new IllegalStateException(
                    "Result fields can only be set when status is COMPLETED (got " + status + ")");
        }

        if ("COMPLETED".equals(status) && completedAt == null) {
            throw new IllegalStateException(
                    "COMPLETED order must have completedAt timestamp");
        }
        if (!"COMPLETED".equals(status) && completedAt != null) {
            throw new IllegalStateException(
                    "completedAt can only be set when status is COMPLETED");
        }

        if (sampleCollectedAt != null && "ORDERED".equals(status)) {
            throw new IllegalStateException(
                    "sampleCollectedAt cannot be set before sample collection");
        }
    }

    // =================================================================
    // Domain transitions
    // =================================================================

    public void collectSample(String sampleId, UUID collectorId) {
        requireTransitionTo("SAMPLE_COLLECTED");
        this.status = "SAMPLE_COLLECTED";
        this.sampleId = sampleId;
        this.sampleCollectedAt = Instant.now();
        this.sampleCollectedBy = collectorId;
    }

    public void startAnalysis() {
        requireTransitionTo("IN_ANALYSIS");
        this.status = "IN_ANALYSIS";
    }

    public void complete(String resultSummary,
                         String referenceRange,
                         String abnormalFlag,
                         String resultFileUrl,
                         UUID reportedBy) {
        requireTransitionTo("COMPLETED");
        this.status = "COMPLETED";
        this.resultSummary = resultSummary;
        this.referenceRange = referenceRange;
        this.abnormalFlag = abnormalFlag != null ? abnormalFlag : "NORMAL";
        this.resultFileUrl = resultFileUrl;
        Instant now = Instant.now();
        this.completedAt = now;
        this.resultReportedAt = now;
        this.resultReportedBy = reportedBy;
    }

    public void setStatus(String status) { this.status = status; }
    public void setAbnormalFlag(String abnormalFlag) { this.abnormalFlag = abnormalFlag; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public void cancel() {
        requireTransitionTo("CANCELLED");
        this.status = "CANCELLED";
    }

    private void requireTransitionTo(String target) {
        String from = this.status;
        boolean ok = switch (from) {
            case "ORDERED"          -> target.equals("SAMPLE_COLLECTED") || target.equals("CANCELLED");
            case "SAMPLE_COLLECTED" -> target.equals("IN_ANALYSIS")      || target.equals("CANCELLED");
            case "IN_ANALYSIS"      -> target.equals("COMPLETED")        || target.equals("CANCELLED");
            case "COMPLETED", "CANCELLED" -> false;   // terminal
            default -> false;
        };
        if (!ok) {
            throw new IllegalStateException(
                    "Invalid lab order status transition: " + from + " → " + target);
        }
    }

    // =================================================================
    // equals / hashCode
    // =================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LabTestOrder)) return false;
        LabTestOrder other = (LabTestOrder) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // =================================================================
    // Getters / setters
    // =================================================================

    public UUID getId() { return id; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public String getUniquePatientId() { return uniquePatientId; }
    public void setUniquePatientId(String uniquePatientId) { this.uniquePatientId = uniquePatientId; }

    public UUID getDoctorId() { return doctorId; }
    public void setDoctorId(UUID doctorId) { this.doctorId = doctorId; }

    public UUID getAppointmentId() { return appointmentId; }
    public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }

    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getSampleId() { return sampleId; }
    public Instant getSampleCollectedAt() { return sampleCollectedAt; }
    public UUID getSampleCollectedBy() { return sampleCollectedBy; }

    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }

    public String getReferenceRange() { return referenceRange; }
    public void setReferenceRange(String referenceRange) { this.referenceRange = referenceRange; }

    public String getAbnormalFlag() { return abnormalFlag; }

    public String getResultFileUrl() { return resultFileUrl; }
    public void setResultFileUrl(String resultFileUrl) { this.resultFileUrl = resultFileUrl; }

    public Instant getResultReportedAt() { return resultReportedAt; }
    public UUID getResultReportedBy() { return resultReportedBy; }

    public Instant getOrderedAt() { return orderedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getOrderedBy() { return orderedBy; }
    public void setOrderedBy(UUID orderedBy) { this.orderedBy = orderedBy; }

    public Long getVersion() { return version; }

    public BigDecimal getTestPrice() { return testPrice; }
    public void setTestPrice(BigDecimal testPrice) { this.testPrice = testPrice; }

    public Boolean getBilled() { return billed; }
    public void setBilled(Boolean billed) { this.billed = billed; }

    public UUID getBillId() { return billId; }
    public void setBillId(UUID billId) { this.billId = billId; }

    public static BigDecimal determineTestPrice(String cat) {
        if (cat == null) return BigDecimal.valueOf(50.00);
        return switch (cat.trim().toUpperCase()) {
            case "BLOOD" -> BigDecimal.valueOf(40.00);
            case "URINE" -> BigDecimal.valueOf(30.00);
            case "RADIOLOGY" -> BigDecimal.valueOf(120.00);
            case "BIOCHEMISTRY" -> BigDecimal.valueOf(60.00);
            case "PATHOLOGY" -> BigDecimal.valueOf(75.00);
            case "MICROBIOLOGY" -> BigDecimal.valueOf(65.00);
            case "IMMUNOLOGY" -> BigDecimal.valueOf(85.00);
            case "HISTOPATHOLOGY" -> BigDecimal.valueOf(110.00);
            default -> BigDecimal.valueOf(50.00);
        };
    }
}
