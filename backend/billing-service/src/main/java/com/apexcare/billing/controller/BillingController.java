package com.apexcare.billing.controller;

import com.apexcare.billing.dto.ApiResponse;
import com.apexcare.billing.dto.BillRequest;
import com.apexcare.billing.dto.BillResponse;
import com.apexcare.billing.dto.BillingSummaryResponse;
import com.apexcare.billing.dto.GenerateBillRequest;
import com.apexcare.billing.dto.PaymentReceiptResponse;
import com.apexcare.billing.dto.PaymentRequest;
import com.apexcare.billing.dto.UnbilledChargesSummary;
import com.apexcare.billing.dto.WardDischargeChargeRequest;
import com.apexcare.billing.entity.PaymentStatus;
import com.apexcare.billing.security.JwtPrincipal;
import com.apexcare.billing.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/billing")
@Tag(name = "Billing", description = "Patient billing, invoice generation, charge calculation, and payment processing")
@SecurityRequirement(name = "bearerAuth")
public class BillingController {

    private static final int MAX_PAGE_SIZE = 100;

    /** Allow-list of sortable fields — prevents PropertyReferenceException and
     *  index misses from arbitrary client-supplied field names. */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "createdAt", "updatedAt", "paidAt", "totalAmount", "paymentStatus");

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    // =================================================================
    // READ
    // =================================================================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_ADMIN', 'RECEPTIONIST', 'PATIENT')")
    @Operation(summary = "List bills with optional filters (paged)",
               description = "Staff can see all or filter by patientId/status. Patients see only their own.")
    public ResponseEntity<ApiResponse<Page<BillResponse>>> getBills(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDir,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        String safeSort = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, safeSort));
        Page<BillResponse> bills = billingService.getBills(patientId, status, pageable, principal);
        return ResponseEntity.ok(ApiResponse.ok("Bills retrieved successfully", bills));
    }

    @GetMapping({"/stats/summary", "/summary"})
    @PreAuthorize("hasAnyRole('ADMIN','BILLING_ADMIN','RECEPTIONIST')")
    @Operation(summary = "Get billing revenue and invoice statistics summary")
    public ResponseEntity<ApiResponse<BillingSummaryResponse>> getBillingSummary(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        BillingSummaryResponse summary = billingService.getSummaryStats(principal);
        return ResponseEntity.ok(ApiResponse.ok("Billing summary retrieved successfully", summary));
    }

    @GetMapping("/{id:[0-9a-fA-F-]{36}}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_ADMIN', 'RECEPTIONIST', 'PATIENT')")
    @Operation(summary = "Get a bill by its UUID")
    public ResponseEntity<ApiResponse<BillResponse>> getBill(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        BillResponse bill = billingService.getBill(id, principal);
        return ResponseEntity.ok(ApiResponse.ok("Bill retrieved successfully", bill));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING_ADMIN', 'RECEPTIONIST', 'PATIENT')")
    @Operation(summary = "Get all bills for a specific patient (staff or the patient themselves)")
    public ResponseEntity<ApiResponse<List<BillResponse>>> getBillsForPatient(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        List<BillResponse> bills = billingService.getBillsForPatient(patientId, principal);
        return ResponseEntity.ok(ApiResponse.ok("Patient bills retrieved successfully", bills));
    }

    @GetMapping("/patient/{patientId}/unbilled-summary")
    @PreAuthorize("hasAnyRole('ADMIN','BILLING_ADMIN','RECEPTIONIST')")
    @Operation(summary = "Get unbilled usage charges summary (medicines, lab tests, bed stays) for a patient")
    public ResponseEntity<ApiResponse<UnbilledChargesSummary>> getUnbilledSummary(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        UnbilledChargesSummary summary = billingService.getUnbilledCharges(patientId, principal);
        return ResponseEntity.ok(ApiResponse.ok("Unbilled charges calculated", summary));
    }

    // =================================================================
    // WRITE
    // =================================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BILLING_ADMIN')")
    @Operation(summary = "Create a new bill (ADMIN/BILLING_ADMIN only)")
    public ResponseEntity<ApiResponse<BillResponse>> createBill(
            @Valid @RequestBody BillRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        BillResponse bill = billingService.createBill(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Bill created successfully", bill));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','BILLING_ADMIN','DOCTOR','SYSTEM')")
    @Operation(summary = "Auto-generate a bill from an appointment (idempotent, called by appointment-service)")
    public ResponseEntity<ApiResponse<BillResponse>> generateBillForAppointment(
            @Valid @RequestBody GenerateBillRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        BillResponse bill = billingService.generateBillForAppointment(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Bill generated successfully", bill));
    }

    @PostMapping("/patient/{patientId}/auto-bill")
    @PreAuthorize("hasAnyRole('ADMIN','BILLING_ADMIN')")
    @Operation(summary = "1-click auto generate consolidated invoice for patient usage (ADMIN/BILLING_ADMIN only)")
    public ResponseEntity<ApiResponse<BillResponse>> generateAutoBill(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        BillResponse bill = billingService.generateAutoBill(patientId, principal);
        return ResponseEntity.ok(ApiResponse.ok("Consolidated bill auto-generated successfully", bill));
    }

    @PostMapping("/ward-discharge")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE','BILLING_ADMIN','SYSTEM') or #principal == null")
    @Operation(summary = "Record inpatient bed discharge and auto-add ward charges to bill in real-time")
    public ResponseEntity<ApiResponse<BillResponse>> recordWardDischarge(
            @RequestBody WardDischargeChargeRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        BillResponse bill = billingService.recordWardDischarge(request, principal);
        return ResponseEntity.ok(ApiResponse.ok("Ward discharge charge added to patient bill in real-time", bill));
    }

    // =================================================================
    // PAYMENT
    // =================================================================

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN','BILLING_ADMIN','RECEPTIONIST','PATIENT')")
    @Operation(summary = "Process payment for a bill",
               description = "Patients may only pay their own bills. Staff may process any.")
    public ResponseEntity<ApiResponse<PaymentReceiptResponse>> processPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        PaymentReceiptResponse receipt = billingService.processPayment(id, request, principal);
        return ResponseEntity.ok(ApiResponse.ok("Payment processed successfully", receipt));
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAnyRole('ADMIN','BILLING_ADMIN','RECEPTIONIST','PATIENT')")
    @Operation(summary = "Get formatted receipt for a bill",
               description = "Patients may only fetch their own receipts.")
    public ResponseEntity<ApiResponse<PaymentReceiptResponse>> getReceipt(
            @PathVariable UUID id,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        PaymentReceiptResponse receipt = billingService.getReceipt(id, principal);
        return ResponseEntity.ok(ApiResponse.ok("Receipt retrieved successfully", receipt));
    }


}
