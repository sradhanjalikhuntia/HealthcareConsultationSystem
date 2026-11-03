package com.apexcare.patient.service;

import com.apexcare.patient.dto.AdmitPatientRequest;
import com.apexcare.patient.dto.BedResponse;
import com.apexcare.patient.dto.DischargeRequest;
import com.apexcare.patient.entity.AdmissionRecord;
import com.apexcare.patient.entity.AdmissionStatus;
import com.apexcare.patient.entity.AuditAction;
import com.apexcare.patient.entity.Bed;
import com.apexcare.patient.entity.BedStatus;
import com.apexcare.patient.entity.Patient;
import com.apexcare.patient.entity.ResourceType;
import com.apexcare.patient.entity.Role;
import com.apexcare.patient.entity.WardType;
import com.apexcare.patient.exception.AuthorizationException;
import com.apexcare.patient.exception.InvalidRequestException;
import com.apexcare.patient.exception.ResourceNotFoundException;
import com.apexcare.patient.repository.AdmissionRecordRepository;
import com.apexcare.patient.repository.BedRepository;
import com.apexcare.patient.repository.PatientRepository;
import com.apexcare.patient.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class WardService {

    private static final Logger log = LoggerFactory.getLogger(WardService.class);

    private final BedRepository bedRepository;
    private final AdmissionRecordRepository recordRepository;
    private final PatientRepository patientRepository;
    private final AuditLogService auditLogService;

    public WardService(BedRepository bedRepository,
                       AdmissionRecordRepository recordRepository,
                       PatientRepository patientRepository,
                       AuditLogService auditLogService) {
        this.bedRepository = bedRepository;
        this.recordRepository = recordRepository;
        this.patientRepository = patientRepository;
        this.auditLogService = auditLogService;
    }

    // -----------------------------------------------------------------
    // READ
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<BedResponse> findBeds(WardType wardType,
                                      BedStatus status,
                                      Pageable pageable,
                                      JwtPrincipal principal) {
        requireStaff(principal);

        if (wardType != null && status != null) {
            return bedRepository.findByWardTypeAndStatus(wardType, status, pageable)
                    .map(BedResponse::from);
        }
        if (wardType != null) {
            return bedRepository.findByWardType(wardType, pageable).map(BedResponse::from);
        }
        if (status != null) {
            return bedRepository.findByStatus(status, pageable).map(BedResponse::from);
        }
        return bedRepository.findAll(pageable).map(BedResponse::from);
    }

    public Page<BedResponse> findBeds(WardType wardType, BedStatus status, Pageable pageable) {
        return findBeds(wardType, status, pageable, null);
    }

    // -----------------------------------------------------------------
    // SUMMARY — real-time ward and bed occupancy metrics
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public Map<String, Object> getWardSummary(JwtPrincipal principal) {
        requireStaff(principal);

        long total = bedRepository.count();
        long available = bedRepository.countByStatus(BedStatus.AVAILABLE);
        long occupied = bedRepository.countByStatus(BedStatus.OCCUPIED);
        long cleaning = bedRepository.countByStatus(BedStatus.CLEANING);
        long outOfService = bedRepository.countByStatus(BedStatus.OUT_OF_SERVICE);

        Map<String, Map<String, Long>> byWard = new java.util.LinkedHashMap<>();
        for (WardType wt : WardType.values()) {
            long wtTotal = bedRepository.countByWardType(wt);
            long wtAvailable = bedRepository.countByWardTypeAndStatus(wt, BedStatus.AVAILABLE);
            long wtOccupied = bedRepository.countByWardTypeAndStatus(wt, BedStatus.OCCUPIED);
            long wtCleaning = bedRepository.countByWardTypeAndStatus(wt, BedStatus.CLEANING);
            byWard.put(wt.name(), Map.of(
                    "total", wtTotal,
                    "available", wtAvailable,
                    "occupied", wtOccupied,
                    "cleaning", wtCleaning
            ));
        }

        return Map.of(
                "totalBeds", total,
                "availableBeds", available,
                "occupiedBeds", occupied,
                "cleaningBeds", cleaning,
                "outOfServiceBeds", outOfService,
                "byWard", byWard
        );
    }

    // -----------------------------------------------------------------
    // ADMIT — clinical action, race-protected by @Version
    // -----------------------------------------------------------------
    @Transactional
    public BedResponse admitPatient(UUID bedId,
                                    AdmitPatientRequest request,
                                    JwtPrincipal principal) {
        requireStaff(principal);
        if (principal != null && principal.isNurse()) {
            throw new AuthorizationException("Nurses are not authorized to admit patients");
        }

        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bed", bedId));

        if (bed.getStatus() != BedStatus.AVAILABLE) {
            throw new InvalidRequestException(
                    "Bed " + bed.getBedNumber() + " is " + bed.getStatus() + ", not available");
        }

        UUID patientId = request.patientId();
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> ResourceNotFoundException.of("Patient", patientId));

        // Patient must not already be admitted
        if (bedRepository.existsByCurrentPatientIdAndStatus(
                patient.getId(), BedStatus.OCCUPIED)) {
            throw new InvalidRequestException(
                    "Patient is already admitted in another bed");
        }

        // ---- Update bed ----
        Instant now = Instant.now();
        bed.setStatus(BedStatus.OCCUPIED);
        bed.setCurrentPatientId(patient.getId());
        bed.setCurrentPatientName(patient.getFirstName() + " " + patient.getLastName());
        bed.setLastOccupiedAt(now);
        Bed savedBed = bedRepository.save(bed);   // @Version guards concurrent admits

        // ---- Write admission record (snapshot semantics) ----
        AdmissionRecord record = new AdmissionRecord();
        record.setPatientId(patient.getId());
        record.setBedId(bed.getId());
        record.setBedNumber(bed.getBedNumber());
        record.setWardType(bed.getWardType());
        record.setPatientName(patient.getFirstName() + " " + patient.getLastName());
        record.setAdmittingDoctorId(principal != null ? principal.userId() : null);
        record.setAdmissionReason(request.reason());
        record.setStatus(AdmissionStatus.ADMITTED);
        record.setDailyRate(bed.getDailyRate() != null ? bed.getDailyRate() : BigDecimal.valueOf(1500.00));
        record.setCreatedBy(principal != null ? principal.userId() : null);
        recordRepository.save(record);

        // ---- Audit ----
        auditLogService.record(principal, AuditAction.ADMIT_PATIENT,
                ResourceType.ADMISSION_RECORD, record.getId().toString(),
                "Admitted patient " + patient.getId() + " to bed " + bed.getBedNumber(),
                null);

        log.info("Patient admitted patientId={} bedId={} bedNumber={} by={}",
                patient.getId(), bed.getId(), bed.getBedNumber(), principal != null ? principal.userId() : "SYSTEM");

        return BedResponse.from(savedBed);
    }

    // -----------------------------------------------------------------
    // DISCHARGE — automatically adds ward stay charges to patient bill
    // -----------------------------------------------------------------
    @Transactional
    public BedResponse dischargePatient(UUID bedId,
                                        DischargeRequest request,
                                        JwtPrincipal principal) {
        requireClinicalStaff(principal);
        if (principal != null && principal.isNurse()) {
            throw new AuthorizationException("Nurses are not authorized to discharge patients");
        }

        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bed", bedId));

        if (bed.getStatus() != BedStatus.OCCUPIED) {
            throw new InvalidRequestException(
                    "Bed " + bed.getBedNumber() + " is not currently occupied");
        }

        AdmissionRecord record = recordRepository
                .findByBedIdAndStatus(bedId, AdmissionStatus.ADMITTED)
                .orElseThrow(() -> new InvalidRequestException(
                        "No active admission record found for bed " + bed.getBedNumber()));

        // ---- Calculate stay duration and charges ----
        Instant now = Instant.now();
        record.setDischargedAt(now);

        Instant admittedAt = record.getAdmittedAt() != null ? record.getAdmittedAt() : now;
        long seconds = Duration.between(admittedAt, now).getSeconds();
        int stayDays = (int) Math.max(1, (long) Math.ceil(Math.max(1.0, (double) seconds / 86400.0)));
        BigDecimal dailyRate = bed.getDailyRate() != null ? bed.getDailyRate() : BigDecimal.valueOf(1500.00);
        BigDecimal totalCharge = dailyRate.multiply(BigDecimal.valueOf(stayDays));

        record.setDailyRate(dailyRate);
        record.setStayDays(stayDays);
        record.setTotalCharge(totalCharge);

        String summary = (request != null && request.dischargeSummary() != null && !request.dischargeSummary().isBlank())
                ? request.dischargeSummary()
                : "Discharged by clinical staff";
        record.setDischargeSummary(summary);
        record.setStatus(AdmissionStatus.DISCHARGED);
        record.setUpdatedBy(principal != null ? principal.userId() : null);

        // ---- Notify billing service in real-time to attach charges to patient bill ----
        UUID billId = notifyBillingOfDischarge(record, bed, stayDays, dailyRate, totalCharge, principal);
        if (billId != null) {
            record.setBilled(true);
            record.setBillId(billId);
        }

        recordRepository.save(record);

        // ---- Update bed ----
        bed.setStatus(BedStatus.CLEANING);
        bed.setCurrentPatientId(null);
        bed.setCurrentPatientName(null);
        bed.setLastDischargedAt(now);
        Bed savedBed = bedRepository.save(bed);

        // ---- Audit ----
        auditLogService.record(principal, AuditAction.DISCHARGE_PATIENT,
                ResourceType.ADMISSION_RECORD, record.getId().toString(),
                "Discharged patient from bed " + bed.getBedNumber()
                        + " (Stay: " + stayDays + " days, Total Charge: " + totalCharge + ")", null);

        log.info("Patient discharged bedId={} bedNumber={} days={} charge={} billId={}",
                bed.getId(), bed.getBedNumber(), stayDays, totalCharge, billId);

        return BedResponse.from(savedBed, stayDays, totalCharge, billId);
    }

    private UUID notifyBillingOfDischarge(AdmissionRecord record,
                                          Bed bed,
                                          int stayDays,
                                          BigDecimal dailyRate,
                                          BigDecimal totalCharge,
                                          JwtPrincipal principal) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

            String json = String.format(java.util.Locale.US,
                    "{\"admissionId\":\"%s\",\"patientId\":\"%s\",\"patientName\":\"%s\",\"bedNumber\":\"%s\",\"wardType\":\"%s\",\"admittedAt\":\"%s\",\"dischargedAt\":\"%s\",\"stayDays\":%d,\"dailyRate\":%.2f,\"totalCharge\":%.2f,\"dischargeSummary\":\"%s\"}",
                    record.getId(),
                    record.getPatientId(),
                    escapeJson(record.getPatientName()),
                    escapeJson(bed.getBedNumber()),
                    bed.getWardType() != null ? bed.getWardType().name() : "GENERAL",
                    record.getAdmittedAt().toString(),
                    record.getDischargedAt().toString(),
                    stayDays,
                    dailyRate.doubleValue(),
                    totalCharge.doubleValue(),
                    escapeJson(record.getDischargeSummary() != null ? record.getDischargeSummary() : "Discharged")
            );

            java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:8084/api/billing/ward-discharge"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json));

            java.net.http.HttpResponse<String> resp = client.send(reqBuilder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 || resp.statusCode() == 201) {
                log.info("Billing service notified of discharge for admissionId={}: {}", record.getId(), resp.body());
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F\\-]+)\"").matcher(resp.body());
                if (m.find()) {
                    return UUID.fromString(m.group(1));
                }
            } else {
                log.warn("Billing notification returned HTTP {}: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.error("Failed to notify billing service of discharge: {}", e.getMessage());
        }
        return null;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // -----------------------------------------------------------------
    // MARK READY (housekeeping)
    // -----------------------------------------------------------------
    @Transactional
    public BedResponse markBedReady(UUID bedId, JwtPrincipal principal) {
        requireStaff(principal);

        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bed", bedId));

        if (bed.getStatus() != BedStatus.CLEANING && bed.getStatus() != BedStatus.OUT_OF_SERVICE) {
            throw new InvalidRequestException(
                    "Bed " + bed.getBedNumber() + " is " + bed.getStatus()
                            + "; only CLEANING or OUT_OF_SERVICE beds can be marked READY");
        }

        bed.setStatus(BedStatus.AVAILABLE);
        bed.setLastSanitizedAt(Instant.now());
        bed.setLastSanitizedBy(principal != null ? principal.userId() : null);
        Bed saved = bedRepository.save(bed);

        auditLogService.record(principal, AuditAction.MARK_BED_READY,
                ResourceType.BED, saved.getId().toString(),
                "Marked bed " + saved.getBedNumber() + " AVAILABLE", null);

        log.info("Bed marked ready bedId={} bedNumber={} by={}",
                saved.getId(), saved.getBedNumber(), principal != null ? principal.userId() : "SYSTEM");

        return BedResponse.from(saved);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------
    private static void requireStaff(JwtPrincipal principal) {
        if (principal == null
                || !principal.hasAnyRole(Role.ADMIN, Role.DOCTOR,
                                         Role.NURSE, Role.RECEPTIONIST, Role.BILLING_ADMIN)) {
            throw new AuthorizationException("Insufficient role to access ward data");
        }
    }

    private static void requireClinicalStaff(JwtPrincipal principal) {
        if (principal == null
                || !principal.hasAnyRole(Role.ADMIN, Role.DOCTOR, Role.NURSE)) {
            throw new AuthorizationException(
                    "Only clinical staff (ADMIN/DOCTOR/NURSE) can perform this action");
        }
    }

    private static void requireNurseOrAdmin(JwtPrincipal principal) {
        if (principal == null
                || !principal.hasAnyRole(Role.ADMIN, Role.NURSE)) {
            throw new AuthorizationException(
                    "Only NURSE or ADMIN can mark a bed ready");
        }
    }
}
