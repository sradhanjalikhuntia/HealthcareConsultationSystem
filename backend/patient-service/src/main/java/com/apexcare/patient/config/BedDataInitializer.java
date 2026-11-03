package com.apexcare.patient.config;

import com.apexcare.patient.entity.AdmissionRecord;
import com.apexcare.patient.entity.AdmissionStatus;
import com.apexcare.patient.entity.Bed;
import com.apexcare.patient.entity.BedStatus;
import com.apexcare.patient.entity.Patient;
import com.apexcare.patient.entity.WardType;
import com.apexcare.patient.repository.AdmissionRecordRepository;
import com.apexcare.patient.repository.BedRepository;
import com.apexcare.patient.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Order(10)
public class BedDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BedDataInitializer.class);

    private final BedRepository bedRepository;
    private final AdmissionRecordRepository admissionRecordRepository;
    private final PatientRepository patientRepository;

    public BedDataInitializer(BedRepository bedRepository,
                              AdmissionRecordRepository admissionRecordRepository,
                              PatientRepository patientRepository) {
        this.bedRepository = bedRepository;
        this.admissionRecordRepository = admissionRecordRepository;
        this.patientRepository = patientRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (bedRepository.count() > 0) {
            log.info("Hospital beds already initialized (count={})", bedRepository.count());
            return;
        }

        log.info("Initializing hospital beds census...");

        List<Patient> patients = patientRepository.findAll();
        Patient p1 = patients.size() > 0 ? patients.get(0) : null;
        Patient p2 = patients.size() > 1 ? patients.get(1) : null;

        // General Ward (6 beds)
        createBed("GEN-101", WardType.GENERAL, BedStatus.OCCUPIED, new BigDecimal("1500.00"), p1, "Admitted for post-op observation");
        createBed("GEN-102", WardType.GENERAL, BedStatus.AVAILABLE, new BigDecimal("1500.00"), null, null);
        createBed("GEN-103", WardType.GENERAL, BedStatus.CLEANING, new BigDecimal("1500.00"), null, null);
        createBed("GEN-104", WardType.GENERAL, BedStatus.AVAILABLE, new BigDecimal("1500.00"), null, null);
        createBed("GEN-105", WardType.GENERAL, BedStatus.AVAILABLE, new BigDecimal("1500.00"), null, null);
        createBed("GEN-106", WardType.GENERAL, BedStatus.AVAILABLE, new BigDecimal("1500.00"), null, null);

        // Intensive Care Unit (4 beds)
        createBed("ICU-201", WardType.ICU, BedStatus.OCCUPIED, new BigDecimal("8500.00"), p2, "Cardiac monitoring in CCU");
        createBed("ICU-202", WardType.ICU, BedStatus.AVAILABLE, new BigDecimal("8500.00"), null, null);
        createBed("ICU-203", WardType.ICU, BedStatus.AVAILABLE, new BigDecimal("8500.00"), null, null);
        createBed("ICU-204", WardType.ICU, BedStatus.AVAILABLE, new BigDecimal("8500.00"), null, null);

        // Emergency Ward (3 beds)
        createBed("ER-01", WardType.EMERGENCY, BedStatus.AVAILABLE, new BigDecimal("4000.00"), null, null);
        createBed("ER-02", WardType.EMERGENCY, BedStatus.CLEANING, new BigDecimal("4000.00"), null, null);
        createBed("ER-03", WardType.EMERGENCY, BedStatus.AVAILABLE, new BigDecimal("4000.00"), null, null);

        // Maternity Ward (3 beds)
        createBed("MAT-301", WardType.MATERNITY, BedStatus.AVAILABLE, new BigDecimal("3500.00"), null, null);
        createBed("MAT-302", WardType.MATERNITY, BedStatus.AVAILABLE, new BigDecimal("3500.00"), null, null);
        createBed("MAT-303", WardType.MATERNITY, BedStatus.AVAILABLE, new BigDecimal("3500.00"), null, null);

        // Pediatric Ward (2 beds)
        createBed("PED-401", WardType.PEDIATRIC, BedStatus.AVAILABLE, new BigDecimal("2500.00"), null, null);
        createBed("PED-402", WardType.PEDIATRIC, BedStatus.AVAILABLE, new BigDecimal("2500.00"), null, null);

        // Isolation Ward (2 beds)
        createBed("ISO-501", WardType.ISOLATION, BedStatus.AVAILABLE, new BigDecimal("6000.00"), null, null);
        createBed("ISO-502", WardType.ISOLATION, BedStatus.AVAILABLE, new BigDecimal("6000.00"), null, null);

        log.info("Initialized 20 hospital beds across 6 wards successfully");
    }

    private void createBed(String bedNumber, WardType wardType, BedStatus status,
                           BigDecimal dailyRate, Patient patient, String reason) {
        Bed bed = new Bed();
        bed.setBedNumber(bedNumber);
        bed.setWardType(wardType);
        bed.setStatus(status);
        bed.setDailyRate(dailyRate);

        Instant now = Instant.now();
        if (status == BedStatus.OCCUPIED && patient != null) {
            bed.setCurrentPatientId(patient.getId());
            bed.setCurrentPatientName(patient.getFirstName() + " " + patient.getLastName());
            bed.setLastOccupiedAt(now);
        } else if (status == BedStatus.CLEANING) {
            bed.setLastDischargedAt(now);
        } else if (status == BedStatus.AVAILABLE) {
            bed.setLastSanitizedAt(now);
        }

        Bed savedBed = bedRepository.save(bed);

        if (status == BedStatus.OCCUPIED && patient != null) {
            AdmissionRecord record = new AdmissionRecord();
            record.setPatientId(patient.getId());
            record.setBedId(savedBed.getId());
            record.setBedNumber(savedBed.getBedNumber());
            record.setWardType(savedBed.getWardType());
            record.setPatientName(patient.getFirstName() + " " + patient.getLastName());
            record.setAdmissionReason(reason != null ? reason : "Clinical inpatient admission");
            record.setStatus(AdmissionStatus.ADMITTED);
            record.setCreatedBy(UUID.fromString("76d954d6-234e-499e-95a7-e941fa2de6f1")); // admin
            admissionRecordRepository.save(record);
        }
    }
}
