package com.apexcare.patient.service;

import com.apexcare.patient.dto.RecordVitalsRequest;
import com.apexcare.patient.dto.VitalsResponse;
import com.apexcare.patient.entity.AuditAction;
import com.apexcare.patient.entity.ConsciousnessLevel;
import com.apexcare.patient.entity.News2RiskLevel;
import com.apexcare.patient.entity.PatientVitals;
import com.apexcare.patient.entity.ResourceType;
import com.apexcare.patient.entity.Spo2Scale;
import com.apexcare.patient.entity.VitalsSource;
import com.apexcare.patient.exception.AuthorizationException;
import com.apexcare.patient.exception.ResourceNotFoundException;
import com.apexcare.patient.repository.PatientRepository;
import com.apexcare.patient.repository.PatientVitalsRepository;
import com.apexcare.patient.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class VitalsService {

    private static final Logger log = LoggerFactory.getLogger(VitalsService.class);

    private final PatientVitalsRepository repository;
    private final PatientRepository patientRepository;
    private final AuditLogService auditLogService;

    public VitalsService(PatientVitalsRepository repository,
                         PatientRepository patientRepository,
                         AuditLogService auditLogService) {
        this.repository = repository;
        this.patientRepository = patientRepository;
        this.auditLogService = auditLogService;
    }

    // -----------------------------------------------------------------
    // RECORD
    // -----------------------------------------------------------------
    @Transactional
    public VitalsResponse recordVitals(RecordVitalsRequest req, JwtPrincipal principal) {
        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }

        // Ensure patient exists
        UUID patientId = req.patientId();
        patientRepository.findById(patientId)
                .orElseThrow(() -> ResourceNotFoundException.of("Patient", patientId));

        PatientVitals v = new PatientVitals();
        v.setPatientId(req.patientId());
        v.setRecordedBy(principal.userId());
        v.setSource(VitalsSource.NURSE_MANUAL);   // or req.source() if provided
        v.setHeartRate(req.heartRate());
        v.setSystolicBp(req.effectiveSystolicBp());
        v.setDiastolicBp(req.effectiveDiastolicBp());
        v.setSpO2(req.effectiveSpO2());
        v.setRespiratoryRate(req.respiratoryRate());
        v.setTemperature(req.temperature());
        v.setWeight(req.weight());
        v.setSpo2Scale(req.spo2Scale() != null ? req.spo2Scale() : Spo2Scale.SCALE_1);
        v.setConsciousness(req.consciousness() != null
                ? req.consciousness() : ConsciousnessLevel.ALERT);
        v.setOnSupplementalOxygen(Boolean.TRUE.equals(req.onSupplementalOxygen()));

        PatientVitals saved = repository.save(v);

        // Raise alert if high risk
        if (saved.getRiskLevel() == News2RiskLevel.HIGH_EMERGENCY
                || saved.isSingleParamCritical()) {
            log.warn("NEWS2 HIGH risk patientId={} score={} singleParamCritical={}",
                    saved.getPatientId(), saved.getNews2Score(), saved.isSingleParamCritical());
        }

        auditLogService.record(principal, AuditAction.VIEW_EMR,
                ResourceType.PATIENT_EMR, saved.getPatientId().toString(),
                "Vitals recorded, NEWS2=" + saved.getNews2Score(), null);

        return VitalsResponse.from(saved);
    }

    // -----------------------------------------------------------------
    // HISTORY — paginated, ownership-checked
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<VitalsResponse> getPatientHistory(@NonNull UUID patientId,
                                                   Instant from,
                                                   Instant to,
                                                   @NonNull Pageable pageable,
                                                   JwtPrincipal principal) {
        verifyAccessToPatient(patientId, principal);

        Page<PatientVitals> page = (from != null && to != null)
                ? repository.findByPatientIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        patientId, from, to, pageable)
                : repository.findByPatientIdOrderByRecordedAtDesc(patientId, pageable);

        return page.map(VitalsResponse::from);
    }

    // -----------------------------------------------------------------
    // LATEST
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public Optional<VitalsResponse> getLatest(@NonNull UUID patientId, JwtPrincipal principal) {
        verifyAccessToPatient(patientId, principal);
        return repository.findFirstByPatientIdOrderByRecordedAtDesc(patientId)
                .map(VitalsResponse::from);
    }

    // -----------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------
    private void verifyAccessToPatient(UUID patientId, JwtPrincipal principal) {
        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }

        boolean isStaff = principal.hasAnyRole(
                com.apexcare.patient.entity.Role.ADMIN,
                com.apexcare.patient.entity.Role.DOCTOR,
                com.apexcare.patient.entity.Role.NURSE);

        boolean isOwn = principal.isLinkedToPatient(patientId);

        if (!isStaff && !isOwn) {
            // 404 to avoid leaking existence
            throw ResourceNotFoundException.of("Patient", patientId);
        }
    }
}
