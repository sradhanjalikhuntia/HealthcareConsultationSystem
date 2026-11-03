package com.apexcare.appointment.controller;

import com.apexcare.appointment.dto.ApiResponse;
import com.apexcare.appointment.dto.AppointmentRequest;
import com.apexcare.appointment.dto.AppointmentResponse;
import com.apexcare.appointment.dto.StatusUpdateRequest;
import com.apexcare.appointment.entity.AppointmentStatus;
import com.apexcare.appointment.security.JwtPrincipal;
import com.apexcare.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @Operation(summary = "Schedule an appointment")
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(
            @Valid @RequestBody AppointmentRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Appointment created successfully", appointmentService.create(request, principal)));
    }

    @GetMapping
    @Operation(summary = "List appointments")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> list(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status,
            @PageableDefault(size = 10, sort = "appointmentDate") Pageable pageable,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Appointments retrieved",
                appointmentService.list(patientId, doctorId, date, status, pageable, principal)));
    }

    @GetMapping("/stats/summary")
    @Operation(summary = "Appointment dashboard stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats(@AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Stats retrieved", appointmentService.stats(principal)));
    }

    @GetMapping({"/availability", "/doctor/{doctorId}/availability"})
    @Operation(summary = "Check doctor booked slots for a date")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> availability(
            @PathVariable(required = false) UUID doctorId,
            @RequestParam(required = false) UUID doctorIdParam,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        UUID effectiveDoctorId = doctorId != null ? doctorId : doctorIdParam;
        return ResponseEntity.ok(ApiResponse.ok("Availability retrieved",
                appointmentService.availability(effectiveDoctorId, date, principal)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get appointment")
    public ResponseEntity<ApiResponse<AppointmentResponse>> get(@PathVariable UUID id, @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Appointment retrieved", appointmentService.get(id, principal)));
    }

    @RequestMapping(value = {"/{id}", "/{id}/reschedule"}, method = {org.springframework.web.bind.annotation.RequestMethod.PUT, org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.PATCH})
    @Operation(summary = "Reschedule appointment")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(
            @PathVariable UUID id,
            @Valid @RequestBody AppointmentRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Appointment rescheduled", appointmentService.reschedule(id, request, principal)));
    }

    @RequestMapping(value = "/{id}/cancel", method = {org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.PUT, org.springframework.web.bind.annotation.RequestMethod.PATCH})
    @Operation(summary = "Cancel appointment")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(@PathVariable UUID id, @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Appointment cancelled", appointmentService.cancel(id, principal)));
    }

    @RequestMapping(value = "/{id}/status", method = {org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.PATCH, org.springframework.web.bind.annotation.RequestMethod.PUT})
    @Operation(summary = "Update appointment status or consultation notes")
    public ResponseEntity<ApiResponse<AppointmentResponse>> status(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Appointment updated", appointmentService.updateStatus(id, request, principal)));
    }
}
