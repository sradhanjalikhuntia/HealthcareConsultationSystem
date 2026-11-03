package com.apexcare.patient.service;

import com.apexcare.patient.dto.PatientRequest;
import com.apexcare.patient.dto.PatientResponse;
import com.apexcare.patient.entity.AuditAction;
import com.apexcare.patient.entity.Patient;
import com.apexcare.patient.entity.PatientStatus;
import com.apexcare.patient.entity.ResourceType;
import com.apexcare.patient.exception.AuthorizationException;
import com.apexcare.patient.exception.InvalidRequestException;
import com.apexcare.patient.exception.ResourceNotFoundException;
import com.apexcare.patient.repository.PatientRepository;
import com.apexcare.patient.repository.AdmissionRecordRepository;
import com.apexcare.patient.entity.AdmissionStatus;
import com.apexcare.patient.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.time.Period;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository repository;
    private final UniquePatientIdService uniquePatientIdService;
    private final AuditLogService auditLogService;
    private final AdmissionRecordRepository admissionRecordRepository;

    public PatientService(PatientRepository repository,
                          UniquePatientIdService uniquePatientIdService,
                          AuditLogService auditLogService,
                          AdmissionRecordRepository admissionRecordRepository) {
        this.repository = repository;
        this.uniquePatientIdService = uniquePatientIdService;
        this.auditLogService = auditLogService;
        this.admissionRecordRepository = admissionRecordRepository;
    }

    // -----------------------------------------------------------------
    // CREATE — staff registers a new patient
    // -----------------------------------------------------------------
    @Transactional
    public PatientResponse create(PatientRequest request, JwtPrincipal principal) {
        requireStaff(principal);
        if (principal != null && principal.isDoctor()) {
            throw new AuthorizationException("Doctors are not allowed to register patients");
        }

        Patient patient = new Patient();
        applyEditableFields(patient, request);
        patient.setStatus(PatientStatus.ACTIVE);
        assignUniqueId(patient);

        Patient saved = repository.save(patient);

        auditLogService.record(principal, AuditAction.CREATE_PATIENT,
                ResourceType.PATIENT_EMR, saved.getId().toString(),
                "Patient registered: " + saved.getUniquePatientId(), null);

        log.info("Patient registered id={} uniquePatientId={}",
                saved.getId(), saved.getUniquePatientId());
        return PatientResponse.from(saved);
    }

    // -----------------------------------------------------------------
    // CREATE — patient self-registers own record (linked account)
    // -----------------------------------------------------------------
    @Transactional
    public PatientResponse createForLinkedAccount(PatientRequest request, JwtPrincipal principal) {
        if (principal == null || !principal.isPatient()) {
            throw new AuthorizationException(
                    "Only patient accounts can self-register a medical record");
        }
        if (principal.linkedPatientId() == null) {
            throw new InvalidRequestException(
                    "Patient account is not linked to a medical record identifier");
        }

        // Idempotent: return existing record if already registered
        UUID linkedId = principal.linkedPatientId();
        return repository.findById(linkedId)
                .map(PatientResponse::from)
                .orElseGet(() -> {
                    Patient patient = new Patient();
                    applyEditableFields(patient, request);
                    patient.setId(principal.linkedPatientId());
                    patient.setStatus(PatientStatus.ACTIVE);
                    assignUniqueId(patient);

                    Patient saved = repository.save(patient);

                    auditLogService.record(principal, AuditAction.CREATE_PATIENT,
                            ResourceType.PATIENT_EMR, saved.getId().toString(),
                            "Patient self-registered: " + saved.getUniquePatientId(), null);

                    log.info("Self-registered patient id={} uniquePatientId={}",
                            saved.getId(), saved.getUniquePatientId());
                    return PatientResponse.from(saved);
                });
    }

    // -----------------------------------------------------------------
    // READ — list / search
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<PatientResponse> list(String q,
                                        PatientStatus status,
                                        Pageable pageable,
                                        JwtPrincipal principal) {
        requireStaff(principal);

        String normalizedQuery = (q == null || q.isBlank()) ? null : q.trim();
        if (normalizedQuery == null && status == null) {
            return repository.findAll(pageable).map(PatientResponse::from);
        }
        if (normalizedQuery == null) {
            return repository.findByStatus(status, pageable).map(PatientResponse::from);
        }
        return repository.search(normalizedQuery, status, pageable).map(PatientResponse::from);
    }

    // -----------------------------------------------------------------
    // READ — get by id
    // -----------------------------------------------------------------
    @Transactional
    public PatientResponse get(UUID id, JwtPrincipal principal) {
        requireAuthenticated(principal);

        Patient patient = repository.findById(id)
                .orElseGet(() -> {
                    // Auto-provision medical record for a signed-in patient account if not created yet
                    if (principal != null && principal.isPatient() && principal.isLinkedToPatient(id)) {
                        Patient p = new Patient();
                        p.setId(id);
                        String name = principal.username() != null ? principal.username() : "Patient";
                        p.setFirstName(name);
                        p.setLastName("User");
                        p.setDateOfBirth(LocalDate.of(2000, 1, 1));
                        p.setGender(com.apexcare.patient.entity.Gender.OTHER);
                        p.setBloodGroup(com.apexcare.patient.entity.BloodGroup.O_POSITIVE);
                        p.setPhone("+1000000000");
                        p.setEmail(id + "@patient.apexcare.com");
                        p.setStatus(PatientStatus.ACTIVE);
                        assignUniqueId(p);
                        Patient saved = repository.save(p);
                        log.info("Auto-provisioned medical record id={} uniquePatientId={} for user={}",
                                saved.getId(), saved.getUniquePatientId(), principal.username());
                        return saved;
                    }
                    throw ResourceNotFoundException.of("Patient", id);
                });

        // IDOR protection — 404 instead of 403, so existence is not leaked
        if (principal.isPatient() && !principal.isLinkedToPatient(id)) {
            throw ResourceNotFoundException.of("Patient", id);
        }

        return PatientResponse.from(patient);
    }

    // -----------------------------------------------------------------
    // UPDATE
    // -----------------------------------------------------------------
    @Transactional
    public PatientResponse update(UUID id, PatientRequest request, JwtPrincipal principal) {
        requireStaff(principal);

        Patient patient = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Patient", id));

        applyEditableFields(patient, request);

        Patient saved = repository.save(patient);

        auditLogService.record(principal, AuditAction.UPDATE_EMR,
                ResourceType.PATIENT_EMR, id.toString(),
                "Patient record updated", null);

        log.info("Patient updated id={}", id);
        return PatientResponse.from(saved);
    }

    // -----------------------------------------------------------------
    // DEACTIVATE (soft delete) — ADMIN only
    // -----------------------------------------------------------------
    @Transactional
    public void deactivate(UUID id, JwtPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new AuthorizationException("ADMIN role required to deactivate patients");
        }

        Patient patient = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Patient", id));

        patient.getStatus().requireTransitionTo(PatientStatus.INACTIVE);
        patient.setStatus(PatientStatus.INACTIVE);
        repository.save(patient);

        auditLogService.record(principal, AuditAction.DELETE_PATIENT,
                ResourceType.PATIENT_EMR, id.toString(),
                "Patient record deactivated", null);

        log.info("Patient deactivated id={}", id);
    }

    // -----------------------------------------------------------------
    // REACTIVATE — ADMIN only
    // -----------------------------------------------------------------
    @Transactional
    public PatientResponse reactivate(UUID id, JwtPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new AuthorizationException("ADMIN role required to reactivate patients");
        }

        Patient patient = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Patient", id));

        patient.getStatus().requireTransitionTo(PatientStatus.ACTIVE);
        patient.setStatus(PatientStatus.ACTIVE);
        Patient saved = repository.save(patient);

        auditLogService.record(principal, AuditAction.UPDATE_EMR,
                ResourceType.PATIENT_EMR, id.toString(),
                "Patient record reactivated to ACTIVE", null);

        log.info("Patient reactivated id={}", id);
        return PatientResponse.from(saved);
    }

    // -----------------------------------------------------------------
    // SUMMARY
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(JwtPrincipal principal) {
        requireStaff(principal);

        long totalPatients = repository.count();
        long activePatients = repository.countByStatus(PatientStatus.ACTIVE);
        long dormantPatients = repository.countByStatus(PatientStatus.DORMANT);
        long dischargedPatients = repository.countByStatus(PatientStatus.DISCHARGED);
        long deceasedPatients = repository.countByStatus(PatientStatus.DECEASED);
        long activeInpatients = admissionRecordRepository.countByStatus(AdmissionStatus.ADMITTED);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalPatients", totalPatients);
        summary.put("totalCount", totalPatients);
        summary.put("activePatients", activePatients);
        summary.put("dormantPatients", dormantPatients);
        summary.put("dischargedPatients", dischargedPatients);
        summary.put("deceasedPatients", deceasedPatients);
        summary.put("activeCount", activeInpatients);
        summary.put("activeInpatients", activeInpatients);
        summary.put("inpatientCount", activeInpatients);

        // Calculate actual demographics
        List<Patient> allPatients = repository.findAll();
        int age0_18 = 0, age19_35 = 0, age36_60 = 0, age60_plus = 0;
        LocalDate now = LocalDate.now();

        for (Patient p : allPatients) {
            if (p.getDateOfBirth() != null) {
                int age = Period.between(p.getDateOfBirth(), now).getYears();
                if (age <= 18) age0_18++;
                else if (age <= 35) age19_35++;
                else if (age <= 60) age36_60++;
                else age60_plus++;
            }
        }

        Map<String, Integer> demographics = new LinkedHashMap<>();
        demographics.put("0-18", age0_18);
        demographics.put("19-35", age19_35);
        demographics.put("36-60", age36_60);
        demographics.put("60+", age60_plus);
        
        summary.put("demographics", demographics);

        return summary;
    }

    public Map<String, Object> getSummary() {
        return getSummary(null);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void applyEditableFields(Patient patient, PatientRequest request) {
        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        if (request.getBloodGroup() == null || request.getBloodGroup().isBlank()) {
            patient.setBloodGroup(null);
        } else {
            String bg = request.getBloodGroup().trim().toUpperCase()
                    .replace("+", "_POSITIVE")
                    .replace("-", "_NEGATIVE");
            try {
                patient.setBloodGroup(com.apexcare.patient.entity.BloodGroup.valueOf(bg));
            } catch (IllegalArgumentException e) {
                patient.setBloodGroup(null);
            }
        }
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setEmergencyContact(request.getEmergencyContact());
        patient.setMedicalHistory(request.getMedicalHistory());
        patient.setAllergies(request.getAllergies());
    }

    private void assignUniqueId(Patient patient) {
        if (patient.getUniquePatientId() == null || patient.getUniquePatientId().isBlank()) {
            patient.setUniquePatientId(uniquePatientIdService.nextId());
        }
    }

    private static void requireAuthenticated(JwtPrincipal principal) {
        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }
    }

    private static void requireStaff(JwtPrincipal principal) {
        requireAuthenticated(principal);
        if (principal.isPatient()) {
            throw new AuthorizationException("Insufficient role to perform this operation");
        }
    }
}
