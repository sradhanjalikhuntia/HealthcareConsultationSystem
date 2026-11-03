package com.apexcare.patient.repository;

import com.apexcare.patient.entity.Bed;
import com.apexcare.patient.entity.BedStatus;
import com.apexcare.patient.entity.WardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BedRepository extends JpaRepository<Bed, UUID> {

    List<Bed> findByWardType(WardType wardType);

    List<Bed> findByStatus(BedStatus status);

    List<Bed> findByWardTypeAndStatus(WardType wardType, BedStatus status);
    Page<Bed> findByWardType(WardType wardType, Pageable pageable);
    Page<Bed> findByStatus(BedStatus status, Pageable pageable);
    Page<Bed> findByWardTypeAndStatus(WardType wardType, BedStatus status, Pageable pageable);

    Optional<Bed> findByBedNumber(String bedNumber);

    boolean existsByBedNumber(String bedNumber);

    boolean existsByCurrentPatientIdAndStatus(UUID currentPatientId, BedStatus status);

    long countByWardTypeAndStatus(WardType wardType, BedStatus status);

    long countByStatus(BedStatus status);

    long countByWardType(WardType wardType);
}
