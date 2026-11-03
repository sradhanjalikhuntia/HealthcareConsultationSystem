package com.apexcare.appointment.service;

import com.apexcare.appointment.client.AuthClient;
import com.apexcare.appointment.client.BillingClient;
import com.apexcare.appointment.client.PatientClient;
import com.apexcare.appointment.dto.ApiResponse;
import com.apexcare.appointment.dto.AppointmentRequest;
import com.apexcare.appointment.dto.AppointmentResponse;
import com.apexcare.appointment.dto.DoctorRemote;
import com.apexcare.appointment.dto.GenerateBillRequest;
import com.apexcare.appointment.dto.PatientRemote;
import com.apexcare.appointment.dto.StatusUpdateRequest;
import com.apexcare.appointment.entity.Appointment;
import com.apexcare.appointment.entity.AppointmentStatus;
import com.apexcare.appointment.entity.Role;
import com.apexcare.appointment.exception.AppointmentConflictException;
import com.apexcare.appointment.exception.AuthorizationException;
import com.apexcare.appointment.exception.InvalidRequestException;
import com.apexcare.appointment.exception.ResourceNotFoundException;
import com.apexcare.appointment.exception.ServiceUnavailableException;
import com.apexcare.appointment.repository.AppointmentRepository;
import com.apexcare.appointment.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    /** Statuses that block a slot from being rebooked. */
    private static final List<AppointmentStatus> ACTIVE_STATUSES = List.of(
            AppointmentStatus.SCHEDULED,
            AppointmentStatus.CONFIRMED
    );

    private final AppointmentRepository repository;
    private final PatientClient patientClient;
    private final AuthClient authClient;
    private final BillingClient billingClient;
    private final BigDecimal consultationFee;

    public AppointmentService(
            AppointmentRepository repository,
            PatientClient patientClient,
            AuthClient authClient,
            BillingClient billingClient,
            @Value("${apexcare.billing.default-consultation-fee:500.00}") BigDecimal consultationFee
    ) {
        this.repository = repository;
        this.patientClient = patientClient;
        this.authClient = authClient;
        this.billingClient = billingClient;
        this.consultationFee = consultationFee;
    }

    // -----------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------
    @Transactional
    public AppointmentResponse create(AppointmentRequest request, JwtPrincipal principal) {
        requireAuthenticated(principal);

        // Authorization: patients can only book for themselves; staff (admin/receptionist) can book for anyone
        if (principal.isPatient()) {
            if (!principal.isLinkedToPatient(request.getPatientId())) {
                throw new AuthorizationException(
                        "Patients may only book appointments for themselves");
            }
        } else if (principal.isDoctor()) {
            throw new AuthorizationException("Doctors are not allowed to book appointments");
        } else {
            requireBookingRole(principal);
        }

        validateTimes(request);

        PatientRemote patient = loadPatient(request.getPatientId());
        if ("INACTIVE".equalsIgnoreCase(patient.getStatus())) {
            throw new InvalidRequestException(
                    "Cannot book an appointment for an inactive patient");
        }

        DoctorRemote doctor = loadDoctor(request.getDoctorId());
        if (!doctor.isEnabled()
                || (doctor.getRole() != null && !"DOCTOR".equalsIgnoreCase(doctor.getRole()))) {
            throw new InvalidRequestException("Selected user is not an active doctor");
        }

        assertNoOverlap(request.getDoctorId(), request.getAppointmentDate(),
                request.getStartTime(), request.getEndTime(), null);

        Appointment appointment = new Appointment();
        appointment.setPatientId(request.getPatientId());
        appointment.setUniquePatientId(patient.getUniquePatientId());
        appointment.setPatientName(displayPatientName(patient));
        appointment.setDoctorId(request.getDoctorId());
        appointment.setDoctorName(displayName(doctor));
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(request.getEndTime());
        appointment.setReason(request.getReason());
        appointment.setNotes(request.getNotes());
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setBillingGenerated(false);

        Appointment saved = repository.save(appointment);

        log.info("Appointment created id={} doctorId={} patientId={} date={}",
                saved.getId(), saved.getDoctorId(), saved.getPatientId(),
                saved.getAppointmentDate());

        tryGenerateBill(saved);

        return AppointmentResponse.from(saved);
    }

    // -----------------------------------------------------------------
    // LIST
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> list(UUID patientId,
                                          UUID doctorId,
                                          LocalDate date,
                                          AppointmentStatus status,
                                          Pageable pageable,
                                          JwtPrincipal principal) {
        requireAuthenticated(principal);

        UUID scopedPatient = patientId;
        UUID scopedDoctor = doctorId;

        if (principal.isPatient()) {
            if (principal.linkedPatientId() == null) {
                throw new AuthorizationException(
                        "Patient account is not linked to a medical record");
            }
            scopedPatient = principal.linkedPatientId();
        } else if (principal.isDoctor()) {
            scopedDoctor = principal.userId();
        } else {
            requireStaff(principal);   // ADMIN / RECEPTIONIST / NURSE
        }

        return repository.search(scopedPatient, scopedDoctor, date, status, pageable)
                .map(AppointmentResponse::from);
    }

    // -----------------------------------------------------------------
    // GET
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id, JwtPrincipal principal) {
        requireAuthenticated(principal);
        Appointment appointment = find(id);
        assertCanView(principal, appointment);
        return AppointmentResponse.from(appointment);
    }

    // -----------------------------------------------------------------
    // RESCHEDULE
    // -----------------------------------------------------------------
    @Transactional
    public AppointmentResponse reschedule(UUID id,
                                          AppointmentRequest request,
                                          JwtPrincipal principal) {
        requireAuthenticated(principal);
        Appointment appointment = find(id);

        if (principal.isPatient()) {
            if (!principal.isLinkedToPatient(appointment.getPatientId())) {
                throw new AuthorizationException(
                        "Patients may only reschedule their own appointments");
            }
        } else {
            requireBookingRole(principal);
        }

        validateTimes(request);

        if (appointment.getStatus().isTerminal()) {
            throw new InvalidRequestException(
                    "Cannot reschedule a " + appointment.getStatus() + " appointment");
        }

        if (!appointment.getPatientId().equals(request.getPatientId())) {
            throw new InvalidRequestException(
                    "Patient cannot be changed during reschedule");
        }

        // Reload patient and doctor to refresh snapshots
        PatientRemote patient = loadPatient(request.getPatientId());
        DoctorRemote doctor = loadDoctor(request.getDoctorId());

        // Overlap check must exclude the current appointment
        assertNoOverlap(request.getDoctorId(), request.getAppointmentDate(),
                request.getStartTime(), request.getEndTime(), id);

        // Preserve CONFIRMED status across reschedule; other actives restart as SCHEDULED
        AppointmentStatus nextStatus = appointment.getStatus() == AppointmentStatus.CONFIRMED
                ? AppointmentStatus.CONFIRMED
                : AppointmentStatus.SCHEDULED;

        appointment.setDoctorId(request.getDoctorId());
        appointment.setDoctorName(displayName(doctor));
        appointment.setUniquePatientId(patient.getUniquePatientId());
        appointment.setPatientName(displayPatientName(patient));
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(request.getEndTime());
        appointment.setReason(request.getReason());
        appointment.setNotes(request.getNotes());
        appointment.setStatus(nextStatus);

        log.info("Appointment rescheduled id={} newDate={} newStart={}",
                id, request.getAppointmentDate(), request.getStartTime());

        return AppointmentResponse.from(repository.save(appointment));
    }

    // -----------------------------------------------------------------
    // CANCEL
    // -----------------------------------------------------------------
    @Transactional
    public AppointmentResponse cancel(UUID id, JwtPrincipal principal) {
        requireAuthenticated(principal);
        Appointment appointment = find(id);

        if (principal.isPatient()) {
            if (!principal.isLinkedToPatient(appointment.getPatientId())) {
                throw new AuthorizationException(
                        "Patients may only cancel their own appointments");
            }
        } else {
            requireBookingRole(principal);
        }

        // Enforce state machine
        appointment.getStatus().requireTransitionTo(AppointmentStatus.CANCELLED);
        appointment.setStatus(AppointmentStatus.CANCELLED);

        log.info("Appointment cancelled id={} by={}", id, principal.userId());
        return AppointmentResponse.from(repository.save(appointment));
    }

    // -----------------------------------------------------------------
    // UPDATE STATUS
    // -----------------------------------------------------------------
    @Transactional
    public AppointmentResponse updateStatus(UUID id,
                                            StatusUpdateRequest request,
                                            JwtPrincipal principal) {
        requireAuthenticated(principal);
        Appointment appointment = find(id);

        // Doctors may only touch their own appointments
        if (principal.isDoctor()
                && !principal.userId().equals(appointment.getDoctorId())) {
            throw new AuthorizationException(
                    "Doctors may only update their own appointments");
        }

        // Patients cannot change status directly
        if (principal.isPatient()) {
            throw new AuthorizationException(
                    "Patients cannot change appointment status");
        }

        // Staff-only past this point
        requireBookingRole(principal);

        AppointmentStatus target = request.getStatus();
        appointment.getStatus().requireTransitionTo(target);
        appointment.setStatus(target);

        if (request.getNotes() != null) {
            appointment.setNotes(request.getNotes());
        }

        log.info("Appointment status updated id={} from={} to={} by={}",
                id, appointment.getStatus(), target, principal.userId());

        return AppointmentResponse.from(repository.save(appointment));
    }

    // -----------------------------------------------------------------
    // AVAILABILITY
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<AppointmentResponse> availability(UUID doctorId,
                                                  LocalDate date,
                                                  JwtPrincipal principal) {
        requireAuthenticated(principal);

        return repository
                .findByDoctorIdAndAppointmentDateAndStatusNotInOrderByStartTimeAsc(
                        doctorId, date,
                        List.of(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW))
                .stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    // -----------------------------------------------------------------
    // STATS
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public Map<String, Object> stats(JwtPrincipal principal) {
        requireAuthenticated(principal);

        if (principal.isPatient()) {
            throw new AuthorizationException("Insufficient role");
        }

        LocalDate today = LocalDate.now();

        long hospitalTotal = repository.count();
        long hospitalScheduled = repository.countByStatus(AppointmentStatus.SCHEDULED);
        long hospitalToday = repository.countByAppointmentDate(today);
        long hospitalCompleted = repository.countByStatus(AppointmentStatus.COMPLETED);
        long hospitalCancelled = repository.countByStatus(AppointmentStatus.CANCELLED);

        long doctorTotal = principal.isDoctor() ? repository.countByDoctorId(principal.userId()) : hospitalTotal;
        long doctorScheduled = principal.isDoctor() ? repository.countByDoctorIdAndStatus(principal.userId(), AppointmentStatus.SCHEDULED) : hospitalScheduled;
        long doctorToday = principal.isDoctor() ? repository.countByDoctorIdAndAppointmentDate(principal.userId(), today) : hospitalToday;
        long doctorCompleted = principal.isDoctor() ? repository.countByDoctorIdAndStatus(principal.userId(), AppointmentStatus.COMPLETED) : hospitalCompleted;
        long doctorCancelled = principal.isDoctor() ? repository.countByDoctorIdAndStatus(principal.userId(), AppointmentStatus.CANCELLED) : hospitalCancelled;

        Map<String, Object> stats = new LinkedHashMap<>();
        // Total consultations: hospital-wide as labeled on dashboard
        stats.put("totalCount", hospitalTotal);
        stats.put("totalAppointments", hospitalTotal);
        stats.put("hospitalTotalCount", hospitalTotal);
        stats.put("doctorTotalCount", doctorTotal);

        // Scheduled queue / Today's queue
        long effectiveScheduled = (principal.isDoctor() && doctorScheduled > 0) ? doctorScheduled : hospitalScheduled;
        long effectiveToday = (principal.isDoctor() && doctorToday > 0) ? doctorToday : hospitalToday;
        stats.put("scheduledCount", effectiveScheduled);
        stats.put("todayAppointments", effectiveToday);
        stats.put("todayCount", effectiveToday);
        stats.put("doctorScheduledCount", doctorScheduled);
        stats.put("doctorTodayAppointments", doctorToday);
        stats.put("hospitalScheduledCount", hospitalScheduled);

        // Completed visits
        long effectiveCompleted = principal.isDoctor() ? doctorCompleted : hospitalCompleted;
        stats.put("completedCount", effectiveCompleted);
        stats.put("completedConsultations", effectiveCompleted);
        stats.put("doctorCompletedCount", doctorCompleted);
        stats.put("hospitalCompletedCount", hospitalCompleted);

        stats.put("cancelledCount", principal.isDoctor() ? doctorCancelled : hospitalCancelled);
        return stats;
    }

    // -----------------------------------------------------------------
    // BILLING (best-effort — never breaks the main flow)
    // -----------------------------------------------------------------
    private void tryGenerateBill(Appointment saved) {
        try {
            GenerateBillRequest bill = new GenerateBillRequest();
            bill.setAppointmentId(saved.getId());
            bill.setPatientId(saved.getPatientId());
            bill.setUniquePatientId(saved.getUniquePatientId());
            bill.setConsultationFee(consultationFee);

            ApiResponse<Object> response = billingClient.generateFromAppointment(bill);
            if (response != null && response.isSuccess()) {
                saved.setBillingGenerated(true);
                saved.setBillingGeneratedAt(java.time.Instant.now());
                repository.save(saved);
                log.info("Bill generated for appointment id={}", saved.getId());
            } else {
                log.warn("Billing returned unsuccessful for appointment id={}",
                        saved.getId());
            }
        } catch (Exception ex) {
            // Billing is best-effort: appointment remains valid, retry job will pick it up.
            log.warn("Billing call failed for appointment id={} (will retry via job): {}",
                    saved.getId(), ex.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // REMOTE LOADERS — fail loud, never fabricate data
    // -----------------------------------------------------------------
    private PatientRemote loadPatient(UUID patientId) {
        try {
            ApiResponse<PatientRemote> response = patientClient.getPatient(patientId);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
            throw new ResourceNotFoundException("Patient", patientId);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to load patient id={}", patientId, ex);
            throw new ServiceUnavailableException(
                    "Patient Service is unavailable. Cannot validate patient right now.");
        }
    }

    private DoctorRemote loadDoctor(UUID doctorId) {
        try {
            ApiResponse<DoctorRemote> response = authClient.getDoctor(doctorId);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
            throw new ResourceNotFoundException("Doctor", doctorId);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to load doctor id={}", doctorId, ex);
            throw new ServiceUnavailableException(
                    "Auth Service is unavailable. Cannot validate doctor right now.");
        }
    }

    // -----------------------------------------------------------------
    // OVERLAP + VALIDATION
    // -----------------------------------------------------------------
    private void assertNoOverlap(UUID doctorId,
                                 LocalDate date,
                                 LocalTime start,
                                 LocalTime end,
                                 UUID excludeId) {
        long overlaps = repository.countOverlaps(doctorId, date, start, end,
                ACTIVE_STATUSES, excludeId);
        if (overlaps > 0) {
            throw new AppointmentConflictException(
                    "Appointment time overlaps with an existing appointment");
        }
    }

    private void validateTimes(AppointmentRequest request) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new InvalidRequestException("Start time and end time are required");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new InvalidRequestException("End time must be after start time");
        }
        if (request.getAppointmentDate() == null) {
            throw new InvalidRequestException("Appointment date is required");
        }
        LocalDate today = LocalDate.now();
        if (request.getAppointmentDate().isBefore(today)) {
            throw new InvalidRequestException("Cannot book or reschedule an appointment for a past date");
        }
        if (request.getAppointmentDate().isEqual(today)) {
            LocalTime now = LocalTime.now();
            if (request.getStartTime().isBefore(now)) {
                throw new InvalidRequestException("Cannot book or reschedule an appointment for a past time slot");
            }
        }
    }

    private Appointment find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Appointment", id));
    }

    // -----------------------------------------------------------------
    // AUTHORIZATION HELPERS
    // -----------------------------------------------------------------
    private static void requireAuthenticated(JwtPrincipal principal) {
        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }
    }

    /** ADMIN / RECEPTIONIST / DOCTOR — may book for others. */
    private static void requireBookingRole(JwtPrincipal principal) {
        if (!principal.hasAnyRole(Role.ADMIN, Role.RECEPTIONIST, Role.DOCTOR)) {
            throw new AuthorizationException(
                    "Insufficient role to manage appointments");
        }
    }

    /** ADMIN / RECEPTIONIST / DOCTOR / NURSE / BILLING_ADMIN — staff-only actions. */
    private static void requireStaff(JwtPrincipal principal) {
        if (!principal.hasAnyRole(Role.ADMIN, Role.RECEPTIONIST, Role.DOCTOR, Role.NURSE, Role.BILLING_ADMIN)) {
            throw new AuthorizationException("Insufficient role");
        }
    }

    private void assertCanView(JwtPrincipal principal, Appointment appointment) {
        if (principal.isPatient()
                && !principal.isLinkedToPatient(appointment.getPatientId())) {
            // 404 instead of 403 — do not leak that the appointment exists
            throw ResourceNotFoundException.of("Appointment", appointment.getId());
        }
        if (principal.isDoctor()
                && !principal.userId().equals(appointment.getDoctorId())) {
            throw ResourceNotFoundException.of("Appointment", appointment.getId());
        }
    }

    // -----------------------------------------------------------------
    // DISPLAY HELPERS
    // -----------------------------------------------------------------
    private String displayPatientName(PatientRemote patient) {
        String first = patient.getFirstName() == null ? "" : patient.getFirstName();
        String last  = patient.getLastName()  == null ? "" : patient.getLastName();
        String name = (first + " " + last).trim();
        return name.isBlank() ? "Patient" : name;
    }

    private String displayName(DoctorRemote doctor) {
        String first = doctor.getFirstName() == null ? "" : doctor.getFirstName();
        String last  = doctor.getLastName()  == null ? "" : doctor.getLastName();
        String name = (first + " " + last).trim();
        return name.isBlank() ? "Doctor" : name;
    }
}
