package com.apexcare.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Remote representation of a doctor as returned by the auth-service.
 *
 * <p>Only the fields the appointment-service consumes are declared; extra
 * fields in the upstream JSON are ignored via {@link JsonIgnoreProperties}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DoctorRemote {

    private UUID id;
    private String firstName;
    private String lastName;
    private String doctorCode;
    private String role;
    private boolean enabled;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getDoctorCode() { return doctorCode; }
    public void setDoctorCode(String doctorCode) { this.doctorCode = doctorCode; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Convenience — full display name. */
    public String fullName() {
        String first = firstName == null ? "" : firstName;
        String last  = lastName  == null ? "" : lastName;
        String name = (first + " " + last).trim();
        return name.isBlank() ? "Doctor" : name;
    }
}
