package com.apexcare.appointment.repository;

import com.apexcare.appointment.entity.Appointment;
import com.apexcare.appointment.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    // -----------------------------------------------------------------
    // Patient / doctor views
    // -----------------------------------------------------------------

    Page<Appointment> findByPatientId(UUID patientId, Pageable pageable);

    Page<Appointment> findByDoctorId(UUID doctorId, Pageable pageable);

    Page<Appointment> findByPatientIdAndStatus(UUID patientId,
                                               AppointmentStatus status,
                                               Pageable pageable);

    Page<Appointment> findByDoctorIdAndStatus(UUID doctorId,
                                              AppointmentStatus status,
                                              Pageable pageable);

    // -----------------------------------------------------------------
    // Doctor daily schedule
    // -----------------------------------------------------------------

    List<Appointment> findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(
            UUID doctorId, LocalDate appointmentDate);

    List<Appointment> findByDoctorIdAndAppointmentDateAndStatusNotInOrderByStartTimeAsc(
            UUID doctorId, LocalDate appointmentDate, List<AppointmentStatus> statuses);

    List<Appointment> findByAppointmentDateOrderByStartTimeAsc(LocalDate appointmentDate);

    // -----------------------------------------------------------------
    // Counts / metrics
    // -----------------------------------------------------------------

    long countByAppointmentDate(LocalDate date);

    long countByDoctorIdAndAppointmentDate(UUID doctorId, LocalDate date);

    long countByStatus(AppointmentStatus status);

    long countByPatientIdAndStatus(UUID patientId, AppointmentStatus status);

    long countByDoctorIdAndStatus(UUID doctorId, AppointmentStatus status);

    long countByDoctorId(UUID doctorId);

    long countByDoctorIdAndAppointmentDateAndStatus(UUID doctorId, LocalDate date, AppointmentStatus status);

    long countByAppointmentDateAndStatus(LocalDate date, AppointmentStatus status);

    // -----------------------------------------------------------------
    // Lookup
    // -----------------------------------------------------------------

    Optional<Appointment> findByIdAndPatientId(UUID id, UUID patientId);

    Optional<Appointment> findByIdAndDoctorId(UUID id, UUID doctorId);

    // -----------------------------------------------------------------
    // Search — dynamic filters, all optional
    // -----------------------------------------------------------------

    @Query("""
            SELECT a FROM Appointment a
            WHERE (:patientId IS NULL OR a.patientId = :patientId)
              AND (:doctorId  IS NULL OR a.doctorId  = :doctorId)
              AND (:date      IS NULL OR a.appointmentDate = :date)
              AND (:status    IS NULL OR a.status = :status)
            """)
    Page<Appointment> search(@Param("patientId") UUID patientId,
                             @Param("doctorId")  UUID doctorId,
                             @Param("date")      LocalDate date,
                             @Param("status")    AppointmentStatus status,
                             Pageable pageable);

    // -----------------------------------------------------------------
    // Overlap detection — used before insert / reschedule
    // -----------------------------------------------------------------

    /**
     * Counts active appointments for the given doctor that overlap with
     * the proposed time window {@code [startTime, endTime)}.
     *
     * <p>Uses the standard interval-overlap predicate:
     * {@code existing.start < new.end AND existing.end > new.start}.
     *
     * <p>Pass {@code excludeId} to ignore the current appointment when
     * rescheduling.
     */
    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.doctorId = :doctorId
              AND a.appointmentDate = :date
              AND a.status NOT IN :excludedStatuses
              AND (:excludeId IS NULL OR a.id <> :excludeId)
              AND a.startTime < :endTime
              AND a.endTime   > :startTime
            """)
    long countOverlaps(@Param("doctorId")         UUID doctorId,
                       @Param("date")             LocalDate date,
                       @Param("startTime")        LocalTime startTime,
                       @Param("endTime")          LocalTime endTime,
                       @Param("excludedStatuses") List<AppointmentStatus> excludedStatuses,
                       @Param("excludeId")        UUID excludeId);
}
