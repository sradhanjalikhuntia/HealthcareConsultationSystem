package com.apexcare.patient.service;

import com.apexcare.patient.entity.Gender;
import com.apexcare.patient.entity.Patient;
import com.apexcare.patient.entity.PatientStatus;
import com.apexcare.patient.exception.ResourceNotFoundException;
import com.apexcare.patient.repository.PatientRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exports a patient record as an HL7 FHIR R4 Bundle (type = collection).
 *
 * <p>Every resource's {@code id} matches the {@code fullUrl} in the bundle,
 * as required by FHIR. Empty/blank values are omitted rather than serialized
 * as empty strings, which would produce an invalid FHIR document.
 */
@Service
public class FhirExportService {

    private static final String ID_SYSTEM = "https://apexcare.local/patient-id";
    private static final String ALLERGY_CLINICAL_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical";
    private static final String CONDITION_CLINICAL_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/condition-clinical";

    private final PatientRepository patientRepository;

    public FhirExportService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportPatientFhir(@NonNull UUID patientId) {
        Patient p = patientRepository.findById(patientId)
                .orElseThrow(() -> ResourceNotFoundException.of("Patient", patientId));

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", UUID.randomUUID().toString());
        bundle.put("meta", Map.of("lastUpdated", Instant.now().toString()));
        bundle.put("type", "collection");
        bundle.put("timestamp", Instant.now().toString());

        List<Map<String, Object>> entries = new ArrayList<>();

        // ---- Patient ----
        entries.add(entry(toPatientResource(p)));

        // ---- AllergyIntolerance (from free-text allergies) ----
        if (StringUtils.hasText(p.getAllergies())) {
            entries.add(entry(toAllergyResource(p)));
        }

        // ---- Condition (from free-text medical history) ----
        if (StringUtils.hasText(p.getMedicalHistory())) {
            entries.add(entry(toConditionResource(p)));
        }

        bundle.put("entry", entries);
        return bundle;
    }

    // -----------------------------------------------------------------
    // Resource builders
    // -----------------------------------------------------------------

    private Map<String, Object> toPatientResource(Patient p) {
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("resourceType", "Patient");
        patient.put("id", p.getId().toString());

        // active = record is in use (i.e. not deceased / not merged into another record)
        boolean active = p.getStatus() != PatientStatus.DECEASED
                && p.getStatus() != PatientStatus.MERGED;
        patient.put("active", active);

        // identifier
        String identifierValue = StringUtils.hasText(p.getUniquePatientId())
                ? p.getUniquePatientId()
                : p.getId().toString();
        patient.put("identifier", List.of(Map.of(
                "use", "official",
                "system", ID_SYSTEM,
                "value", identifierValue
        )));

        // name
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("use", "official");
        if (StringUtils.hasText(p.getLastName()))  name.put("family", p.getLastName());
        if (StringUtils.hasText(p.getFirstName())) name.put("given", List.of(p.getFirstName()));
        patient.put("name", List.of(name));

        // gender (FHIR: male | female | other | unknown)
        patient.put("gender", toFhirGender(p.getGender()));

        // birthDate (omit if null — empty string is invalid FHIR)
        if (p.getDateOfBirth() != null) {
            patient.put("birthDate", p.getDateOfBirth().toString());
        }

        // deceased
        if (p.getStatus() == PatientStatus.DECEASED) {
            patient.put("deceasedBoolean", true);
        }

        // telecom — only include non-blank entries
        List<Map<String, Object>> telecom = new ArrayList<>();
        if (StringUtils.hasText(p.getPhone())) {
            telecom.add(Map.of("system", "phone", "value", p.getPhone()));
        }
        if (StringUtils.hasText(p.getEmail())) {
            telecom.add(Map.of("system", "email", "value", p.getEmail()));
        }
        if (!telecom.isEmpty()) {
            patient.put("telecom", telecom);
        }

        // address
        if (StringUtils.hasText(p.getAddress())) {
            patient.put("address", List.of(Map.of("text", p.getAddress())));
        }

        return patient;
    }

    private Map<String, Object> toAllergyResource(Patient p) {
        String id = UUID.randomUUID().toString();

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "AllergyIntolerance");
        resource.put("id", id);
        resource.put("clinicalStatus", Map.of(
                "coding", List.of(Map.of(
                        "system", ALLERGY_CLINICAL_SYSTEM,
                        "code", "active"
                ))
        ));
        resource.put("patient", Map.of("reference", "Patient/" + p.getId()));
        resource.put("note", List.of(Map.of("text", p.getAllergies())));
        resource.put("recordedDate", Instant.now().toString());
        return resource;
    }

    private Map<String, Object> toConditionResource(Patient p) {
        String id = UUID.randomUUID().toString();

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Condition");
        resource.put("id", id);
        resource.put("clinicalStatus", Map.of(
                "coding", List.of(Map.of(
                        "system", CONDITION_CLINICAL_SYSTEM,
                        "code", "active"
                ))
        ));
        resource.put("subject", Map.of("reference", "Patient/" + p.getId()));
        resource.put("note", List.of(Map.of("text", p.getMedicalHistory())));
        resource.put("recordedDate", Instant.now().toString());
        return resource;
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /** Wraps a resource with its fullUrl; fullUrl's UUID matches resource.id. */
    private Map<String, Object> entry(Map<String, Object> resource) {
        String id = (String) resource.get("id");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("fullUrl", "urn:uuid:" + id);
        entry.put("resource", resource);
        return entry;
    }

    private static String toFhirGender(Gender gender) {
        if (gender == null) return "unknown";
        return switch (gender) {
            case MALE   -> "male";
            case FEMALE -> "female";
            case OTHER  -> "other";
            case PREFER_NOT_TO_SAY -> "unknown";
        };
    }
}
