package com.apexcare.appointment.service;

import com.apexcare.appointment.dto.QueueTokenDto;
import com.apexcare.appointment.entity.Appointment;
import com.apexcare.appointment.entity.AppointmentStatus;
import com.apexcare.appointment.exception.AuthorizationException;
import com.apexcare.appointment.exception.ResourceNotFoundException;
import com.apexcare.appointment.repository.AppointmentRepository;
import com.apexcare.appointment.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    /** Average consultation duration used for wait-time estimation. */
    private static final int AVG_CONSULTATION_MINUTES = 15;

    private final AppointmentRepository repository;

    public QueueService(AppointmentRepository repository) {
        this.repository = repository;
    }

    // -----------------------------------------------------------------
    // HOSPITAL-WIDE QUEUE
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<QueueTokenDto> getHospitalLiveQueue(JwtPrincipal principal) {
        requireStaff(principal);

        LocalDate today = LocalDate.now();
        List<Appointment> appts = repository
                .findByAppointmentDateOrderByStartTimeAsc(today);

        return mapToTokens(appts);
    }

    // -----------------------------------------------------------------
    // PER-DOCTOR QUEUE
    // -----------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<QueueTokenDto> getDoctorQueue(UUID doctorId, JwtPrincipal principal) {
        requireStaff(principal);

        LocalDate today = LocalDate.now();
        List<Appointment> appts = repository
                .findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(doctorId, today);

        return mapToTokens(appts);
    }

    // -----------------------------------------------------------------
    // CALL NEXT PATIENT — persists CALLED state to DB
    // -----------------------------------------------------------------
    @Transactional
    public Optional<QueueTokenDto> callNextPatient(UUID doctorId, JwtPrincipal principal) {
        requireDoctorOrStaff(principal, doctorId);

        LocalDate today = LocalDate.now();

        List<Appointment> candidates = repository
                .findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(doctorId, today)
                .stream()
                .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED
                          || a.getStatus() == AppointmentStatus.CONFIRMED
                          || a.getStatus() == AppointmentStatus.CHECKED_IN)
                .filter(a -> a.getCalledAt() == null)
                .toList();

        if (candidates.isEmpty()) {
            log.info("No patients waiting for doctorId={}", doctorId);
            return Optional.empty();
        }

        Appointment next = candidates.get(0);

        // Enforce state machine
        next.getStatus().requireTransitionTo(AppointmentStatus.CALLED);
        next.setStatus(AppointmentStatus.CALLED);
        next.setCalledAt(Instant.now());
        Appointment saved = repository.save(next);

        log.info("Called next patient appointmentId={} doctorId={} by={}",
                saved.getId(), doctorId, principal.userId());

        // Rebuild queue AFTER persisting so the new CALLED state is reflected
        List<QueueTokenDto> queue = mapToTokens(repository
                .findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(doctorId, today));

        return queue.stream()
                .filter(t -> t.getAppointmentId().equals(saved.getId()))
                .findFirst();
    }

    // -----------------------------------------------------------------
    // UPDATE TOKEN STATUS — persists to DB
    // -----------------------------------------------------------------
    @Transactional
    public QueueTokenDto updateTokenStatus(UUID appointmentId,
                                           AppointmentStatus newStatus,
                                           JwtPrincipal principal) {
        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }

        Appointment appointment = repository.findById(appointmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Appointment", appointmentId));

        // Doctors can only manage their own queue
        if (principal.isDoctor()
                && !principal.userId().equals(appointment.getDoctorId())) {
            throw new AuthorizationException(
                    "Doctors may only update their own queue");
        }
        // Patients cannot change queue status
        if (principal.isPatient()) {
            throw new AuthorizationException(
                    "Patients cannot change queue status");
        }

        appointment.getStatus().requireTransitionTo(newStatus);
        appointment.setStatus(newStatus);

        if (newStatus == AppointmentStatus.CALLED && appointment.getCalledAt() == null) {
            appointment.setCalledAt(Instant.now());
        }

        Appointment saved = repository.save(appointment);

        log.info("Queue status updated appointmentId={} status={} by={}",
                saved.getId(), newStatus, principal.userId());

        // Return the updated token DTO (rebuild from current day's queue)
        LocalDate date = saved.getAppointmentDate();
        List<Appointment> queue = repository
                .findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(
                        saved.getDoctorId(), date);

        return mapToTokens(queue).stream()
                .filter(t -> t.getAppointmentId().equals(saved.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment disappeared from queue after update"));
    }

    // =================================================================
    // MAPPING
    // =================================================================
    private List<QueueTokenDto> mapToTokens(List<Appointment> appts) {
        int position = 0;
        int cumulativeWait = 0;

        List<QueueTokenDto> tokens = new java.util.ArrayList<>(appts.size());

        for (Appointment a : appts) {
            // Skip appointments that are not part of the active queue
            if (a.getStatus() == AppointmentStatus.CANCELLED
                    || a.getStatus() == AppointmentStatus.NO_SHOW) {
                continue;
            }

            position++;

            QueueTokenDto t = new QueueTokenDto();
            t.setTokenNumber(String.format("TK-%02d", position));
            t.setAppointmentId(a.getId());
            t.setPatientId(a.getPatientId());

            // Use the REAL stored patient name (snapshot from booking)
            t.setPatientName(a.getPatientName() != null
                    ? a.getPatientName()
                    : "Patient " + a.getPatientId().toString().substring(0, 8));

            t.setDoctorId(a.getDoctorId());
            t.setDoctorName(a.getDoctorName() != null
                    ? a.getDoctorName()
                    : "Doctor");

            t.setSlotTime((a.getStartTime() != null ? a.getStartTime() : java.time.LocalTime.of(10, 0))
                    + " - "
                    + (a.getEndTime() != null ? a.getEndTime() : java.time.LocalTime.of(10, 30)));

            // Status derived strictly from persisted appointment state
            t.setStatus(mapStatus(a.getStatus()));

            // Estimated wait: 0 for terminal / called states, else cumulative
            if (a.getStatus() == AppointmentStatus.CALLED
                    || a.getStatus() == AppointmentStatus.IN_PROGRESS
                    || a.getStatus().isTerminal()) {
                t.setEstimatedWaitMinutes(0);
            } else {
                t.setEstimatedWaitMinutes(cumulativeWait);
                cumulativeWait += AVG_CONSULTATION_MINUTES;
            }

            t.setCalledAt(a.getCalledAt());

            // Position-based room assignment (deterministic, same for all callers)
            t.setRoomNumber(roomForPosition(position));

            // Waiting duration for UI
            if (a.getCalledAt() != null) {
                t.setWaitingSinceMinutes((int) Duration.between(
                        a.getCalledAt(), Instant.now()).toMinutes());
            }

            tokens.add(t);
        }

        return tokens;
    }

    private static com.apexcare.appointment.dto.QueueStatus mapStatus(AppointmentStatus status) {
        return switch (status) {
            case SCHEDULED, CONFIRMED, CHECKED_IN -> com.apexcare.appointment.dto.QueueStatus.WAITING;
            case CALLED -> com.apexcare.appointment.dto.QueueStatus.CALLED;
            case IN_PROGRESS -> com.apexcare.appointment.dto.QueueStatus.IN_PROGRESS;
            case COMPLETED -> com.apexcare.appointment.dto.QueueStatus.COMPLETED;
            case CANCELLED -> com.apexcare.appointment.dto.QueueStatus.SKIPPED;
            case NO_SHOW -> com.apexcare.appointment.dto.QueueStatus.NO_SHOW;
        };
    }

    private static String roomForPosition(int position) {
        // 5 rooms cycled deterministically by queue position
        int room = 101 + ((position - 1) % 5);
        return "Room " + room;
    }

    // =================================================================
    // AUTHORIZATION
    // =================================================================
    private static void requireStaff(JwtPrincipal principal) {
        if (principal == null) {
            throw new AuthorizationException("Authentication required");
        }
        if (principal.isPatient()) {
            throw new AuthorizationException(
                    "Only clinical staff can view the live queue");
        }
    }

    private static void requireDoctorOrStaff(JwtPrincipal principal, UUID doctorId) {
        requireStaff(principal);
        if (principal.isDoctor() && !principal.userId().equals(doctorId)) {
            throw new AuthorizationException(
                    "Doctors may only manage their own queue");
        }
    }
}
