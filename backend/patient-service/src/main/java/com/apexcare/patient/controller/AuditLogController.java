package com.apexcare.patient.controller;

import com.apexcare.patient.dto.ApiResponse;
import com.apexcare.patient.dto.AuditLogDto;
import com.apexcare.patient.entity.AuditAction;
import com.apexcare.patient.entity.ResourceType;
import com.apexcare.patient.exception.ResourceNotFoundException;
import com.apexcare.patient.security.JwtPrincipal;
import com.apexcare.patient.service.AuditLogService;
import com.apexcare.patient.service.FhirExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/patients")
@Tag(name = "HIPAA Compliance & Interoperability")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogService auditLogService;
    private final FhirExportService fhirExportService;

    public AuditLogController(AuditLogService auditLogService,
                              FhirExportService fhirExportService) {
        this.auditLogService = auditLogService;
        this.fhirExportService = fhirExportService;
    }

    // -----------------------------------------------------------------
    // GET /api/patients/audit-logs   — ADMIN only
    // -----------------------------------------------------------------
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Get immutable HIPAA access audit logs",
            description = "Returns paginated audit trail. Restricted to ADMIN role."
    )
    public ResponseEntity<ApiResponse<Page<AuditLogDto>>> getAuditLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size
    ) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "eventTimestamp"));
        Page<AuditLogDto> logs = auditLogService.getLogs(pageable).map(AuditLogDto::from);
        return ResponseEntity.ok(ApiResponse.ok("Audit logs retrieved", logs));
    }

    // -----------------------------------------------------------------
    // GET /api/patients/{id}/fhir-export
    // ADMIN / DOCTOR  → any patient
    // PATIENT         → only own record
    // -----------------------------------------------------------------
    @GetMapping("/{id}/fhir-export")
    @Operation(
            summary = "Export patient health record in HL7/FHIR R4 JSON format",
            description = "ADMIN/DOCTOR can export any patient. A PATIENT can export only their own record."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportFhir(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest request
    ) {
        if (principal == null) {
            // Should never happen because SecurityConfig requires authentication,
            // but keep the check for defence in depth.
            throw new ResourceNotFoundException("Patient", id);
        }

        boolean privileged = principal.isAdmin() || principal.isDoctor();
        boolean ownRecord  = principal.isLinkedToPatient(id);

        if (!privileged && !ownRecord) {
            // 404 instead of 403 — do not leak that the patient exists
            throw new ResourceNotFoundException("Patient", id);
        }

        Map<String, Object> fhirBundle = fhirExportService.exportPatientFhir(id);

        // HIPAA: every FHIR export must be logged
        auditLogService.record(
                principal,
                AuditAction.EXPORT_FHIR,
                ResourceType.PATIENT_EMR,
                id.toString(),
                "FHIR R4 bundle exported",
                clientIp(request)
        );

        return ResponseEntity.ok(ApiResponse.ok("FHIR bundle generated", fhirBundle));
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
