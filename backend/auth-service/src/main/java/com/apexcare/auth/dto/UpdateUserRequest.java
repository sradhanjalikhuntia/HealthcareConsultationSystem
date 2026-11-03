package com.apexcare.auth.dto;

import com.apexcare.auth.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Fields that may be updated through the user-update endpoint.
 */
public class UpdateUserRequest {

    @Email
    @Size(max = 160)
    private String email;

    @Size(max = 80)
    private String firstName;

    @Size(max = 80)
    private String lastName;

    private Role role;

    private Boolean enabled;

    // ---- getters / setters ----

    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase();
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) {
        this.firstName = firstName == null ? null : firstName.trim();
    }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) {
        this.lastName = lastName == null ? null : lastName.trim();
    }

    public Role getRole() { return role; }
    public void setRole(Role role) {
        this.role = role;
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
