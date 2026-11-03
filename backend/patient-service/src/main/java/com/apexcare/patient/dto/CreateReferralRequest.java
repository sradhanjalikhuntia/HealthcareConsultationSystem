package com.apexcare.patient.dto;

import com.apexcare.patient.entity.ReferralPriority;
import com.apexcare.patient.entity.Specialty;
import java.util.UUID;

public record CreateReferralRequest(UUID patientId, Specialty targetSpecialty,
                                   ReferralPriority priority, String clinicalSummary,
                                   String referralQuestion) {}
