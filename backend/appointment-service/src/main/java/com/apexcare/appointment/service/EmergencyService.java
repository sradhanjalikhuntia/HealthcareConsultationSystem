package com.apexcare.appointment.service;

import com.apexcare.appointment.dto.AmbulanceTriageRequest;
import com.apexcare.appointment.dto.AmbulanceTriageResponse;
import com.apexcare.appointment.dto.EmergencyAlertRequest;
import com.apexcare.appointment.dto.EmergencyAlertResponse;
import com.apexcare.appointment.entity.AmbulanceStatus;
import com.apexcare.appointment.entity.AmbulanceTriage;
import com.apexcare.appointment.entity.AuditAction;
import com.apexcare.appointment.entity.EmergencyAlert;
import com.apexcare.appointment.entity.EmergencySeverity;
import com.apexcare.appointment.entity.ResourceType;
import com.apexcare.appointment.entity.Role;
import com.apexcare.appointment.exception.AuthorizationException;
import com.apexcare.appointment.exception.InvalidRequestException;
import com.apexcare.appointment.exception.ResourceNotFoundException;
import com.apexcare.appointment.repository.AmbulanceTriageRepository;
import com.apexcare.appointment.repository.EmergencyAlertRepository;
import com.apexcare.appointment.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EmergencyService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyService.class);

    private final EmergencyAlertRepository alertRepository;
    private final AmbulanceTriageRepository ambulanceRepository;
    private final AuditLogService auditLogService;

    public EmergencyService(EmergencyAlertRepository alertRepository,
                            AmbulanceTriageRepository ambulanceRepository,
                            AuditLogService auditLogService) {
        this.alertRepository = alertRepository;
        this.ambulanceRepository = ambulanceRepository;
        this.auditLogService = auditLogService;
    }

    // =================================================================
    // EMERGENCY ALERTS
    // =================================================================

    @Transactional(readOnly = true)
    public List<EmergencyAlertResponse> getActiveAlerts(JwtPrincipal principal) {
        requireClinical(principal);
        return alertRepository.findByActiveTrueOrderByTriggeredAtDesc()
                .stream()
                .map(EmergencyAlertResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<EmergencyAlertResponse> getAllAlerts(Pageable pageable, JwtPrincipal principal) {
        requireClinical(principal);
        return alertRepository.findAllByOrderByTriggeredAtDesc(pageable)
                .map(EmergencyAlertResponse::from);
    }

    @Transactional(readOnly = true)
    public EmergencyAlertResponse getAlert(UUID alertId, JwtPrincipal principal) {
        requireClinical(principal);
        EmergencyAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> ResourceNotFoundException.of("EmergencyAlert", alertId));
        return EmergencyAlertResponse.from(alert);
    }

    @Transactional
    public EmergencyAlertResponse broadcastCode(EmergencyAlertRequest request, JwtPrincipal principal) {
        requireClinical(principal);

        if (request.codeType() == null) {
            throw new InvalidRequestException("codeType is required");
        }
        if (request.location() == null || request.location().isBlank()) {
            throw new InvalidRequestException("location is required");
        }

        // Prevent duplicate active alerts of the same type at the same location
        if (alertRepository.existsByActiveTrueAndCodeType(request.codeType())) {
            log.warn("Duplicate active {} alert broadcast ignored by={}",
                    request.codeType(), principal.userId());
            throw new InvalidRequestException(
                    "An active " + request.codeType() + " alert already exists. "
                    + "Resolve the existing alert first.");
        }

        EmergencyAlert alert = new EmergencyAlert();
        alert.setCodeType(request.codeType());
        alert.setLocation(request.location().trim());
        alert.setSeverity(request.severity() != null
                ? request.severity() : EmergencySeverity.CRITICAL);
        alert.setTriggeredBy(principal.userId());
        alert.setTriggeredByName(principal.username());
        alert.setNotes(request.notes());

        EmergencyAlert saved = alertRepository.save(alert);

        auditLogService.record(principal, AuditAction.BROADCAST_CODE,
                ResourceType.EMERGENCY_ALERT, saved.getId().toString(),
                "Broadcast " + saved.getCodeType() + " at " + saved.getLocation(),
                null);

        log.warn("EMERGENCY {} broadcast id={} location={} by={}",
                saved.getCodeType(), saved.getId(), saved.getLocation(), principal.userId());

        return EmergencyAlertResponse.from(saved);
    }

    @Transactional
    public EmergencyAlertResponse resolveAlert(UUID alertId,
                                                String resolutionNotes,
                                                JwtPrincipal principal) {
        requireClinical(principal);

        EmergencyAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> ResourceNotFoundException.of("EmergencyAlert", alertId));

        // Domain method enforces idempotency
        alert.resolve(principal.userId(), resolutionNotes);
        EmergencyAlert saved = alertRepository.save(alert);

        auditLogService.record(principal, AuditAction.RESOLVE_CODE,
                ResourceType.EMERGENCY_ALERT, saved.getId().toString(),
                "Resolved " + saved.getCodeType() + " at " + saved.getLocation(),
                null);

        log.info("EMERGENCY {} resolved id={} by={}",
                saved.getCodeType(), saved.getId(), principal.userId());

        return EmergencyAlertResponse.from(saved);
    }

    // =================================================================
    // AMBULANCE TRIAGE
    // =================================================================

    @Transactional(readOnly = true)
    public Page<AmbulanceTriageResponse> getAmbulances(Pageable pageable, JwtPrincipal principal) {
        requireClinical(principal);
        return ambulanceRepository.findAllByOrderByDispatchedAtDesc(pageable)
                .map(AmbulanceTriageResponse::from);
    }

    @Transactional(readOnly = true)
    public AmbulanceTriageResponse getAmbulance(UUID id, JwtPrincipal principal) {
        requireClinical(principal);
        AmbulanceTriage amb = ambulanceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AmbulanceTriage", id));
        return AmbulanceTriageResponse.from(amb);
    }

    @Transactional
    public AmbulanceTriageResponse registerAmbulance(AmbulanceTriageRequest request,
                                                     JwtPrincipal principal) {
        requireClinical(principal);

        AmbulanceTriage amb = new AmbulanceTriage();
        amb.setPatientName(request.patientName());
        amb.setPatientAge(request.patientAge());
        amb.setChiefComplaint(request.chiefComplaint());
        amb.setLocation(request.location());
        amb.setSeverity(request.severity() != null
                ? request.severity() : EmergencySeverity.HIGH);
        amb.setStatus(AmbulanceStatus.EN_ROUTE);
        amb.setRegisteredBy(principal.userId());

        AmbulanceTriage saved = ambulanceRepository.save(amb);

        auditLogService.record(principal, AuditAction.REGISTER_AMBULANCE,
                ResourceType.AMBULANCE, saved.getId().toString(),
                "Ambulance registered for " + saved.getPatientName()
                        + " from " + saved.getLocation(),
                null);

        log.info("Ambulance registered id={} by={}", saved.getId(), principal.userId());

        return AmbulanceTriageResponse.from(saved);
    }

    @Transactional
    public AmbulanceTriageResponse updateAmbulanceStatus(UUID id,
                                                         AmbulanceStatus newStatus,
                                                         JwtPrincipal principal) {
        requireClinical(principal);

        if (newStatus == null) {
            throw new InvalidRequestException("New status is required");
        }

        AmbulanceTriage amb = ambulanceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AmbulanceTriage", id));

        // State machine transition
        amb.getStatus().requireTransitionTo(newStatus);
        amb.setStatus(newStatus);

        AmbulanceTriage saved = ambulanceRepository.save(amb);

        auditLogService.record(principal, AuditAction.UPDATE_AMBULANCE_STATUS,
                ResourceType.AMBULANCE, saved.getId().toString(),
                "Ambulance status → " + newStatus,
                null);

        log.info("Ambulance status updated id={} status={} by={}",
                saved.getId(), newStatus, principal.userId());

        return AmbulanceTriageResponse.from(saved);
    }

    // =================================================================
    // HELPERS
    // =================================================================

    private static void requireClinical(JwtPrincipal principal) {
        if (principal == null
                || !principal.hasAnyRole(Role.ADMIN, Role.DOCTOR, Role.NURSE)) {
            throw new AuthorizationException(
                    "Only clinical staff can access emergency operations");
        }
    }
}
