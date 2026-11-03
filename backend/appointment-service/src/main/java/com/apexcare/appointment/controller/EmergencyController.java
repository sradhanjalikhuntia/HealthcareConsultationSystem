package com.apexcare.appointment.controller;

import com.apexcare.appointment.dto.*;
import com.apexcare.appointment.entity.AmbulanceStatus;
import com.apexcare.appointment.security.JwtPrincipal;
import com.apexcare.appointment.service.EmergencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments/emergency")
@Tag(name = "Emergency Department & Code Broadcasts")
public class EmergencyController {

    private final EmergencyService emergencyService;

    public EmergencyController(EmergencyService emergencyService) {
        this.emergencyService = emergencyService;
    }

    @GetMapping("/alerts")
    @Operation(summary = "Get active hospital emergency code alerts")
    public ResponseEntity<ApiResponse<List<EmergencyAlertResponse>>> getActiveAlerts(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Active alerts retrieved",
                emergencyService.getActiveAlerts(principal)));
    }

    @GetMapping("/alerts/all")
    @Operation(summary = "Get historical emergency code log")
    public ResponseEntity<ApiResponse<Page<EmergencyAlertResponse>>> getAllAlerts(
            Pageable pageable, @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Emergency alerts log retrieved",
                emergencyService.getAllAlerts(pageable, principal)));
    }

    @PostMapping("/broadcast")
    @Operation(summary = "Trigger hospital-wide emergency code broadcast")
    public ResponseEntity<ApiResponse<EmergencyAlertResponse>> broadcastCode(
            @RequestBody EmergencyAlertRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                "Hospital emergency code broadcasted",
                emergencyService.broadcastCode(request, principal)));
    }

    @PutMapping("/alerts/{alertId}/resolve")
    @Operation(summary = "Resolve active emergency code siren")
    public ResponseEntity<ApiResponse<EmergencyAlertResponse>> resolveAlert(
            @PathVariable UUID alertId,
            @RequestParam(required = false) String resolutionNotes,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Emergency code resolved",
                emergencyService.resolveAlert(alertId, resolutionNotes, principal)));
    }

    @GetMapping("/ambulances")
    @Operation(summary = "Get incoming emergency ambulance fleet")
    public ResponseEntity<ApiResponse<Page<AmbulanceTriageResponse>>> getAmbulances(
            Pageable pageable, @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Ambulance queue retrieved",
                emergencyService.getAmbulances(pageable, principal)));
    }

    @GetMapping("/ambulances/{id}")
    public ResponseEntity<ApiResponse<AmbulanceTriageResponse>> getAmbulance(
            @PathVariable UUID id, @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Ambulance retrieved",
                emergencyService.getAmbulance(id, principal)));
    }

    @PostMapping("/ambulances")
    @Operation(summary = "Register incoming emergency ambulance")
    public ResponseEntity<ApiResponse<AmbulanceTriageResponse>> registerAmbulance(
            @RequestBody AmbulanceTriageRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                "Ambulance registered in triage queue",
                emergencyService.registerAmbulance(request, principal)));
    }

    @PutMapping("/ambulances/{id}/status")
    @Operation(summary = "Update ambulance status")
    public ResponseEntity<ApiResponse<AmbulanceTriageResponse>> updateAmbulanceStatus(
            @PathVariable UUID id,
            @RequestParam AmbulanceStatus status,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Ambulance status updated",
                emergencyService.updateAmbulanceStatus(id, status, principal)));
    }
}
