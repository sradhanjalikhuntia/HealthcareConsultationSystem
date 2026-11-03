package com.apexcare.appointment.repository;

import com.apexcare.appointment.entity.AmbulanceTriage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AmbulanceTriageRepository extends JpaRepository<AmbulanceTriage, UUID> {
    Page<AmbulanceTriage> findAllByOrderByDispatchedAtDesc(Pageable pageable);
}
