package com.apexcare.patient.dto;

import java.util.UUID;

public record AdmitPatientRequest(UUID patientId, String reason) {}
