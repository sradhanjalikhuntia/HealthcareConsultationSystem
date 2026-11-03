package com.apexcare.billing.service;

import com.apexcare.billing.entity.InsuranceClaim;
import com.apexcare.billing.repository.InsuranceClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.apexcare.billing.controller.InsuranceController.AdjudicateClaimRequest;
import com.apexcare.billing.controller.InsuranceController.SubmitClaimRequest;
import com.apexcare.billing.security.JwtPrincipal;

@Service
public class InsuranceService {

    private final InsuranceClaimRepository repository;

    public InsuranceService(InsuranceClaimRepository repository) {
        this.repository = repository;
    }

    public List<InsuranceClaim> getAllClaims() {
        return repository.findAll();
    }

    public List<InsuranceClaim> getPatientClaims(UUID patientId) {
        return repository.findByPatientIdOrderBySubmittedAtDesc(patientId);
    }

    public Page<InsuranceClaim> getAllClaims(Pageable pageable, JwtPrincipal principal) {
        return repository.findAll(pageable);
    }

    public List<InsuranceClaim> getPatientClaims(UUID patientId, JwtPrincipal principal) {
        if (principal != null && principal.isPatient() && !principal.isLinkedToPatient(patientId)) {
            throw new SecurityException("Patients may only access their own claims");
        }
        return getPatientClaims(patientId);
    }

    public InsuranceClaim getClaim(UUID claimId, JwtPrincipal principal) {
        InsuranceClaim claim = repository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
        if (principal != null && principal.isPatient() && !principal.isLinkedToPatient(claim.getPatientId())) {
            throw new SecurityException("Patients may only access their own claims");
        }
        return claim;
    }

    @Transactional
    public InsuranceClaim submitClaim(SubmitClaimRequest request, JwtPrincipal principal) {
        InsuranceClaim claim = new InsuranceClaim();
        claim.setPatientId(request.patientId());
        claim.setBillId(request.billId());
        claim.setProviderName(request.providerName());
        claim.setPolicyNumber(request.policyNumber());
        claim.setClaimedAmount(request.claimedAmount());
        claim.setSubmittedBy(principal == null ? null : principal.userId());
        return submitClaim(claim);
    }

    @Transactional
    public InsuranceClaim adjudicateClaim(UUID claimId, AdjudicateClaimRequest request, JwtPrincipal principal) {
        return adjudicateClaim(claimId, request.approvedAmount(), request.deductibleAmount(),
                request.status(), request.remarks());
    }

    @Transactional
    public InsuranceClaim submitClaim(InsuranceClaim claim) {
        if (claim.getStatus() == null) claim.setStatus("SUBMITTED");
        return repository.save(claim);
    }

    @Transactional

    public InsuranceClaim adjudicateClaim(UUID claimId, BigDecimal approvedAmount, BigDecimal deductible, String status, String remarks) {
        InsuranceClaim claim = repository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));

        claim.setApprovedAmount(approvedAmount != null ? approvedAmount : claim.getClaimedAmount());
        claim.setDeductibleAmount(deductible != null ? deductible : BigDecimal.ZERO);
        claim.setPatientCopay(claim.getClaimedAmount().subtract(claim.getApprovedAmount()));
        claim.setStatus(status.toUpperCase());
        claim.setRemarks(remarks);
        claim.setSettledAt(Instant.now());
        return repository.save(claim);
    }
}
