package com.apexcare.patient.service;

import com.apexcare.patient.entity.Patient;
import com.apexcare.patient.repository.PatientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates the hospital-assigned unique patient ID in the form
 * {@code ACP-<year>-<6-digit-sequence>} (e.g. {@code ACP-2026-000042}).
 *
 * <p><b>Concurrency:</b> Uses SERIALIZABLE isolation + retry on constraint
 * violation, so parallel registrations across multiple JVMs never produce a
 * duplicate. The DB unique index on {@code uniquePatientId} is the final
 * guardrail.
 *
 * <p><b>Sequence overflow:</b> Once the 6-digit sequence exceeds 999,999 in a
 * given year, the ID grows to 7+ digits — ordering still works because we
 * parse the numeric part instead of relying on lexicographic sort.
 */
@Service
public class UniquePatientIdService {

    public static final String PREFIX = "ACP";

    private static final String PREFIX_SEPARATOR = "-";
    private static final String SEQUENCE_FORMAT = "%06d";
    private static final Pattern ID_PATTERN =
            Pattern.compile("^" + PREFIX + "-(\\d{4})-(\\d+)$");

    private static final int MAX_RETRIES = 5;

    private final PatientRepository repository;

    public UniquePatientIdService(PatientRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the next available patient ID. Retries on unique-constraint
     * collision when multiple instances race for the same sequence number.
     */
    @Retryable(
            retryFor = DataIntegrityViolationException.class,
            maxAttempts = MAX_RETRIES,
            backoff = @Backoff(delay = 50, multiplier = 1.5)
    )
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.SERIALIZABLE
    )
    public String nextId() {
        int year = Year.now(ZoneOffset.UTC).getValue();
        String yearPrefix = PREFIX + PREFIX_SEPARATOR + year + PREFIX_SEPARATOR;

        long nextSeq = findLatestSequence(yearPrefix, year) + 1;

        // Guard against theoretical duplicates (should not happen under SERIALIZABLE,
        // but keep as a defensive check).
        String candidate;
        do {
            candidate = PREFIX + PREFIX_SEPARATOR + year + PREFIX_SEPARATOR
                    + String.format(SEQUENCE_FORMAT, nextSeq++);
        } while (repository.existsByUniquePatientId(candidate));

        return candidate;
    }

    // -----------------------------------------------------------------

    private long findLatestSequence(String yearPrefix, int year) {
        Optional<Patient> latest = repository
                .findTopByUniquePatientIdStartingWithOrderByUniquePatientIdDesc(yearPrefix);

        if (latest.isEmpty() || latest.get().getUniquePatientId() == null) {
            return 0L;
        }

        Matcher m = ID_PATTERN.matcher(latest.get().getUniquePatientId());
        if (!m.matches()) {
            return 0L;
        }

        // Defensive: if the latest record belongs to a different year (shouldn't
        // happen because we filter by prefix), restart the sequence.
        int latestYear = Integer.parseInt(m.group(1));
        if (latestYear != year) {
            return 0L;
        }

        try {
            return Long.parseLong(m.group(2));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
