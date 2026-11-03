package com.apexcare.appointment.dto;

import com.apexcare.appointment.entity.AppointmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class StatusUpdateRequest {

    @NotNull
    private AppointmentStatus status;

    @Size(max = 4000)
    private String notes;

    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
