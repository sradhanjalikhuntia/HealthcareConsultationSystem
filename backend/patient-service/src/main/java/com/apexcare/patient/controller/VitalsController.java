package com.apexcare.patient.controller;

import com.apexcare.patient.dto.ApiResponse;
import com.apexcare.patient.dto.RecordVitalsRequest;
import com.apexcare.patient.dto.VitalsResponse;
import com.apexcare.patient.security.JwtPrincipal;
import com.apexcare.patient.service.VitalsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/vitals")
@Tag(name = "IoT Vitals & NEWS2 Deterioration Engine")
@SecurityRequirement(name = "bearerAuth")
public class VitalsController {

    private final VitalsService vitalsService;

    public VitalsController(VitalsService vitalsService) {
        this.vitalsService = vitalsService;
    }

    // -----------------------------------------------------------------
    // POST /api/vitals — record new reading (clinical staff only)
    // -----------------------------------------------------------------
    @PostMapping
    @PreAuthorize("hasAnyRole('NURSE','DOCTOR','ADMIN')")
    @Operation(summary = "Record bedside IoT vitals and calculate NEWS2 score",
            description = "NEWS2 score is calculated server-side; clients cannot set it.")
    public ResponseEntity<ApiResponse<VitalsResponse>> recordVitals(
            @Valid @RequestBody RecordVitalsRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        VitalsResponse saved = vitalsService.recordVitals(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Vitals recorded and NEWS2 evaluated", saved));
    }

    // -----------------------------------------------------------------
    // GET /api/vitals/patient/{patientId} — paginated history
    // -----------------------------------------------------------------
    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get historical vitals telemetry for a patient (paginated)")
    public ResponseEntity<ApiResponse<Page<VitalsResponse>>> getPatientHistory(
            @PathVariable UUID patientId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50, sort = "recordedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Page<VitalsResponse> history = vitalsService.getPatientHistory(
                patientId, from, to, pageable, principal);
        return ResponseEntity.ok(ApiResponse.ok("Vitals history retrieved", history));
    }

    // -----------------------------------------------------------------
    // GET /api/vitals/patient/{patientId}/latest — latest snapshot
    // -----------------------------------------------------------------
    @GetMapping("/patient/{patientId}/latest")
    @Operation(summary = "Get latest vital signs snapshot and NEWS2 score")
    public ResponseEntity<ApiResponse<VitalsResponse>> getLatest(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return vitalsService.getLatest(patientId, principal)
                .map(v -> ResponseEntity.ok(ApiResponse.ok("Latest vitals retrieved", v)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("No vitals recorded for this patient")));
    }
}
