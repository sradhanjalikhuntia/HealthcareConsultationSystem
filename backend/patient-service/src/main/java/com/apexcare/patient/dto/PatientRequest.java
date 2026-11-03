package com.apexcare.patient.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import com.apexcare.patient.entity.Gender;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatientRequest {

    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "^[\\p{L} .'-]+$",
             message = "First name may contain only letters, spaces, dots, apostrophes, hyphens")
    private String firstName;

    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "^[\\p{L} .'-]+$",
             message = "Last name may contain only letters, spaces, dots, apostrophes, hyphens")
    private String lastName;

    @NotNull
    @PastOrPresent
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    @NotNull
    private Gender gender;                    // enum

    @Pattern(regexp = "^(|A\\+|A-|B\\+|B-|AB\\+|AB-|O\\+|O-|A_POSITIVE|A_NEGATIVE|B_POSITIVE|B_NEGATIVE|AB_POSITIVE|AB_NEGATIVE|O_POSITIVE|O_NEGATIVE)$",
             message = "Blood group must be like A+, O-, AB+")
    private String bloodGroup;

    @NotBlank
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$",
             message = "Phone must be a valid E.164 number (e.g. +919876543210)")
    private String phone;

    @Email
    @Size(max = 255)
    private String email;                     // optional

    @Size(max = 500)
    private String address;

    @Size(max = 200)
    private String emergencyContact;

    @Size(max = 5000)
    private String medicalHistory;

    @Size(max = 2000)
    private String allergies;

    // ---- getters/setters ----

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = trim(firstName); }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = trim(lastName); }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = trim(bloodGroup); }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = trim(phone); }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = trim(email); }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = trim(address); }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String c) { this.emergencyContact = trim(c); }

    public String getMedicalHistory() { return medicalHistory; }
    public void setMedicalHistory(String h) { this.medicalHistory = trim(h); }

    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = trim(allergies); }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
