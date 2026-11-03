package com.apexcare.patient.service;

import com.apexcare.patient.dto.CreateReferralRequest;
import com.apexcare.patient.dto.ReferralResponse;
import com.apexcare.patient.entity.AuditAction;
import com.apexcare.patient.entity.DoctorReferral;
import com.apexcare.patient.entity.ReferralPriority;
import com.apexcare.patient.entity.ReferralStatus;
import com.apexcare.patient.entity.ResourceType;
import com.apexcare.patient.entity.Specialty;
import com.apexcare.patient.exception.AuthorizationException;
import com.apexcare.patient.exception.InvalidRequestException;
import com.apexcare.patient.exception.ResourceNotFoundException;
import com.apexcare.patient.repository.DoctorReferralRepository;
import com.apexcare.patient.security.JwtPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DoctorReferralService {

    private final DoctorReferralRepository repository;
    private final AuditLogService auditLogService;

    public DoctorReferralService(DoctorReferralRepository repository,
                                 AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    /** Legal status transitions — enforced on every update. */
    private static final Map<ReferralStatus, Set<ReferralStatus>> ALLOWED_TRANSITIONS = Map.of(
            ReferralStatus.PENDING,   EnumSet.of(ReferralStatus.ACCEPTED,
                                                 ReferralStatus.DECLINED,
                                                 ReferralStatus.CANCELLED),
            ReferralStatus.ACCEPTED,  EnumSet.of(ReferralStatus.COMPLETED,
                                                 ReferralStatus.CANCELLED),
            ReferralStatus.COMPLETED, EnumSet.noneOf(ReferralStatus.class),
            ReferralStatus.DECLINED,  EnumSet.noneOf(ReferralStatus.class),
            ReferralStatus.CANCELLED, EnumSet.noneOf(ReferralStatus.class)
    );

    // -----------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------

    /** ADMIN-only: list every referral in the hospital. */
    public Page<ReferralResponse> getAllReferrals(@NonNull Pageable pageable) {
        return repository.findAll(pageable).map(ReferralResponse::from);
    }

    /** Referrals assigned to the given doctor (incoming). */
    public List<ReferralResponse> getIncomingReferrals(@NonNull UUID doctorId) {
        return repository.findByReferredDoctorIdOrderByCreatedAtDesc(doctorId)
                .stream()
                .map(ReferralResponse::from)
                .toList();
    }

    /** Referrals initiated by the given doctor (outgoing). */
    public List<ReferralResponse> getOutgoingReferrals(@NonNull UUID doctorId) {
        return repository.findByReferringDoctorIdOrderByCreatedAtDesc(doctorId)
                .stream()
                .map(ReferralResponse::from)
                .toList();
    }

    public List<ReferralResponse> getPatientReferrals(@NonNull UUID patientId) {
        return repository.findByPatientIdOrderByCreatedAtDesc(patientId)
                .stream()
                .map(ReferralResponse::from)
                .toList();
    }

    public ReferralResponse getById(@NonNull UUID referralId) {
        DoctorReferral ref = repository.findById(referralId)
                .orElseThrow(() -> ResourceNotFoundException.of("Referral", referralId));
        return ReferralResponse.from(ref);
    }

    // -----------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------

    @Transactional
    public ReferralResponse createReferral(CreateReferralRequest request, JwtPrincipal principal) {
        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }

        DoctorReferral ref = new DoctorReferral();
        ref.setPatientId(request.patientId());
        ref.setReferringDoctorId(principal.userId());
        ref.setReferringDoctorName(principal.username());
        ref.setTargetSpecialty(request.targetSpecialty() != null ? request.targetSpecialty() : Specialty.OTHER);
        ref.setPriority(request.priority() != null ? request.priority() : ReferralPriority.ROUTINE);
        ref.setClinicalSummary(request.clinicalSummary());
        ref.setReferralQuestion(request.referralQuestion());
        ref.setStatus(ReferralStatus.PENDING);

        DoctorReferral saved = repository.save(ref);

        auditLogService.record(
                principal,
                AuditAction.CREATE_REFERRAL,
                ResourceType.REFERRAL,
                saved.getId().toString(),
                "Referral created for patient " + saved.getPatientId()
                        + " to specialty " + saved.getTargetSpecialty(),
                null
        );

        return ReferralResponse.from(saved);
    }

    // -----------------------------------------------------------------
    // UPDATE STATUS (with state machine + authorization)
    // -----------------------------------------------------------------

    @Transactional
    public ReferralResponse updateStatus(@NonNull UUID referralId,
                                         ReferralStatus newStatus,
                                         JwtPrincipal principal) {

        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }

        DoctorReferral ref = repository.findById(referralId)
                .orElseThrow(() -> ResourceNotFoundException.of("Referral", referralId));

        // 1. Authorization
        boolean isAdmin      = principal.isAdmin();
        boolean isReferring  = ref.getReferringDoctorId() != null
                && ref.getReferringDoctorId().equals(principal.userId());
        boolean isReferred   = ref.getReferredDoctorId() != null
                && ref.getReferredDoctorId().equals(principal.userId());

        switch (newStatus) {
            case ACCEPTED, DECLINED, COMPLETED -> {
                // Only the doctor the referral was routed to (or ADMIN) can act
                if (!isAdmin && !isReferred) {
                    throw new AuthorizationException(
                            "Only the assigned doctor can update this referral to " + newStatus);
                }
            }
            case CANCELLED -> {
                // Only the referring doctor (or ADMIN) can cancel
                if (!isAdmin && !isReferring) {
                    throw new AuthorizationException(
                            "Only the referring doctor can cancel this referral");
                }
            }
            case PENDING -> throw new InvalidRequestException(
                    "Cannot transition back to PENDING");
        }

        // 2. State machine
        Set<ReferralStatus> allowed =
                ALLOWED_TRANSITIONS.getOrDefault(ref.getStatus(), Set.of());
        if (!allowed.contains(newStatus)) {
            throw new InvalidRequestException(
                    "Invalid referral transition: " + ref.getStatus() + " → " + newStatus);
        }

        // 3. Apply + workflow timestamps
        Instant now = Instant.now();
        ref.setStatus(newStatus);
        switch (newStatus) {
            case ACCEPTED -> {
                ref.setReferredDoctorId(principal.userId());
                ref.setReferredDoctorName(principal.username());
                ref.setAcceptedAt(now);
            }
            case COMPLETED -> ref.setCompletedAt(now);
            case DECLINED, CANCELLED -> { /* updatedAt handled by @PreUpdate */ }
            default -> { /* no-op */ }
        }

        DoctorReferral saved = repository.save(ref);

        // 4. Audit
        auditLogService.record(
                principal,
                AuditAction.UPDATE_REFERRAL_STATUS,
                ResourceType.REFERRAL,
                saved.getId().toString(),
                "Referral status → " + newStatus,
                null
        );

        return ReferralResponse.from(saved);
    }
}
