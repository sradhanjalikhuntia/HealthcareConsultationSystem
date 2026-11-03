package com.apexcare.appointment.dto;

import com.apexcare.appointment.entity.Appointment;
import com.apexcare.appointment.entity.AppointmentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppointmentResponse {

    private UUID appointmentId;
    private UUID patientId;
    private String uniquePatientId;
    private String patientName;
    private UUID doctorId;
    private String doctorName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime endTime;

    private String reason;
    private AppointmentStatus status;
    private String notes;

    private boolean billingGenerated;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant billingGeneratedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant calledAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant cancelledAt;

    private UUID cancelledBy;
    private String cancellationReason;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    public static AppointmentResponse from(Appointment a) {
        if (a == null) return null;

        AppointmentResponse r = new AppointmentResponse();
        r.appointmentId = a.getId();
        r.patientId = a.getPatientId();
        r.uniquePatientId = a.getUniquePatientId();
        r.patientName = a.getPatientName();
        r.doctorId = a.getDoctorId();
        r.doctorName = a.getDoctorName();
        r.appointmentDate = a.getAppointmentDate();
        r.startTime = a.getStartTime();
        r.endTime = a.getEndTime();
        r.reason = a.getReason();
        r.status = a.getStatus();
        r.notes = a.getNotes();
        r.billingGenerated = a.isBillingGenerated();
        r.billingGeneratedAt = a.getBillingGeneratedAt();
        r.calledAt = a.getCalledAt();
        r.cancelledAt = a.getCancelledAt();
        r.cancelledBy = a.getCancelledBy();
        r.cancellationReason = a.getCancellationReason();
        r.createdAt = a.getCreatedAt();
        r.updatedAt = a.getUpdatedAt();
        return r;
    }

    // ---- getters ----

    public UUID getAppointmentId() { return appointmentId; }

    @JsonIgnore
    public UUID getId() { return appointmentId; }

    public UUID getPatientId() { return patientId; }
    public String getUniquePatientId() { return uniquePatientId; }
    public String getPatientName() { return patientName; }
    public UUID getDoctorId() { return doctorId; }
    public String getDoctorName() { return doctorName; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getReason() { return reason; }
    public AppointmentStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public boolean isBillingGenerated() { return billingGenerated; }
    public Instant getBillingGeneratedAt() { return billingGeneratedAt; }
    public Instant getCalledAt() { return calledAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public UUID getCancelledBy() { return cancelledBy; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
