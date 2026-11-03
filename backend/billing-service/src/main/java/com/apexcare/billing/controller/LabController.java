package com.apexcare.billing.controller;

import com.apexcare.billing.dto.ApiResponse;
import com.apexcare.billing.dto.LabTestOrderResponse;
import com.apexcare.billing.entity.LabTestOrder;
import com.apexcare.billing.exception.InvalidRequestException;
import com.apexcare.billing.security.JwtPrincipal;
import com.apexcare.billing.service.LabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/billing/lab")
@Tag(name = "Laboratory Diagnostics",
     description = "Lab test ordering, sample tracking, and result reporting")
@SecurityRequirement(name = "bearerAuth")
public class LabController {

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    // =================================================================
    // READ
    // =================================================================

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','LAB_TECHNICIAN','RECEPTIONIST')")
    @Operation(summary = "List all lab orders (staff only, paginated)")
    public ResponseEntity<ApiResponse<Page<LabTestOrderResponse>>> getAllOrders(
            @PageableDefault(size = 20, sort = "orderedAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Page<LabTestOrderResponse> page = labService
                .getAllOrders(pageable, principal)
                .map(LabTestOrderResponse::from);
        return ResponseEntity.ok(ApiResponse.ok("Lab orders retrieved", page));
    }

    @GetMapping("/orders/patient/{patientId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get lab orders for a specific patient",
               description = "Patients may only access their own orders. Staff may access any.")
    public ResponseEntity<ApiResponse<List<LabTestOrderResponse>>> getPatientOrders(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        List<LabTestOrderResponse> orders = labService
                .getPatientOrders(patientId, principal)
                .stream()
                .map(LabTestOrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Patient lab orders retrieved", orders));
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a single lab order by id")
    public ResponseEntity<ApiResponse<LabTestOrderResponse>> getOrderById(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        LabTestOrder order = labService.getOrderById(id, principal);
        return ResponseEntity.ok(ApiResponse.ok("Lab order retrieved",
                LabTestOrderResponse.from(order)));
    }

    // =================================================================
    // CREATE
    // =================================================================

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    @Operation(summary = "Create a laboratory diagnostic test order (clinical staff only)")
    public ResponseEntity<ApiResponse<LabTestOrderResponse>> createOrder(
            @RequestBody CreateLabOrderRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        LabTestOrder created = labService.createOrder(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Lab test order created",
                        LabTestOrderResponse.from(created)));
    }

    // =================================================================
    // UPDATE — record results
    // =================================================================

    @PutMapping("/orders/{id}/results")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','LAB_TECHNICIAN')")
    @Operation(
            summary = "Record laboratory test results and abnormal flags",
            description = "Only ADMIN, DOCTOR, or LAB_TECHNICIAN can record results. "
                    + "Status must be IN_ANALYSIS before results are accepted."
    )
    public ResponseEntity<ApiResponse<LabTestOrderResponse>> recordResult(
            @PathVariable UUID id,
            @RequestBody RecordLabResultRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        LabTestOrder updated = labService.recordResult(id, request, principal);
        return ResponseEntity.ok(ApiResponse.ok("Lab results recorded",
                LabTestOrderResponse.from(updated)));
    }

    // =================================================================
    // Request DTOs
    // =================================================================

    /**
     * Payload for creating a new lab order.
     *
     * <p>Server-controlled fields ({@code id}, {@code status}, {@code orderedAt},
     * {@code orderedBy}, result fields) are NOT part of this DTO.
     */
    public record CreateLabOrderRequest(
            UUID patientId,
            UUID doctorId,
            UUID appointmentId,
            String testName,
            String category,
            String priority
    ) {
        public CreateLabOrderRequest {
            if (patientId == null) {
                throw new InvalidRequestException("patientId is required");
            }
            if (testName == null || testName.isBlank()) {
                throw new InvalidRequestException("testName is required");
            }
            if (category == null || !LabTestOrder.VALID_CATEGORIES.contains(category)) {
                throw new InvalidRequestException(
                        "category must be one of " + LabTestOrder.VALID_CATEGORIES);
            }
            if (priority != null && !LabTestOrder.VALID_PRIORITIES.contains(priority)) {
                throw new InvalidRequestException(
                        "priority must be one of " + LabTestOrder.VALID_PRIORITIES);
            }
        }
    }

    /**
     * Payload for recording lab results.
     *
     * <p>Only result-related fields are exposed. Status is not part of the DTO —
     * the service transitions the order through its state machine
     * ({@code IN_ANALYSIS → COMPLETED}).
     */
    public record RecordLabResultRequest(
            String resultSummary,
            String referenceRange,
            String abnormalFlag,
            String resultFileUrl
    ) {
        public RecordLabResultRequest {
            if (resultSummary == null || resultSummary.isBlank()) {
                throw new InvalidRequestException("resultSummary is required");
            }
            if (abnormalFlag != null
                    && !LabTestOrder.VALID_ABNORMAL_FLAGS.contains(abnormalFlag)) {
                throw new InvalidRequestException(
                        "abnormalFlag must be one of " + LabTestOrder.VALID_ABNORMAL_FLAGS);
            }
        }
    }
}
