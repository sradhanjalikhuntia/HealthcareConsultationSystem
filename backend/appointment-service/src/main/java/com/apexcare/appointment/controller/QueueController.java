package com.apexcare.appointment.controller;

import com.apexcare.appointment.dto.ApiResponse;
import com.apexcare.appointment.dto.QueueTokenDto;
import com.apexcare.appointment.entity.AppointmentStatus;
import com.apexcare.appointment.security.JwtPrincipal;
import com.apexcare.appointment.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments/queue")
@Tag(name = "Live Waiting Room & Token Queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/live")
    @Operation(summary = "Get live hospital token queue")
    public ResponseEntity<ApiResponse<List<QueueTokenDto>>> getHospitalLiveQueue(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Live queue retrieved",
                queueService.getHospitalLiveQueue(principal)));
    }

    @GetMapping("/doctor/{doctorId}")
    @Operation(summary = "Get live doctor queue")
    public ResponseEntity<ApiResponse<List<QueueTokenDto>>> getDoctorQueue(
            @PathVariable UUID doctorId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Doctor queue retrieved",
                queueService.getDoctorQueue(doctorId, principal)));
    }

    @PostMapping("/doctor/{doctorId}/call-next")
    @Operation(summary = "Doctor calls next patient into the consultation room")
    public ResponseEntity<ApiResponse<QueueTokenDto>> callNextPatient(
            @PathVariable UUID doctorId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        QueueTokenDto token = queueService.callNextPatient(doctorId, principal).orElse(null);
        if (token != null) {
            return ResponseEntity.ok(ApiResponse.ok("Patient buzzer alert triggered", token));
        }
        return ResponseEntity.ok(ApiResponse.ok("No waiting patients in queue", null));
    }

    @PostMapping("/{appointmentId}/status")
    @Operation(summary = "Update token status (IN_CONSULTATION, COMPLETED, SKIPPED)")
    public ResponseEntity<ApiResponse<QueueTokenDto>> updateTokenStatus(
            @PathVariable UUID appointmentId,
            @RequestParam String status,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        QueueTokenDto token = queueService.updateTokenStatus(
                appointmentId, AppointmentStatus.valueOf(status.toUpperCase()), principal);
        return ResponseEntity.ok(ApiResponse.ok("Token status updated", token));
    }
}
