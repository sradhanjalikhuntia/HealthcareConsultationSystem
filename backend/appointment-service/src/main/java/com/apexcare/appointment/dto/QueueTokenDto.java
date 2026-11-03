package com.apexcare.appointment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * A single row in the live consultation queue.
 *
 * <p>{@code status} is a {@link QueueStatus} — a display-level status derived
 * from the underlying {@code AppointmentStatus}. It is not persisted directly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueueTokenDto {

    private String tokenNumber;               // e.g. TK-01
    private UUID appointmentId;
    private UUID patientId;
    private String patientName;
    private UUID doctorId;
    private String doctorName;

    private QueueStatus status;

    /** Estimated minutes until this patient is called. 0 for CALLED/terminal. */
    private Integer estimatedWaitMinutes;

    /** Minutes this patient has been in CALLED state. Null if not yet called. */
    private Integer waitingSinceMinutes;

    private String roomNumber;
    private String slotTime;                  // "10:00 - 10:30"

    private Instant calledAt;

    // -----------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------

    public String getTokenNumber() { return tokenNumber; }
    public void setTokenNumber(String tokenNumber) { this.tokenNumber = tokenNumber; }

    public UUID getAppointmentId() { return appointmentId; }
    public void setAppointmentId(UUID appointmentId) { this.appointmentId = appointmentId; }

    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public UUID getDoctorId() { return doctorId; }
    public void setDoctorId(UUID doctorId) { this.doctorId = doctorId; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public QueueStatus getStatus() { return status; }
    public void setStatus(QueueStatus status) { this.status = status; }

    public Integer getEstimatedWaitMinutes() { return estimatedWaitMinutes; }
    public void setEstimatedWaitMinutes(Integer estimatedWaitMinutes) { this.estimatedWaitMinutes = estimatedWaitMinutes; }

    public Integer getWaitingSinceMinutes() { return waitingSinceMinutes; }
    public void setWaitingSinceMinutes(Integer waitingSinceMinutes) { this.waitingSinceMinutes = waitingSinceMinutes; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public String getSlotTime() { return slotTime; }
    public void setSlotTime(String slotTime) { this.slotTime = slotTime; }

    public Instant getCalledAt() { return calledAt; }
    public void setCalledAt(Instant calledAt) { this.calledAt = calledAt; }
}
