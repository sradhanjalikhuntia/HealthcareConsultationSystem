package com.apexcare.billing.controller;

import com.apexcare.billing.dto.ApiResponse;
import com.apexcare.billing.dto.InsuranceClaimResponse;
import com.apexcare.billing.entity.InsuranceClaim;
import com.apexcare.billing.exception.InvalidRequestException;
import com.apexcare.billing.security.JwtPrincipal;
import com.apexcare.billing.service.InsuranceService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/billing/insurance")
@Tag(name = "Insurance Pre-Auth & Claims",
     description = "Insurance claim submission, adjudication, and settlement")
@SecurityRequirement(name = "bearerAuth")
public class InsuranceController {

    private final InsuranceService insuranceService;

    public InsuranceController(InsuranceService insuranceService) {
        this.insuranceService = insuranceService;
    }

    // =================================================================
    // READ
    // =================================================================

    @GetMapping("/claims")
    @PreAuthorize("hasAnyRole('ADMIN','BILLING_ADMIN','RECEPTIONIST')")
    @Operation(summary = "List all insurance claims (staff only, paginated)")
    public ResponseEntity<ApiResponse<Page<InsuranceClaimResponse>>> getAllClaims(
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Page<InsuranceClaimResponse> page = insuranceService
                .getAllClaims(pageable, principal)
                .map(InsuranceClaimResponse::from);
        return ResponseEntity.ok(ApiResponse.ok("Claims retrieved", page));
    }

    @GetMapping("/claims/patient/{patientId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get insurance claims for a patient",
               description = "Patients may only access their own claims. Staff may access any.")
    public ResponseEntity<ApiResponse<List<InsuranceClaimResponse>>> getPatientClaims(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        List<InsuranceClaimResponse> claims = insuranceService
                .getPatientClaims(patientId, principal)
                .stream()
                .map(InsuranceClaimResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Patient claims retrieved", claims));
    }

    @GetMapping("/claims/{claimId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a single claim by id")
    public ResponseEntity<ApiResponse<InsuranceClaimResponse>> getClaim(
            @PathVariable UUID claimId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        InsuranceClaim claim = insuranceService.getClaim(claimId, principal);
        return ResponseEntity.ok(ApiResponse.ok("Claim retrieved",
                InsuranceClaimResponse.from(claim)));
    }

    // =================================================================
    // CREATE
    // =================================================================

    @PostMapping("/claims")
    @PreAuthorize("hasAnyRole('ADMIN','BILLING_ADMIN','RECEPTIONIST')")
    @Operation(summary = "Submit insurance claim for pre-authorization or reimbursement (staff only)")
    public ResponseEntity<ApiResponse<InsuranceClaimResponse>> submitClaim(
            @RequestBody SubmitClaimRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        InsuranceClaim saved = insuranceService.submitClaim(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Insurance claim filed successfully",
                        InsuranceClaimResponse.from(saved)));
    }

    // =================================================================
    // ADJUDICATE
    // =================================================================

    @PutMapping("/claims/{claimId}/adjudicate")
    @PreAuthorize("hasAnyRole('ADMIN','BILLING_ADMIN')")
    @Operation(
            summary = "Adjudicate claim (APPROVED, REJECTED, SETTLED)",
            description = "Only ADMIN or BILLING_ADMIN can adjudicate. Status transitions "
                    + "are enforced — e.g. REJECTED → APPROVED is not allowed."
    )
    public ResponseEntity<ApiResponse<InsuranceClaimResponse>> adjudicateClaim(
            @PathVariable UUID claimId,
            @RequestBody AdjudicateClaimRequest request,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        InsuranceClaim adjudicated = insuranceService.adjudicateClaim(claimId, request, principal);
        return ResponseEntity.ok(ApiResponse.ok("Claim adjudicated successfully",
                InsuranceClaimResponse.from(adjudicated)));
    }

    // =================================================================
    // Request DTOs (nested records — keep controller self-contained)
    // =================================================================

    /**
     * Client payload for submitting a new claim.
     *
     * <p>Note: {@code status}, {@code claimNumber}, and adjudication fields are
     * deliberately NOT part of this DTO — those are server-controlled.
     */
    public record SubmitClaimRequest(
            UUID patientId,
            UUID billId,
            String providerName,
            String policyNumber,
            BigDecimal claimedAmount
    ) {
        public SubmitClaimRequest {
            if (patientId == null) {
                throw new InvalidRequestException("patientId is required");
            }
            if (providerName == null || providerName.isBlank()) {
                throw new InvalidRequestException("providerName is required");
            }
            if (policyNumber == null || policyNumber.isBlank()) {
                throw new InvalidRequestException("policyNumber is required");
            }
            if (claimedAmount == null || claimedAmount.signum() <= 0) {
                throw new InvalidRequestException("claimedAmount must be greater than zero");
            }
        }
    }

    /**
     * Client payload for adjudicating an existing claim.
     *
     * <p>{@code status} must be one of APPROVED / REJECTED / SETTLED.
     */
    public record AdjudicateClaimRequest(
            String status,
            BigDecimal approvedAmount,
            BigDecimal deductibleAmount,
            BigDecimal patientCopay,
            String remarks
    ) {
        public AdjudicateClaimRequest {
            if (status == null || status.isBlank()) {
                throw new InvalidRequestException("status is required");
            }
            if (!InsuranceClaim.VALID_STATUSES.contains(status)) {
                throw new InvalidRequestException(
                        "status must be one of " + InsuranceClaim.VALID_STATUSES);
            }
        }
    }
}
