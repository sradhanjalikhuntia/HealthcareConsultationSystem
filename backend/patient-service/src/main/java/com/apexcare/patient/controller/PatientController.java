package com.apexcare.patient.controller;

import com.apexcare.patient.dto.ApiResponse;
import com.apexcare.patient.dto.PatientRequest;
import com.apexcare.patient.dto.PatientResponse;
import com.apexcare.patient.entity.PatientStatus;
import com.apexcare.patient.security.JwtPrincipal;
import com.apexcare.patient.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patients")
@SecurityRequirement(name = "bearerAuth")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    // -----------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------
    @PostMapping
    @Operation(summary = "Register a patient",
               description = "Creates a new patient record. Restricted to staff roles.")
    public ResponseEntity<ApiResponse<PatientResponse>> create(
            @Valid @RequestBody PatientRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Patient created successfully",
                        patientService.create(request, principal)));
    }

    @PostMapping("/self-register")
    @Operation(summary = "Create the medical record linked to the signed-in patient account")
    public ResponseEntity<ApiResponse<PatientResponse>> selfRegister(
            @Valid @RequestBody PatientRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Patient record ready",
                        patientService.createForLinkedAccount(request, principal)));
    }

    // -----------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------
    @GetMapping
    @Operation(summary = "List / search patients with pagination",
               description = "Use `q` to filter by name, email, or unique patient ID.")
    public ResponseEntity<ApiResponse<Page<PatientResponse>>> list(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) PatientStatus status,
            @PageableDefault(size = 10, sort = "lastName") Pageable pageable,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Patients retrieved",
                patientService.list(query, status, pageable, principal)));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get aggregate patient count metrics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        return ResponseEntity.ok(ApiResponse.ok("Summary loaded",
                patientService.getSummary(principal)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get patient by id")
    public ResponseEntity<ApiResponse<PatientResponse>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Patient retrieved",
                patientService.get(id, principal)));
    }

    // -----------------------------------------------------------------
    // UPDATE
    // -----------------------------------------------------------------
    @PutMapping("/{id}")
    @Operation(summary = "Update patient")
    public ResponseEntity<ApiResponse<PatientResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody PatientRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Patient updated successfully",
                patientService.update(id, request, principal)));
    }

    // -----------------------------------------------------------------
    // DELETE / DEACTIVATE — ADMIN only
    // -----------------------------------------------------------------
    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate patient (ADMIN only)",
               description = "Soft-deletes the patient record. Only ADMIN can perform this.")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        patientService.deactivate(id, principal);   // role check in service layer
        return ResponseEntity.ok(ApiResponse.ok("Patient deactivated", null));
    }

    // -----------------------------------------------------------------
    // REACTIVATE — ADMIN only
    // -----------------------------------------------------------------
    @PostMapping("/{id}/activate")
    @Operation(summary = "Reactivate patient (ADMIN only)",
               description = "Restores an inactive patient record to ACTIVE. Only ADMIN can perform this.")
    public ResponseEntity<ApiResponse<PatientResponse>> reactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Patient reactivated successfully",
                patientService.reactivate(id, principal)));
    }
}
